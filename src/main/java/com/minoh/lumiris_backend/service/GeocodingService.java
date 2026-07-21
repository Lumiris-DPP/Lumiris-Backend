package com.minoh.lumiris_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minoh.lumiris_backend.entity.GeocodeCache;
import com.minoh.lumiris_backend.repository.GeocodeCacheRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class GeocodingService {

    private static final String PROVIDER = "nominatim";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final GeocodeCacheRepository cacheRepository;
    private final MeterRegistry meterRegistry;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter providerErrorCounter;

    public GeocodingService(RestClient.Builder builder,
                             GeocodeCacheRepository cacheRepository,
                             MeterRegistry meterRegistry,
                             @Value("${geocoding.user-agent}") String userAgent) {
        this.restClient = builder
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", userAgent)
                .build();
        this.cacheRepository = cacheRepository;
        this.meterRegistry = meterRegistry;
        this.cacheHitCounter = Counter.builder("geocoding.cache")
                .tag("result", "hit").register(meterRegistry);
        this.cacheMissCounter = Counter.builder("geocoding.cache")
                .tag("result", "miss").register(meterRegistry);
        this.providerErrorCounter = Counter.builder("geocoding.provider.errors")
                .tag("provider", PROVIDER).register(meterRegistry);
    }

    public record Coordinates(Double latitude, Double longitude) {}

    /**
     * Resolves free-text location to coordinates, using the geocode_cache table as a
     * cache-aside layer so a given place is only ever sent to Nominatim once.
     */
    public Optional<Coordinates> geocode(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);

        Optional<GeocodeCache> cached = cacheRepository.findByQueryNormalized(normalized);
        if (cached.isPresent()) {
            cacheHitCounter.increment();
            return cached.map(cache -> new Coordinates(cache.getLatitude(), cache.getLongitude()));
        }

        cacheMissCounter.increment();
        return callProviderAndCache(normalized, query.trim());
    }

    private Optional<Coordinates> callProviderAndCache(String normalized, String originalQuery) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String rawJson = restClient.get()
                    .uri("/search?q={q}&format=json&limit=1", originalQuery)
                    .retrieve()
                    .body(String.class);
            sample.stop(providerTimer("success"));

            JsonNode root = MAPPER.readTree(rawJson);
            if (!root.isArray() || root.isEmpty()) {
                log.info("Geocoding: no result for '{}'", originalQuery);
                return Optional.empty();
            }

            JsonNode first = root.get(0);
            Double lat = first.path("lat").isMissingNode() ? null : first.path("lat").asDouble();
            Double lon = first.path("lon").isMissingNode() ? null : first.path("lon").asDouble();

            cacheRepository.save(new GeocodeCache(normalized, lat, lon, PROVIDER));

            return Optional.of(new Coordinates(lat, lon));
        } catch (Exception e) {
            sample.stop(providerTimer("error"));
            providerErrorCounter.increment();
            log.warn("Geocoding failed for '{}': {}", originalQuery, e.getMessage());
            return Optional.empty();
        }
    }

    private Timer providerTimer(String outcome) {
        return Timer.builder("geocoding.provider.latency")
                .tag("provider", PROVIDER)
                .tag("outcome", outcome)
                .register(meterRegistry);
    }
}