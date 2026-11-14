package com.example.brainblocks;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 2026-11-14: on an in-memory database - it used to boot against the real MySQL on this PC (it only
 * added empty new tables there, but a test must never touch real data).
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:contextloads;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa", "spring.datasource.password=", "spring.config.import=", "brainblocks.rooms-file=off"})
class BrainblocksApplicationTests {

	@Test
	void contextLoads() {
	}

}
