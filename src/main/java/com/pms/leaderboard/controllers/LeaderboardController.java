package com.pms.leaderboard.controllers;

import java.util.List;
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

    // GET /api/leaderboard/top?board=leaderboard:global:daily&top=100
    @GetMapping("/top")
    public List<Map<String,Object>> top(@RequestParam String board, @RequestParam(defaultValue = "100") int top) {
        return leaderboardService.getTop(board, top);
    }

    // GET /api/leaderboard/around?board=leaderboard:global:daily&portfolioId=<id>&range=10
    @GetMapping("/around")
    public List<Map<String,Object>> around(@RequestParam String board, @RequestParam String portfolioId,
                                           @RequestParam(defaultValue = "10") int range) {
        return leaderboardService.getAround(board, portfolioId, range);
    }
}
