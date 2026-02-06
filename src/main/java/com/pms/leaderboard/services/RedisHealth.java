package com.pms.leaderboard.services;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.pms.leaderboard.events.RedisDownEvent;
import com.pms.leaderboard.events.RedisUpEvent;

@Component
public class RedisHealth {


    public static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RedisHealth.class);

    private final AtomicBoolean available = new AtomicBoolean(true);

    @Autowired
    private ApplicationEventPublisher publisher;

    public boolean isAvailable() {
        return available.get();
    }

    public void down() {
        if (available.compareAndSet(true, false)) {
            log.warn("🟥🟥🟥🟥 REDIS MARKED DOWN");
            System.out.println("🟥🟥🟥🟥 REDIS MARKED DOWN");
            // notify listeners
            try {
                publisher.publishEvent(new RedisDownEvent(this));
            } catch (Exception ignored) {
            }
        }
    }

    public void up() {
        if (available.compareAndSet(false, true)) {
            log.warn("🟩🟩🟩🟩 REDIS MARKED UP");
            System.out.println("🟩🟩🟩🟩 REDIS MARKED UP");
            // notify listeners
            try {
                publisher.publishEvent(new RedisUpEvent(this));
            } catch (Exception ignored) {
            }
        }
    }
}
