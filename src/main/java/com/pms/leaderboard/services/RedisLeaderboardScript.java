package com.pms.leaderboard.services;

import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisLeaderboardScript {

    public RedisScript<Long> upsertAndRank() {
        return RedisScript.of("""
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
            return redis.call('ZREVRANK', KEYS[1], ARGV[2])
        """, Long.class);
    }
    
}
