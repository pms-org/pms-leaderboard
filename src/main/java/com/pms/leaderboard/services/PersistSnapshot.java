package com.pms.leaderboard.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pms.leaderboard.dto.BatchDTO;
import com.pms.leaderboard.entities.Leaderboard_Snapshot;
import com.pms.leaderboard.repositories.LeaderboardSnapshotRepository;

@Service
public class PersistSnapshot {

    private static final Logger log =
            LoggerFactory.getLogger(PersistSnapshot.class);


    @Autowired
    LeaderboardSnapshotRepository snapshotRepo;

    // // @Async("dbExecutor")
    // @Transactional
    // public CompletableFuture<Void> persistSnapshot(List<BatchDTO> rows) {
    //     Instant stamp = Instant.now();
    //     List<Leaderboard_Snapshot> snapshots = new ArrayList<>();
    //     for (BatchDTO r : rows) {
    //         if (r.getAvgRateOfReturn() == null ||
    //             r.getSharpeRatio() == null ||
    //             r.getSortinoRatio() == null) {
    //             throw new IllegalStateException(
    //                 "Snapshot contains null metrics for portfolio " + r.getPid()
    //             );
    //         }
    //         Leaderboard_Snapshot snap = new Leaderboard_Snapshot();
    //         snap.setHistoryId(UUID.randomUUID());
    //         snap.setPortfolioId(r.getPid());
    //         snap.setPortfolioScore(r.getScore());
    //         snap.setLeaderboardRanking(r.getRank());
    //         snap.setAvgRateOfReturn(r.getAvgRateOfReturn());
    //         snap.setSharpeRatio(r.getSharpeRatio());
    //         snap.setSortinoRatio(r.getSortinoRatio());
    //         snap.setUpdatedAt(stamp);
    //         snapshots.add(snap);
    //     }
    //     snapshotRepo.saveAll(snapshots);
    //     return CompletableFuture.completedFuture(null);
    // }
    @Transactional
    public void persistSnapshot(List<BatchDTO> rows) {

        log.info("PersistSnapshot rows={}", rows.size());


        Instant stamp = Instant.now();
        List<Leaderboard_Snapshot> snapshots = new ArrayList<>();

        for (BatchDTO r : rows) {

            log.debug(
                "Persisting pid={} score={} rank={} sharpe={} sortino={} avgReturn={}",
                r.getPid(),
                r.getScore(),
                r.getRank(),
                r.getSharpeRatio(),
                r.getSortinoRatio(),
                r.getAvgRateOfReturn()
            );

            // hard guard
            if (r.getAvgRateOfReturn() == null
                    || r.getSharpeRatio() == null
                    || r.getSortinoRatio() == null) {
                throw new IllegalStateException("NULL metrics for " + r.getPid());
            }

            Leaderboard_Snapshot snap = new Leaderboard_Snapshot();
            snap.setHistoryId(UUID.randomUUID());
            snap.setPortfolioId(r.getPid());
            snap.setPortfolioScore(r.getScore());
            snap.setLeaderboardRanking(r.getRank());
            snap.setAvgRateOfReturn(r.getAvgRateOfReturn());
            snap.setSharpeRatio(r.getSharpeRatio());
            snap.setSortinoRatio(r.getSortinoRatio());
            snap.setUpdatedAt(stamp);

            snapshots.add(snap);
        }

        snapshotRepo.saveAll(snapshots);
        log.info("DB COMMIT successful rows={}", snapshots.size());
    
        // commit happens HERE or rollback happens HERE
    }

}
