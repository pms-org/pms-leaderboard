// package com.pms.leaderboard.controllers;

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.web.bind.annotation.PostMapping;
// import org.springframework.web.bind.annotation.RequestBody;
// import org.springframework.web.bind.annotation.RestController;
// import com.pms.leaderboard.Handler.WebSocketHandler;

// @RestController
// public class TestController {

//     @Autowired
//     WebSocketHandler handler;

//     @PostMapping("/test")
//     public String sendTestMessage(@RequestBody String message) throws Exception {
//         handler.broadcastSnapshot(message);
//         return "Message sent";
//     }
// }
