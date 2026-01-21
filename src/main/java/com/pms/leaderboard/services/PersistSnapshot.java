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
    
    }

}
