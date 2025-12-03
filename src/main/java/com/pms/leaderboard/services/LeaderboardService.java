package com.pms.leaderboard.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
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

    private final ConcurrentLinkedQueue<MessageDTO> pending = new ConcurrentLinkedQueue<>();

    @Autowired
    LeaderboardRepository currentRepo;

    @Autowired
    LeaderboardSnapshotRepository snapshotRepo;

    @Autowired
    WebSocketHandler wsHandler;


      // use a local map to coalesce updates per portfolio (latest takes precedence)
    private final Map<UUID, MessageDTO> latestMap = new ConcurrentHashMap<>();


    public void enqueue(MessageDTO ev) {
        if (ev == null || ev.getPortfolioId() == null) return;
        // add to queue and map
        pending.add(ev);
        latestMap.put(ev.getPortfolioId(), ev);
    }

    // Score calculation
    private BigDecimal computeScore(MessageDTO e) {

        BigDecimal rate = e.getAvgRateOfReturn(); // already BigDecimal
        BigDecimal sharpe = e.getSharpeRatio(); // BigDecimal
        BigDecimal sortino = e.getSortinoRatio(); // BigDecimal

        return rate.multiply(BigDecimal.valueOf(100.0))
                .add(sharpe.multiply(BigDecimal.valueOf(50.0)))
                .add(sortino.multiply(BigDecimal.valueOf(10.0)));
    }
// Compose a composite double score for Redis to incorporate tie-breakers
    private double composeRedisScore(BigDecimal baseScore, Instant eventTime, UUID portfolioId) {
        double base = baseScore.doubleValue();
        long ts = (eventTime != null) ? eventTime.toEpochMilli() : Instant.now().toEpochMilli();

        // stampFraction: more recent gets slightly higher value -> (ts % 1000)/1e9
        double stampFraction = (ts % 1000) / 1e9;

        // idFraction: deterministic tiny fraction from UUID hash to fully break ties
        int idHash = Math.abs(portfolioId.hashCode() % 1000);
        double idFraction = idHash / 1e12;

        return base + stampFraction + idFraction;
    }

    // scheduled flush every 250ms — coalesce and apply updates
    @Scheduled(fixedRate = 250)
    public void flushAndProcess() {
        // drain the pending queue (we also have latestMap but draining queue keeps memory bounded)
        MessageDTO ev;
        while ((ev = pending.poll()) != null) {
            // nothing to do here because latestMap already has latest per portfolio
            // this drains the queue while latestMap holds the latest object
        }

        if (latestMap.isEmpty()) return;

        String zkey = "leaderboard:global:daily"; // example key; adjust based on context param
        ZSetOperations<String, String> zset = redis.opsForZSet();

        // capture snapshot of current batch and clear latestMap for next window
        Map<UUID, MessageDTO> batch = new HashMap<>(latestMap);
        latestMap.clear();

        // process each unique portfolio update
        for (MessageDTO m : batch.values()) {
            UUID pid = m.getPortfolioId();
            BigDecimal score = computeScore(m);
            Instant stamp = (m.getTimeStamp() == null) ? Instant.now() : m.getTimeStamp().atZone(ZoneOffset.UTC).toInstant();

            double rscore = composeRedisScore(score, stamp, pid);

            // update Redis ZSET: score ordering uses composite score
            zset.add(zkey, pid.toString(), rscore);

            // compute rank (1-based)
            Long rankPos = zset.reverseRank(zkey, pid.toString());
            long rank = (rankPos == null) ? -1L : rankPos + 1L;

            // upsert into DB current leaderboard (by portfolioId)
            Leaderboard cur = currentRepo.findByPortfolioId(pid).orElseGet(() -> {
                Leaderboard lb = new Leaderboard();
                lb.setPortfolioId(pid);
                return lb;
            });
            cur.setPortfolioScore(score);
            cur.setLeaderboardRanking(rank);
            cur.setUpdatedAt(Instant.now());
            currentRepo.save(cur);

            // save snapshot with the metrics
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

        // After processing batch, fetch Top N (example top 50) and broadcast as single snapshot
        int topN = 50;
        Set<ZSetOperations.TypedTuple<String>> top = zset.reverseRangeWithScores(zkey, 0, topN - 1);

        List<Map<String, Object>> rows = new ArrayList<>();
        if (top != null) {
            int pos = 1;
            for (ZSetOperations.TypedTuple<String> t : top) {
                String pidStr = t.getValue();
                double composite = (t.getScore() == null) ? 0.0 : t.getScore();

                Map<String, Object> r = new HashMap<>();
                r.put("rank", pos++);
                r.put("portfolioId", pidStr);
                r.put("compositeScore", composite);
                // prefer DB current row for human-friendly score & timestamp
                try {
                    UUID pid = UUID.fromString(pidStr);
                    currentRepo.findByPortfolioId(pid).ifPresent(lb -> {
                        r.put("score", lb.getPortfolioScore());
                        r.put("updatedAt", lb.getUpdatedAt());
                    });
                } catch (Exception ex) { /* ignore parse issues */ }
                rows.add(r);
            }
        }

        // broadcast one payload
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event", "leaderboardSnapshot");
        envelope.put("timestamp", Instant.now().toEpochMilli());
        envelope.put("top", rows);
        wsHandler.broadcast(envelope);

        System.out.println("Broadcasted snapshot, entries=" + rows.size());
    }

    // REST helper used by Controller: fetch top N and return list of maps
    public List<Map<String,Object>> getTop(String boardKey, int topN) {
        ZSetOperations<String, String> zset = redis.opsForZSet();
        Set<ZSetOperations.TypedTuple<String>> top = zset.reverseRangeWithScores(boardKey, 0, topN - 1);
        List<Map<String,Object>> rows = new ArrayList<>();
        if (top != null) {
            int pos = 1;
            for (ZSetOperations.TypedTuple<String> t : top) {
                Map<String, Object> r = new HashMap<>();
                r.put("rank", pos++);
                r.put("portfolioId", t.getValue());
                r.put("scoreComposite", t.getScore());
                // attach db info if present
                try {
                    UUID pid = UUID.fromString(t.getValue());
                    currentRepo.findByPortfolioId(pid).ifPresent(lb -> {
                        r.put("score", lb.getPortfolioScore());
                        r.put("updatedAt", lb.getUpdatedAt());
                    });
                } catch(Exception ex) {}
                rows.add(r);
            }
        }
        return rows;
    }

    public List<Map<String,Object>> getAround(String boardKey, String portfolioIdStr, int range) {
        ZSetOperations<String, String> zset = redis.opsForZSet();
        Long centerRank = zset.reverseRank(boardKey, portfolioIdStr);
        if (centerRank == null) return Collections.emptyList();
        long start = Math.max(0, centerRank - range);
        long end = centerRank + range;
        Set<ZSetOperations.TypedTuple<String>> slice = zset.reverseRangeWithScores(boardKey, start, end);
        List<Map<String,Object>> rows = new ArrayList<>();
        if (slice != null) {
            long pos = start + 1;
            for (ZSetOperations.TypedTuple<String> t : slice) {
                Map<String,Object> r = new HashMap<>();
                r.put("rank", pos++);
                r.put("portfolioId", t.getValue());
                r.put("scoreComposite", t.getScore());
                try {
                    UUID pid = UUID.fromString(t.getValue());
                    currentRepo.findByPortfolioId(pid).ifPresent(lb -> {
                        r.put("score", lb.getPortfolioScore());
                        r.put("updatedAt", lb.getUpdatedAt());
                    });
                } catch(Exception ex) {}
                rows.add(r);
            }
        }
        return rows;
    }
}

