package com.agentplatform.hub.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

class FeishuCryptoTest {

	@Test
	void decryptsOfficialCipherLayout() throws Exception {
		String key = "test-key";
		String plain = "{\"challenge\":\"abc\"}";
		byte[] aesKey = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
		byte[] iv = new byte[16];
		Arrays.fill(iv, (byte) 3);
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
		byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
		byte[] packed = new byte[iv.length + encrypted.length];
		System.arraycopy(iv, 0, packed, 0, iv.length);
		System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);

		assertEquals(plain, FeishuCrypto.decrypt(key, Base64.getEncoder().encodeToString(packed)));
	}

	@Test
	void rejectsWrongEncryptKey() {
		assertThrows(IllegalArgumentException.class, () -> FeishuCrypto.decrypt("nope", "bm90LWNpcGhlcg=="));
	}

	@Test
	void checksSignatureAndRejectsStaleOrWrongOnes() {
		String body = "{\"encrypt\":\"abc\"}";
		String timestamp = "1700000000";
		String nonce = "nonce";
		String key = "encrypt-key";
		String sign = FeishuCrypto.sign(timestamp, nonce, key, body);

		FeishuCrypto.checkSignature(timestamp, nonce, sign.toUpperCase(), key, body, 1_700_000_100L);
		assertThrows(
				FeishuCrypto.SignatureException.class,
				() -> FeishuCrypto.checkSignature(timestamp, nonce, "deadbeef", key, body, 1_700_000_000L));
		assertThrows(
				FeishuCrypto.SignatureException.class,
				() -> FeishuCrypto.checkSignature("100", nonce, sign, key, body, 1_700_000_000L));
	}

}
