package com.example.buildmyschema.filters;


import com.example.buildmyschema.service.RateLimitingService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;

    public RateLimitFilter(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 1. Extract the IP address (or API Key from headers)
        String clientIp = getClientIp(request);
        log.info("Client IP: {}", clientIp);
        // 2. Get the bucket for this specific IP
        Bucket tokenBucket = rateLimitingService.resolveBucket(clientIp);

        // 3. Try to consume 1 token
        ConsumptionProbe probe = tokenBucket.tryConsumeAndReturnRemaining(1);

        if(probe.isConsumed()) {
            // 4. Success: Add "X-Rate-Limit-Remaining" header and proceed
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            // 5. Failure: Return 429 Too Many Requests
            long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
            response.setContentType("application/json");

            String jsonResponse = """
                {
                    "status": %s,
                    "error": "Too Many Requests",
                    "message": "You have exhausted your API Request Quota",
                    "retryAfterSeconds": %s
                }
                """.formatted(HttpStatus.TOO_MANY_REQUESTS.value(), waitForRefill);

            response.getWriter().write(jsonResponse);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}