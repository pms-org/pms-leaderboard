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

// @Component
// @EnableScheduling
// public class RedisHealthChecker {

//     private static final Logger log =
//             LoggerFactory.getLogger(RedisHealthChecker.class);

//     private final StringRedisTemplate redisTemplate;
//     private final RedisHealth redisHealth;

//     private long retryDelay = 1000; // start with 1s
//     private static final long MAX_DELAY = 30000; // 30 seconds

//     public RedisHealthChecker(StringRedisTemplate redisTemplate,
//                               RedisHealth redisHealth) {
//         this.redisTemplate = redisTemplate;
//         this.redisHealth = redisHealth;
//     }

//     @Scheduled(fixedDelay = 3000)
//     public void checkRedis() {

//         try {

//             redisTemplate.opsForValue().get("health-check");

//             if (!redisHealth.isAvailable()) {
//                 log.warn("🟩 Redis reachable again");
//                 redisHealth.up();
//             }

//             retryDelay = 1000; // reset

//         } catch (Exception e) {

//             if (redisHealth.isAvailable()) {
//                 redisHealth.down();
//             }

//             log.error("Redis unreachable — retrying in {} ms", retryDelay);

//             try {
//                 Thread.sleep(retryDelay);
//             } catch (InterruptedException ignored) {}

//             retryDelay = Math.min(retryDelay * 2, MAX_DELAY);
//         }
//     }
// }


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
