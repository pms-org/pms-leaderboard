package com.pms.leaderboard.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LeaderboardDTO {
    
    private long rank;
    private UUID portfolioId;
    private double compositeScore;
    private BigDecimal avgReturn;
    private BigDecimal sharpe;
    private BigDecimal sortino;
    private String updated;
}
