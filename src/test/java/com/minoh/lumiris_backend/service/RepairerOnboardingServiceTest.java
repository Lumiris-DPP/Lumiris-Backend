package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.RepairerRegisterRequest;
import com.minoh.lumiris_backend.dto.out.RepairerProfileResponse;
import com.minoh.lumiris_backend.dto.out.RepairerSearchResult;
import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.RepairerStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.repository.RepairerProfileRepository;
import com.minoh.lumiris_backend.repository.RepairerReviewRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairerOnboardingServiceTest {

    @Mock
    private RepairerProfileRepository repairerRepo;

    @Mock
    private RepairerReviewRepository reviewRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private SireneService sireneService;

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private KybMapper kybMapper;

    @Mock
    private StorageService storageService;

    @Mock
    private MailService mailService;

    @InjectMocks
    private RepairerOnboardingService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("repairer@lumiris.com");
        lenient().when(userRepo.findByEmail("repairer@lumiris.com")).thenReturn(Optional.of(user));
        lenient().when(reviewRepo.averageRating(any())).thenReturn(null);
        lenient().when(reviewRepo.countByRepairerProfile(any())).thenReturn(0L);
    }

    @Test
    void register_setsPendingStatusAndCompanyNameFromSirene() {
        when(repairerRepo.findByUser(user)).thenReturn(Optional.empty());
        when(sireneService.validate("73282932000074"))
                .thenReturn(new SireneService.SireneData(
                        "Atelier Réparation SARL", "95.29Z", "{}", "732829320", "1 rue Test 75001 Paris", "5499", null));
        when(repairerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RepairerProfileResponse response = service.register("repairer@lumiris.com", new RepairerRegisterRequest("73282932000074"));

        // KYB simplifié à l'inscription, mais reste en attente d'un dossier KYB complet + revue admin.
        assertThat(response.status()).isEqualTo(RepairerStatus.PENDING);
        assertThat(response.companyName()).isEqualTo("Atelier Réparation SARL");
        assertThat(response.displayName()).isEqualTo("Atelier Réparation SARL");
    }

    @Test
    void search_convertsDistanceFromMetersToKilometers() {
        UUID id = UUID.randomUUID();
        // Postgres text[] columns surface as String[] (JDBC array) in a native query row, not List.
        Object[] row = {id, "Atelier Test", "Atelier Test SARL", new String[]{"couture"}, new String[]{"Paris"}, "Lun-Ven", "1 rue Test", "Paris", "Île-de-France", 2500.0};
        when(repairerRepo.searchNearby(48.85, 2.35, null, 20_000.0)).thenReturn(Collections.singletonList(row));

        List<RepairerSearchResult> results = service.search(48.85, 2.35, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(id);
        assertThat(results.get(0).specialties()).containsExactly("couture");
        assertThat(results.get(0).distanceKm()).isEqualTo(2.5);
    }

    @Test
    void register_reusesExistingProfile_whenAlreadySignedUp() {
        RepairerProfile existing = new RepairerProfile();
        existing.setUser(user);
        existing.setDisplayName("Déjà rempli");
        when(repairerRepo.findByUser(user)).thenReturn(Optional.of(existing));
        when(sireneService.validate("73282932000074"))
                .thenReturn(new SireneService.SireneData(
                        "Atelier Réparation SARL", "95.29Z", "{}", "732829320", "1 rue Test 75001 Paris", "5499", null));
        when(repairerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RepairerProfileResponse response = service.register("repairer@lumiris.com", new RepairerRegisterRequest("73282932000074"));

        assertThat(response.displayName()).isEqualTo("Déjà rempli");
    }
}
