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
@Table(name = "Leaderboard_Snapshot")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Leaderboard_Snapshot {
    
    @Id
    @Column(updatable = false, nullable = false)
    private UUID historyId = UUID.randomUUID();

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "portfolio_score")
    private BigDecimal portfolioScore;

    @Column(name = "leaderboard_ranking")
    private Long leaderboardRanking;

    @Column(name = "avg_rate_of_return")
    private  BigDecimal avgRateOfReturn;

    @Column(name = "share_ratio")
    private BigDecimal sharpeRatio;

    @Column(name = "sortino_ratio")
    private BigDecimal sortinoRatio;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
