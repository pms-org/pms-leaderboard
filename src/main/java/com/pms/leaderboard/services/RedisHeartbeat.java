package com.pms.leaderboard.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RedisHeartbeat {

    public static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RedisHeartbeat.class);

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private RedisHealth redisHealth;

    @Scheduled(fixedDelay = 3000)
    public void ping() {

        log.info("🔔🔔🔔🔔 Pinging Redis to check health...");
        System.out.println("🔔🔔🔔🔔 Pinging Redis to check health...");

        try {
            redis.getConnectionFactory()
                 .getConnection()
                 .ping();

            log.info("✅✅✅✅ Redis ping successful");
            System.out.println("✅✅✅✅ Redis ping successful");
            redisHealth.up();

        } catch (Exception e) {

            log.error("❌❌❌❌ Redis ping failed", e);
            System.out.println("❌❌❌❌ Redis ping failed");

            redisHealth.down();
        }
    }
}

