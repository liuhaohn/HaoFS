package com.hao.server.service.impl;

import com.google.gson.Gson;
import com.hao.server.fabric.*;
import com.hao.server.mapper.NodeMapper;
import com.hao.server.model.Folder;
import com.hao.server.model.Node;
import com.hao.server.pojo.FolderView;
import com.hao.server.service.DistributeFileService;
import com.hao.server.service.FileService;
import com.hao.server.fabric.feature.FileFeatureExtractor;
import com.hao.server.fabric.feature.ImageFileFeatureExtractor;
import com.hao.server.util.ConfigureReader;
import com.hao.server.util.FileBlockUtil;
import com.hao.server.util.ServerTimeUtil;
import de.uni_postdam.hpi.jerasure.Decoder;
import de.uni_postdam.hpi.jerasure.Encoder;
import de.uni_postdam.hpi.utils.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class DistributeFileServiceImpl implements DistributeFileService {
    // 需要保存登录状态的对象，所以应该放到session中
    private final FileService fileService;
    private final FabricDao fabricDao;
    private final FileBlockUtil fileBlockUtil;
    private final NodeMapper nodeMapper;
    private final FileFeatureExtractor fileFeature = new ImageFileFeatureExtractor();
    private final Gson gson;
    private final RPCHttpClient rpcHttpClient;

    @Autowired
    public DistributeFileServiceImpl(FileService fileService, FabricDao fabricDao, FileBlockUtil fileBlockUtil, NodeMapper nodeMapper, Gson gson, RPCHttpClient rpcHttpClient) {
        this.gson = gson;
        this.fileService = fileService;
        this.fabricDao = fabricDao;
        this.fileBlockUtil = fileBlockUtil;
        this.nodeMapper = nodeMapper;
        this.rpcHttpClient = rpcHttpClient;
    }

    @Override
    public String doUploadFile(HttpServletRequest request, HttpServletResponse response, MultipartFile file) {
        // 权限控制
        String account = (String) request.getSession().getAttribute("ACCOUNT");
        if (account == null || !account.equals(fabricDao.getLocalMspId())) { // 只有本组织的用户可以上传
            return FileService.UPLOAD_ERROR;
        }

        // 本机存一份
        String fileHash;
        try {
            fileHash = FabricUtil.getFileHash(file.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            return FileService.UPLOAD_ERROR;
        }
        Object s = fileService.uploadFile(request, file, fileHash);
        if (s instanceof String) return (String) s;
        File org = (File) s;

        try {
            // 特征提取
            String featureHash = fileFeature.generateFeature(org);

            // 信息写入区块链
            List<NodeState> nodeList = fabricDao.getNodes(3);
            List<String> sliceOrganization = Arrays.asList(nodeList.get(0).getMspId(), nodeList.get(1).getMspId(), nodeList.get(2).getMspId());
            FileState fileState = new FileState(fileHash, file.getOriginalFilename(), account, sliceOrganization,
                    file.getSize(), String.format("%d", System.currentTimeMillis()),
                    FileState.STATE_EXIST, new ArrayList<>(), featureHash);
            if (!fabricDao.insertFile(fileState)) {
                return "fileidentical";    // 和其他节点的文件存在冲突
            }

            // 写入区块链后才分发文件，这是因为其他节点需要进行权限验证，看是否要保存上传的文件块
            // TODO 文件加密

            // 文件分片
            new Encoder(2, 1, 3).encode(org);
            String absolutePath = org.getAbsolutePath();
            File k1 = new File(FileUtils.generatePartPath(absolutePath, "k", 1));
            File k2 = new File(FileUtils.generatePartPath(absolutePath, "k", 2));
            File m1 = new File(FileUtils.generatePartPath(absolutePath, "m", 1));

            // 所有机器参与存储分片，选取策略封装到fabricDao中
            boolean success = rpcHttpClient.distributeBlocks(nodeList, fileHash, k1, k2, m1);
            k1.delete();
            k2.delete();
            m1.delete();

            if (!success) {
                String fileId = "file-" + fileHash;
                final Node node = this.nodeMapper.queryById(fileId);
                // 从节点删除
                this.nodeMapper.deleteById(fileId);
                this.fileBlockUtil.deleteFromFileBlocks(node);
                return FileService.UPLOAD_ERROR;
            }
            return FileService.UPLOAD_SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            org.delete();
            nodeMapper.deleteById("file-" + fileHash);
        }
        return FileService.UPLOAD_ERROR;
    }


    @Override
    public String doDeleteFile(HttpServletRequest request) {
        // 权限控制
        String account = (String) request.getSession().getAttribute("ACCOUNT");
        if (account == null || !account.equals(fabricDao.getLocalMspId())) { // 只有本组织的用户可以删除
            return FileService.DELETE_NO_AUTHORIZED;
        }

        String fileId = request.getParameter("fileId"); // 文件hash，是file-[hash]
        String fileHash = fileId.split("-")[1];
        FileState fileState = fabricDao.getFileByHash(fileHash);
        if (fileState == null) return FileService.DELETE_ERROR_PARAMETER; // 根本没有这个文件
        RPCHttpClient rpcHttpClient = (RPCHttpClient) request.getSession().getAttribute("RPCHttpClient");

        // 先删除其他节点，需要此文件是本组织的
        boolean deleted = true;
        if (fileState.getOrganization().equals(account)) {
            List<String> sliceOrganization = fileState.getSliceOrganization();
            List<NodeState> nodeList = new ArrayList<>();
            for (String mspId : sliceOrganization) {
                nodeList.add(fabricDao.getNodeByMspId(mspId));
            }
            deleted = rpcHttpClient.deleteDistributedBlocks(nodeList, fileState.getFileHash());

            // 信息写入区块链
            if (!fabricDao.deleteFile(fileHash)) {
                return FileService.DELETE_CANNOT;
            }
        }

        // 本地删除，本地删除直接授权
        if (deleted) {
            fileService.deleteFile(request);
        }
        return FileService.DELETE_FILE_SUCCESS;
    }

    @Override
    public void doDownloadFile(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 权限控制
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        if (account == null || !account.equals(fabricDao.getLocalMspId())) {  // 只有本组织的可以在下载，其他组织只可以下载文件块
            response.sendError(404);
            return;
        }

        // 本地查找是否有次文件，有则下载，本地可能已经缓存过从其他组织下载的文件，也可以可以直接下载
        if (!fileService.downloadFile(request, response)) { // 本地下载不成功
            // 查看是否有下载此文件的权限，有则请求下载到本地
            String fileId = request.getParameter("fileId");
            String fileHash = fileId.split("-", 2)[1];
            FileState fileState = fabricDao.getFileByHash(fileHash);
            List<String> authorized = fileState.getAuthorizedOrganization(); // 除了本地用户

            if (!(account.equals(fileState.getOrganization()) || authorized.contains(account))) {
                response.sendError(404);
                return; // 只有文件所有者和被授权者可以下载块
            }

            RPCHttpClient rpcHttpClient = (RPCHttpClient) request.getSession().getAttribute("RPCHttpClient");
            List<String> sliceOrganization = fileState.getSliceOrganization();
            List<NodeState> nodeList = new ArrayList<>();
            for (String mspId : sliceOrganization) {
                nodeList.add(fabricDao.getNodeByMspId(mspId));
            }
            List<InputStream> files = rpcHttpClient.downloadDistributedBlocks(nodeList, fileHash);
            if (files == null) {
                response.sendError(404);
                return;
            }

            // 恢复文件
            //   将文件块（临时的）保存为一个file_[uuid]_[k01|k02|m01].block，其中uuid是本机生成的，和原存储的uuid不同
            String fileBaseName = "file_" + UUID.randomUUID().toString().replace("-", "");
            String[] suffix = new String[]{"_k01", "_k02", "_m01"};
            List<File> tempFiles = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                InputStream inputStream = files.get(i);
                if (inputStream == null) continue;
                File file = this.fileBlockUtil.saveBlock(inputStream, fileBaseName + suffix[i] + ".block");
                tempFiles.add(file);
            }
            //   恢复
            File originFile = new File(ConfigureReader.instance().getFileBlockPath() + fileBaseName + ".block");
            new Decoder(originFile, 2, 1, 3).decode(fileState.getFileSize());
            for (File tempFile : tempFiles) {
                tempFile.delete(); // 恢复后删除文件块
            }
            // TODO 解密


            // 本地保存，即将文件导入数据库
            this.fileBlockUtil.insertNewNode(fileId, fileState.getFileName(),
                    fileState.getOrganization(), originFile.getName(),
                    Long.toString(fileState.getFileSize()), "ROOT");

            // 下载
            fileService.downloadFile(request, response);

            // 谁下载过文件，信息写入区块链
            if (!fileState.getOrganization().equals(fabricDao.getLocalMspId())) {
                fabricDao.addUserOrganization(fileHash, fabricDao.getLocalMspId());
            }
        }
        // 纯本地响应，无需写区块链
    }

    @Override
    public String getFileInfo(HttpServletRequest request) {
        String fid = request.getParameter("fid");
        String fHash = fid.split("-")[1];
        FileState fileState = fabricDao.getFileByHash(fHash);
        return new Gson().toJson(fileState);
    }

    @Override
    public String getSearchView(HttpSession session, HttpServletRequest request) {
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        if (account == null || !account.equals(fabricDao.getLocalMspId())) {
            return "permissiondenied";
        }

        List<String> hits = null;
        try {
            Part image = request.getPart("image");
            hits = fabricDao.getFilesByFeature(ImageIO.read(image.getInputStream()),
                    Integer.parseInt(request.getParameter("hits")));
        } catch (IOException | ServletException e) {
            e.printStackTrace();
            return "uploaderror";
        }
        List<Node> fileList = new ArrayList<>();
        for (int i = hits.size() - 1; i >= 0; i--) {

            String[] split = hits.get(i).split(",", 2);
            FileState fileState = this.fabricDao.getFileByHash(split[0]);
            if (fileState != null)
                fileList.add(new Node("file-" + fileState.getFileHash(),
                        fileState.getFileName(),
                        fileState.getFileSize() + "",
                        "ROOT",
                        fileState.getTime(),
                        fileState.getOrganization(),
                        "", fileState.getAuthorizedOrganization(),
                        split[1]));
        }


        final FolderView fv = new FolderView();
        fv.setSelectStep(250);// 返回查询步长
        fv.setAccount((String) request.getSession().getAttribute("ACCOUNT"));
        fv.setFolder(new Folder("root", "ROOT",
                "--", "--", "null", 0));
        fv.setFolderList(new ArrayList<>());
        fv.setParentList(new ArrayList<>());

        long filesOffset = this.nodeMapper.countByParentFolderId("ROOT");
        fv.setFilesOffset(filesOffset); // 有多少files
        Map<String, Object> keyMap2 = new HashMap<>();
        keyMap2.put("pfid", "ROOT");
        long fiOffset = filesOffset - 250;
        keyMap2.put("offset", fiOffset > 0L ? fiOffset : 0L);
        keyMap2.put("rows", 250);

        fileList.forEach(FolderViewServiceImpl::formatNode);
        fv.setFileList(fileList); // 文件没有组管理，能进文件夹可以见文件

        if (ConfigureReader.instance().isAllowChangePassword()) {
            fv.setAllowChangePassword("true");
        } else {
            fv.setAllowChangePassword("false");
        }
        if (ConfigureReader.instance().isAllowSignUp()) {
            fv.setAllowSignUp("true");
        } else {
            fv.setAllowSignUp("false");
        }
        final List<String> authList = new ArrayList<String>();
        fv.setAuthList(authList);
        fv.setPublishTime(ServerTimeUtil.accurateToMinute());
        fv.setEnableDownloadZip(ConfigureReader.instance().isEnableDownloadByZip());

        return gson.toJson(fv);
    }


    @Override
    public String requestAuthorization(HttpSession session, HttpServletRequest request) {
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        if (account == null || !account.equals(fabricDao.getLocalMspId())) {
            return "permissiondenied";
        }
        String fileId = request.getParameter("fileId");
        String fileHash = fileId.split("-", 2)[1];
        FileState fileState = fabricDao.getFileByHash(fileHash);

        NodeState nodeState = fabricDao.getNodeByMspId(fileState.getOrganization());

        if (rpcHttpClient.requestAuthorization(nodeState, fileHash)) {
            return "success";
        } else {
            return "failed";
        }
    }
}
