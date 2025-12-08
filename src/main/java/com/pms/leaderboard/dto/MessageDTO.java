package com.pms.leaderboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageDTO {
    private UUID portfolioId;
    private BigDecimal sharpeRatio;
    private BigDecimal sortinoRatio;
    private BigDecimal avgRateOfReturn;
    private LocalDateTime timeStamp;
}
