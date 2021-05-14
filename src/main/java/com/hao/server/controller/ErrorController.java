package com.hao.server.controller;

import com.hao.printer.Printer;
import com.hao.server.util.FileBlockUtil;
import com.hao.server.util.LogUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ErrorController {
	@Autowired
	private FileBlockUtil fbu;
	@Autowired
	private LogUtil lu;

	@ExceptionHandler({ Exception.class })
	public void handleException(final Exception e) {
		this.lu.writeException(e);
		this.fbu.checkFileBlocks();
		Printer.instance
				.print("\u5904\u7406\u8bf7\u6c42\u65f6\u53d1\u751f\u9519\u8bef\uff1a\n\r------\u4fe1\u606f------\n\r"
						+ e.getMessage() + "\n\r------\u4fe1\u606f------");
	}
}
