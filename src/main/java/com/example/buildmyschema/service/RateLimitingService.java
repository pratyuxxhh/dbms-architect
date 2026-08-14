package com.example.buildmyschema.service;


import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Service
public class RateLimitingService {
    private static final int REQUESTS_PER_MINUTE = 10;
    // Storage for buckets (IP Address -> Bucket)
    private final ProxyManager<String> proxyManager;

    public RateLimitingService(ProxyManager<String> proxyManager) {
        this.proxyManager = proxyManager;
    }

    public Bucket resolveBucket(String key) {
        Supplier<BucketConfiguration> configSupplier = this::getConfig;

        // This effectively says: "Get the bucket for this key from Redis.
        // If it doesn't exist, create it using configSupplier."
        return proxyManager.builder().build(key, configSupplier);
    }

    private BucketConfiguration getConfig() {
        // Rule: 10 requests per 1 minute
        // This state is stored in Redis
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(REQUESTS_PER_MINUTE, Refill.intervally(REQUESTS_PER_MINUTE, Duration.ofMinutes(1))))
                .build();
    }
}