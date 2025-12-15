package com.pms.leaderboard.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
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
import com.pms.leaderboard.dto.MessageDTO;
import com.pms.leaderboard.entities.Leaderboard;
import com.pms.leaderboard.entities.Leaderboard_Snapshot;
import com.pms.leaderboard.exceptions.DataAccessException;
import com.pms.leaderboard.exceptions.DatabaseWriteException;
import com.pms.leaderboard.repositories.LeaderboardRepository;
import com.pms.leaderboard.repositories.LeaderboardSnapshotRepository;

@Service
public class LeaderboardService {

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    LeaderboardRepository currentRepo;

    @Autowired
    LeaderboardSnapshotRepository snapshotRepo;

    @Autowired
    WebSocketHandler wsHandler;

    @Autowired
    RedisRecoveryService rebuildService;

    @Autowired
    RedisScoreService redisScoreService;

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

    private static final String ZKEY_DAILY_GLOBAL = "leaderboard:global:daily";

    // public void processBatch(List<MessageDTO> batchList) {

    //     if (batchList == null || batchList.isEmpty())
    //         return;

    //     String zkey = "leaderboard:global:daily";
    //     ZSetOperations<String, String> zset = redis.opsForZSet();

    //     Map<UUID, MessageDTO> latest = new HashMap<>();
    //     for (MessageDTO m : batchList)
    //         latest.put(m.getPortfolioId(), m);

    //     for (MessageDTO m : latest.values()) {

    //         UUID pid = m.getPortfolioId();
    //         BigDecimal score = computeScore(m);

    //         Instant stamp = (m.getTimeStamp() == null)
    //                 ? Instant.now()
    //                 : m.getTimeStamp().atZone(ZoneOffset.UTC).toInstant();

    //         double rScore = redisScoreService.compositeScore(score, stamp, pid);

    //         long rank;

    //         // writing in redis
    //         if (rebuildService.isRedisHealthy()) {
    //             try {
    //                 zset.add(zkey, pid.toString(), rScore);
    //                 Long r = zset.reverseRank(zkey, pid.toString());
    //                 rank = (r == null) ? -1 : r + 1;
    //             } catch (Exception ex) {
    //                 rebuildService.markRedisDown();
    //                 log.error("Redis failed for portfolio {}", pid, ex);
    //                 rank = computeRankFromDB(score);
    //             }
    //         } else {
    //             rank = computeRankFromDB(score);
    //         }

    //         try {
    //             Leaderboard cur = currentRepo.findByPortfolioId(pid).orElseGet(() -> {
    //                 Leaderboard lb = new Leaderboard();
    //                 lb.setPortfolioId(pid);
    //                 return lb;
    //             });

    //             cur.setPortfolioScore(score);
    //             cur.setLeaderboardRanking(rank);
    //             cur.setAvgRateOfReturn(m.getAvgRateOfReturn());
    //             cur.setSharpeRatio(m.getSharpeRatio());
    //             cur.setSortinoRatio(m.getSortinoRatio());
    //             cur.setUpdatedAt(Instant.now());
    //             currentRepo.save(cur);

    //             Leaderboard_Snapshot snap = new Leaderboard_Snapshot();
    //             snap.setHistoryId(UUID.randomUUID());
    //             snap.setPortfolioId(pid);
    //             snap.setPortfolioScore(score);
    //             snap.setLeaderboardRanking(rank);
    //             snap.setAvgRateOfReturn(m.getAvgRateOfReturn());
    //             snap.setSharpeRatio(m.getSharpeRatio());
    //             snap.setSortinoRatio(m.getSortinoRatio());
    //             snap.setUpdatedAt(Instant.now());
    //             snapshotRepo.save(snap);

    //         } catch (Exception ex) {
    //             throw new DatabaseWriteException("DB write failed for portfolio " + pid, ex);
    //         }
    //     }

    //     broadcastLeaderboard(zkey);
    // }

    public void processBatch(List<MessageDTO> batchList) {

        if (batchList == null || batchList.isEmpty()) return;

        ZSetOperations<String, String> zset = redis.opsForZSet();

        // last write wins per portfolio in the batch
        Map<UUID, MessageDTO> latest = new HashMap<>();
        for (MessageDTO m : batchList) {
            if (m != null && m.getPortfolioId() != null) {
                latest.put(m.getPortfolioId(), m);
            }
        }

        for (MessageDTO m : latest.values()) {

            UUID pid = m.getPortfolioId();
            BigDecimal score = computeScore(m);

            Instant stamp = (m.getTimeStamp() == null)
                    ? Instant.now()
                    : m.getTimeStamp().atZone(ZoneOffset.UTC).toInstant();

            double compositeScore = redisScoreService.compositeScore(score, stamp, pid);

            long rank;

            // 1) Redis attempt (if healthy)
            if (rebuildService.isRedisHealthy()) {
                try {
                    zset.add(ZKEY_DAILY_GLOBAL, pid.toString(), compositeScore);
                    Long r = zset.reverseRank(ZKEY_DAILY_GLOBAL, pid.toString());
                    rank = (r == null) ? -1 : r + 1;
                } catch (Exception ex) {
                    rebuildService.markRedisDown();
                    log.error("Redis write/rank failed for portfolio {}", pid, ex);
                    rank = computeRankFromDB(score);
                }
            } else {
                // 2) DB fallback rank calculation
                rank = computeRankFromDB(score);
            }

            // 3) Always persist to DB (source of truth)
            try {
                Leaderboard cur = currentRepo.findByPortfolioId(pid).orElseGet(() -> {
                    Leaderboard lb = new Leaderboard();
                    lb.setPortfolioId(pid);
                    return lb;
                });

                cur.setPortfolioScore(score);
                cur.setLeaderboardRanking(rank);
                cur.setAvgRateOfReturn(m.getAvgRateOfReturn());
                cur.setSharpeRatio(m.getSharpeRatio());
                cur.setSortinoRatio(m.getSortinoRatio());
                cur.setUpdatedAt(Instant.now());
                currentRepo.save(cur);

                Leaderboard_Snapshot snap = new Leaderboard_Snapshot();
                snap.setHistoryId(UUID.randomUUID());
                snap.setPortfolioId(pid);
                snap.setPortfolioScore(score);
                snap.setLeaderboardRanking(rank);
                snap.setAvgRateOfReturn(m.getAvgRateOfReturn());
                snap.setSharpeRatio(m.getSharpeRatio());
                snap.setSortinoRatio(m.getSortinoRatio());
                snap.setUpdatedAt(Instant.now());
                snapshotRepo.save(snap);

            } catch (Exception ex) {
                throw new DatabaseWriteException("DB write failed for portfolio " + pid, ex);
            }
        }

        broadcastLeaderboard();
    }

    private long computeRankFromDB(BigDecimal score) {
        try {
            return currentRepo.countByPortfolioScoreGreaterThan(score) + 1;
        } catch (Exception e) {
            log.error(" DB rank fallback failed!", e);
            return -1;
        }
    }

    // private void broadcastLeaderboard(String key) {
    // List<Map<String, Object>> response;

    // if (!rebuildService.isRedisHealthy()) {
    // try {
    // response = fetchTopFromRedis(key);
    // wsHandler.broadcast(buildEnvelope(response));
    // return;
    // } catch (Exception e) {
    // rebuildService.markRedisDown();
    // log.error("Redis fetch failed → falling back to DB");
    // }

    // try {
    // wsHandler.broadcast(buildEnvelope(fetchTopFromDB()));
    // } catch (Exception ex) {
    // throw new WebSocketBroadcastException("Leaderboard broadcast failed", ex);
    // }
    // }
    // }

    // private void broadcastLeaderboard(String key) {
    //     try {
    //         if (rebuildService.isRedisHealthy()) {
    //             wsHandler.broadcast(buildEnvelope(fetchTopFromRedis(key)));
    //         } else {
    //             wsHandler.broadcast(buildEnvelope(fetchTopFromDB()));
    //         }
    //     } catch (Exception e) {
    //         rebuildService.markRedisDown();
    //         wsHandler.broadcast(buildEnvelope(fetchTopFromDB()));
    //     }
    // }
    private void broadcastLeaderboard() {
        try {
            if (rebuildService.isRedisHealthy()) {
                wsHandler.broadcast(buildEnvelope(fetchTopFromRedis(ZKEY_DAILY_GLOBAL, 50)));
            } else {
                wsHandler.broadcast(buildEnvelope(fetchTopFromDB(50)));
            }
        } catch (Exception e) {
            rebuildService.markRedisDown();
            wsHandler.broadcast(buildEnvelope(fetchTopFromDB(50)));
        }
    }

    private Map<String, Object> buildEnvelope(List<Map<String, Object>> response) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event", "leaderboardSnapshot");
        envelope.put("timestamp", Instant.now().toEpochMilli());
        envelope.put("top", response);
        return envelope;
    }

    private Map<String, Object> toRow(long rank, UUID pid, Double compositeScore, Leaderboard lb) {
        Map<String, Object> r = new HashMap<>();
        r.put("rank", rank);
        r.put("portfolioId", pid.toString());
        r.put("compositeScore", compositeScore);

        if (lb != null) {
            r.put("avgReturn", lb.getAvgRateOfReturn());
            r.put("sharpe", lb.getSharpeRatio());
            r.put("sortino", lb.getSortinoRatio());
            r.put("updatedAt", lb.getUpdatedAt());
        }
        return r;
    }

    // private List<Map<String, Object>> fetchTopFromRedis(String key) {
    //     ZSetOperations<String, String> zset = redis.opsForZSet();
    //     Set<ZSetOperations.TypedTuple<String>> top = zset.reverseRangeWithScores(key, 0, 49);

    //     List<Map<String, Object>> response = new ArrayList<>();
    //     if (top == null)
    //         return response;

    //     int rank = 1;
    //     for (ZSetOperations.TypedTuple<String> t : top) {
    //         UUID pid = UUID.fromString(t.getValue());
    //         Map<String, Object> r = new HashMap<>();

    //         r.put("rank", rank++);
    //         r.put("portfolioId", pid.toString());
    //         r.put("compositeScore", t.getScore());

    //         currentRepo.findByPortfolioId(pid).ifPresent(lb -> {
    //             r.put("avgReturn", lb.getAvgRateOfReturn());
    //             r.put("sharpe", lb.getSharpeRatio());
    //             r.put("sortino", lb.getSortinoRatio());
    //             r.put("updatedAt", lb.getUpdatedAt());
    //         });

    //         response.add(r);
    //     }

    //     return response;
    // }

        private List<Map<String, Object>> fetchTopFromRedis(String key, int topN) {
        ZSetOperations<String, String> zset = redis.opsForZSet();
        Set<ZSetOperations.TypedTuple<String>> top = zset.reverseRangeWithScores(key, 0, topN - 1);

        List<Map<String, Object>> response = new ArrayList<>();
        if (top == null) return response;

        long rank = 1;
        for (ZSetOperations.TypedTuple<String> t : top) {
            if (t == null || t.getValue() == null) continue;

            UUID pid = UUID.fromString(t.getValue());
            Leaderboard lb = currentRepo.findByPortfolioId(pid).orElse(null);

            response.add(toRow(rank++, pid, t.getScore(), lb));
        }
        return response;
    }

    // private List<Map<String, Object>> fetchTopFromDB() {
    //     List<Leaderboard> rows = currentRepo.findTop50ByOrderByPortfolioScoreDesc();
    //     List<Map<String, Object>> list = new ArrayList<>();
    //     int rank = 1;

    //     for (Leaderboard lb : rows) {
    //         Map<String, Object> r = new HashMap<>();
    //         r.put("rank", rank++);
    //         r.put("portfolioId", lb.getPortfolioId());
    //         r.put("score", lb.getPortfolioScore());
    //         r.put("avgReturn", lb.getAvgRateOfReturn());
    //         r.put("sharpe", lb.getSharpeRatio());
    //         r.put("sortino", lb.getSortinoRatio());
    //         r.put("updatedAt", lb.getUpdatedAt());
    //         list.add(r);
    //     }

    //     return list;
    // }
        private List<Map<String, Object>> fetchTopFromDB(int topN) {
        // You already have findTop50ByOrderByPortfolioScoreDesc().
        // For dynamic topN, either:
        // 1) add a Pageable repo method, OR
        // 2) keep top50 and subList. Here: safe subList.
        List<Leaderboard> rows = currentRepo.findTop50ByOrderByPortfolioScoreDesc();
        if (rows == null) rows = Collections.emptyList();

        if (rows.size() > topN) {
            rows = rows.subList(0, topN);
        }

        List<Map<String, Object>> list = new ArrayList<>();
        long rank = 1;

        for (Leaderboard lb : rows) {
            if (lb == null || lb.getPortfolioId() == null || lb.getPortfolioScore() == null) continue;

            Instant stamp = (lb.getUpdatedAt() != null) ? lb.getUpdatedAt() : Instant.now();

            double composite = redisScoreService.compositeScore(
                    lb.getPortfolioScore(),
                    stamp,
                    lb.getPortfolioId()
            );

            list.add(toRow(rank++, lb.getPortfolioId(), composite, lb));
        }

        return list;
    }


    private BigDecimal computeScore(MessageDTO e) {
        return e.getAvgRateOfReturn().multiply(BigDecimal.valueOf(100))
                .add(e.getSharpeRatio().multiply(BigDecimal.valueOf(50)))
                .add(e.getSortinoRatio().multiply(BigDecimal.valueOf(10)));
    }

    // public List<Map<String, Object>> getTop(String boardKey, int topN) {
    //     ZSetOperations<String, String> zset = redis.opsForZSet();
    //     Set<ZSetOperations.TypedTuple<String>> top = zset.reverseRangeWithScores(boardKey, 0, topN - 1);
    //     List<Map<String, Object>> rows = new ArrayList<>();
    //     if (top != null) {
    //         int pos = 1;
    //         for (ZSetOperations.TypedTuple<String> t : top) {
    //             Map<String, Object> r = new HashMap<>();
    //             r.put("rank", pos++);
    //             r.put("portfolioId", t.getValue());
    //             r.put("scoreComposite", t.getScore());
    //             try {
    //                 UUID pid = UUID.fromString(t.getValue());
    //                 currentRepo.findByPortfolioId(pid).ifPresent(lb -> {
    //                     r.put("score", lb.getPortfolioScore());
    //                     r.put("updatedAt", lb.getUpdatedAt());
    //                 });
    //             } catch (Exception ex) {
    //                 log.error("Data could not be accessed");
    //                 throw new DataAccessException("Data could not be accessed", ex);
    //             }
    //             rows.add(r);
    //         }
    //     }
    //     return rows;
    // }
    public List<Map<String, Object>> getTop(String boardKey, int topN) {
        try {
            if (rebuildService.isRedisHealthy()) {
                return fetchTopFromRedis(boardKey, topN);
            }
        } catch (Exception ex) {
            rebuildService.markRedisDown();
        }
        return fetchTopFromDB(topN);
    }

    // public List<Map<String, Object>> getAround(String boardKey, String portfolioIdStr, int range) {
    //     ZSetOperations<String, String> zset = redis.opsForZSet();
    //     Long centerRank = zset.reverseRank(boardKey, portfolioIdStr);
    //     if (centerRank == null)
    //         return Collections.emptyList();
    //     long start = Math.max(0, centerRank - range);
    //     long end = centerRank + range;
    //     Set<ZSetOperations.TypedTuple<String>> slice = zset.reverseRangeWithScores(boardKey, start, end);
    //     List<Map<String, Object>> rows = new ArrayList<>();
    //     if (slice != null) {
    //         long pos = start + 1;
    //         for (ZSetOperations.TypedTuple<String> t : slice) {
    //             Map<String, Object> r = new HashMap<>();
    //             r.put("rank", pos++);
    //             r.put("portfolioId", t.getValue());
    //             r.put("scoreComposite", t.getScore());
    //             try {
    //                 UUID pid = UUID.fromString(t.getValue());
    //                 currentRepo.findByPortfolioId(pid).ifPresent(lb -> {
    //                     r.put("score", lb.getPortfolioScore());
    //                     r.put("updatedAt", lb.getUpdatedAt());
    //                 });
    //             } catch (Exception ex) {
    //                 log.error("Data could not be accessed");
    //                 throw new DataAccessException("Database could not access : ", ex);
    //             }
    //             rows.add(r);
    //         }
    //     }
    //     return rows;
    // }
        public List<Map<String, Object>> getAround(String boardKey, String portfolioIdStr, int range) {

        // If Redis is down: "around me" needs a DB version too.
        // This DB version uses leaderboard_score order (same as your top list)
        if (!rebuildService.isRedisHealthy()) {
            return getAroundFromDB(portfolioIdStr, range);
        }

        try {
            ZSetOperations<String, String> zset = redis.opsForZSet();
            Long centerRank = zset.reverseRank(boardKey, portfolioIdStr);
            if (centerRank == null) return Collections.emptyList();

            long start = Math.max(0, centerRank - range);
            long end = centerRank + range;

            Set<ZSetOperations.TypedTuple<String>> slice =
                    zset.reverseRangeWithScores(boardKey, start, end);

            List<Map<String, Object>> rows = new ArrayList<>();
            if (slice == null) return rows;

            long rank = start + 1;
            for (ZSetOperations.TypedTuple<String> t : slice) {
                if (t == null || t.getValue() == null) continue;

                UUID pid = UUID.fromString(t.getValue());
                Leaderboard lb = currentRepo.findByPortfolioId(pid).orElse(null);

                rows.add(toRow(rank++, pid, t.getScore(), lb));
            }
            return rows;

        } catch (Exception ex) {
            rebuildService.markRedisDown();
            return getAroundFromDB(portfolioIdStr, range);
        }
    }

    private List<Map<String, Object>> getAroundFromDB(String portfolioIdStr, int range) {
        UUID centerId;
        try {
            centerId = UUID.fromString(portfolioIdStr);
        } catch (Exception ex) {
            throw new DataAccessException("Invalid portfolioId for around-me query", ex);
        }

        // Pull enough rows to compute around-me. For now using top50, adjust if needed.
        List<Leaderboard> rows = currentRepo.findTop50ByOrderByPortfolioScoreDesc();
        if (rows == null || rows.isEmpty()) return Collections.emptyList();

        // Find index of the portfolio in this list
        int idx = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (centerId.equals(rows.get(i).getPortfolioId())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return Collections.emptyList();

        int start = Math.max(0, idx - range);
        int end = Math.min(rows.size() - 1, idx + range);

        List<Map<String, Object>> out = new ArrayList<>();
        long rank = start + 1;

        for (int i = start; i <= end; i++) {
            Leaderboard lb = rows.get(i);
            if (lb == null || lb.getPortfolioId() == null || lb.getPortfolioScore() == null) continue;

            Instant stamp = (lb.getUpdatedAt() != null) ? lb.getUpdatedAt() : Instant.now();
            double composite = redisScoreService.compositeScore(lb.getPortfolioScore(), stamp, lb.getPortfolioId());

            out.add(toRow(rank++, lb.getPortfolioId(), composite, lb));
        }

        return out;
    }
}
