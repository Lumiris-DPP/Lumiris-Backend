package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.EmailSuppression;
import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.RepairerProspectOutreach;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.EmailSuppressionRepository;
import com.minoh.lumiris_backend.repository.RepairerProfileRepository;
import com.minoh.lumiris_backend.repository.RepairerProspectOutreachRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairerProspectingServiceTest {

    @Mock private RepairerProfileRepository repairerRepo;
    @Mock private RepairerProspectOutreachRepository outreachRepo;
    @Mock private EmailSuppressionRepository suppressionRepo;
    @Mock private RepairerClaimService claimService;
    @Mock private MailService mailService;

    private RepairerProspectingService service;

    private RepairerProfile profile;
    private final UUID profileId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RepairerProspectingService(
                repairerRepo, outreachRepo, suppressionRepo, claimService, mailService);
        ReflectionTestUtils.setField(service, "repairerAppUrl", "https://atelier.lumiris.fr");
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://api.lumiris.fr");

        profile = new RepairerProfile();
        profile.setId(profileId);
        profile.setDisplayName("Retouche Bastille");
    }

    @Test
    void invite_issuesTokenSavesOutreachAndSendsEmail() {
        when(suppressionRepo.existsByEmailIgnoreCase("pro@atelier.fr")).thenReturn(false);
        when(repairerRepo.findById(profileId)).thenReturn(Optional.of(profile));
        when(claimService.issueClaimToken(profileId, "pro@atelier.fr")).thenReturn(UUID.randomUUID());
        when(outreachRepo.save(any())).thenAnswer(i -> {
            RepairerProspectOutreach o = i.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        service.invite(profileId, "  PRO@Atelier.fr ");

        verify(claimService).issueClaimToken(profileId, "pro@atelier.fr");
        verify(mailService).sendRepairerProspecting(
                eq("pro@atelier.fr"), eq("Retouche Bastille"), anyString(), anyString(), anyString());
    }

    @Test
    void invite_rejectsASuppressedAddress() {
        when(suppressionRepo.existsByEmailIgnoreCase("pro@atelier.fr")).thenReturn(true);

        assertThatThrownBy(() -> service.invite(profileId, "pro@atelier.fr"))
                .isInstanceOf(ConflictException.class);
        verify(claimService, never()).issueClaimToken(any(), any());
    }

    @Test
    void invite_rejectsABlankAddress() {
        assertThatThrownBy(() -> service.invite(profileId, "   "))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void recordClick_stampsClickedAtAndReturnsTheLandingUrl() {
        UUID outreachId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        RepairerProspectOutreach outreach = new RepairerProspectOutreach();
        outreach.setToken(token);
        when(outreachRepo.findById(outreachId)).thenReturn(Optional.of(outreach));

        String url = service.recordClick(outreachId);

        assertThat(outreach.getClickedAt()).isNotNull();
        assertThat(url).isEqualTo("https://atelier.lumiris.fr/retoucheurs/reclamer?token=" + token);
    }

    @Test
    void followUp_reSendsAndBumpsContactCount() {
        UUID outreachId = UUID.randomUUID();
        RepairerProspectOutreach outreach = new RepairerProspectOutreach();
        outreach.setEmail("pro@atelier.fr");
        outreach.setToken(UUID.randomUUID());
        outreach.setRepairerProfile(profile);
        outreach.setContactCount(1);
        when(outreachRepo.findById(outreachId)).thenReturn(Optional.of(outreach));
        when(suppressionRepo.existsByEmailIgnoreCase("pro@atelier.fr")).thenReturn(false);

        service.followUp(outreachId);

        assertThat(outreach.getContactCount()).isEqualTo(2);
        assertThat(outreach.getLastContactedAt()).isNotNull();
        verify(mailService).sendRepairerProspecting(eq("pro@atelier.fr"), any(), anyString(), anyString(), anyString());
    }

    @Test
    void followUp_skipsAnAlreadyClaimedOrUnsubscribedProspect() {
        UUID outreachId = UUID.randomUUID();
        RepairerProspectOutreach outreach = new RepairerProspectOutreach();
        outreach.setClaimedAt(java.time.Instant.now());
        when(outreachRepo.findById(outreachId)).thenReturn(Optional.of(outreach));

        service.followUp(outreachId);

        verify(mailService, never()).sendRepairerProspecting(any(), any(), any(), any(), any());
    }

    @Test
    void unsubscribe_recordsSuppressionAndMarksOutreach() {
        UUID outreachId = UUID.randomUUID();
        RepairerProspectOutreach outreach = new RepairerProspectOutreach();
        outreach.setEmail("pro@atelier.fr");
        when(outreachRepo.findById(outreachId)).thenReturn(Optional.of(outreach));
        when(suppressionRepo.existsByEmailIgnoreCase("pro@atelier.fr")).thenReturn(false);

        service.unsubscribe(outreachId);

        assertThat(outreach.getUnsubscribedAt()).isNotNull();
        verify(suppressionRepo).save(any(EmailSuppression.class));
    }

    @Test
    void unsubscribe_unknownLink_is404() {
        UUID outreachId = UUID.randomUUID();
        when(outreachRepo.findById(outreachId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unsubscribe(outreachId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
