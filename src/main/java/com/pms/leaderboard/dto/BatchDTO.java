package com.pms.leaderboard.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@AllArgsConstructor 
@NoArgsConstructor
public class BatchDTO {
    private UUID pid;
    private BigDecimal score;
    private long rank;
    private BigDecimal avgRateOfReturn;
    private BigDecimal sharpeRatio;
    private BigDecimal sortinoRatio;

}
