package com.pms.leaderboard.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pms.leaderboard.dto.BatchDTO;
import com.pms.leaderboard.entities.Leaderboard_Snapshot;
import com.pms.leaderboard.exceptions.DataValidationException;
import com.pms.leaderboard.exceptions.TransientDbException;
import com.pms.leaderboard.repositories.LeaderboardSnapshotRepository;

import jakarta.persistence.QueryTimeoutException;

@Service
public class PersistSnapshot {

    private static final Logger log =
            LoggerFactory.getLogger(PersistSnapshot.class);

    @Autowired
    private LeaderboardSnapshotRepository snapshotRepo;

    @Autowired
    private DbHealth dbHealth;

    @Transactional
    public void persistSnapshot(List<BatchDTO> rows) {

        try {
            Instant stamp = Instant.now();
            List<Leaderboard_Snapshot> snapshots = new ArrayList<>();

            for (BatchDTO r : rows) {

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

            if (!dbHealth.isAvailable()) {
                dbHealth.up();
            }

            log.info("✅ DB COMMIT OK rows={}", snapshots.size());

        }
        // ---------- DATA ERRORS (NO RETRY) ----------
        catch (DataIntegrityViolationException e) {
            log.error("❌ Data integrity violation", e);
            throw new DataValidationException("Invalid leaderboard data");
        }
        // ---------- TRANSIENT DB ERRORS (RETRY) ----------
        catch (CannotAcquireLockException |
               QueryTimeoutException e) {

            log.warn("🔁 Transient DB failure", e);
            throw new TransientDbException(e);
        }
        // ---------- UNKNOWN DB ERRORS (TREAT AS TRANSIENT) ----------
        catch (Exception e) {
            dbHealth.down();
            log.error("❌ Unknown DB failure", e);
            throw new TransientDbException(e);
        }
    }
}
