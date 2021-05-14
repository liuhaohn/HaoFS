package com.hao.server.util;

import com.hao.printer.Printer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class RSAKeyUtil {
	private static final int KEY_SIZE = 1024;
	private Key publicKey;
	private Key privateKey;
	private Base64.Encoder encoder;
	private String publicKeyStr;
	private String privateKeyStr;

	public RSAKeyUtil() {
		this.encoder = Base64.getEncoder();
		try {
			final KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
			g.initialize(KEY_SIZE);
			final KeyPair pair = g.genKeyPair();
			this.publicKey = pair.getPublic();
			this.privateKey = pair.getPrivate();
			this.publicKeyStr = new String(this.encoder.encode(this.publicKey.getEncoded()), StandardCharsets.UTF_8);
			this.privateKeyStr = new String(this.encoder.encode(this.privateKey.getEncoded()), StandardCharsets.UTF_8);
		} catch (NoSuchAlgorithmException e) {
			Printer.instance.print(e.getMessage());
			Printer.instance.print("错误：RSA密钥生成失败。");
		}
	}

	public String getPublicKey() {
		return this.publicKeyStr;
	}

	public String getPrivateKey() {
		return this.privateKeyStr;
	}

	public int getKeySize() {
		return KEY_SIZE;
	}
}
