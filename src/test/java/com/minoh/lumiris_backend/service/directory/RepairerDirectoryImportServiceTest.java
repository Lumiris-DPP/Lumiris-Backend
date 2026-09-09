package com.minoh.lumiris_backend.service.directory;

import com.minoh.lumiris_backend.entity.RepairerProfile;
import com.minoh.lumiris_backend.entity.RepairerSource;
import com.minoh.lumiris_backend.entity.RepairerStatus;
import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.repository.RepairerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairerDirectoryImportServiceTest {

    @Mock private RepairerProfileRepository repairerRepo;

    private RepairerDirectoryImportService service;

    private final DirectoryEntry entry = new DirectoryEntry(
            "73282932000074", "Retouche Bastille", "Retouche Bastille EURL", "73282932000074",
            "25 rue de la Roquette", "Paris", "Île-de-France", 48.855, 2.372, "{}");

    @BeforeEach
    void setUp() {
        RepairerDirectorySource fakeSource = new RepairerDirectorySource() {
            @Override public RepairerSource source() { return RepairerSource.SIRENE; }
            @Override public List<DirectoryEntry> fetch(ImportCriteria criteria) { return List.of(entry); }
        };
        service = new RepairerDirectoryImportService(repairerRepo, List.of(fakeSource));
    }

    @Test
    void unknownSource_isRejected() {
        assertThatThrownBy(() -> service.importFrom(RepairerSource.OSM, new ImportCriteria(null, null, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newEntry_isCreatedAsUnclaimedWithLocation() {
        when(repairerRepo.findBySourceAndExternalRef(RepairerSource.SIRENE, "73282932000074"))
                .thenReturn(Optional.empty());
        when(repairerRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ImportReport report = service.importFrom(RepairerSource.SIRENE, new ImportCriteria(null, null, 1));

        assertThat(report.created()).isEqualTo(1);
        verify(repairerRepo).save(org.mockito.ArgumentMatchers.argThat(p -> {
            RepairerProfile rp = (RepairerProfile) p;
            return rp.getStatus() == RepairerStatus.UNCLAIMED
                    && rp.getSource() == RepairerSource.SIRENE
                    && rp.getExternalRef().equals("73282932000074")
                    && rp.getLocation() != null
                    && rp.getImportedAt() != null;
        }));
    }

    @Test
    void existingUnclaimedEntry_isRefreshed() {
        RepairerProfile existing = new RepairerProfile();
        existing.setSource(RepairerSource.SIRENE);
        existing.setExternalRef("73282932000074");
        existing.setStatus(RepairerStatus.UNCLAIMED);
        when(repairerRepo.findBySourceAndExternalRef(RepairerSource.SIRENE, "73282932000074"))
                .thenReturn(Optional.of(existing));
        when(repairerRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ImportReport report = service.importFrom(RepairerSource.SIRENE, new ImportCriteria(null, null, 1));

        assertThat(report.updated()).isEqualTo(1);
        assertThat(existing.getDisplayName()).isEqualTo("Retouche Bastille");
    }

    @Test
    void alreadyClaimedEntry_isNeverTouched() {
        RepairerProfile claimed = new RepairerProfile();
        claimed.setUser(new User());
        when(repairerRepo.findBySourceAndExternalRef(RepairerSource.SIRENE, "73282932000074"))
                .thenReturn(Optional.of(claimed));

        ImportReport report = service.importFrom(RepairerSource.SIRENE, new ImportCriteria(null, null, 1));

        assertThat(report.skipped()).isEqualTo(1);
        verify(repairerRepo, never()).save(any());
    }
}
