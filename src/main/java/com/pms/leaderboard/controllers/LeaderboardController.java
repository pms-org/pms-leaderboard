package com.pms.leaderboard.controllers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pms.leaderboard.services.LeaderboardService;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    @Autowired
    private LeaderboardService leaderboardService;

    /**
     * GET /api/leaderboard/top?top=100
     */
    @GetMapping("/top")
    public Map<String, Object> getTop(
            @RequestParam(defaultValue = "50") int top) {

        return leaderboardService.getTop(top);
    }

    /**
     * GET /api/leaderboard/around?portfolioId=<uuid>&range=10
     */
    @GetMapping("/around")
    public Map<String, Object> getAround(
            @RequestParam String portfolioId,
            @RequestParam(defaultValue = "5") int range) {

        return leaderboardService.getAround(portfolioId, range);
    }
}
