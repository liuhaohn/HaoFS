package com.hao.server.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.hao.server.util.ConfigureReader;
import com.hao.server.util.IpAddrGetter;

/**
 * 
 * <h2>阻止特定IP访问过滤器</h2>
 * <p>该过滤器用于阻止特定IP进行访问，从而保护用户资源。</p>
 * @version 1.0
 */
@WebFilter
@Order(1)
public class IPFilter implements Filter {

	private IpAddrGetter idg;

	@Override
	public void init(FilterConfig filterConfig) {
		ApplicationContext context = WebApplicationContextUtils
				.getWebApplicationContext(filterConfig.getServletContext());
		idg = context.getBean(IpAddrGetter.class);
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		if(ConfigureReader.instance().enableIPRule()) {
			HttpServletRequest hsr = (HttpServletRequest) request;
			if(ConfigureReader.instance().filterAccessIP(idg.getIpAddr(hsr))) {
				chain.doFilter(request, response);
			}else {
				((HttpServletResponse)response).sendError(HttpServletResponse.SC_FORBIDDEN);
			}
		}else {
			chain.doFilter(request, response);
		}
	}

	@Override
	public void destroy() {
	}

}
