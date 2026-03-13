package com.r2s.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> RATE_LIMITED_PATHS = Set.of("/auth/login","/auth/register");
    private static final long MAX_REQUESTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private final StringRedisTemplate redisTemplate;

    public RateLimitFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (RATE_LIMITED_PATHS.contains(path)) {
            String clientIP = resolveClientIp(request);
            String key = buildKey(path, clientIP);

            Long count = redisTemplate.opsForValue().increment(key);

            if(count != null && count == 1L){
                redisTemplate.expire(key,WINDOW);
            }

            if (count != null && count > MAX_REQUESTS) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("""
                        {"message": "Too many requests. Please try again later."}
                        """);
                response.flushBuffer();
                return;

            }
        }
        filterChain.doFilter(request, response);
    }
    private String buildKey(String path, String clientIP) {
        return "ratelimit:" + path + ":" + clientIP;
    }
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
