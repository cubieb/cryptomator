package org.cryptomator.crypto.engine.impl;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.util.encoders.Base64;
import org.junit.Assert;
import org.junit.Test;

public class FileHeaderPayloadTest {

	private static final SecureRandom RANDOM_MOCK = new SecureRandom() {

		private static final long serialVersionUID = 1505563778398085504L;

		@Override
		public void nextBytes(byte[] bytes) {
			Arrays.fill(bytes, (byte) 0x00);
		}

	};

	@Test
	public void testEncryption() {
		final byte[] keyBytes = new byte[32];
		final SecretKey headerKey = new SecretKeySpec(keyBytes, "AES");
		final FileHeaderPayload header = new FileHeaderPayload(RANDOM_MOCK);
		header.setFilesize(42);
		final ByteBuffer encrypted = header.toCiphertextByteBuffer(headerKey, new byte[16]);

		// echo -n "AAAAAAAAACoAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==" | base64 --decode | openssl enc -aes-256-cbc -K 0000000000000000000000000000000000000000000000000000000000000000 -iv
		// 00000000000000000000000000000000 | base64
		Assert.assertArrayEquals(Base64.decode("S+uR3CoV6Mp/PWStVf2upywdYw2W84hMLWfINiTodqKaCopvSvdY6sqRYcnQF9J5"), Arrays.copyOfRange(encrypted.array(), 0, encrypted.remaining()));
	}

	@Test
	public void testDecryption() {
		final byte[] keyBytes = new byte[32];
		final SecretKey headerKey = new SecretKeySpec(keyBytes, "AES");
		final ByteBuffer ciphertextBuf = ByteBuffer.wrap(Base64.decode("S+uR3CoV6Mp/PWStVf2upywdYw2W84hMLWfINiTodqKaCopvSvdY6sqRYcnQF9J5"));
		final FileHeaderPayload header = FileHeaderPayload.fromCiphertextByteBuffer(ciphertextBuf, headerKey, new byte[16]);
		Assert.assertEquals(42, header.getFilesize());
		Assert.assertArrayEquals(new byte[32], header.getContentKey().getEncoded());
	}

}