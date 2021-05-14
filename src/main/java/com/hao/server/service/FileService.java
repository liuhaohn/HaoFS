package com.hao.server.service;

import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

public interface FileService {
    String FOLDERS_TOTAL_OUT_OF_LIMIT = "foldersTotalOutOfLimit";// 文件夹数量超限标识
    String FILES_TOTAL_OUT_OF_LIMIT = "filesTotalOutOfLimit";// 文件数量超限标识
    String ERROR_PARAMETER = "errorParameter";// 参数错误标识
    String NO_AUTHORIZED = "noAuthorized";// 权限错误标识
    String UPLOAD_SUCCESS = "uploadsuccess";// 上传成功标识
    String UPLOAD_ERROR = "uploaderror";// 上传失败标识
    String FILE_IDENTICAL = "fileidentical"; //上传同hash的文件错误标识
    String FILE_NOT_EXIST = "filenotexist";
    String FILE_DELETED = "filedeleted";

    String DELETE_NO_AUTHORIZED = "noAuthorized";
    String DELETE_FILE_SUCCESS = "deleteFileSuccess";
    String DELETE_CANNOT = "cannotDeleteFile";
    String DELETE_ERROR_PARAMETER = "errorParameter";


    String checkUploadFile(final HttpServletRequest request, final HttpServletResponse response);

    /**
     * 存储分布的文件块
     */
    boolean uploadBlock(final HttpServletRequest request, final Part file);

    boolean deleteBlock(HttpServletRequest request);

    void downloadBlock(HttpServletRequest request, HttpServletResponse response);

    Object uploadFile(final HttpServletRequest request, final MultipartFile file, String fileHash);

    boolean deleteFile(final HttpServletRequest request);

    boolean downloadFile(final HttpServletRequest request, final HttpServletResponse response);

    String doRenameFile(final HttpServletRequest request);

    String getPackTime(final HttpServletRequest request);

    String downloadCheckedFiles(final HttpServletRequest request);

    void downloadCheckedFilesZip(final HttpServletRequest request, final HttpServletResponse response) throws Exception;

    String checkUploadCheckFile(HttpServletRequest request, HttpServletResponse response);

    String doUploadCheckFile(HttpServletRequest request, HttpServletResponse response, MultipartFile file);

    boolean requestAuthorization(HttpServletRequest request, HttpServletResponse response);

    boolean responseAuthorization(HttpServletRequest request);
}
