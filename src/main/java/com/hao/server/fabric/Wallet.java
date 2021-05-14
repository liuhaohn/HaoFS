package com.hao.server.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.hyperledger.fabric.gateway.X509Identity;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import static org.hyperledger.fabric.gateway.Identities.*;

@Component
public class Wallet {
    private final Map<String, X509Identity> identityMap = new HashMap<>();
    private final Map<String, String> identityStringMap = new HashMap<>();
    private File connectionConfig;
    private String localMspId = "";

    public Wallet() {
        File walletF = new File(System.getProperty("user.dir"), "conf");
        String[] list = walletF.list();
        for (String s : list) {
            File file = new File(walletF, s);
            if (s.matches("Admin.*")) {
                this.addIdentity("admin", file);
                this.localMspId = MSP.parseOrganization(identityMap.get("admin").getCertificate());
            }
        }
        if (localMspId == null) throw new Error("no admin certificate");
        for (String s : list) {
            File file = new File(walletF, s);
            if (s.matches("User.*")) {
                this.addIdentity("user", file);
            } else if (s.matches("Visitor.*")) {
                this.addIdentity("visitor", file);
            } else if (s.matches("connection.*")) {
                this.connectionConfig = file;
            }
        }
    }

    private void addIdentity(String name, File file) {
        X509Identity x509Identity = null;
        JsonObject jsonObject = null;
        try {
            jsonObject = JsonParser.parseReader(new FileReader(file)).getAsJsonObject();
            String mspId = jsonObject.get("mspId").getAsString();
            JsonObject credentials = jsonObject.get("credentials").getAsJsonObject();
            String pem = credentials.get("certificate").getAsString();
            X509Certificate certificate = readX509Certificate(pem);
            pem = credentials.get("privateKey").getAsString();
            if (pem == null || !file.getName().contains(localMspId)) return;    // 没有私钥，则不是控制的用户
            PrivateKey privateKey = readPrivateKey(pem);
            x509Identity = newX509Identity(mspId, certificate, privateKey);
        } catch (FileNotFoundException | CertificateException | InvalidKeyException ignored) {
        }
        assert x509Identity != null;
        identityMap.put(name, x509Identity);
        identityStringMap.put(name, jsonObject.toString());
    }

    /**
     * 只能获取本地用户
     */
    public X509Identity getIdentity(String userName) {
        return identityMap.get(userName);
    }

    /**
     * 只能获取本地用户，visitor、user、admin
     */
    public String getStringIdentity(String userName) {
        return identityStringMap.get(userName);
    }

    public byte[] sign(byte[] plainText, String mspId) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(identityMap.get(mspId).getPrivateKey());
            signature.update(plainText);
            return signature.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException ignored) {
        }
        return null;
    }

    public File getConnectionConfig() {
        return connectionConfig;
    }

    public String getLocalMspId() {
        return localMspId;
    }
}
