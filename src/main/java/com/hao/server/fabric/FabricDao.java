package com.hao.server.fabric;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.hao.server.fabric.feature.MemImageSearcher;
import com.hao.server.util.ConfigureReader;
import org.hyperledger.fabric.gateway.Contract;
import org.hyperledger.fabric.gateway.ContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Component
@DependsOn({"contract", "memImageSearcher"})
public class FabricDao {
    private static String mspId;
    private final Contract contract;
    private final AllNodeState allNodeState;
    private final AllFileState allFileState;

    @Autowired
    public FabricDao(Contract contract, Wallet wallet, MemImageSearcher memImageSearcher, ApplicationContext context) throws UnknownHostException {
        this.contract = contract;
        mspId = wallet.getLocalMspId();

        this.addInsertNodeEventListener();
        this.addInsertFileEventListener();
        List<NodeState> allNodeState = selectAllNode(); // 可能取出在添加监听器后新插入的，也没有关系，只是会覆盖一下
        List<FileState> allFileState = selectAllFile();
        this.allNodeState = new AllNodeState(allNodeState == null ? new ArrayList<>() : allNodeState);
        this.allFileState = new AllFileState(allFileState == null ? new ArrayList<>() : allFileState, memImageSearcher, context);

        String hostAddress = InetAddress.getLocalHost().getHostAddress();
        NodeState nodeState = new NodeState(mspId, hostAddress, ConfigureReader.instance().getInitPort());
        if (!this.insertNode(nodeState)) {
            throw new Error("Can't insert file node into fabric");
        }
    }

    /**
     * 插入一个File需要：
     * 1.更新AllFileState
     * 2.更新AllNodeState
     */
    private void addInsertFileEventListener() {
        // 监听文件添加事件
        contract.addContractListener(contractEvent -> {
            contractEvent.getPayload().ifPresent(bytes -> {
                FileState deserialize = FileState.deserialize(bytes, FileState.class);
                allFileState.addFileState(deserialize);
                allNodeState.updateMspLoadAndDebt(deserialize);
            });
        }, "InsertFileEvent");

        // 监听文件删除事件
        contract.addContractListener(contractEvent -> {
            contractEvent.getPayload().ifPresent(bytes -> {
                FileState deserialize = FileState.deserialize(bytes, FileState.class);
                allFileState.removeFileState(deserialize.getFileHash());
                deserialize.setFileSize(-deserialize.getFileSize());   // 删除文件，使文件大小变为负
                allNodeState.updateMspLoadAndDebt(deserialize);
            });
        }, "DeleteFileEvent");

        // 监听文件addUserOrganization和addAuthorizedOrganization更新事件，两种更新作同一操作，所以是同一事件
        contract.addContractListener(contractEvent -> {
            contractEvent.getPayload().ifPresent(bytes -> {
                FileState deserialize = FileState.deserialize(bytes, FileState.class);
                allFileState.updateFileState(deserialize);
            });
        }, "UpdateFileEvent");

    }

    private void addInsertNodeEventListener() {
        contract.addContractListener(contractEvent -> {
            contractEvent.getPayload().ifPresent(bytes -> {
                NodeState nodeState = NodeState.deserialize(bytes, NodeState.class);
                allNodeState.addNodeState(nodeState);
            });
        }, "InsertNodeEvent");
    }

    private byte[] submit(String name, String... args) {
        for (int i = 0; i < 3; i++) {
            try {
                return contract.submitTransaction(name, args);
            } catch (ContractException e) {
                e.printStackTrace();
                return null;
            } catch (TimeoutException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        return null;
    }


    /**
     * 主动读发生在Node启动时，其读取Fabric上所有记录来初始化
     */
    private List<NodeState> selectAllNode() {
        byte[] nodes = submit("selectAllNode");
        if (nodes == null) return null;

        List<NodeState> res = new ArrayList<>();
        JsonArray jsonArray = JsonParser.parseString(new String(nodes, StandardCharsets.UTF_8)).getAsJsonArray();
        for (int i = 0; i < jsonArray.size(); i++) {
            NodeState nodeState = new Gson().fromJson(jsonArray.get(i).getAsString(), NodeState.class);
            res.add(nodeState);
        }
        return res;
    }

    /**
     * 只获取所有Exist的File
     */
    private List<FileState> selectAllFile() {
        byte[] files = submit("selectAllFile");
        if (files == null) return null;

        List<FileState> res = new ArrayList<>();
        JsonArray jsonArray = JsonParser.parseString(new String(files, StandardCharsets.UTF_8)).getAsJsonArray();
        for (int i = 0; i < jsonArray.size(); i++) {
            FileState fileState = new Gson().fromJson(jsonArray.get(i).getAsString(), FileState.class);
            res.add(fileState);
        }
        return res;
    }

    public boolean insertNode(NodeState nodeState) {
        return submit("insertNode", nodeState.toJson()) != null;
    }

    public boolean insertFile(FileState fileState) {
        return submit("insertFile", fileState.toJson()) != null;
    }

    public boolean deleteFile(String fileHash) {
        return submit("deleteFile", fileHash) != null;
    }

    public FileState getFileByHash(String fileHash) {
        return allFileState.getFileByHash(fileHash);
    }

    /**
     * 找到几个相似的文件
     */
    public List<String> getFilesByFeature(BufferedImage feature, int n) {
        return allFileState.getFilesByFeature(feature, n);
    }

    public NodeState getNodeByMspId(String mspId) {
        return allNodeState.getNodeByMspId(mspId);
    }

    /**
     * 获取n个不同的nodeState，这些是通过nodeState的负载随机产生的
     */
    public List<NodeState> getNodes(int n) {
        return this.allNodeState.getNodeStatesByLoad(n);
    }

    public static String getLocalMspId() {
        return mspId;
    }

    public FileState selectFileByHash(String fileHash) {
        return FileState.deserialize(submit("selectFile", fileHash), FileState.class);
    }

    public FileState selectFileContainDeletedByHash(String fileHash) {
        return FileState.deserialize(submit("selectFileContainDeleted", fileHash), FileState.class);
    }

    public boolean addAuthorizedOrganization(String fileHash, String mspId) {
        return submit("addAuthorizedOrganization", fileHash, mspId) != null;
    }

    public boolean addUserOrganization(String fileHash, String mspId) {
        return submit("addUserOrganization", fileHash, mspId) != null;
    }
}
