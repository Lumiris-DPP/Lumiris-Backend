package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.User;
import com.minoh.lumiris_backend.entity.UserRole;
import com.minoh.lumiris_backend.repository.DppFormRepository;
import com.minoh.lumiris_backend.repository.NotificationRepository;
import com.minoh.lumiris_backend.repository.RepairRequestRepository;
import com.minoh.lumiris_backend.repository.SubscriptionRepository;
import com.minoh.lumiris_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDataExportServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private DppFormRepository dppFormRepository;
    @Mock private RepairRequestRepository repairRequestRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private SubscriptionRepository subscriptionRepository;

    @InjectMocks private AccountDataExportService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("alice@example.com");
        user.setName("Alice");
        user.setRole(UserRole.CONSUMER);
        when(userRepository.getByEmail("alice@example.com")).thenReturn(user);
        when(dppFormRepository.findByUserId(user.getId())).thenReturn(List.of());
        when(repairRequestRepository.findByConsumerUserOrderByCreatedAtDesc(user)).thenReturn(List.of());
        when(notificationRepository.findByUser_IdOrderByCreatedAtDesc(eq(user.getId()), any())).thenReturn(List.of());
        when(subscriptionRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
    }

    @Test
    void export_producesAZipContainingTheAccountData() throws Exception {
        byte[] archive = service.export("alice@example.com");

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            String accountJson = null;
            boolean sawReadme = false;
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                if (e.getName().equals("account.json")) {
                    accountJson = new String(zip.readAllBytes());
                } else if (e.getName().equals("README.txt")) {
                    sawReadme = true;
                }
            }
            assertThat(accountJson).contains("alice@example.com").contains("\"role\" : \"CONSUMER\"");
            assertThat(sawReadme).isTrue();
        }
    }
}
