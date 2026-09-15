package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.entity.RepairerProspectOutreach;
import com.minoh.lumiris_backend.repository.RepairerProspectOutreachRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairerProspectingReminderSchedulerTest {

    @Mock private RepairerProspectOutreachRepository outreachRepo;
    @Mock private RepairerProspectingService prospectingService;

    @InjectMocks private RepairerProspectingReminderScheduler scheduler;

    private RepairerProspectOutreach outreach(int contactCount, Instant lastContactedAt) {
        RepairerProspectOutreach o = new RepairerProspectOutreach();
        o.setId(UUID.randomUUID());
        o.setContactCount(contactCount);
        o.setLastContactedAt(lastContactedAt);
        return o;
    }

    @Test
    void secondContact_firesAtSevenDays_notBefore() {
        RepairerProspectOutreach fresh = outreach(1, Instant.now().minus(3, ChronoUnit.DAYS));
        RepairerProspectOutreach ripe = outreach(1, Instant.now().minus(8, ChronoUnit.DAYS));
        when(outreachRepo.findDueForFollowUp(anyInt(), any())).thenReturn(List.of(fresh, ripe));

        scheduler.sendFollowUps();

        verify(prospectingService).followUp(ripe.getId());
        verify(prospectingService, never()).followUp(fresh.getId());
    }

    @Test
    void thirdContact_needsFourteenDaysSinceTheSecond() {
        // Second contact sent 10 days ago — not yet due for the third (needs 14).
        RepairerProspectOutreach o = outreach(2, Instant.now().minus(10, ChronoUnit.DAYS));
        when(outreachRepo.findDueForFollowUp(anyInt(), any())).thenReturn(List.of(o));

        scheduler.sendFollowUps();

        verify(prospectingService, never()).followUp(any());
    }
}
