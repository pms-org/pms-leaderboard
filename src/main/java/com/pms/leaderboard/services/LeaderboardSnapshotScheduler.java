package com.pms.leaderboard.services;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pms.leaderboard.Handler.WebSocketHandler;
import com.pms.leaderboard.dto.LeaderboardDTO;
import com.pms.leaderboard.dto.MessageDTO;
import com.pms.leaderboard.services.LeaderboardService;

@Component
public class LeaderboardSnapshotScheduler {

    private static final Logger log
            = LoggerFactory.getLogger(LeaderboardSnapshotScheduler.class);

    private static final int TOP_N = 50;

    @Autowired
    LeaderboardService leaderboardService;

    @Autowired
    WebSocketHandler wsHandler;

    @Scheduled(fixedRate = 250)
    public void publishSnapshot() {
        try {
            List<LeaderboardDTO> top = leaderboardService.fetchTop(TOP_N);
            wsHandler.broadcastSnapshot(top);

            log.debug("Leaderboard snapshot sent: {} rows", top.size());
        } catch (Exception e) {
            log.warn("Failed to publish leaderboard snapshot", e);
        }
    }
}
