package com.hao.server.fabric;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;
import org.hyperledger.fabric.shim.ledger.CompositeKey;

import java.util.ArrayList;
import java.util.List;

@DataType()
public class FileState extends State {
    @Property
    private String fileHash;            // 以MD5 hash为id，是为了防篡改
    @Property
    private String fileName;            // 原始文件名
    @Property
    private Long fileSize;            // 文件大小B
    @Property
    private String time;                // 创建时间
    @Property
    private String state;               // 文件状态
    @Property
    private String featureHash;         // 构建特征树，用于检索

    @Property
    private String organization;        // 文件拥有者
    @Property
    private List<String> sliceOrganization; // 文件块存储者，有顺序的，三个k1k2m1，存储的是mspId
    @Property
    private List<String> authorizedOrganization;    // 授权访问者
    @Property
    private List<String> userOrganization;  // 已经下载过此文件的mspId，应该是authorizedOrganization的子集


    public final static String STATE_DELETED = "DELETED";
    public final static String STATE_EXIST = "EXIST";

    public FileState() {
        authorizedOrganization = new ArrayList<>();
    }

    public FileState(String fileHash, String fileName, String organization,
                     List<String> sliceOrganization, Long fileSize, String time,
                     String state, List<String> authorizedOrganization, String featureHash) {
        this.fileHash = fileHash;
        this.fileName = fileName;
        this.organization = organization;
        this.sliceOrganization = sliceOrganization;
        this.fileSize = fileSize;
        this.time = time;
        this.state = state;
        this.authorizedOrganization = authorizedOrganization;
        this.featureHash = featureHash;
    }

    @Override
    public CompositeKey getKey() {
        return new CompositeKey(FileState.class.getSimpleName(), fileHash);
    }

    public boolean isDeleted() {
        return FileState.STATE_DELETED.equals(this.state);
    }

    public boolean isExist() {
        return FileState.STATE_EXIST.equals(this.state);
    }

    public String getState() {
        return this.state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }


    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public List<String> getAuthorizedOrganization() {
        return authorizedOrganization;
    }

    public void setAuthorizedOrganization(List<String> authorizedOrganization) {
        this.authorizedOrganization = authorizedOrganization;
    }

    public String getFeatureHash() {
        return featureHash;
    }

    public void setFeatureHash(String featureHash) {
        this.featureHash = featureHash;
    }

    public List<String> getSliceOrganization() {
        return sliceOrganization;
    }

    public void setSliceOrganization(List<String> sliceOrganization) {
        this.sliceOrganization = sliceOrganization;
    }

    public List<String> getUserOrganization() {
        return userOrganization;
    }

    public void setUserOrganization(List<String> userOrganization) {
        this.userOrganization = userOrganization;
    }

    @Override
    public String toString() {
        return "FileState{" +
                "fileHash='" + fileHash + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize='" + fileSize + '\'' +
                ", time='" + time + '\'' +
                ", state='" + state + '\'' +
                ", featureHash='" + featureHash + '\'' +
                ", organization='" + organization + '\'' +
                ", sliceOrganization=" + sliceOrganization +
                ", authorizedOrganization=" + authorizedOrganization +
                ", userOrganization=" + userOrganization +
                '}';
    }
}
