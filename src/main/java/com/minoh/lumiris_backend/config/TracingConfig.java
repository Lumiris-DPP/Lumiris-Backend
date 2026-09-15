package com.minoh.lumiris_backend.config;

import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// spring-boot-micrometer-tracing (Spring Boot 4.0.6) auto-wires the tracing observation handlers
// around whatever io.micrometer.tracing.Tracer bean it finds, but — unlike Spring Boot 3.4's
// actuator-autoconfigure — it ships no OTel/OTLP-specific autoconfiguration to actually CREATE
// that bean: without one it falls back to NoopTracerAutoConfiguration and nothing gets exported.
// This wires the OTel SDK bridge by hand, the documented fallback when the framework doesn't.
@Configuration
public class TracingConfig {

    @Bean
    SpanExporter otlpSpanExporter(@Value("${management.otlp.tracing.endpoint}") String endpoint) {
        return OtlpHttpSpanExporter.builder().setEndpoint(endpoint).build();
    }

    @Bean
    SdkTracerProvider sdkTracerProvider(SpanExporter spanExporter, @Value("${spring.application.name}") String serviceName) {
        return SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .setResource(Resource.getDefault().merge(
                        Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), serviceName))))
                .build();
    }

    @Bean
    OpenTelemetry openTelemetry(SdkTracerProvider sdkTracerProvider) {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
    }

    @Bean
    io.opentelemetry.api.trace.Tracer otelSdkTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("com.minoh.lumiris_backend");
    }

    @Bean
    OtelCurrentTraceContext otelCurrentTraceContext() {
        return new OtelCurrentTraceContext();
    }

    @Bean
    OtelTracer.EventPublisher otelTracerEventPublisher(ApplicationEventPublisher publisher) {
        return publisher::publishEvent;
    }

    // The io.micrometer.tracing.Tracer implementation — its mere presence flips
    // MicrometerTracingAutoConfiguration's observation handlers from noop to real spans.
    @Bean
    io.micrometer.tracing.Tracer micrometerTracer(
            io.opentelemetry.api.trace.Tracer otelSdkTracer,
            OtelCurrentTraceContext otelCurrentTraceContext,
            OtelTracer.EventPublisher eventPublisher
    ) {
        return new OtelTracer(otelSdkTracer, otelCurrentTraceContext, eventPublisher);
    }

    @Bean
    io.micrometer.tracing.propagation.Propagator propagator(
            OpenTelemetry openTelemetry, io.opentelemetry.api.trace.Tracer otelSdkTracer
    ) {
        return new OtelPropagator(openTelemetry.getPropagators(), otelSdkTracer);
    }
}
