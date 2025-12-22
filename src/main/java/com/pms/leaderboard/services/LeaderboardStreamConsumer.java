package com.pms.leaderboard.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import static org.springframework.data.redis.connection.stream.StreamOffset.create;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.pms.leaderboard.dto.BatchDTO;

import jakarta.annotation.PostConstruct;

@Service
public class LeaderboardStreamConsumer {

    private static final Logger log =
        LoggerFactory.getLogger(LeaderboardStreamConsumer.class);


    private static final String STREAM_KEY = "leaderboard:stream";
    private static final String GROUP = "leaderboard-db-group";
    private static final String CONSUMER = "db-writer-1";

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private PersistSnapshot persistSnapshotService;

    @PostConstruct
    public void initGroup() {
        try {
            redis.opsForStream().createGroup(STREAM_KEY, GROUP);
        } catch (Exception e) {
            // group already exists → ignore
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void consume() {

        List<MapRecord<String, Object, Object>> records
                = redis.opsForStream().read(Consumer.from(GROUP, CONSUMER),
                        StreamReadOptions.empty().count(100),
                        create(STREAM_KEY, ReadOffset.lastConsumed())
                );

        if (records == null || records.isEmpty()) {
            return;
        }

        List<BatchDTO> batch = new ArrayList<>();

        for (MapRecord<String, Object, Object> r : records) {
            Map<Object, Object> v = r.getValue();

            BatchDTO dto = new BatchDTO(
                    UUID.fromString(v.get("portfolioId").toString()),
                    new BigDecimal(v.get("score").toString()),
                    Long.parseLong(v.get("rank").toString()),
                    null // metrics already included
            );

            batch.add(dto);
        }

        try {
            persistSnapshotService.persistSnapshot(batch);

            // ACK only after DB success
            String[] ids = records.stream()
                    .map(r -> r.getId().getValue())
                    .toArray(String[]::new);

            redis.opsForStream().acknowledge(STREAM_KEY, GROUP, ids);

        } catch (Exception e) {
            log.error(
                    "Failed to persist leaderboard snapshot. Will retry via Redis Stream. batchSize={}",
                    batch.size(),
                    e
            );
        }
    }

}
