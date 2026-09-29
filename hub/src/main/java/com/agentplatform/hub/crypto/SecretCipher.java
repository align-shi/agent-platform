package com.agentplatform.hub.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class SecretCipher {

	private static final String ALGO = "AES";
	private static final String TRANSFORM = "AES/GCM/NoPadding";
	private static final int IV_LEN = 12;
	private static final int TAG_BITS = 128;

	private final SecretKeySpec key;
	private final SecureRandom random = new SecureRandom();

	public SecretCipher(byte[] rawKey) {
		if (rawKey == null || rawKey.length != 32) {
			throw new IllegalArgumentException("Master key must be 32 bytes");
		}
		this.key = new SecretKeySpec(rawKey, ALGO);
	}

	public String encrypt(String plain) {
		try {
			byte[] iv = new byte[IV_LEN];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORM);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
			buffer.put(iv);
			buffer.put(encrypted);
			return Base64.getEncoder().encodeToString(buffer.array());
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Failed to encrypt secret", ex);
		}
	}

	public String decrypt(String payload) {
		try {
			byte[] raw = Base64.getDecoder().decode(payload);
			ByteBuffer buffer = ByteBuffer.wrap(raw);
			byte[] iv = new byte[IV_LEN];
			buffer.get(iv);
			byte[] encrypted = new byte[buffer.remaining()];
			buffer.get(encrypted);
			Cipher cipher = Cipher.getInstance(TRANSFORM);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException ex) {
			throw new IllegalStateException("Failed to decrypt secret", ex);
		}
	}

}
