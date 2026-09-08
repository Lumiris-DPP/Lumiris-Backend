package com.minoh.lumiris_backend.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Fixed-window rate limit, one Redis counter per (IP, calendar minute). Runs in the servlet
 * chain ahead of Spring Security so unauthenticated floods (login brute force included) are
 * capped too. This is a safety net behind the Cloudflare WAF — not an auth control.
 */
@Component
@Order(-101) // one ahead of Spring Security's FilterChainProxy (DEFAULT_FILTER_ORDER = -100)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final StringRedisTemplate redis;

    @Value("${lumiris.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${lumiris.rate-limit.per-minute:100}")
    private int perMinute;

    public RateLimitFilter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Health/metrics probes hammer these and must never be throttled.
        return !enabled || request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String key = "rl:" + clientIp(request) + ":" + (Instant.now().getEpochSecond() / 60);

        long count;
        try {
            Long incremented = redis.opsForValue().increment(key);
            count = incremented == null ? 0 : incremented;
            if (count == 1L) {
                redis.expire(key, Duration.ofSeconds(120));
            }
        } catch (RuntimeException e) {
            // ponytail: fail open. A Redis blip must not take the whole API down; the WAF in
            // front is the hard edge. Upgrade path: local in-process fallback counter if Redis
            // outages ever get frequent enough to matter.
            log.warn("Rate-limit check skipped, Redis unavailable: {}", e.getMessage());
            chain.doFilter(request, response);
            return;
        }

        if (count > perMinute) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            JSON.writeValue(response.getWriter(),
                    Map.of("status", 429, "message", "Trop de requêtes. Réessayez dans une minute."));
            return;
        }

        chain.doFilter(request, response);
    }

    private static String clientIp(HttpServletRequest request) {
        // ponytail: trust the first X-Forwarded-For hop. Holds because prod traffic only reaches
        // the app through Cloudflare -> ingress, both of which append. Revisit if the container
        // is ever exposed directly.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
