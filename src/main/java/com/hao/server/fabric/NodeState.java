package com.hao.server.fabric;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;
import org.hyperledger.fabric.shim.ledger.CompositeKey;

import java.util.Objects;

@DataType
public class NodeState extends State {
    @Property
    private String mspId;
    @Property
    private String index;                     // index都不相同
    @Property
    private String ipAddress;
    @Property
    private String port;
    @Property
    private long load = 1L;                   // 负载，只读取，不需要传入，新建的节点Node的负载是最小值1
    @Property
    private long debt = 1L;                   // 负债，存储原文件时会增加负债，值是所有文件大小*1.5
    @Property
    private boolean alive = true;                    // 是否挂掉，TODO 主从备份，太难了，这里只考虑node不会挂掉

    public NodeState(String mspId, String ipAddress, String port) {
        this.mspId = mspId;
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public long getDebt() {
        return debt;
    }

    public void setDebt(long debt) {
        this.debt = debt;
    }

    @Override
    public CompositeKey getKey() {
        return new CompositeKey(NodeState.class.getSimpleName(), ipAddress, port, index);
    }

    public String getMspId() {
        return mspId;
    }

    public void setMspId(String mspId) {
        this.mspId = mspId;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public long getLoad() {
        return load;
    }

    public void setLoad(long load) {
        this.load = load;
    }

    @Override
    public String toString() {
        return "NodeState{" +
                "mspId='" + mspId + '\'' +
                ", index='" + index + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", port='" + port + '\'' +
                ", load=" + load +
                ", debt=" + debt +
                ", alive=" + alive +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeState nodeState = (NodeState) o;
        return Objects.equals(mspId, nodeState.mspId) &&
                Objects.equals(ipAddress, nodeState.ipAddress) &&
                Objects.equals(port, nodeState.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mspId, ipAddress, port);
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }
}
