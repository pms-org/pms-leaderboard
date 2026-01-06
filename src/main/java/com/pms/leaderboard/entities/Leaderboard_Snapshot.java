package com.pms.leaderboard.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "leaderboard_snapshot")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Leaderboard_Snapshot {
    
    @Id
    @Column(updatable = false, nullable = false)
    private UUID historyId = UUID.randomUUID();

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "portfolio_score", nullable= false)
    private BigDecimal portfolioScore;

    @Column(name = "leaderboard_ranking", nullable= false)
    private Long leaderboardRanking;

    @Column(name = "avg_rate_of_return", nullable = false)
    private BigDecimal avgRateOfReturn;

    @Column(name = "sharpe_ratio", nullable = false)
    private BigDecimal sharpeRatio;

    @Column(name = "sortino_ratio", nullable = false)
    private BigDecimal sortinoRatio;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
