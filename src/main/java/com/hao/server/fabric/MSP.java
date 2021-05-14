package com.hao.server.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.hyperledger.fabric.gateway.Identities;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MSP {
    private final Map<String, PublicKey> visitorPublicKeyMap = new HashMap<>();
    private final Map<String, PublicKey> rootPublicKeyMap = new HashMap<>();

    public MSP() {
        File walletF = new File(System.getProperty("user.dir"), "conf");
        String[] walletList = walletF.list();
        assert walletList != null;
        for (String s : walletList) {
            if (s.matches("Visitor.*")) {   // 该msp只给visitor授予身份
                try {
                    JsonObject jsonObject = JsonParser.parseReader(new FileReader(new File(walletF, s))).getAsJsonObject();
                    JsonObject credentials = jsonObject.get("credentials").getAsJsonObject();
                    String pem = credentials.get("certificate").getAsString();
                    X509Certificate x509Certificate = Identities.readX509Certificate(pem);
                    PublicKey publicKey = x509Certificate.getPublicKey();
                    visitorPublicKeyMap.put(parseOrganization(x509Certificate), publicKey);
                } catch (CertificateException | FileNotFoundException ignored) {
                }
            } else if (s.matches("ca.*")) {
                try {
                    X509Certificate x509Certificate = Identities.readX509Certificate(new FileReader(new File(walletF, s)));
                    PublicKey publicKey = x509Certificate.getPublicKey();     // 不验证证书的合法性了，根证书这里自动相信
                    assert publicKey != null;
                    rootPublicKeyMap.put(parseOrganization(x509Certificate), publicKey);
                } catch (IOException | CertificateException ignored) {
                }
            }
        }
    }

    public static String parseOrganization(X509Certificate x509Certificate) {
        String name = x509Certificate.getSubjectX500Principal().getName();
        Matcher matcher = Pattern.compile("O=(.*)").matcher(name);
        if (matcher.find()) {
            return matcher.group(1).split(",")[0];
        }
        return null;
    }

    public static String parseOrganizationUnion(X509Certificate x509Certificate) {
        String name = x509Certificate.getSubjectX500Principal().getName();
        Matcher matcher = Pattern.compile("OU=(.*)").matcher(name);
        if (matcher.find()) {
            return matcher.group(1).split(",")[0];
        }
        return null;
    }

    public static String parseCommentName(X509Certificate x509Certificate) {
        String name = x509Certificate.getSubjectX500Principal().getName();
        Matcher matcher = Pattern.compile("CN=(.*)").matcher(name);
        if (matcher.find()) {
            return matcher.group(1).split(",")[0];
        }
        return null;
    }

    public PublicKey getVisitorPublicKey(String mspId) {
        return visitorPublicKeyMap.get(mspId);

    }

    public PublicKey getRootPublicKey(String mspId) {
        return rootPublicKeyMap.get(mspId);
    }

    public boolean verifySignature(byte[] signatureText, byte[] plainText, String mspId) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            assert signature != null;
            signature.initVerify(visitorPublicKeyMap.get(mspId));
            signature.update(plainText);
            return signature.verify(signatureText);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException ignored) {
        }
        return false;
    }

    public boolean verifyCertificate(String certificate) {
        try {
            return verifyCertificate(Identities.readX509Certificate(certificate));
        } catch (CertificateException ignored) {
            return false;
        }
    }

    public boolean verifyCertificate(X509Certificate x509Certificate) {
        String organization = parseOrganization(x509Certificate);     // 那个组织的证书
        // 使用该组织的根证书验证合法性
        if (organization != null) {
            PublicKey key = rootPublicKeyMap.get(organization);
            try {
                x509Certificate.verify(key);
            } catch (Exception ignored) {
                return false;
            }
            return true;
        }
        return false;
    }

    public boolean verifyPrivateKey(String privateKey, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            byte[] challenge = new byte[1024];

            signature.initSign(Identities.readPrivateKey(privateKey));
            new Random().nextBytes(challenge);
            signature.update(challenge);
            byte[] sign = signature.sign();

            signature.initVerify(publicKey);
            signature.update(challenge);
            return signature.verify(sign);
        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException ignored) {
        }
        return false;
    }
}
