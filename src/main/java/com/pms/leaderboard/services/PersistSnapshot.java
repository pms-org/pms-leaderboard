package com.pms.leaderboard.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.dto.BatchDTO;
import com.pms.leaderboard.entities.Leaderboard_Snapshot;
import com.pms.leaderboard.repositories.LeaderboardSnapshotRepository;

import jakarta.transaction.Transactional;

@Service
public class PersistSnapshot {

    @Autowired
    LeaderboardSnapshotRepository snapshotRepo;

    @Transactional
    public void persistSnapshot(List<BatchDTO> rows) {

        Instant stamp = Instant.now();
        List<Leaderboard_Snapshot> snapshots = new ArrayList<>();

        for (BatchDTO r : rows) {
            Leaderboard_Snapshot snap = new Leaderboard_Snapshot();

            snap.setHistoryId(UUID.randomUUID());
            snap.setPortfolioId(r.pid);
            snap.setPortfolioScore(r.score);
            snap.setLeaderboardRanking(r.rank);
            snap.setUpdatedAt(stamp);

            snapshots.add(snap);
        }

        snapshotRepo.saveAll(snapshots);
    }

}
