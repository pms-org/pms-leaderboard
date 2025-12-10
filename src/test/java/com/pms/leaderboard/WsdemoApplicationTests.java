package com.pms.leaderboard;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Context load test disabled until Kafka test config is set up")
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:testdb"
})
class WsdemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
