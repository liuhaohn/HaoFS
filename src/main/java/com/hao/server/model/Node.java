package com.hao.server.model;

import java.util.List;
import java.util.Set;

/**
 * <h2>文件节点模型</h2>
 * <p>该模型描述了kiftd文件管理机制中的一个文件节点，从而生成一个文件的抽象对象，所有外部操作均应基于此对象进行而不是直接操作文件块。
 * 该模型对应了文件系统数据库中的FILE表。</p>
 *
 * @version 1.0
 */
public class Node {
    // 可返回前端的字段
    private String fileId;
    private String fileName;
    private String fileSize;
    private String fileParentFolder;
    private String fileCreationDate;
    private String fileCreator;

    private List<String> fileAuthorized;
    private String distance;
    private Set<String> requestAuthorization;

    public Node() {
    }

    public Node(String fileId, String fileName, String fileSize, String fileParentFolder, String fileCreationDate, String fileCreator, String filePath) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileParentFolder = fileParentFolder;
        this.fileCreationDate = fileCreationDate;
        this.fileCreator = fileCreator;
        this.filePath = filePath;
    }

    public Node(String fileId, String fileName, String fileSize, String fileParentFolder, String fileCreationDate, String fileCreator, String filePath, List<String> fileAuthorized, String distance) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileParentFolder = fileParentFolder;
        this.fileCreationDate = fileCreationDate;
        this.fileCreator = fileCreator;
        this.filePath = filePath;
        this.fileAuthorized = fileAuthorized;
        this.distance = distance;
    }

    public Set<String> getRequestAuthorization() {
        return requestAuthorization;
    }

    public void setRequestAuthorization(Set<String> requestAuthorization) {
        this.requestAuthorization = requestAuthorization;
    }

    public List<String> getFileAuthorized() {
        return fileAuthorized;
    }

    public void setFileAuthorized(List<String> fileAuthorized) {
        this.fileAuthorized = fileAuthorized;
    }

    public String getDistance() {
        return distance;
    }

    public void setDistance(String distance) {
        this.distance = distance;
    }

    // 不需要返回前端、仅应在后端中使用的字段
    private transient String filePath;

    public String getFileId() {
        return this.fileId;
    }

    public void setFileId(final String fileId) {
        this.fileId = fileId;
    }

    public String getFileName() {
        return this.fileName;
    }

    public void setFileName(final String fileName) {
        this.fileName = fileName;
    }

    public String getFileSize() {
        return this.fileSize;
    }

    public void setFileSize(final String fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileParentFolder() {
        return this.fileParentFolder;
    }

    public void setFileParentFolder(final String fileParentFolder) {
        this.fileParentFolder = fileParentFolder;
    }

    public String getFileCreationDate() {
        return this.fileCreationDate;
    }

    public void setFileCreationDate(final String fileCreationDate) {
        this.fileCreationDate = fileCreationDate;
    }

    public String getFileCreator() {
        return this.fileCreator;
    }

    public void setFileCreator(final String fileCreator) {
        this.fileCreator = fileCreator;
    }

    public String getFilePath() {
        return this.filePath;
    }

    public void setFilePath(final String filePath) {
        this.filePath = filePath;
    }
}
