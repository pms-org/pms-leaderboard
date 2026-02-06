package com.pms.leaderboard.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.dto.BatchDTO;
import com.pms.leaderboard.dto.LeaderboardDTO;
import com.pms.leaderboard.dto.MessageDTO;

@Service
public class LeaderboardService {

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private RedisScoreService redisScoreService;

    @Autowired
    private RedisLeaderboardScript rscript;

    @Autowired
    private RedisHealth redisHealth;

    @Autowired
    @Qualifier("redisExecutor")
    private ExecutorService redisExecutor;

    private static final String ZKEY = "leaderboard:global:daily";
    private static final String HKEY_PREFIX = "leaderboard:portfolio:";
    private static final String STREAM_KEY = "leaderboard:stream";

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

  
    public void processBatch(List<MessageDTO> batchList) {

        if (batchList == null || batchList.isEmpty()) {
            log.warn("processBatch called with EMPTY list");
            return;
        }

        if (!redisHealth.isAvailable()) {
            throw new RuntimeException("Redis unavailable");
        }

        log.info("Processing batch size={}", batchList.size());

        Map<UUID, MessageDTO> latest = new HashMap<>();
        for (MessageDTO m : batchList) {
            latest.put(m.getPortfolioId(), m);
        }

        List<MessageDTO> failed = new ArrayList<>();

        for (MessageDTO m : latest.values()) {

            try {

                if (m.getAvgRateOfReturn() == null
                        || m.getSharpeRatio() == null
                        || m.getSortinoRatio() == null) {
                    throw new IllegalStateException("NULL metrics for portfolio " + m.getPortfolioId());
                }

                UUID pid = m.getPortfolioId();
                BigDecimal score = computeScore(m);

                double redisScore = redisScoreService.compositeScore(
                        score, Instant.now(), pid
                );

              
                try {
                    redisExecutor.submit(() -> {
                        executeWithRetry("redis-write-" + pid, () -> {

                            Long rank = redis.execute(
                                    rscript.upsertAndRank(),
                                    List.of(ZKEY),
                                    String.valueOf(redisScore),
                                    pid.toString()
                            );

                            if (rank == null) {
                                throw new IllegalStateException("Redis rank failed");
                            }

                            String hkey = HKEY_PREFIX + pid;

                            redis.opsForHash().put(hkey, "score", score.toString());
                            redis.opsForHash().put(hkey, "sharpeRatio", m.getSharpeRatio().toString());
                            redis.opsForHash().put(hkey, "sortinoRatio", m.getSortinoRatio().toString());
                            redis.opsForHash().put(hkey, "avgRateOfReturn", m.getAvgRateOfReturn().toString());
                            redis.opsForHash().put(hkey, "updatedAt", Instant.now().toString());

                            redis.opsForStream().add(STREAM_KEY, Map.of(
                                    "portfolioId", pid.toString(),
                                    "score", score.toString(),
                                    "rank", String.valueOf(rank + 1),
                                    "avgRateOfReturn", m.getAvgRateOfReturn().toString(),
                                    "sharpeRatio", m.getSharpeRatio().toString(),
                                    "sortinoRatio", m.getSortinoRatio().toString(),
                                    "updatedAt", Instant.now().toString()
                            ));

                        });

                    });

                } catch (java.util.concurrent.RejectedExecutionException ex) {

                    log.error(" REDIS EXECUTOR SATURATED — STOPPING KAFKA");

                    redisHealth.down();   

                    throw new RuntimeException("Redis overloaded");
                }

            } catch (Exception ex) {
                log.error("Failed processing portfolio {}", m.getPortfolioId(), ex);
                failed.add(m);
            }
        }

        if (!failed.isEmpty()) {
            log.warn("Failed portfolios count = {}", failed.size());
        }
    }

    public Map<String, Object> getTop(int n) {

        List<LeaderboardDTO> top = fetchTop(n);

        return Map.of(
                "event", "leaderboardTop",
                "timestamp", Instant.now().toEpochMilli(),
                "count", top.size(),
                "top", top
        );
    }

    public Map<String, Object> getAround(String portfolioId, int range) {

        Long centerRank = redis.opsForZSet()
                .reverseRank(ZKEY, portfolioId);

        if (centerRank == null) {
            return Map.of(
                    "event", "leaderboardAround",
                    "timestamp", Instant.now().toEpochMilli(),
                    "centerRank", null,
                    "top", List.of()
            );
        }

        long start = Math.max(0, centerRank - range);
        long end = centerRank + range;

        Set<ZSetOperations.TypedTuple<String>> slice
                = redis.opsForZSet().reverseRangeWithScores(ZKEY, start, end);

        List<Map<String, Object>> rows = new ArrayList<>();
        long rank = start + 1;

        if (slice != null) {
            for (var t : slice) {
                String pid = t.getValue();
                Map<Object, Object> h
                        = redis.opsForHash().entries(HKEY_PREFIX + pid);

                Map<String, Object> r = new HashMap<>();
                r.put("rank", rank++);
                r.put("portfolioId", pid);
                r.put("compositeScore", t.getScore());
                r.put("sharpe", h.get("sharpeRatio"));
                r.put("sortino", h.get("sortinoRatio"));
                r.put("avgReturn", h.get("avgRateOfReturn"));
                r.put("updated", h.get("updatedAt"));

                rows.add(r);
            }
        }

        return Map.of(
                "event", "leaderboardAround",
                "timestamp", Instant.now().toEpochMilli(),
                "centerRank", centerRank + 1,
                "top", rows
        );
    }

    public List<LeaderboardDTO> fetchTop(int n) {

        Set<ZSetOperations.TypedTuple<String>> top
                = redis.opsForZSet().reverseRangeWithScores(ZKEY, 0, n - 1);

        List<LeaderboardDTO> rows = new ArrayList<>();
        long rank = 1;

        if (top != null) {
            for (var t : top) {
                String pid = t.getValue();
                Map<Object, Object> h
                        = redis.opsForHash().entries(HKEY_PREFIX + pid);

                rows.add(new LeaderboardDTO(
                        rank++,
                        UUID.fromString(pid),
                        t.getScore(),
                        new BigDecimal(h.get("avgRateOfReturn").toString()),
                        new BigDecimal(h.get("sharpeRatio").toString()),
                        new BigDecimal(h.get("sortinoRatio").toString()),
                        h.get("updatedAt").toString()
                ));
            }
        }

        return rows;
    }

    private BigDecimal computeScore(MessageDTO e) {
        return e.getAvgRateOfReturn().multiply(BigDecimal.valueOf(50))
                .add(e.getSharpeRatio().multiply(BigDecimal.valueOf(30)))
                .add(e.getSortinoRatio().multiply(BigDecimal.valueOf(20)));
    }

    // Retry helper: execute operation with exponential backoff. Max 3 attempts:
    // 100ms, 200ms, 400ms.
    
    private void executeWithRetry(String operationName, Runnable operation) {
        int maxAttempts = 3;
        int initialDelayMs = 100;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                operation.run();
                log.debug("{} succeeded on attempt {}", operationName, attempt);
                return;
            } catch (Exception e) {
                if (attempt == maxAttempts) {
                    log.error("{} failed after {} attempts", operationName, maxAttempts, e);
                    throw new RuntimeException(operationName + " failed after retries", e);
                }
                long delayMs = initialDelayMs * (long) Math.pow(2, attempt - 1);
                log.warn("{} failed on attempt {}, retrying in {}ms", operationName, attempt, delayMs, e);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }
    }
}
