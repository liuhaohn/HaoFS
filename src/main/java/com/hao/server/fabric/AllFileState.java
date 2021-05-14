package com.hao.server.fabric;

import com.hao.server.fabric.feature.MemImageSearcher;
import com.hao.server.mapper.NodeMapper;
import com.hao.server.model.Node;
import com.hao.server.util.FileBlockUtil;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.springframework.context.ApplicationContext;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;

public class AllFileState {
    // TODO 使用数据结构保存状态

    private final Map<String, FileState> allFileState = new HashMap<>();
    private final MemImageSearcher memImageSearcher;
    private NodeMapper nodeMapper = null;
    private final ApplicationContext context;

    public AllFileState(List<FileState> fileStates, MemImageSearcher memImageSearcher, ApplicationContext context) {
        this.memImageSearcher = memImageSearcher;
        this.context = context;
        for (FileState fileState : fileStates) {
            addFileState(fileState);
        }
    }

    public void addFileState(FileState fileState) {
        if (allFileState.containsKey(fileState.getFileHash())) return;
        allFileState.put(fileState.getFileHash(), fileState);
        Document doc = new Document();
        doc.add(new StringField("FileName", fileState.getFileHash(), Field.Store.YES));
        doc.add(new StoredField("SaCoCo", Base64.getDecoder().decode(fileState.getFeatureHash())));
        memImageSearcher.addDocument(doc);
    }

    public FileState getFileByHash(String fileHash) {
        return allFileState.get(fileHash);
    }

    public List<String> getFilesByFeature(BufferedImage feature, int n) {
        try {
            return memImageSearcher.search(feature, n);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean removeFileState(String fileHash) {
        FileState localFileState = allFileState.get(fileHash);
        if (localFileState != null) {
            allFileState.remove(fileHash);
            memImageSearcher.removeDocument(fileHash);
            return true;
        }
        return false;
    }

    public boolean updateFileState(FileState fileState) {
        // 找到id，然后更改对象状态
        FileState localFileState = allFileState.get(fileState.getFileHash());
        if (localFileState != null) {
            if (nodeMapper == null) {
                nodeMapper = context.getBean(NodeMapper.class);
            }
            List<String> authorizedOrganization = fileState.getAuthorizedOrganization();
            if (authorizedOrganization != null && authorizedOrganization.contains(FabricDao.getLocalMspId()) && nodeMapper.queryById("file-" + fileState.getFileHash()) == null) {
                nodeMapper.insert(new Node(
                        "file-" + fileState.getFileHash(),
                        fileState.getFileName(),
                        Long.toString(fileState.getFileSize()),
                        "ROOT",
                        fileState.getTime(),
                        fileState.getOrganization(),
                        "file_" + UUID.randomUUID().toString().replace("-", "")));
            }

            // 只可能更新这两个字段的值，直接修改对象，就可以不用修改feature索引了，因为引用的是一个对象
            localFileState.setAuthorizedOrganization(fileState.getAuthorizedOrganization());
            localFileState.setUserOrganization(fileState.getUserOrganization());
            return true;
        }
        return false;
    }
}
