package com.hao.server.util;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import com.hao.printer.Printer;

public class RSACipherUtil {
    private static Base64.Decoder base64Decoder;
    private static KeyFactory rsaKeyFactory;
    private static Cipher rsaCipher;

    static {
        try {
            RSACipherUtil.base64Decoder = Base64.getDecoder();
            RSACipherUtil.rsaKeyFactory = KeyFactory.getInstance("RSA");
            RSACipherUtil.rsaCipher = Cipher.getInstance("RSA");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            e.printStackTrace();
        }
    }

    public static String decryption(final String context, final String privateKey) {
        final byte[] b = RSACipherUtil.base64Decoder.decode(privateKey);
        final byte[] s = RSACipherUtil.base64Decoder.decode(context.getBytes(StandardCharsets.UTF_8));
        final PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(b); // 私钥序列化标准
        try {
            final PrivateKey key = RSACipherUtil.rsaKeyFactory.generatePrivate(spec);
            RSACipherUtil.rsaCipher.init(Cipher.DECRYPT_MODE, key);
//			final byte[] f = RSADecryptUtil.c.doFinal(s);
            int inputLength = s.length;
            int offSet = 0;
            byte[] resultBytes = {};
            byte[] cache = {};
            while (inputLength - offSet > 0) {
                if (inputLength - offSet > 128) {
                    cache = rsaCipher.doFinal(s, offSet, 128);
                    offSet += 128;
                } else {
                    cache = rsaCipher.doFinal(s, offSet, inputLength - offSet);
                    offSet = inputLength;
                }
                resultBytes = Arrays.copyOf(resultBytes, resultBytes.length + cache.length);
                System.arraycopy(cache, 0, resultBytes, resultBytes.length - cache.length, cache.length);
            }
            return new String(resultBytes);
        } catch (Exception e) {
            Printer.instance.print(e.getMessage());
            Printer.instance.print("错误：RSA解密失败。");
        }
        return null;
	}

	public static String encryption(String context, String publicKey) {
        byte[] pk = RSACipherUtil.base64Decoder.decode(publicKey);
        byte[] ctx = context.getBytes(StandardCharsets.UTF_8);
        try {
            PublicKey key = RSACipherUtil.rsaKeyFactory.generatePublic(new X509EncodedKeySpec(pk)); // 公钥存储格式标准，公钥需要签名所以用x.509证书
            RSACipherUtil.rsaCipher.init(Cipher.ENCRYPT_MODE, key);

            int inputLength = ctx.length;
            int offSet = 0;
            byte[] resultBytes = {};
            byte[] buff;
            while (inputLength - offSet > 0) {
                if (inputLength - offSet > 117) {
                    buff = rsaCipher.doFinal(ctx, offSet, 117);
                    offSet += 117;
                } else {
                    buff = rsaCipher.doFinal(ctx, offSet, inputLength - offSet);
                    offSet = inputLength;
                }
                resultBytes = Arrays.copyOf(resultBytes, resultBytes.length + buff.length);
                System.arraycopy(buff, 0, resultBytes, resultBytes.length - buff.length, buff.length);
            }

            return Base64.getEncoder().encodeToString(resultBytes);

        } catch (InvalidKeySpecException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }
        return null;

    }
}
