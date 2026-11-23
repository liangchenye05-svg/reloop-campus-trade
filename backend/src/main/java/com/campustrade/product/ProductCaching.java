package com.campustrade.product;

import com.campustrade.common.Api.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.transaction.event.TransactionalEventListener;

@org.springframework.context.annotation.Profile("!identity")
@Configuration
@EnableCaching
public class ProductCaching implements CachingConfigurer {
    static final String DETAILS = "product-details";
    static final String HOT = "product-hot";
    private static final Logger log = LoggerFactory.getLogger(ProductCaching.class);

    public record Changed(List<Long> ids) {}

    @Bean
    @ConditionalOnProperty(name = "campus.cache.enabled", havingValue = "false", matchIfMissing = true)
    CacheManager noCache() {
        return new NoOpCacheManager();
    }

    @Bean
    @ConditionalOnProperty(name = "campus.cache.enabled", havingValue = "true")
    CacheManager redisCache(RedisConnectionFactory connection, ObjectMapper json) {
        var defaults = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("campus-trade:v2:");
        var details = defaults.serializeValuesWith(SerializationPair.fromSerializer(
                new Jackson2JsonRedisSerializer<>(json, Product.Details.class)))
                .entryTtl((key, value) -> value == null ? Duration.ofSeconds(10) : Duration.ofSeconds(60));
        var pageType = json.getTypeFactory().constructParametricType(Page.class, Product.View.class);
        var hot = defaults.serializeValuesWith(SerializationPair.fromSerializer(
                new Jackson2JsonRedisSerializer<>(json, pageType))).entryTtl(Duration.ofSeconds(30));
        var writer = RedisCacheWriter.nonLockingRedisCacheWriter(connection, BatchStrategies.scan(128));
        return RedisCacheManager.builder(writer).cacheDefaults(defaults)
                .withInitialCacheConfigurations(Map.of(DETAILS, details, HOT, hot))
                .disableCreateOnMissingCache().build();
    }

    @Bean
    Invalidation productCacheInvalidation(CacheManager manager) {
        return new Invalidation(manager);
    }

    static class Invalidation {
        private final CacheManager manager;

        Invalidation(CacheManager manager) {
            this.manager = manager;
        }

        @TransactionalEventListener
        void changed(Changed event) {
            try {
                Cache details = manager.getCache(DETAILS);
                event.ids().forEach(details::evict);
                manager.getCache(HOT).clear();
            } catch (org.springframework.dao.DataAccessException exception) {
                log.warn("Redis invalidation unavailable, entries will expire via TTL: {}", exception.getMessage());
            }
        }
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException failure, Cache cache, Object key) {
                log.warn("Redis read failed for {}: {}", cache.getName(), failure.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException failure, Cache cache, Object key, Object value) {
                log.warn("Redis write failed for {}: {}", cache.getName(), failure.getMessage());
            }
        };
    }
}
