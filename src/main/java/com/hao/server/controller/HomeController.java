package com.hao.server.controller;

import com.hao.server.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * <h2>主控制器</h2>
 * <p>
 * 该控制器用于负责处理kiftd主页（home.html）的所有请求，具体过程请见各个方法注释。
 * </p>
 *
 * @version 1.0
 */
@Controller
@RequestMapping({"/homeController"})
public class HomeController {
    private static final String CHARSET_BY_AJAX = "text/html; charset=utf-8";
    @Autowired
    private ServerInfoService serverInfoService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private FolderViewService folderViewService;
    @Autowired
    private DistributeFileService distributeFileService;
    @Autowired
    private FileService fileService;
    @Autowired
    private PlayVideoService playVideoService;
    @Autowired
    private ShowPictureService showPictureService;
    @Autowired
    private PlayAudioService playAudioService;
    @Autowired
    private FileChainService fileChainService;


    @RequestMapping(value = {"/getPublicKey.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String getPublicKey() {
        return this.accountService.getPublicKey();
    }

    @RequestMapping({"/doLogin.ajax"})
    @ResponseBody
    public String doLogin(final HttpServletRequest request, final HttpSession session) {
        return this.accountService.checkLoginRequest(request, session);
    }

    // 获取一个新验证码并存入请求者的Session中
    @RequestMapping({"/getNewVerCode.do"})
    public void getNewVerCode(final HttpServletRequest request, final HttpServletResponse response,
                              final HttpSession session) {
        accountService.getNewLoginVerCode(request, response, session);
    }

    // 修改密码
    @RequestMapping(value = {"/doChangePassword.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String doChangePassword(final HttpServletRequest request) {
        return accountService.changePassword(request);
    }

    @RequestMapping(value = {"/getFolderView.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String getFolderView(final HttpSession session, final HttpServletRequest request) {
        return folderViewService.getFolderViewToJson(session, request);
    }


    @RequestMapping(value = {"/getSearchView.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String getSearchView(final HttpSession session, final HttpServletRequest request) {
        return distributeFileService.getSearchView(session, request);
    }


    @RequestMapping(value = {"/requestAuthorization.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String requestAuthorization(final HttpSession session, final HttpServletRequest request) {
        return distributeFileService.requestAuthorization(session, request);
    }

    @RequestMapping(value = {"/responseAuthorization.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String responseAuthorization(final HttpServletRequest request) {
        if (fileService.responseAuthorization(request)) {
            return "success";
        } else {
            return "failed";
        }
    }

    @RequestMapping(value = {"/getRemainingFolderView.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String getRemainingFolderView(final HttpServletRequest request) {
        return folderViewService.getRemainingFolderViewToJson(request);
    }

    @RequestMapping({"/doLogout.ajax"})
    public @ResponseBody
    String doLogout(final HttpSession session) {
        this.accountService.logout(session);
        return "SUCCESS";
    }

    @RequestMapping(value = {"/douploadFile.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String doUploadFile(final HttpServletRequest request, final HttpServletResponse response,
                               final MultipartFile file) {
        return this.distributeFileService.doUploadFile(request, response, file);
    }

    @RequestMapping({"/deleteFile.ajax"})
    @ResponseBody
    public String deleteFile(final HttpServletRequest request) {
        return this.distributeFileService.doDeleteFile(request);
    }

    @RequestMapping({"/downloadFile.do"})
    public void downloadFile(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        this.distributeFileService.doDownloadFile(request, response);
    }

    @RequestMapping(value = {"/doUploadCheckFile.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String doUploadCheckFile(final HttpServletRequest request, final HttpServletResponse response,
                                    final MultipartFile file) {
        return this.fileService.doUploadCheckFile(request, response, file);
    }

    @RequestMapping(value = {"/checkUploadFile.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String checkUploadFile(final HttpServletRequest request, final HttpServletResponse response) {
        return this.fileService.checkUploadFile(request, response);
    }

    @RequestMapping(value = {"/checkUploadCheckFile.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String checkUploadCheckFile(final HttpServletRequest request, final HttpServletResponse response) {
        return this.fileService.checkUploadCheckFile(request, response);
    }

    @RequestMapping({"/renameFile.ajax"})
    @ResponseBody
    public String renameFile(final HttpServletRequest request) {
        return this.fileService.doRenameFile(request);
    }

    @RequestMapping({"/getFileInfo.ajax"})
    @ResponseBody
    public String getFileInfo(final HttpServletRequest request) {
        return this.distributeFileService.getFileInfo(request);
    }

    @RequestMapping(value = {"/playVideo.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String playVideo(final HttpServletRequest request, final HttpServletResponse response) {
        return this.playVideoService.getPlayVideoJson(request);
    }

    /**
     * <h2>预览图片请求</h2>
     * <p>
     * 该方法用于处理预览图片请求。配合Viewer.js插件，返回指定格式的JSON数据。
     * </p>
     *
     * @param request HttpServletRequest 请求对象
     * @return String 预览图片的JSON信息
     */
    @RequestMapping(value = {"/getPrePicture.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String getPrePicture(final HttpServletRequest request) {
        return this.showPictureService.getPreviewPictureJson(request);
    }

    /**
     * <h2>获取压缩的预览图片</h2>
     * <p>
     * 该方法用于预览较大图片时获取其压缩版本以加快预览速度，该请求会根据预览目标的大小自动决定压缩等级。
     * </p>
     *
     * @param request  HttpServletRequest 请求对象，其中应包含fileId指定预览图片的文件块ID。
     * @param response HttpServletResponse 响应对象，用于写出压缩后的数据流。
     */
    @RequestMapping({"/showCondensedPicture.do"})
    public void showCondensedPicture(final HttpServletRequest request, final HttpServletResponse response) {
        showPictureService.getCondensedPicture(request, response);
    }

/*	@RequestMapping({ "/deleteCheckedFiles.ajax" })
	@ResponseBody
	public String deleteCheckedFiles(final HttpServletRequest request) {
		return this.fis.deleteCheckedFiles(request);
	}*/

    @RequestMapping({"/getPackTime.ajax"})
    @ResponseBody
    public String getPackTime(final HttpServletRequest request) {
        return this.fileService.getPackTime(request);
    }

    @RequestMapping({"/downloadCheckedFiles.ajax"})
    @ResponseBody
    public String downloadCheckedFiles(final HttpServletRequest request) {
        return this.fileService.downloadCheckedFiles(request);
    }

    @RequestMapping({"/downloadCheckedFilesZip.do"})
    public void downloadCheckedFilesZip(final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        this.fileService.downloadCheckedFilesZip(request, response);
    }

    @RequestMapping(value = {"/playAudios.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String playAudios(final HttpServletRequest request) {
        return this.playAudioService.getAudioInfoListByJson(request);
    }

    /**
     * <h2>执行全局查询</h2>
     * <p>该逻辑用于进行全局搜索，将会迭代搜索目标文件夹及其全部子文件夹以查找符合关键字的结果，并返回单独的搜索结果视图。</p>
     *
     * @param request javax.servlet.http.HttpServletRequest 请求对象
     * @return java.lang.String 搜索结果，详情请见具体实现
     */
    @RequestMapping(value = {"/sreachInCompletePath.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String sreachInCompletePath(final HttpServletRequest request) {
        return folderViewService.getSreachViewToJson(request);
    }

    /**
     * <h2>应答机制</h2>
     * <p>
     * 该机制旨在防止某些长耗时操作可能导致Session失效的问题（例如上传、视频播放等），方便用户持续操作。
     * </p>
     *
     * @return String “pong”或“”
     */
    @RequestMapping(value = {"/ping.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String pong(final HttpServletRequest request) {
        return accountService.doPong(request);
    }

    // 询问是否开启自由注册新账户功能
    @RequestMapping(value = {"/askForAllowSignUpOrNot.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String askForAllowSignUpOrNot(final HttpServletRequest request) {
        return accountService.isAllowSignUp();
    }

    // 处理注册新账户请求
    @RequestMapping(value = {"/doSigUp.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String doSigUp(final HttpServletRequest request) {
        return accountService.doSignUp(request);
    }

    // 获取永久资源链接的对应ckey
    @RequestMapping(value = {"/getFileChainKey.ajax"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String getFileChainKey(final HttpServletRequest request) {
        return fileChainService.getChainKeyByFid(request);
    }
}
