// package com.pms.leaderboard.events;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.kafka.annotation.KafkaListener;
// import org.springframework.stereotype.Service;
// import com.pms.leaderboard.proto.RiskEvent;

// @Service
// public class AnalyticsDLTConsumer {

//     private static final Logger log =
//             LoggerFactory.getLogger(AnalyticsDLTConsumer.class);

//     @KafkaListener(
//         topics = "portfolio-metrics.dlt",
//         groupId = "leaderboard-dlt-group"
//     )
//     public void consumeDlt(RiskEvent event) {

//         log.error(" DLT USED! Message permanently failed: {}", event);

//         notifyOps(event);
//     }

//     private void notifyOps(RiskEvent event) {
//         System.err.println(
//             "ALERT: DLT message detected for portfolioId=" +
//             event.getPortfolioId()
//         );
//     }
// }

