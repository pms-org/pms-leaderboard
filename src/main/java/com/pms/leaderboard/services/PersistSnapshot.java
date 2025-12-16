package com.pms.leaderboard.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.dto.BatchDTO;
import com.pms.leaderboard.dto.MessageDTO;
import com.pms.leaderboard.entities.Leaderboard;
import com.pms.leaderboard.entities.Leaderboard_Snapshot;
import com.pms.leaderboard.exceptions.DatabaseWriteException;
import com.pms.leaderboard.repositories.LeaderboardRepository;
import com.pms.leaderboard.repositories.LeaderboardSnapshotRepository;

import jakarta.transaction.Transactional;

@Service
public class PersistSnapshot {

    @Autowired
    LeaderboardSnapshotRepository snapshotRepo;

    @Autowired
    LeaderboardRepository currentRepo;

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

    @Transactional
    public void persistSnapshot(List<BatchDTO> rows) {
        List<Leaderboard> currents = new ArrayList<>();
        List<Leaderboard_Snapshot> snapshots = new ArrayList<>();

        Instant now = Instant.now();

        for (BatchDTO r : rows) {

            Leaderboard cur = currentRepo.findByPortfolioId(r.pid)
                    .orElseGet(() -> {
                        Leaderboard lb = new Leaderboard();
                        lb.setPortfolioId(r.pid);
                        return lb;
                    });

            cur.setPortfolioScore(r.score);
            cur.setLeaderboardRanking(r.rank);
            cur.setAvgRateOfReturn(r.m.getAvgRateOfReturn());
            cur.setSharpeRatio(r.m.getSharpeRatio());
            cur.setSortinoRatio(r.m.getSortinoRatio());
            cur.setUpdatedAt(now);

            currents.add(cur);

            Leaderboard_Snapshot snap = new Leaderboard_Snapshot();
            snap.setHistoryId(UUID.randomUUID());
            snap.setPortfolioId(r.pid);
            snap.setPortfolioScore(r.score);
            snap.setLeaderboardRanking(r.rank);
            snap.setAvgRateOfReturn(r.m.getAvgRateOfReturn());
            snap.setSharpeRatio(r.m.getSharpeRatio());
            snap.setSortinoRatio(r.m.getSortinoRatio());
            snap.setUpdatedAt(now);

            snapshots.add(snap);
        }

        currentRepo.saveAll(currents);
        snapshotRepo.saveAll(snapshots);
    }
}
