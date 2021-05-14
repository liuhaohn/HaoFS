package com.hao.server.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.hao.server.enumeration.AccountAuth;
import org.springframework.stereotype.Service;

import com.hao.server.mapper.FolderMapper;
import com.hao.server.mapper.NodeMapper;
import com.hao.server.model.Node;
import com.hao.server.service.ExternalDownloadService;
import com.hao.server.util.ConfigureReader;
import com.hao.server.util.FileBlockUtil;
import com.hao.server.util.FolderUtil;
import com.hao.server.util.LogUtil;
import com.hao.server.util.RangeFileStreamWriter;

@Service
public class ExternalDownloadServiceImpl extends RangeFileStreamWriter implements ExternalDownloadService {

	private static Map<String, String> downloadKeyMap = new HashMap<>();// 凭证池，用于存储生成好的下载凭证
	private static final String CONTENT_TYPE = "application/octet-stream";

	@Autowired
	private NodeMapper nm;
	@Autowired
	private LogUtil lu;
	@Autowired
	private FileBlockUtil fbu;
	@Autowired
	private FolderUtil fu;
	@Autowired
	private FolderMapper fm;

	@Override
	public String getDownloadKey(HttpServletRequest request) {
		// 首先进行权限检查
		final String account = (String) request.getSession().getAttribute("ACCOUNT");
		// 找到要下载的文件节点
		final String fileId = request.getParameter("fId");
		if (fileId != null) {
			final Node f = this.nm.queryById(fileId);
			if (f != null) {
				// 权限检查
				if (ConfigureReader.instance().authorized(account, AccountAuth.DOWNLOAD_FILES,
						fu.getAllFoldersId(f.getFileParentFolder()))
						&& ConfigureReader.instance().accessFolder(fm.queryById(f.getFileParentFolder()), account)) {
					// 获取凭证
					synchronized (downloadKeyMap) {
						// 查找该资源是否已经生成了一个凭证，如有，则直接使用，否则，新生成一个加入到凭证表。
						this.lu.writeShareFileURLEvent(request, f);
						if (downloadKeyMap.containsValue(f.getFileId())) {
							Entry<String, String> k = downloadKeyMap.entrySet().parallelStream()
									.filter((e) -> e.getValue().equals(f.getFileId())).findFirst().get();
							return k.getKey();
						} else {
							String dKey = UUID.randomUUID().toString();
							downloadKeyMap.put(dKey, f.getFileId());
							return dKey;
						}
					}
				}
			}
		}
		return "ERROR";
	}

	@Override
	public void downloadFileByKey(HttpServletRequest request, HttpServletResponse response) {
		final String dkey = request.getParameter("dkey");
		// 权限凭证有效性并确认其对应的资源
		if (dkey != null) {
			// 找到要下载的文件节点
			String fId = null;
			synchronized (downloadKeyMap) {
				fId = downloadKeyMap.get(dkey);
			}
			if (fId != null) {
				Node f = this.nm.queryById(fId);
				if (f != null) {
					File target = this.fbu.getFileFromBlocks(f);
					if (target != null && target.isFile()) {
						String range = request.getHeader("Range");
						int status = writeRangeFileStream(request, response, target, f.getFileName(), CONTENT_TYPE,
								ConfigureReader.instance().getDownloadMaxRate(null), fbu.getETag(target), true);
						if (status == HttpServletResponse.SC_OK || (range != null && range.startsWith("bytes=0-"))) {
							this.lu.writeDownloadFileByKeyEvent(request, f);
						}
						return;
					}
				}
			}
		}
		try {
			//  处理无法下载的资源
			response.sendError(404);
		} catch (IOException e) {

		}
	}

}
