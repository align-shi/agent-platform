package com.agentplatform.hub;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HubApplication {

	public static void main(String[] args) throws IOException {
		String dataDir = System.getProperty("agent-platform.data-dir", "data");
		Files.createDirectories(Path.of(dataDir));
		SpringApplication.run(HubApplication.class, args);
	}

}
