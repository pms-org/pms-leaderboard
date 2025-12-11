package com.pms.leaderboard.repositories;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.pms.leaderboard.entities.Leaderboard;

@Repository
public interface LeaderboardRepository extends JpaRepository<Leaderboard, UUID> {

    Optional<Leaderboard> findByPortfolioId(UUID pid);

    int countByPortfolioScoreGreaterThan(BigDecimal score);

    List<Leaderboard> findTop50ByOrderByPortfolioScoreDesc();
    
}
