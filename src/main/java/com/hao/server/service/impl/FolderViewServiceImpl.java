package com.hao.server.service.impl;

import ch.qos.logback.core.util.FileSize;
import com.google.gson.Gson;
import com.hao.server.enumeration.AccountAuth;
import com.hao.server.mapper.FolderMapper;
import com.hao.server.mapper.NodeMapper;
import com.hao.server.model.Folder;
import com.hao.server.model.Node;
import com.hao.server.pojo.FolderView;
import com.hao.server.pojo.RemainingFolderView;
import com.hao.server.pojo.SreachView;
import com.hao.server.service.FolderViewService;
import com.hao.server.util.ConfigureReader;
import com.hao.server.util.FolderUtil;
import com.hao.server.util.KiftdFFMPEGLocator;
import com.hao.server.util.ServerTimeUtil;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class FolderViewServiceImpl implements FolderViewService {

    private static int SELECT_STEP = 256;// 每次查询的文件或文件夹的最大限额，即查询步进长度

    @Autowired
    private FolderUtil folderUtil;
    @Autowired
    private FolderMapper folderMapper;
    @Autowired
    private NodeMapper nodeMapper;
    @Autowired
    private Gson gson;
    @Autowired
    private KiftdFFMPEGLocator kiftdFFMPEGLocator;
    @Autowired
    private FileServiceImpl fileService;

    @Override
    public String getFolderViewToJson(final HttpSession session, final HttpServletRequest request) {
        final FolderView fv = new FolderView();
        fv.setSelectStep(SELECT_STEP);// 返回查询步长
        fv.setAccount((String) request.getSession().getAttribute("ACCOUNT"));
        fv.setFolder(new Folder("root", "ROOT",
                "--", "--", "null", 0));
        fv.setFolderList(new ArrayList<>());
        fv.setParentList(new ArrayList<>());

        long filesOffset = this.nodeMapper.countByParentFolderId("ROOT");
        fv.setFilesOffset(filesOffset); // 有多少files
        Map<String, Object> keyMap2 = new HashMap<>();
        keyMap2.put("pfid", "ROOT");
        long fiOffset = filesOffset - SELECT_STEP;
        keyMap2.put("offset", fiOffset > 0L ? fiOffset : 0L);
        keyMap2.put("rows", SELECT_STEP);
        List<Node> fileList = this.nodeMapper.queryByParentFolderIdSection(keyMap2);
        fileList.forEach(FolderViewServiceImpl::formatNode);
        for (Node node : fileList) {
            Set<String> list = fileService.requestAuthorizationMap.get(node.getFileId().split("-")[1]);
            node.setRequestAuthorization(list);
        }

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
        fv.setEnableFFMPEG(kiftdFFMPEGLocator.isEnableFFmpeg());
        fv.setEnableDownloadZip(ConfigureReader.instance().isEnableDownloadByZip());

        return gson.toJson(fv);
    }

    @Override
    public String getSreachViewToJson(HttpServletRequest request) {
        final ConfigureReader cr = ConfigureReader.instance();
        String fid = request.getParameter("fid");
        String keyWorld = request.getParameter("keyworld");
        if (fid == null || fid.length() == 0 || keyWorld == null) {
            return "ERROR";
        }
        // 如果啥么也不查，那么直接返回指定文件夹标准视图
        if (keyWorld.length() == 0) {
            return getFolderViewToJson(request.getSession(), request);
        }
        Folder vf = this.folderMapper.queryById(fid);
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        // 检查访问文件夹视图请求是否合法
        if (!ConfigureReader.instance().accessFolder(vf, account)) {
            return "notAccess";// 如无访问权限则直接返回该字段，令页面回到ROOT视图。
        }
        final SreachView sv = new SreachView();
        // 先准备搜索视图的文件夹信息
        Folder sf = new Folder();
        sf.setFolderId(vf.getFolderId());// 搜索视图的主键设置与搜索路径一致
        sf.setFolderName("在“" + vf.getFolderName() + "”内搜索“" + keyWorld + "”的结果...");// 名称就是搜索的描述
        sf.setFolderParent(vf.getFolderId());// 搜索视图的父级也与搜索路径一致
        sf.setFolderCreator("--");// 搜索视图是虚拟的，没有这些
        sf.setFolderCreationDate("--");
        sf.setFolderConstraint(vf.getFolderConstraint());// 其访问等级也与搜索路径一致
        sv.setFolder(sf);// 搜索视图的文件夹信息已经准备就绪
        // 设置上级路径为搜索路径
        List<Folder> pl = this.folderUtil.getParentList(fid);
        pl.add(vf);
        sv.setParentList(pl);
        // 设置所有搜索到的文件夹和文件，该方法迭查找：
        List<Node> ns = new LinkedList<>();
        List<Folder> fs = new LinkedList<>();
        sreachFilesAndFolders(fid, keyWorld, account, ns, fs);
        sv.setFileList(ns);
        sv.setFolderList(fs);
        // 搜索不支持分段加载，所以统计数据直接写入实际查询到的列表大小
        sv.setFoldersOffset(0L);
        sv.setFilesOffset(0L);
        sv.setSelectStep(SELECT_STEP);
        // 账户视图与文件夹相同
        if (account != null) {
            sv.setAccount(account);
        }
        if (ConfigureReader.instance().isAllowChangePassword()) {
            sv.setAllowChangePassword("true");
        } else {
            sv.setAllowChangePassword("false");
        }
        // 设置操作权限，对于搜索视图而言，只能进行下载操作（因为是虚拟的）
        final List<String> authList = new ArrayList<String>();
        // 搜索结果只接受“下载”操作
        if (cr.authorized(account, AccountAuth.DOWNLOAD_FILES, folderUtil.getAllFoldersId(fid))) {
            authList.add("L");
            if (cr.isOpenFileChain()) {
                sv.setShowFileChain("true");// 显示永久资源链接
            } else {
                sv.setShowFileChain("false");
            }
        }
        // 同时额外具备普通文件夹没有的“定位”功能。
        authList.add("O");
        sv.setAuthList(authList);
        // 写入实时系统时间
        sv.setPublishTime(ServerTimeUtil.accurateToMinute());
        // 设置查询字段
        sv.setKeyWorld(keyWorld);
        // 返回公告MD5
        sv.setEnableFFMPEG(kiftdFFMPEGLocator.isEnableFFmpeg());
        sv.setEnableDownloadZip(ConfigureReader.instance().isEnableDownloadByZip());
        return gson.toJson(sv);
    }

    // 迭代查找所有匹配项，参数分别是：从哪找、找啥、谁要找、添加的前缀是啥（便于分辨不同路径下的同名文件）、找到的文件放哪、找到的文件夹放哪
    private void sreachFilesAndFolders(String fid, String key, String account, List<Node> ns, List<Folder> fs) {
        for (Folder f : this.folderMapper.queryByParentId(fid)) {
            if (ConfigureReader.instance().accessFolder(f, account)) {
                if (f.getFolderName().indexOf(key) >= 0) {
                    f.setFolderName(f.getFolderName());
                    fs.add(f);
                }
                sreachFilesAndFolders(f.getFolderId(), key, account, ns, fs);
            }
        }
        for (Node n : this.nodeMapper.queryByParentFolderId(fid)) {
            if (n.getFileName().indexOf(key) >= 0) {
                formatNode(n);
                ns.add(n);
            }
        }
    }

    public static void formatNode(Node n) {
        // 文件名不超过30个英文字符长度
        String fileName = n.getFileName();
        int gbkLen = 0;
        try {
            gbkLen = fileName.getBytes("gbk").length;
        } catch (UnsupportedEncodingException ignored) {
        }
        if (gbkLen > 30) {
            n.setFileName(fileName.substring(0, (int) ((30. / gbkLen) * fileName.length()) - 1) + "...");
        }

        // 文件大小友好化
        double l = Long.parseLong(n.getFileSize());
        String size = String.format("%.0fB", l);
        if (l > 1024) {
            l = l / 1024;
            size = String.format("%.2fKB", l);
        }
        if (l > 1024) {
            l = l / 1024;
            size = String.format("%.2fMB", l);
        }
        if (l > 1024) {
            l = l / 1024;
            size = String.format("%.2fGB", l);
        }
        n.setFileSize(size);


        String creationDate = n.getFileCreationDate();
        try {
            long stamp = Long.parseLong(creationDate);
            n.setFileCreationDate(new SimpleDateFormat("yyyy.MM.dd").format(new Date(stamp)));
        } catch (Exception ignore) {
        }

    }

    @Override
    public String getRemainingFolderViewToJson(HttpServletRequest request) {
        final String fid = request.getParameter("fid");
        final String foldersOffset = request.getParameter("foldersOffset");
        final String filesOffset = request.getParameter("filesOffset");
        if (fid == null || fid.length() == 0) {
            return "ERROR";
        }
        Folder vf = this.folderMapper.queryById(fid);
        if (vf == null) {
            return "NOT_FOUND";// 如果用户请求一个不存在的文件夹，则返回“NOT_FOUND”，令页面回到ROOT视图
        }
        final String account = (String) request.getSession().getAttribute("ACCOUNT");
        // 检查访问文件夹视图请求是否合法
        if (!ConfigureReader.instance().accessFolder(vf, account)) {
            return "notAccess";// 如无访问权限则直接返回该字段，令页面回到ROOT视图。
        }
        final RemainingFolderView fv = new RemainingFolderView();
        if (foldersOffset != null) {
            try {
                long newFoldersOffset = Long.parseLong(foldersOffset);
                if (newFoldersOffset > 0L) {
                    Map<String, Object> keyMap1 = new HashMap<>();
                    keyMap1.put("pid", fid);
                    long nfOffset = newFoldersOffset - SELECT_STEP;
                    keyMap1.put("offset", nfOffset > 0L ? nfOffset : 0L);
                    keyMap1.put("rows", nfOffset > 0L ? SELECT_STEP : newFoldersOffset);
                    List<Folder> folders = this.folderMapper.queryByParentIdSection(keyMap1);
                    List<Folder> fs = new LinkedList<>();
                    for (Folder f : folders) {
                        if (ConfigureReader.instance().accessFolder(f, account)) {
                            fs.add(f);
                        }
                    }
                    fv.setFolderList(fs);
                }
            } catch (NumberFormatException e) {
                return "ERROR";
            }
        }
        if (filesOffset != null) {
            try {
                long newFilesOffset = Long.parseLong(filesOffset);
                if (newFilesOffset > 0L) {
                    Map<String, Object> keyMap2 = new HashMap<>();
                    keyMap2.put("pfid", fid);
                    long nfiOffset = newFilesOffset - SELECT_STEP;
                    keyMap2.put("offset", nfiOffset > 0L ? nfiOffset : 0L);
                    keyMap2.put("rows", nfiOffset > 0L ? SELECT_STEP : newFilesOffset);
                    fv.setFileList(this.nodeMapper.queryByParentFolderIdSection(keyMap2));
                }
            } catch (NumberFormatException e) {
                return "ERROR";
            }
        }
        return gson.toJson(fv);
    }
}
