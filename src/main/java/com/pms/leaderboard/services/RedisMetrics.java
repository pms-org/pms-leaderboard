package com.pms.leaderboard.services;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.pms.leaderboard.events.RedisDownEvent;
import com.pms.leaderboard.events.RedisUpEvent;

@Component
public class RedisMetrics {

    private static final Logger log = LoggerFactory.getLogger(RedisMetrics.class);

    private final AtomicLong downEventCount = new AtomicLong(0);
    private final AtomicLong upEventCount = new AtomicLong(0);
    private final AtomicLong ioFailureCount = new AtomicLong(0);

    @Autowired
    private RedisHealth redisHealth;

    @EventListener
    public void onRedisDown(RedisDownEvent ev) {
        long count = downEventCount.incrementAndGet();
        log.info("METRIC: redis.down.events = {}", count);
    }

    @EventListener
    public void onRedisUp(RedisUpEvent ev) {
        long count = upEventCount.incrementAndGet();
        log.info("METRIC: redis.up.events = {}", count);
    }

    public void recordIOFailure() {
        long count = ioFailureCount.incrementAndGet();
        log.warn("METRIC: redis.io.failures = {}", count);
    }

    public void logHealthMetrics() {
        log.info("METRICS: redis.health={} down_events={} up_events={} io_failures={}",
                redisHealth.isAvailable() ? 1 : 0,
                downEventCount.get(),
                upEventCount.get(),
                ioFailureCount.get());
    }

    public long getDownEventCount() {
        return downEventCount.get();
    }

    public long getUpEventCount() {
        return upEventCount.get();
    }

    public long getIOFailureCount() {
        return ioFailureCount.get();
    }
}
