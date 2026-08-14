package com.example.buildmyschema.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimitConfig {
    @Value("${REDIS_URL}")
    private String redisUrl;

    @Bean
    public RedisClient redisClient() {
        return RedisClient.create(RedisURI.create(redisUrl));
    }

    @Bean
    public ProxyManager<String> proxyManager(RedisClient redisClient) {

        StatefulRedisConnection<String, byte[]> redisConnection =
                redisClient.connect(
                        RedisCodec.of(
                                StringCodec.UTF8,
                                ByteArrayCodec.INSTANCE
                        )
                );

        // Remove unused buckets from Redis after 1 hour
        ExpirationAfterWriteStrategy expirationStrategy =
                ExpirationAfterWriteStrategy
                        .basedOnTimeForRefillingBucketUpToMax(
                                Duration.ofHours(1)
                        );

        ClientSideConfig clientConfig =
                ClientSideConfig.getDefault()
                        .withExpirationAfterWriteStrategy(
                                expirationStrategy
                        );

        return LettuceBasedProxyManager
                .builderFor(redisConnection)
                .withClientSideConfig(clientConfig)
                .build();
    }
}