package com.bajar.saman.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Bucket4j (the rate-limiting algorithm library) to our Redis instance, using
 * Lettuce as the Redis client (same client Spring's own Redis starter defaults to,
 * so we're not running two different Redis client libraries side by side).
 *
 * The bean produced here — a ProxyManager<byte[]> — is Bucket4j's abstraction for
 * "somewhere remote that can atomically store/read/update a bucket's token count."
 * RateLimitFilter (below) asks THIS bean for a Bucket whenever it needs to check/
 * consume a token; the actual Redis reads/writes happen behind that call.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public ProxyManager<byte[]> rateLimitProxyManager(
            @Value("${spring.data.redis.host}") String redisHost,
            @Value("${spring.data.redis.port}") int redisPort) {

        // Build a direct Lettuce connection to Redis. We're not reusing Spring's own
        // RedisConnectionFactory bean here — Bucket4j's Lettuce integration needs a
        // very specific connection shape (ByteArrayCodec, raw bytes in and out) that
        // doesn't cleanly come out of Spring Data Redis's higher-level abstractions,
        // so it's simpler and clearer to build this one specifically for Bucket4j's
        // needs, reusing only the host/port values from application.properties.
        RedisURI redisUri = RedisURI.Builder.redis(redisHost, redisPort).build();
        RedisClient redisClient = RedisClient.create(redisUri);

        // ByteArrayCodec: bucket keys and values are stored as raw bytes in Redis
        // (not Strings) — this is what Bucket4j's Redis integration expects.
        StatefulRedisConnection<byte[], byte[]> connection =
                redisClient.connect(ByteArrayCodec.INSTANCE);

        // "casBasedBuilder" = Compare-And-Swap based. This means every bucket
        // read-modify-write cycle (checking tokens, consuming a token) happens as
        // one atomic Redis operation — this is EXACTLY why we needed Redis instead
        // of an in-memory Map: if two requests from the same IP arrive at the same
        // instant (or from two different backend instances once we scale
        // horizontally), CAS guarantees they can't both "see" the same token and
        // both get allowed through — the same class of race condition we fixed in
        // UserRegistrationService earlier, just in a different spot.
        return Bucket4jLettuce.casBasedBuilder(connection).build();
    }
}