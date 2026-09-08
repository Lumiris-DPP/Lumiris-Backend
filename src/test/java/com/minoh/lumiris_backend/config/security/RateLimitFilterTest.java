package com.minoh.lumiris_backend.config.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private FilterChain chain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(redis);
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "perMinute", 100);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void underTheLimit_passesThrough() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(50L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(new MockHttpServletRequest("GET", "/api/auth/me"), response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void overTheLimit_returns429AndStopsTheChain() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(101L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(new MockHttpServletRequest("POST", "/api/auth/sign-in"), response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    void redisDown_failsOpen() throws Exception {
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("connection refused"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(new MockHttpServletRequest("GET", "/api/auth/me"), response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
