package com.hao.server.fabric;

import org.apache.commons.math3.util.Pair;

import java.util.*;

public class AllNodeState {
    private final Map<String, NodeState> mspNodeMap = new HashMap<>();

    /**
     * 初始化，这些都是已经在链上的，node只能添加不能清除
     */
    public AllNodeState(List<NodeState> allNodeState) {
        for (NodeState nodeState : allNodeState) {
            addNodeState(nodeState);
        }
    }

    /**
     * 插入一个新Node需要调用
     */
    public void addNodeState(NodeState nodeState) {
        String mspId = nodeState.getMspId();
        mspNodeMap.put(mspId, nodeState);
    }

    /**
     * 插入一个file时需要调用
     * 更新msp的load，在插入file时load为正，删除file时load为负
     * <p>
     * 注意这里更新load只是更新了本地的load，区块链上load实际上是通过智能合约更新的，智能合约也需要保证在插入、删除file时更改node的load
     */
    public void updateMspLoadAndDebt(FileState fileState) {
        long fileSize = fileState.getFileSize();
        NodeState ownerNodeState = mspNodeMap.get(fileState.getOrganization());
        ownerNodeState.setDebt(ownerNodeState.getDebt() + (long) (fileSize * 1.5)); // 文件所有者的负债增加

        List<String> sliceOrganization = fileState.getSliceOrganization();
        for (String org : sliceOrganization) {
            NodeState sliceNodeState = mspNodeMap.get(org);
            sliceNodeState.setLoad(sliceNodeState.getLoad() + fileSize / 2);        // 所有slice存储者的负载增加
        }

    }

    /**
     * 通过(负债-负载)，获取值最大的n个msp
     */
    public List<NodeState> getNodeStatesByLoad(int n) {
        Object[] msps = mspNodeMap.keySet().toArray();
        List<Pair<NodeState, Long>> pairs = new ArrayList<>();
        for (Object msp : msps) {
            NodeState nodeState = mspNodeMap.get(msp);
            pairs.add(new Pair<>(nodeState, nodeState.getLoad() - nodeState.getDebt()));
        }
        pairs.sort(Comparator.comparing(Pair::getValue));   // load从小到大排列
        List<NodeState> res = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            try {
                Pair<NodeState, Long> pair = pairs.get(i);
                NodeState nodeState = pair.getKey();
                if (nodeState.isAlive()) res.add(nodeState);
            } catch (Exception ignored) {
            }
        }
        int i1 = n - res.size();
        for (int i = 0; i < i1; i++) {
            res.add(res.get(0));
        }
        return res;
    }

    public NodeState getNodeByMspId(String mspId) {
        return mspNodeMap.get(mspId);
    }
}
