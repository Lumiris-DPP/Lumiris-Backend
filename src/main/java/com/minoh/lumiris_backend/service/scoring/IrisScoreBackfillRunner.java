package com.minoh.lumiris_backend.service.scoring;

import com.minoh.lumiris_backend.service.DppFormService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IrisScoreBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(IrisScoreBackfillRunner.class);

    private final DppFormService dppFormService;

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        int restored = dppFormService.backfillMissingIrisScores();
        if (restored > 0) {
            log.info("Score Iris calculé et persisté pour {} passeport(s) publié(s) qui n'en avaient pas", restored);
        }
    }
}
