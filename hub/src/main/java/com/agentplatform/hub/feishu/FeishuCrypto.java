package com.agentplatform.hub.feishu;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Feishu event encryption: AES-256-CBC, key = SHA-256(Encrypt Key), IV = first 16 bytes.
 * Signature = SHA-256 hex of timestamp + nonce + Encrypt Key + raw body.
 */
public final class FeishuCrypto {

	private FeishuCrypto() {
	}

	public static final class SignatureException extends RuntimeException {

		public SignatureException(String message) {
			super(message);
		}

	}

	public static String decrypt(String encryptKey, String cipherText) {
		try {
			byte[] decoded = Base64.getMimeDecoder().decode(cipherText);
			if (decoded.length <= 16) {
				throw new IllegalArgumentException("无法解密飞书事件，请核对 Encrypt Key");
			}
			byte[] iv = Arrays.copyOfRange(decoded, 0, 16);
			byte[] data = Arrays.copyOfRange(decoded, 16, decoded.length);
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey(encryptKey), "AES"), new IvParameterSpec(iv));
			return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException ex) {
			throw new IllegalArgumentException("无法解密飞书事件，请核对 Encrypt Key");
		}
	}

	public static void checkSignature(
			String timestamp,
			String nonce,
			String signature,
			String encryptKey,
			String rawBody,
			long nowEpochSeconds) {
		if (isBlank(timestamp) || isBlank(nonce) || isBlank(signature)) {
			throw new SignatureException("缺少飞书签名头");
		}
		long ts;
		try {
			ts = Long.parseLong(timestamp.trim());
		}
		catch (NumberFormatException ex) {
			throw new SignatureException("飞书时间戳无效");
		}
		if (ts > 10_000_000_000L) {
			ts = ts / 1000L;
		}
		if (Math.abs(nowEpochSeconds - ts) > 3600) {
			throw new SignatureException("飞书请求已过期");
		}
		String expected = sign(timestamp.trim(), nonce.trim(), encryptKey, rawBody);
		String actual = signature.trim().toLowerCase(Locale.ROOT);
		if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
			throw new SignatureException("飞书签名校验失败");
		}
	}

	static String sign(String timestamp, String nonce, String encryptKey, String rawBody) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest((timestamp + nonce + encryptKey + rawBody).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static byte[] aesKey(String encryptKey) throws GeneralSecurityException {
		return MessageDigest.getInstance("SHA-256").digest(encryptKey.getBytes(StandardCharsets.UTF_8));
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
