package com.hao.server.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.hyperledger.fabric.shim.ledger.CompositeKey;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 一个State是<Key, Value>元组，Value可以被序列化和反序列化，存入状态数据库
 */
public abstract class State {

    public abstract CompositeKey getKey();

    public byte[] serialize() {
        return new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(this).getBytes(UTF_8);
    }

    public static <T extends State> T deserialize(byte[] buffer, Class<T> type) {
        if (buffer != null && buffer.length != 0) {
            return new GsonBuilder().create().fromJson(new String(buffer, UTF_8), type);
        }
        return null;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public static <T extends State> T fromJson(String json, Class<T> type) {
        return new Gson().fromJson(json, type);
    }

}
