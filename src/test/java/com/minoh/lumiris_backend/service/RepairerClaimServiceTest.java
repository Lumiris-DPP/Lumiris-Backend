package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.RepairerSource;
import com.minoh.lumiris_backend.entity.RepairerStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.exception.ConflictException;
import com.minoh.lumiris_backend.exception.ResourceNotFoundException;
import com.minoh.lumiris_backend.repository.RepairerProfileRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairerClaimServiceTest {

    @Mock private RepairerProfileRepository repairerRepo;
    @Mock private UserRepository userRepo;
    @Mock private RepairerOnboardingService onboardingService;

    private RepairerClaimService service;

    private User user;
    private RepairerProfile listing;
    private final UUID token = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RepairerClaimService(repairerRepo, userRepo, onboardingService);

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("bob@atelier.fr");

        listing = new RepairerProfile();
        listing.setId(UUID.randomUUID());
        listing.setDisplayName("Retouche Bastille");
        listing.setSource(RepairerSource.CMA);
        listing.setStatus(RepairerStatus.VERIFIED); // fiche annuaire curée, sans compte
        listing.setClaimToken(token);

        lenient().when(userRepo.getByEmail("bob@atelier.fr")).thenReturn(user);
        lenient().when(repairerRepo.findByUser(user)).thenReturn(Optional.empty());
        lenient().when(repairerRepo.findByClaimToken(token)).thenReturn(Optional.of(listing));
        lenient().when(repairerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void resolveToken_returnsPrefilledPreview() {
        var preview = service.resolveToken(token);
        assertThat(preview.displayName()).isEqualTo("Retouche Bastille");
    }

    @Test
    void claim_linksTheExistingListing_withoutCreatingADuplicate() {
        service.claim("bob@atelier.fr", token);

        assertThat(listing.getUser()).isSameAs(user);
        assertThat(listing.getStatus()).isEqualTo(RepairerStatus.PENDING);
        assertThat(listing.getClaimedAt()).isNotNull();
        assertThat(listing.getClaimToken()).isNull(); // jeton consommé
    }

    @Test
    void claim_rejectsIfAccountAlreadyHasAProfile() {
        when(repairerRepo.findByUser(user)).thenReturn(Optional.of(new RepairerProfile()));

        assertThatThrownBy(() -> service.claim("bob@atelier.fr", token))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void claim_rejectsAnAlreadyUsedOrUnknownToken() {
        when(repairerRepo.findByClaimToken(token)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.claim("bob@atelier.fr", token))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void claim_rejectsATokenOnAnAlreadyClaimedListing() {
        listing.setUser(new User());

        assertThatThrownBy(() -> service.claim("bob@atelier.fr", token))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void issueClaimToken_setsATokenOnAnUnclaimedListing() {
        when(repairerRepo.findById(listing.getId())).thenReturn(Optional.of(listing));
        listing.setClaimToken(null);

        UUID issued = service.issueClaimToken(listing.getId());

        assertThat(issued).isNotNull();
        assertThat(listing.getClaimToken()).isEqualTo(issued);
    }

    @Test
    void issueClaimToken_rejectsAListingThatAlreadyHasAnAccount() {
        listing.setUser(new User());
        when(repairerRepo.findById(listing.getId())).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.issueClaimToken(listing.getId()))
                .isInstanceOf(ConflictException.class);
    }
}
