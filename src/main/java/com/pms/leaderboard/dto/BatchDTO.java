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
    public UUID pid;
    public BigDecimal score;
    public long rank;
    public MessageDTO m;

}
