package com.agentplatform.hub.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfig {

	@Bean
	SecretCipher secretCipher(
			@Value("${agent-platform.data-dir}") String dataDir,
			@Value("${agent-platform.master-key:}") String envKey) throws IOException {
		byte[] raw = resolveKey(Path.of(dataDir), envKey);
		return new SecretCipher(raw);
	}

	private static byte[] resolveKey(Path dataDir, String envKey) throws IOException {
		if (envKey != null && !envKey.isBlank()) {
			byte[] decoded = tryDecode(envKey.trim());
			if (decoded.length == 32) {
				return decoded;
			}
			throw new IllegalStateException("AGENT_PLATFORM_MASTER_KEY must be 32 bytes (raw or base64)");
		}
		Files.createDirectories(dataDir);
		Path keyFile = dataDir.resolve("master.key");
		if (Files.exists(keyFile)) {
			return tryDecode(Files.readString(keyFile).trim());
		}
		byte[] generated = new byte[32];
		new SecureRandom().nextBytes(generated);
		Files.writeString(keyFile, Base64.getEncoder().encodeToString(generated));
		return generated;
	}

	private static byte[] tryDecode(String value) {
		try {
			byte[] decoded = Base64.getDecoder().decode(value);
			if (decoded.length == 32) {
				return decoded;
			}
		}
		catch (IllegalArgumentException ignored) {
			// fall through to raw bytes
		}
		return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

}
