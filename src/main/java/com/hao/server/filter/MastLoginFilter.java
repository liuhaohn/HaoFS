package com.hao.server.filter;

import com.hao.server.util.ConfigureReader;
import org.springframework.core.annotation.Order;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;

@WebFilter
@Order(2)
public class MastLoginFilter implements Filter {
    public void init(final FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        final ConfigureReader cr = ConfigureReader.instance();
        final boolean s = cr.mustLogin();
        final HttpServletRequest hsq = (HttpServletRequest) request;
        final HttpServletResponse hsr = (HttpServletResponse) response;
        final String url = hsq.getServletPath();
        final HttpSession session = hsq.getSession();
        if (url.startsWith("/externalLinksController/") || url.startsWith("//externalLinksController/")
                || url.startsWith("/homeController/getNewVerCode.do")
                || url.startsWith("//homeController/getNewVerCode.do")) {
            chain.doFilter(request, response);// 对于外部链接控制器和验证码的请求直接放行。
            return;
        }
        // 如果是无需登录的请求，那么直接放行（如果访问者已经登录，那么会被后面的过滤器重定向至主页，此处无需处理）
        switch (url) {
            case "/prv/login.html":
            case "//prv/login.html":
            case "/homeController/askForAllowSignUpOrNot.ajax":
            case "//homeController/askForAllowSignUpOrNot.ajax":
            case "/prv/signup.html":
            case "//prv/signup.html":
                chain.doFilter(request, response);
                return;
            default:
                break;
        }
        if (s) {
            if (url.equals("/") || url.endsWith(".html") || url.endsWith(".do")) {
                if (session.getAttribute("ACCOUNT") != null) {
                    chain.doFilter(request, response);
                } else {
                    hsr.sendRedirect("/prv/login.html");
                }
            } else if (url.endsWith(".ajax")) {
                if (url.equals("/homeController/doLogin.ajax") || url.equals("/homeController/getPublicKey.ajax")
                        || url.equals("/homeController/doSigUp.ajax")) {
                    chain.doFilter(request, response);
                } else if (session.getAttribute("ACCOUNT") != null) {
                    chain.doFilter(request, response);
                } else {
                    hsr.setCharacterEncoding("UTF-8");
                    final PrintWriter pw2 = hsr.getWriter();
                    pw2.print("mustLogin");
                    pw2.flush();
                }
            } else {
                chain.doFilter(request, response);
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    public void destroy() {
    }
}
