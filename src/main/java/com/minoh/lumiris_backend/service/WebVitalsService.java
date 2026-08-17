package com.minoh.lumiris_backend.service;

import com.minoh.lumiris_backend.dto.in.WebVitalRequest;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Les Web Vitals des quatre surfaces atterrissent dans Prometheus (/actuator/prometheus).
// `route` et `sessionId` restent hors des tags : ce sont des dimensions non bornées qui feraient
// exploser la cardinalité de la série temporelle.
@Service
public class WebVitalsService {

    private static final String METER = "lumiris.web_vitals";

    private final MeterRegistry meterRegistry;
    private final Map<String, DistributionSummary> summaries = new ConcurrentHashMap<>();

    public WebVitalsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(WebVitalRequest request) {
        summaries.computeIfAbsent(
                request.app() + '|' + request.name() + '|' + request.rating(),
                key -> DistributionSummary.builder(METER)
                        .tag("app", request.app())
                        .tag("metric", request.name())
                        .tag("rating", request.rating())
                        .publishPercentileHistogram()
                        .register(meterRegistry))
                .record(request.value());
    }
}
