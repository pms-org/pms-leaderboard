package com.pms.leaderboard.services;

import java.util.List;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.pms.leaderboard.Handler.WebSocketHandler;
import com.pms.leaderboard.dto.LeaderboardDTO;

@Component
public class LeaderboardSnapshotScheduler {

    private static final Logger log
            = LoggerFactory.getLogger(LeaderboardSnapshotScheduler.class);

    private static final int TOP_N = 50;

    @Autowired
    LeaderboardService leaderboardService;

    @Autowired
    WebSocketHandler wsHandler;

     @Autowired
    @Qualifier("realtimeExecutor")
    private ExecutorService realtimeExecutor;

    @Scheduled(fixedRate = 250)
    public void publishSnapshot() {
        realtimeExecutor.submit(() -> {
            try {
                List<LeaderboardDTO> top = leaderboardService.fetchTop(TOP_N);
                wsHandler.broadcastSnapshot(top);
            } catch (Exception e) {
                log.warn("Snapshot publish failed", e);
            }
        });
    }

}
