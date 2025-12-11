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

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

    public void processBatch(List<MessageDTO> batchList) {

        if (batchList == null || batchList.isEmpty())
            return;

        String zkey = "leaderboard:global:daily";
        ZSetOperations<String, String> zset = redis.opsForZSet();

        Map<UUID, MessageDTO> latest = new HashMap<>();

        for (MessageDTO m : batchList) {
            latest.put(m.getPortfolioId(), m);
        }

        for (MessageDTO m : latest.values()) {

            UUID pid = m.getPortfolioId();
            BigDecimal score = computeScore(m);

            Instant stamp = (m.getTimeStamp() == null)
                    ? Instant.now()
                    : m.getTimeStamp().atZone(ZoneOffset.UTC).toInstant();

            double rScore = composeRedisScore(score, stamp, pid);

            // writing to redis if fails db
            boolean redisOk = true;
            try {
                zset.add(zkey, pid.toString(), rScore);
            } catch (Exception ex) {
                redisOk = false;
                log.error("REDIS FAILED during zset.add for portfolio {}", pid, ex);
            }

            // TRY GET RANK FROM REDIS, FALLBACK TO DB
            long rank = -1;
            if (redisOk) {
                try {
                    Long rankPos = zset.reverseRank(zkey, pid.toString());
                    rank = (rankPos == null) ? -1 : rankPos + 1;
                } catch (Exception ex) {
                    log.error("Redis rank fetch failed, fallback to DB rank for {}", pid);
                    rank = computeRankFromDB(score);
                }
            } else {
                rank = computeRankFromDB(score);
            }

            //  CURRENT UPDATE
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

            // SNAPSHOT — HISTORY DB
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

        }

        broadcastLeaderboard(zkey);
    }

    private long computeRankFromDB(BigDecimal score) {
        try {
            return currentRepo.countByPortfolioScoreGreaterThan(score) + 1;
        } catch (Exception e) {
            log.error("DB rank fallback failed!", e);
            return -1;
        }
    }

    private void broadcastLeaderboard(String key) {

        List<Map<String, Object>> response;

        try {
            response = fetchTopFromRedis(key);
        } catch (Exception e) {
            log.error("Redis fetch failed! Broadcasting top from DB instead.");
            response = fetchTopFromDB();
        }

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event", "leaderboardSnapshot");
        envelope.put("timestamp", Instant.now().toEpochMilli());
        envelope.put("top", response);

        wsHandler.broadcast(envelope);
        log.info("Broadcasted snapshot: {}", response.size());
    }

    private List<Map<String, Object>> fetchTopFromRedis(String key) {
        ZSetOperations<String, String> zset = redis.opsForZSet();
        Set<ZSetOperations.TypedTuple<String>> top = zset.reverseRangeWithScores(key, 0, 49);

        List<Map<String, Object>> response = new ArrayList<>();

        if (top == null)
            return response;

        int rank = 1;
        for (ZSetOperations.TypedTuple<String> t : top) {
            UUID pid = UUID.fromString(t.getValue());
            Map<String, Object> r = new HashMap<>();

            r.put("rank", rank++);
            r.put("portfolioId", pid.toString());
            r.put("compositeScore", t.getScore());

            currentRepo.findByPortfolioId(pid).ifPresent(lb -> {
                r.put("avgReturn", lb.getAvgRateOfReturn());
                r.put("sharpe", lb.getSharpeRatio());
                r.put("sortino", lb.getSortinoRatio());
                r.put("updatedAt", lb.getUpdatedAt());
            });

            response.add(r);
        }

        return response;
    }

    private List<Map<String, Object>> fetchTopFromDB() {
        List<Leaderboard> rows = currentRepo.findTop50ByOrderByPortfolioScoreDesc();

        List<Map<String, Object>> list = new ArrayList<>();
        int rank = 1;

        for (Leaderboard lb : rows) {
            Map<String, Object> r = new HashMap<>();
            r.put("rank", rank++);
            r.put("portfolioId", lb.getPortfolioId());
            r.put("score", lb.getPortfolioScore());
            r.put("avgReturn", lb.getAvgRateOfReturn());
            r.put("sharpe", lb.getSharpeRatio());
            r.put("sortino", lb.getSortinoRatio());
            r.put("updatedAt", lb.getUpdatedAt());
            list.add(r);
        }

        return list;
    }

    private BigDecimal computeScore(MessageDTO e) {
        return e.getAvgRateOfReturn().multiply(BigDecimal.valueOf(100.0))
                .add(e.getSharpeRatio().multiply(BigDecimal.valueOf(50.0)))
                .add(e.getSortinoRatio().multiply(BigDecimal.valueOf(10.0)));
    }

    private double composeRedisScore(BigDecimal baseScore, Instant eventTime, UUID portfolioId) {
        double base = baseScore.doubleValue();
        long ts = eventTime.toEpochMilli();
        double stampFraction = (ts % 1000) / 1e9;
        int idHash = Math.abs(portfolioId.hashCode() % 1000);
        double hashFrac = idHash / 1e12;
        return base + stampFraction + hashFrac;
    }

    public List<Map<String, Object>> getTop(String boardKey, int topN) {
        ZSetOperations<String, String> zset = redis.opsForZSet();
        Set<ZSetOperations.TypedTuple<String>> top = zset.reverseRangeWithScores(boardKey, 0, topN - 1);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (top != null) {
            int pos = 1;
            for (ZSetOperations.TypedTuple<String> t : top) {
                Map<String, Object> r = new HashMap<>();
                r.put("rank", pos++);
                r.put("portfolioId", t.getValue());
                r.put("scoreComposite", t.getScore());
                try {
                    UUID pid = UUID.fromString(t.getValue());
                    currentRepo.findByPortfolioId(pid).ifPresent(lb -> {
                        r.put("score", lb.getPortfolioScore());
                        r.put("updatedAt", lb.getUpdatedAt());
                    });
                } catch (Exception ex) {
                }
                rows.add(r);
            }
        }
        return rows;
    }

    public List<Map<String, Object>> getAround(String boardKey, String portfolioIdStr, int range) {
        ZSetOperations<String, String> zset = redis.opsForZSet();
        Long centerRank = zset.reverseRank(boardKey, portfolioIdStr);
        if (centerRank == null)
            return Collections.emptyList();
        long start = Math.max(0, centerRank - range);
        long end = centerRank + range;
        Set<ZSetOperations.TypedTuple<String>> slice = zset.reverseRangeWithScores(boardKey, start, end);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (slice != null) {
            long pos = start + 1;
            for (ZSetOperations.TypedTuple<String> t : slice) {
                Map<String, Object> r = new HashMap<>();
                r.put("rank", pos++);
                r.put("portfolioId", t.getValue());
                r.put("scoreComposite", t.getScore());
                try {
                    UUID pid = UUID.fromString(t.getValue());
                    currentRepo.findByPortfolioId(pid).ifPresent(lb -> {
                        r.put("score", lb.getPortfolioScore());
                        r.put("updatedAt", lb.getUpdatedAt());
                    });
                } catch (Exception ex) {
                }
                rows.add(r);
            }
        }
        return rows;
    }
}
