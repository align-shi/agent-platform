package com.agentplatform.hub;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HubApplicationTests {

	@BeforeAll
	static void dataDir() throws Exception {
		Files.createDirectories(Path.of("data-test"));
	}

	@Test
	void contextLoads() {
	}

}
