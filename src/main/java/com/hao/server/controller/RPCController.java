package com.hao.server.controller;

import com.hao.server.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <h2>分布式peer执行类</h2>
 *
 * @version 1.0
 */
@Controller
@RequestMapping({"/rpcController"})
public class RPCController {
    private static final String CHARSET_BY_AJAX = "text/html; charset=utf-8";

    @Autowired
    private FileService fileService;

    @RequestMapping(value = {"/uploadBlock.rpc"}, produces = {CHARSET_BY_AJAX})
    @ResponseBody
    public String uploadBlock(final HttpServletRequest request) throws IOException, ServletException {
        // 存储到自己的节点
        if (!this.fileService.uploadBlock(request, request.getPart("block"))) {
            return "FAILED";
        }
        // 发现是否还有其他兄弟节点，有则都发一份
        return "SUCCESS";
    }

    @RequestMapping({"/deleteBlock.rpc"})
    @ResponseBody
    public String deleteBlock(final HttpServletRequest request) {
        if (!this.fileService.deleteBlock(request)) {
            return "FAILED";
        }
        return "SUCCESS";
    }

    @RequestMapping({"/downloadBlock.rpc"})
    public void downloadBlock(final HttpServletRequest request, final HttpServletResponse response) {
        this.fileService.downloadBlock(request, response);
    }

    @RequestMapping({"/requestAuthorization.rpc"})
    @ResponseBody
    public String requestAuthorization(final HttpServletRequest request, final HttpServletResponse response) {
        if (this.fileService.requestAuthorization(request, response)) {
            return "success";
        } else {
            return "failed";
        }
    }
}
