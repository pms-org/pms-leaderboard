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

    /**
     * Called once by Kafka Consumer batch for all messages in this poll
     */
    public void processBatch(List<MessageDTO> batchList) {

        if (batchList == null || batchList.isEmpty())
            return;

        String zkey = "leaderboard:global:daily";
        ZSetOperations<String, String> zset = redis.opsForZSet();

        // keep latest per portfolio within just this batch
        Map<UUID, MessageDTO> latest = new HashMap<>();

        for (MessageDTO m : batchList) {
            latest.put(m.getPortfolioId(), m);
        }

        for (MessageDTO m : latest.values()) {

            UUID pid = m.getPortfolioId();
            BigDecimal score = computeScore(m);

            Instant stamp = (m.getTimeStamp() == null) ? Instant.now()
                    : m.getTimeStamp().atZone(ZoneOffset.UTC).toInstant();

            double rScore = composeRedisScore(score, stamp, pid);

            zset.add(zkey, pid.toString(), rScore);

            // ranking
            Long rankPos = zset.reverseRank(zkey, pid.toString());
            long rank = (rankPos == null) ? -1L : rankPos + 1L;

            // Write current leaderboard row
            Leaderboard cur = currentRepo.findByPortfolioId(pid)
                    .orElseGet(() -> {
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

            // Snapshot history row
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

    private void broadcastLeaderboard(String key) {

        ZSetOperations<String, String> zset = redis.opsForZSet();
        Set<ZSetOperations.TypedTuple<String>> top = zset.reverseRangeWithScores(key, 0, 49);

        List<Map<String, Object>> response = new ArrayList<>();
        if (top != null) {
            int rank = 1;
            for (ZSetOperations.TypedTuple<String> t : top) {
                Map<String, Object> r = new HashMap<>();
                r.put("rank", rank++);
                r.put("portfolioId", t.getValue());
                r.put("compositeScore", t.getScore());
                try {
                    UUID pid = UUID.fromString(t.getValue());
                    currentRepo.findByPortfolioId(pid).ifPresent(lb -> {
                        r.put("avgReturn", lb.getAvgRateOfReturn());
                        r.put("sharpe", lb.getSharpeRatio());
                        r.put("sortino", lb.getSortinoRatio());
                        r.put("updatedAt", lb.getUpdatedAt());
                    });
                } catch (Exception ex) {
                }

                response.add(r);
            }
        }

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event", "leaderboardSnapshot");
        envelope.put("timestamp", Instant.now().toEpochMilli());
        envelope.put("top", response);

        wsHandler.broadcast(envelope);
        System.out.println("Broadcasted snapshot: " + response.size());
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
