package com.pms.leaderboard.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.entities.Leaderboard;
import com.pms.leaderboard.entities.Leaderboard_Snapshot;
import com.pms.leaderboard.repositories.LeaderboardRepository;
import com.pms.leaderboard.repositories.LeaderboardSnapshotRepository;

@Service
public class PersistenceService {

    @Autowired LeaderboardRepository currentRepo;
    @Autowired LeaderboardSnapshotRepository snapshotRepo;

    public void saveCurrent(Leaderboard entity) { currentRepo.save(entity); }
    public void saveSnapshot(Leaderboard_Snapshot snap) { snapshotRepo.save(snap); }
    
}
