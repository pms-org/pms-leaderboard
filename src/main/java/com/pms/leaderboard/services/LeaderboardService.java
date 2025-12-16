package com.pms.leaderboard.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.Handler.WebSocketHandler;
import com.pms.leaderboard.dto.BatchDTO;
import com.pms.leaderboard.dto.MessageDTO;

@Service
public class LeaderboardService {

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private WebSocketHandler wsHandler;

    @Autowired
    private RedisScoreService redisScoreService;

    @Autowired
    private PersistSnapshot persistSnapshotService;

    @Autowired
    private RedisLeaderboardScript rscript;

    private static final String ZKEY = "leaderboard:global:daily";
    private static final String HKEY_PREFIX = "leaderboard:portfolio:";

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

    // =========================
    // KAFKA BATCH PROCESSING
    // =========================
    public void processBatch(List<MessageDTO> batchList) {

        if (batchList == null || batchList.isEmpty()) return;

        // Deduplicate by portfolio
        Map<UUID, MessageDTO> latest = new HashMap<>();
        for (MessageDTO m : batchList) {
            latest.put(m.getPortfolioId(), m);
        }

        List<BatchDTO> snapshotRows = new ArrayList<>();
        List<MessageDTO> failed = new ArrayList<>();

        for (MessageDTO m : latest.values()) {
            try {
                UUID pid = m.getPortfolioId();
                BigDecimal score = computeScore(m);

                double redisScore = redisScoreService.compositeScore(
                        score, Instant.now(), pid
                );

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
                redis.opsForHash().put(hkey, "sharpe", m.getSharpeRatio().toString());
                redis.opsForHash().put(hkey, "sortino", m.getSortinoRatio().toString());
                redis.opsForHash().put(hkey, "avgReturn", m.getAvgRateOfReturn().toString());
                redis.opsForHash().put(hkey, "updatedAt", Instant.now().toString());

                snapshotRows.add(new BatchDTO(pid, score, rank + 1, m));

            } catch (Exception ex) {
                log.error("Failed processing portfolio {}", m.getPortfolioId(), ex);
                failed.add(m);
            }
        }

        // DB snapshot should NEVER block Kafka
        if (!snapshotRows.isEmpty()) {
            try {
                persistSnapshotService.persistSnapshot(snapshotRows);
            } catch (Exception e) {
                log.error("DB snapshot failed — Redis remains source of truth", e);
            }
        }

        // WebSocket is best-effort
        try {
            wsHandler.broadcast(fetchTop(50));
        } catch (Exception e) {
            log.warn("WebSocket broadcast failed", e);
        }

        if (!failed.isEmpty()) {
            log.warn("Failed portfolios count = {}", failed.size());
        }
    }

    // =========================
    // REDIS-ONLY READ APIs
    // =========================
    public Map<String, Object> getTop(int n) {
        return fetchTop(n);
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

        Set<ZSetOperations.TypedTuple<String>> slice =
                redis.opsForZSet().reverseRangeWithScores(ZKEY, start, end);

        List<Map<String, Object>> rows = new ArrayList<>();
        long rank = start + 1;

        if (slice != null) {
            for (var t : slice) {
                String pid = t.getValue();
                Map<Object, Object> h =
                        redis.opsForHash().entries(HKEY_PREFIX + pid);

                Map<String, Object> r = new HashMap<>();
                r.put("rank", rank++);
                r.put("portfolioId", pid);
                r.put("composite score", t.getScore());
                // r.put("score", h.get("score"));
                r.put("sharpe", h.get("sharpe"));
                r.put("sortino", h.get("sortino"));
                r.put("avgReturn", h.get("avgReturn"));
                r.put("updatedAt", h.get("updatedAt"));

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

    // =========================
    // INTERNAL HELPERS
    // =========================
    private Map<String, Object> fetchTop(int n) {

        Set<ZSetOperations.TypedTuple<String>> top =
                redis.opsForZSet().reverseRangeWithScores(ZKEY, 0, n - 1);

        List<Map<String, Object>> rows = new ArrayList<>();
        int rank = 1;

        if (top != null) {
            for (var t : top) {
                String pid = t.getValue();
                Map<Object, Object> h =
                        redis.opsForHash().entries(HKEY_PREFIX + pid);

                Map<String, Object> r = new HashMap<>();
                r.put("rank", rank++);
                r.put("portfolioId", pid);
                r.put("composite score", t.getScore());
                // r.put("score", h.get("score"));
                r.put("sharpe", h.get("sharpe"));
                r.put("sortino", h.get("sortino"));
                r.put("avgReturn", h.get("avgReturn"));
                r.put("updatedAt", h.get("updatedAt"));

                rows.add(r);
            }
        }

        return Map.of(
                "event", "leaderboardSnapshot",
                "timestamp", Instant.now().toEpochMilli(),
                "top", rows
        );
    }

    private BigDecimal computeScore(MessageDTO e) {
        return e.getAvgRateOfReturn().multiply(BigDecimal.valueOf(100))
                .add(e.getSharpeRatio().multiply(BigDecimal.valueOf(50)))
                .add(e.getSortinoRatio().multiply(BigDecimal.valueOf(10)));
    }
}
