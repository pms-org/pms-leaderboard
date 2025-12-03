package com.pms.leaderboard.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.pms.leaderboard.entities.Leaderboard_Snapshot;

@Repository
public interface LeaderboardSnapshotRepository extends JpaRepository<Leaderboard_Snapshot, UUID> {
    
}