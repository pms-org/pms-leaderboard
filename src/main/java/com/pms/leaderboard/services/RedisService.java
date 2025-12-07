package com.pms.leaderboard.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

@Service
public class RedisService {
    
    @Autowired
    private StringRedisTemplate redis;

    private static final String KEY = "leaderboard:global:daily";

    public long update(UUID pid, double score) {
        ZSetOperations<String, String> zset = redis.opsForZSet();
        zset.add(KEY, pid.toString(), score);
        Long rank = zset.reverseRank(KEY, pid.toString());
        return (rank == null) ? -1 : rank + 1;
    }

    public Set<ZSetOperations.TypedTuple<String>> top(int n) {
        return redis.opsForZSet().reverseRangeWithScores(KEY, 0, n - 1);
    }

    public List<Map<String,Object>> fetchTopRows(int topN) {
    Set<ZSetOperations.TypedTuple<String>> top =
            redis.opsForZSet().reverseRangeWithScores(KEY, 0, topN - 1);

    List<Map<String,Object>> rows = new ArrayList<>();
    if (top != null) {
        int pos = 1;
        for (ZSetOperations.TypedTuple<String> t : top) {
            Map<String,Object> r = new HashMap<>();
            r.put("rank", pos++);
            r.put("portfolioId", t.getValue());
            r.put("compositeScore", t.getScore());
            rows.add(r);
        }
    }
    return rows;
}

}
