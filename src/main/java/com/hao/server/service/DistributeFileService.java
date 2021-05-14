package com.hao.server.service;

import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;


/**
 * 分布式处理文件请求
 * */
public interface DistributeFileService {
    String doUploadFile(HttpServletRequest request, HttpServletResponse response, MultipartFile file);

    String doDeleteFile(HttpServletRequest request);

    void doDownloadFile(HttpServletRequest request, HttpServletResponse response) throws IOException;

    String getFileInfo(HttpServletRequest request);

    String getSearchView(HttpSession session, HttpServletRequest request);

    String requestAuthorization(HttpSession session, HttpServletRequest request);
}
