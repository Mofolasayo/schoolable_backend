package com.schoolable.backend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting Filter
 * Limits requests per user/IP to prevent abuse.
 * 100 requests per minute per user for AI endpoints.
 * 1000 requests per minute for general endpoints.
 */
@Component
public class RateLimitingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    // Per-user bucket cache
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // AI endpoints get stricter limits
    private static final int AI_REQUESTS_PER_MINUTE = 100;
    private static final int GENERAL_REQUESTS_PER_MINUTE = 1000;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientId = getClientIdentifier(httpRequest);
        String path = httpRequest.getRequestURI();
        boolean isAiEndpoint = isAiEndpoint(path);

        Bucket bucket = buckets.computeIfAbsent(clientId, 
            k -> createBucket(isAiEndpoint));

        if (bucket.tryConsume(1)) {
            // Add rate limit headers
            httpResponse.addHeader("X-RateLimit-Remaining", 
                String.valueOf(bucket.getAvailableTokens()));
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for client: {} on path: {}", clientId, path);
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                "{\"error\":\"Rate limit exceeded\",\"message\":\"Too many requests. Please wait and try again.\",\"retryAfterSeconds\":60}"
            );
        }
    }

    private Bucket createBucket(boolean isAiEndpoint) {
        int requestsPerMinute = isAiEndpoint ? AI_REQUESTS_PER_MINUTE : GENERAL_REQUESTS_PER_MINUTE;
        
        Bandwidth limit = Bandwidth.classic(
            requestsPerMinute,
            Refill.greedy(requestsPerMinute, Duration.ofMinutes(1))
        );

        return Bucket.builder()
            .addLimit(limit)
            .build();
    }

    private boolean isAiEndpoint(String path) {
        return path.contains("/insights") ||
               path.contains("/ai") ||
               path.contains("/analyze") ||
               path.contains("/grade");
    }

    private String getClientIdentifier(HttpServletRequest request) {
        // Try to get user ID from auth header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Hash the token for privacy
            return "user:" + authHeader.hashCode();
        }

        // Fall back to IP address
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }

        return "ip:" + request.getRemoteAddr();
    }

    @Override
    public void init(FilterConfig filterConfig) {}

    @Override
    public void destroy() {
        buckets.clear();
    }
}
