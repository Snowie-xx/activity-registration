package com.example.activity.service;

import com.example.activity.dto.ActivityBasicInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 活动基础信息缓存（标题/状态/总名额）。只缓存不随报名变化的静态字段，
 * 剩余名额一律实时读 MySQL，报名写路径也绝不读取缓存余量。
 *
 * <p>Redis 不可用或读写异常时静默回源（返回 miss / 跳过写入），
 * 保证核心报名流程不受缓存故障影响，同时保留可定位日志。</p>
 */
@Service
public class ActivityCacheService {

    private static final Logger log = LoggerFactory.getLogger(ActivityCacheService.class);
    private static final String KEY_PREFIX = "activity:info:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public ActivityCacheService(StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper,
                                @Value("${app.cache.ttl-seconds:120}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<ActivityBasicInfo> get(long activityId) {
        String key = key(activityId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                log.debug("redis cache miss key={}", key);
                return Optional.empty();
            }
            log.info("redis cache hit key={}", key);
            return Optional.of(objectMapper.readValue(json, ActivityBasicInfo.class));
        } catch (Exception e) {
            log.warn("redis read failed, fallback to db: key={} err={}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(long activityId, ActivityBasicInfo info) {
        String key = key(activityId);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(info), ttl);
            log.info("redis cache set key={} ttl={}s", key, ttl.getSeconds());
        } catch (Exception e) {
            log.warn("redis write failed, continue without cache: key={} err={}", key, e.getMessage());
        }
    }

    private String key(long activityId) {
        return KEY_PREFIX + activityId;
    }
}
