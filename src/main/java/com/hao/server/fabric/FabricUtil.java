package com.hao.server.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hao.printer.Printer;
import org.apache.commons.codec.binary.Hex;
import org.hyperledger.fabric.gateway.Contract;
import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.Identities;
import org.hyperledger.fabric.gateway.X509Identity;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Objects;

public class FabricUtil {
    @Autowired
    private Gateway gateway;

    public Gateway getGateway() {
        return gateway;
    }

    public static Contract getContract() {
        return null;
    }

    public static String getFileHash(byte[] file) {
        byte[] md5s = new byte[0];
        try {
            md5s = MessageDigest.getInstance("MD5").digest(file);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            Printer.instance.print(e.getMessage());
        }
        return Hex.encodeHexString(md5s);
    }


}
