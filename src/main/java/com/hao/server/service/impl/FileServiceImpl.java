package com.hao.server.service.impl;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hao.server.enumeration.AccountAuth;
import com.hao.server.fabric.FabricDao;
import com.hao.server.fabric.FabricUtil;
import com.hao.server.fabric.FileState;
import com.hao.server.mapper.FolderMapper;
import com.hao.server.mapper.NodeMapper;
import com.hao.server.model.Folder;
import com.hao.server.model.Node;
import com.hao.server.pojo.CheckUploadFilesRespons;
import com.hao.server.service.FileService;
import com.hao.server.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * <h2>文件服务功能实现类</h2>
 * <p>
 * 单节点上文件操作
 * <p>
 * 向上提供的文件操作接口分为两类，File结尾是原文件，Block结尾是分布式存储的文件块
 * <p>
 * 向下需要的是系统存储文件统一接口fileBlockUtil
 * </p>
 *
 * @version 1.0
 * @see FileService
 */
@Service
public class FileServiceImpl extends RangeFileStreamWriter implements FileService {

    @Autowired
    private NodeMapper nodeMapper;
    @Autowired
    private FolderMapper folderMapper;
    @Autowired
    private LogUtil logUtil;
    @Autowired
    private Gson gson;
    @Autowired
    private FileBlockUtil fileBlockUtil;
    @Autowired
    private FolderUtil folderUtil;
    @Autowired
    private IpAddrGetter ipAddrGetter;
    @Autowired
    private FabricDao fabricDao;
    protected HashMap<String, Set<String>> requestAuthorizationMap = new HashMap<>();


    private static final String CONTENT_TYPE = "application/octet-stream";

    // 检查上传文件列表的实现（上传文件的前置操作）
    public String checkUploadFile(final HttpServletRequest request, final HttpServletResponse response) {
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        final String folderId = request.getParameter("folderId");
        final String nameList = request.getParameter("namelist");
        final String maxUploadFileSize = request.getParameter("maxSize");
        final String maxUploadFileIndex = request.getParameter("maxFileIndex");
        // 目标文件夹合法性检查
        if (folderId == null || folderId.length() == 0) {
            return ERROR_PARAMETER;
        }
        // 获取上传目标文件夹，如果没有直接返回错误
        Folder folder = folderMapper.queryById(folderId);
        if (folder == null) {
            return ERROR_PARAMETER;
        }
        // 权限检查
        if (!ConfigureReader.instance().authorized(account, AccountAuth.UPLOAD_FILES, folderUtil.getAllFoldersId(folderId))
                || !ConfigureReader.instance().accessFolder(folder, account)) {
            return NO_AUTHORIZED;
        }
        // 获得上传文件名列表
        final List<String> namelistObj = gson.fromJson(nameList, new TypeToken<List<String>>() {
        }.getType());
        // 准备一个检查结果对象
        CheckUploadFilesRespons cufr = new CheckUploadFilesRespons();
        // 开始文件上传体积限制检查
        try {
            // 获取最大文件体积（以Byte为单位）
            long mufs = Long.parseLong(maxUploadFileSize);
            // 获取最大文件的名称
            String mfname = namelistObj.get(Integer.parseInt(maxUploadFileIndex));
            long pMaxUploadSize = ConfigureReader.instance().getUploadFileSize(account);
            if (pMaxUploadSize >= 0) {
                if (mufs > pMaxUploadSize) {
                    cufr.setCheckResult("fileTooLarge");
                    cufr.setMaxUploadFileSize(formatMaxUploadFileSize(pMaxUploadSize));
                    cufr.setOverSizeFile(mfname);
                    return gson.toJson(cufr);
                }
            }
        } catch (Exception e) {
            return ERROR_PARAMETER;
        }
        // 开始文件命名冲突检查
        final List<String> pereFileNameList = new ArrayList<>();
        // 查找目标目录下是否存在与待上传文件同名的文件（或文件夹），如果有，记录在上方的列表中
        for (final String fileName : namelistObj) {
            if (folderId == null || folderId.length() <= 0 || fileName == null || fileName.length() <= 0) {
                return ERROR_PARAMETER;
            }
            final List<Node> files = this.nodeMapper.queryByParentFolderId(folderId);
            if (files.stream().parallel().anyMatch((n) -> n.getFileName().equals(fileName))) {
                pereFileNameList.add(fileName);
            }
        }
        // 判断如果上传了这一批文件的话，会不会引起文件数量超限
        long estimatedTotal = nodeMapper.countByParentFolderId(folderId) - pereFileNameList.size() + namelistObj.size();
        if (estimatedTotal > FileNodeUtil.MAXIMUM_NUM_OF_SINGLE_FOLDER || estimatedTotal < 0) {
            return "filesTotalOutOfLimit";
        }
        // 如果存在同名文件，则写入同名文件的列表；否则，直接允许上传
        if (pereFileNameList.size() > 0) {
            cufr.setCheckResult("hasExistsNames");
            cufr.setPereFileNameList(pereFileNameList);
        } else {
            cufr.setCheckResult("permitUpload");
            cufr.setPereFileNameList(new ArrayList<String>());
        }
        return gson.toJson(cufr);// 以JSON格式写回该结果
    }

    // 校验文件hash时，文件上传校验
    public String checkUploadCheckFile(HttpServletRequest request, HttpServletResponse response) {
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        final String nameList = request.getParameter("namelist");
        final String maxUploadFileSize = request.getParameter("maxSize");
        final String maxUploadFileIndex = request.getParameter("maxFileIndex");

        // 获得上传文件名列表
        final List<String> namelistObj = gson.fromJson(nameList, new TypeToken<List<String>>() {
        }.getType());
        // 准备一个检查结果对象
        CheckUploadFilesRespons cufr = new CheckUploadFilesRespons();
        // 开始文件上传体积限制检查
        try {
            // 获取最大文件体积（以Byte为单位）
            long mufs = Long.parseLong(maxUploadFileSize);
            // 获取最大文件的名称
            String mfname = namelistObj.get(Integer.parseInt(maxUploadFileIndex));
            long pMaxUploadSize = ConfigureReader.instance().getUploadFileSize(account);
            if (pMaxUploadSize >= 0) {
                if (mufs > pMaxUploadSize) {
                    cufr.setCheckResult("fileTooLarge");
                    cufr.setMaxUploadFileSize(formatMaxUploadFileSize(pMaxUploadSize));
                    cufr.setOverSizeFile(mfname);
                    return gson.toJson(cufr);
                }
            }
        } catch (Exception e) {
            return ERROR_PARAMETER;
        }
        cufr.setCheckResult("permitUpload");
        return gson.toJson(cufr);// 以JSON格式写回该结果
    }

    // 格式化存储体积，便于返回上传文件体积的检查提示信息（如果传入0，则会直接返回错误提示信息，将该提示信息发送至前端即可）。
    public String formatMaxUploadFileSize(long size) {
        double result = (double) size;
        String unit = "B";
        if (size <= 0) {
            return "设置无效，请联系管理员";
        }
        if (size >= 1024 && size < 1048576) {
            result = (double) size / 1024;
            unit = "KB";
        } else if (size >= 1048576 && size < 1073741824) {
            result = (double) size / 1048576;
            unit = "MB";
        } else if (size >= 1073741824) {
            result = (double) size / 1073741824;
            unit = "GB";
        }
        return String.format("%.1f", result) + " " + unit;
    }

    /**
     * 接收的文件名格式必须是 [hash]_[k01|k02|m01]
     * 保存一个块：需要登录，然后multipart上传文件就行，文件id是 block-[hash]-[k1|k2|m1]
     */
    @Override
    public boolean uploadBlock(final HttpServletRequest request, final Part file) {
        String fHash = request.getParameter("fHash");
        String fileId = fHash;
        fHash = fHash.split("[\\_-]", 2)[0];
        if (!fileId.matches(".+[_\\-](?:k01|k02|m01)")) {
            return false;
        }
        fileId = "block-" + fileId.replaceAll("_", "-");

        String account = (String) request.getSession().getAttribute("VISITOR");
        if (account == null) return false;  // 需要登陆才能上传块
        // 块已经被保存
        if (nodeMapper.queryById(fileId) != null) {
            return true;
        }

        // 要保存块：查询区块链上有此块的信息，并且发块的是此文件拥有者
        FileState fileState = null;
        for (int i = 0; i < 10; i++) {
            fileState = fabricDao.getFileByHash(fHash);
            if (fileState == null) { // 可能是Fabric系统没有一致，需要等待
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                break;
            }
        }
        if (fileState == null || !fileState.getSliceOrganization().contains(account)) return false;

        // 将文件存入节点并获取其存入生成路径，型如“block_<fileHash>.block”形式。
        final File block = this.fileBlockUtil.savePartBlock(file, "block");
        if (block == null) {
            return false;
        }
        try {
            final String fsize = Long.toString(file.getSize());
            Node newNode = fileBlockUtil.insertNewNode(fileId, file.getSubmittedFileName(), account, block.getName(), fsize, "block");
            if (newNode == null) {
                block.delete();
                return false;
            }
            return true;
        } catch (Exception e) {
            block.delete();
            return false;
        }
    }

    /**
     * 给定fileID可以删除
     */
    @Override
    public boolean deleteBlock(HttpServletRequest request) {
        String account = (String) request.getSession().getAttribute("VISITOR");
        if (account == null) return false;
        String fileId = request.getParameter("fileId"); // [hash]-k01
        if (fileId == null || !fileId.matches(".+[_\\-](?:k01|k02|m01)")) {
            return false;
        }
        fileId = "block-" + fileId.replaceAll("_", "-");
        final Node node = this.nodeMapper.queryById(fileId);
        if (node == null) return true;
        if (!node.getFileCreator().equals(account)) return false; // 只有创建者可以删除

        // 从节点删除
        if (this.nodeMapper.deleteById(fileId) >= 0) {
            // 从文件块删除
            if (this.fileBlockUtil.deleteFromFileBlocks(node)) {
                this.logUtil.writeDeleteFileEvent(request, node);
                return true;
            }
        }
        return false;
    }

    /**
     * 登录后可以下载文件，TODO 文件是否有权下载可以通过区块链中每个文件都分配一个授权用户集合，授权集合应该缓存到本地（通过监听区块链事件更新）
     */
    @Override
    public void downloadBlock(HttpServletRequest request, HttpServletResponse response) {
        // 检查权限
        String fileId = request.getParameter("fileId"); // [hash]-k01
        if (fileId == null || !fileId.matches(".+[_\\-](?:k01|k02|m01)")) return;

        final Node f = this.nodeMapper.queryById("block-" + fileId.replaceAll("_", "-"));
        if (f == null) return;

        final String account = (String) request.getSession().getAttribute("VISITOR");
        FileState fileState = fabricDao.getFileByHash(fileId.split("[\\-_]", 2)[0]);
        List<String> authorized = fileState.getAuthorizedOrganization(); // 除了本地用户
//        List<String> authorized = new ArrayList<>();
        if (account == null || !(account.equals(fileState.getOrganization()) || authorized.contains(account))) {
            try {
                response.sendError(404);
            } catch (IOException e) {
            }
            return; // 只有文件所有者和被授权者可以下载块
            // 本地保存的FileCreator和fabricDao查出来的organization会是一致的，因为只有登录后才能上传文件块，而文件块一定是文件拥有者登录后上传的
        }

        // 执行写出
        final File fo = this.fileBlockUtil.getFileFromBlocks(f);
        final String ip = ipAddrGetter.getIpAddr(request);
        final String range = request.getHeader("Range");    // 支持断点续传
        if (fo != null) {
            int status = writeRangeFileStream(request, response, fo, f.getFileName(), CONTENT_TYPE,
                    ConfigureReader.instance().getDownloadMaxRate(account), fileBlockUtil.getETag(fo), true);
            // 日志记录（仅针对一次下载）
            if (status == HttpServletResponse.SC_OK
                    || (range != null && range.startsWith("bytes=0-"))) {
                this.logUtil.writeDownloadFileEvent(account, ip, f);
            }
            return;
        }
        try {
            //  处理无法下载的资源
            response.sendError(404);
        } catch (IOException e) {
        }
    }

    /**
     * 执行上传操作，接收文件并存入文件节点
     * <p>
     * 文件的id是 file-[hash]，存储到文件系统的文件名格式是 file_[uuid].block
     */
    @Override
    public Object uploadFile(HttpServletRequest request, MultipartFile file, String fileHash) {
        String account = (String) request.getSession().getAttribute("ACCOUNT");
        if (account == null) return NO_AUTHORIZED;

        final String fname = request.getParameter("fname");
        final String originalFileName = (fname != null ? fname : file.getOriginalFilename());
        String fileId = "file-" + fileHash;

        Node node = nodeMapper.queryById(fileId);
        if (node != null) {
            return UPLOAD_SUCCESS;
        }

        // 将文件存入节点并获取其存入生成路径，型如“file_<uuid>.block”形式。
        final File block = this.fileBlockUtil.saveMultipartBlock(file, "file");
        if (block == null) {
            return UPLOAD_ERROR;
        }

        try {
            final String fsize = Long.toString(file.getSize());
            Node newNode = fileBlockUtil.insertNewNode(fileId, originalFileName, account, block.getName(), fsize, "ROOT");
            if (newNode != null) {
                this.logUtil.writeUploadFileEvent(request, newNode, account);
                return block;
            } else {
                block.delete();
                return UPLOAD_ERROR;
            }
        } catch (Exception e) {
            block.delete();
            return UPLOAD_ERROR;
        }
    }

    // 校验操作：文件上传并校验hash
    @Override
    public String doUploadCheckFile(HttpServletRequest request, HttpServletResponse response, MultipartFile file) {
        String fileHash = null;
        try {
            fileHash = FabricUtil.getFileHash(file.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
        String account = (String) request.getSession().getAttribute("ACCOUNT");
        // 检查上传文件体积是否超限
        long mufs = ConfigureReader.instance().getUploadFileSize(account);
        if (mufs >= 0 && file.getSize() > mufs) {
            return UPLOAD_ERROR;
        }
        try {
            FileState fileState = fabricDao.selectFileContainDeletedByHash(fileHash);
            if (fileState == null) {
                return FILE_NOT_EXIST;
            } else if (fileState.isExist()) {
                return UPLOAD_SUCCESS;
            } else {
                return FILE_DELETED;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return UPLOAD_ERROR;
        }
    }

    @Override
    public boolean requestAuthorization(HttpServletRequest request, HttpServletResponse response) {
        String account = (String) request.getSession().getAttribute("VISITOR");
        if (account == null) return false;
        String fileHash = request.getParameter("fileHash");
        FileState state = fabricDao.getFileByHash(fileHash);
        List<String> authorizedOrganization = state.getAuthorizedOrganization();
        if (authorizedOrganization != null && authorizedOrganization.contains(account)) return true; // 已授权
        if (account.equals(FabricDao.getLocalMspId())) return true; // 自己给自己授权
        Set<String> list = requestAuthorizationMap.getOrDefault(fileHash, new HashSet<>());
        if (list.contains(account)) return true; // 已请求授权
        list.add(account);
        requestAuthorizationMap.put(fileHash, list);
        return true;
    }

    @Override
    public boolean responseAuthorization(HttpServletRequest request) {
        String account = (String) request.getSession().getAttribute("ACCOUNT");
        if (!account.equals(FabricDao.getLocalMspId())) {
            return false;
        }
        String fileHash = request.getParameter("fileId").split("-")[1];
        String organization = request.getParameter("organization");
        Set<String> list = requestAuthorizationMap.get(fileHash);
        if (list == null || !list.contains(organization)) {
            return false;
        }
        if (!fabricDao.addAuthorizedOrganization(fileHash, organization)) {
            return false;
        }

        list.remove(organization);
        if (list.size() == 0) requestAuthorizationMap.remove(fileHash);
        return true;
    }

    @Override
    public boolean deleteFile(final HttpServletRequest request) {
        final String fileId = request.getParameter("fileId");
        if (fileId == null || fileId.length() <= 0) {
            return false;
        }

        // 确认要删除的文件存在
        final Node node = this.nodeMapper.queryById(fileId);
        if (node == null) {
            return true;
        }

        // 从节点删除
        if (this.nodeMapper.deleteById(fileId) >= 0) {
            // 从文件块删除
            if (this.fileBlockUtil.deleteFromFileBlocks(node)) {
                this.logUtil.writeDeleteFileEvent(request, node);
                return true;
            }
        }
        return false;
    }


    // 普通下载：下载单个文件
    @Override
    public boolean downloadFile(final HttpServletRequest request, final HttpServletResponse response) {
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        // 权限检查
        // 找到要下载的文件节点
        final String fileId = request.getParameter("fileId");
        if (fileId != null) {
            final Node f = this.nodeMapper.queryById(fileId);
            if (f != null) {
                // 执行写出
                final File fo = this.fileBlockUtil.getFileFromBlocks(f);
                final String ip = ipAddrGetter.getIpAddr(request);
                final String range = request.getHeader("Range");
                if (fo != null) {
                    int status = writeRangeFileStream(request, response, fo, f.getFileName(), CONTENT_TYPE,
                            ConfigureReader.instance().getDownloadMaxRate(account), fileBlockUtil.getETag(fo), true);
                    // 日志记录（仅针对一次下载）
                    if (status == HttpServletResponse.SC_OK
                            || (range != null && range.startsWith("bytes=0-"))) {
                        this.logUtil.writeDownloadFileEvent(account, ip, f);
                    }
                    return true;
                }
            }
        }
        return false;
    }


    // TODO 不需要重命名文件
    public String doRenameFile(final HttpServletRequest request) {
        final String fileId = request.getParameter("fileId");
        final String newFileName = request.getParameter("newFileName");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        // 参数检查
        if (fileId == null || fileId.length() <= 0 || newFileName == null || newFileName.length() <= 0) {
            return ERROR_PARAMETER;
        }
        if (!TextFormateUtil.instance().matcherFileName(newFileName) || newFileName.indexOf(".") == 0) {
            return ERROR_PARAMETER;
        }
        final Node file = this.nodeMapper.queryById(fileId);
        if (file == null) {
            return ERROR_PARAMETER;
        }
        final Folder folder = folderMapper.queryById(file.getFileParentFolder());
        if (!ConfigureReader.instance().accessFolder(folder, account) || !account.equals(file.getFileCreator())) {
            return NO_AUTHORIZED;
        }
        // 权限检查
        if (!ConfigureReader.instance().authorized(account, AccountAuth.RENAME_FILE_OR_FOLDER,
                folderUtil.getAllFoldersId(file.getFileParentFolder()))) {
            return NO_AUTHORIZED;
        }
        if (!file.getFileName().equals(newFileName)) {
            // 不允许重名
            if (nodeMapper.queryBySomeFolder(fileId).parallelStream().anyMatch((e) -> e.getFileName().equals(newFileName))) {
                return "nameOccupied";
            }
            // 更新文件名
            final Map<String, String> map = new HashMap<String, String>();
            map.put("fileId", fileId);
            map.put("newFileName", newFileName);
            if (this.nodeMapper.updateFileNameById(map) == 0) {
                // 并写入日志
                return "cannotRenameFile";
            }
        }
        this.logUtil.writeRenameFileEvent(request, file, newFileName);
        return "renameFileSuccess";
    }


    // 打包下载功能：前置——压缩要打包下载的文件
    public String downloadCheckedFiles(final HttpServletRequest request) {
        if (ConfigureReader.instance().isEnableDownloadByZip()) {
            final String account = (String) request.getSession().getAttribute("ACCOUNT");
            final String strIdList = request.getParameter("strIdList");
            final String strFidList = request.getParameter("strFidList");
            try {
                // 获得要打包下载的文件ID
                final List<String> idList = gson.fromJson(strIdList, new TypeToken<List<String>>() {
                }.getType());
                final List<String> fidList = gson.fromJson(strFidList, new TypeToken<List<String>>() {
                }.getType());
                // 创建ZIP压缩包并将全部文件压缩
                if (idList.size() > 0 || fidList.size() > 0) {
                    final String zipname = this.fileBlockUtil.createZip(idList, fidList, account);
                    this.logUtil.writeDownloadCheckedFileEvent(request, idList, fidList);
                    // 返回生成的压缩包路径
                    return zipname;
                }
            } catch (Exception ex) {
                logUtil.writeException(ex);
            }
        }
        return "ERROR";
    }

    // 打包下载功能：执行——下载压缩好的文件
    public void downloadCheckedFilesZip(final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        final String zipname = request.getParameter("zipId");
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        if (zipname != null && !zipname.equals("ERROR")) {
            final String tfPath = ConfigureReader.instance().getTemporaryfilePath();
            final File zip = new File(tfPath, zipname);
            String fname = "kiftd_" + ServerTimeUtil.accurateToDay() + "_\u6253\u5305\u4e0b\u8f7d.zip";
            if (zip.exists()) {
                writeRangeFileStream(request, response, zip, fname, CONTENT_TYPE,
                        ConfigureReader.instance().getDownloadMaxRate(account), fileBlockUtil.getETag(zip), true);
                zip.delete();
            }
        }
    }

    public String getPackTime(final HttpServletRequest request) {
        if (ConfigureReader.instance().isEnableDownloadByZip()) {
            final String account = (String) request.getSession().getAttribute("ACCOUNT");
            final String strIdList = request.getParameter("strIdList");
            final String strFidList = request.getParameter("strFidList");
            try {
                final List<String> idList = gson.fromJson(strIdList, new TypeToken<List<String>>() {
                }.getType());
                final List<String> fidList = gson.fromJson(strFidList, new TypeToken<List<String>>() {
                }.getType());
                for (String fid : fidList) {
                    countFolderFilesId(account, fid, idList);
                }
                long packTime = 0L;
                for (final String fid : idList) {
                    final Node n = this.nodeMapper.queryById(fid);
                    if (ConfigureReader.instance().authorized(account, AccountAuth.DOWNLOAD_FILES,
                            folderUtil.getAllFoldersId(n.getFileParentFolder()))
                            && ConfigureReader.instance().accessFolder(folderMapper.queryById(n.getFileParentFolder()),
                            account)) {
                        final File f = fileBlockUtil.getFileFromBlocks(n);
                        if (f != null && f.exists()) {
                            packTime += f.length() / 25000000L;
                        }
                    }
                }
                if (packTime < 4L) {
                    return "\u9a6c\u4e0a\u5b8c\u6210";
                }
                if (packTime >= 4L && packTime < 10L) {
                    return "\u5927\u7ea610\u79d2";
                }
                if (packTime >= 10L && packTime < 35L) {
                    return "\u4e0d\u5230\u534a\u5206\u949f";
                }
                if (packTime >= 35L && packTime < 65L) {
                    return "\u5927\u7ea61\u5206\u949f";
                }
                if (packTime >= 65L) {
                    return "\u8d85\u8fc7" + packTime / 60L
                            + "\u5206\u949f\uff0c\u8017\u65f6\u8f83\u957f\uff0c\u5efa\u8bae\u76f4\u63a5\u4e0b\u8f7d";
                }
            } catch (Exception ex) {
                logUtil.writeException(ex);
            }
        }
        return "0";
    }

    // 用于迭代获得全部文件夹内的文件ID（方便预测耗时）
    private void countFolderFilesId(String account, String fid, List<String> idList) {
        Folder f = folderMapper.queryById(fid);
        if (ConfigureReader.instance().accessFolder(f, account)) {
            try {
                idList.addAll(Arrays.asList(nodeMapper.queryByParentFolderId(fid).parallelStream().map((e) -> e.getFileId())
                        .toArray(String[]::new)));
                List<Folder> cFolders = folderMapper.queryByParentId(fid);
                for (Folder cFolder : cFolders) {
                    countFolderFilesId(account, cFolder.getFolderId(), idList);
                }
            } catch (Exception e2) {
                // 超限？那就不再加了。
            }
        }
    }
}
