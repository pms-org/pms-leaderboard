package com.pms.leaderboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class MessageDTO {

    private UUID portfolioId;
    
    private BigDecimal sharpeRatio;

    private BigDecimal sortinoRatio;

    private BigDecimal avgRateOfReturn;

    private LocalDateTime timeStamp;

    public MessageDTO(UUID portfolioId, BigDecimal sharpeRatio, BigDecimal sortinoRatio, BigDecimal avgRateOfReturn,
            LocalDateTime timeStamp) {
        this.portfolioId = portfolioId;
        this.sharpeRatio = sharpeRatio;
        this.sortinoRatio = sortinoRatio;
        this.avgRateOfReturn = avgRateOfReturn;
        this.timeStamp = timeStamp;
    }

    public UUID getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(UUID portfolioId) {
        this.portfolioId = portfolioId;
    }

    public BigDecimal getSharpeRatio() {
        return sharpeRatio;
    }

    public void setSharpeRatio(BigDecimal sharpeRatio) {
        this.sharpeRatio = sharpeRatio;
    }

    public BigDecimal getSortinoRatio() {
        return sortinoRatio;
    }

    public void setSortinoRatio(BigDecimal sortinoRatio) {
        this.sortinoRatio = sortinoRatio;
    }

    public BigDecimal getAvgRateOfReturn() {
        return avgRateOfReturn;
    }

    public void setAvgRateOfReturn(BigDecimal avgRateOfReturn) {
        this.avgRateOfReturn = avgRateOfReturn;
    }

    public LocalDateTime getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(LocalDateTime timeStamp) {
        this.timeStamp = timeStamp;
    }

    public MessageDTO() {
    }

    
    

}
