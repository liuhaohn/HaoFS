package com.hao.server.service.impl;

import com.google.gson.Gson;
import com.hao.server.enumeration.AccountAuth;
import com.hao.server.mapper.FolderMapper;
import com.hao.server.mapper.NodeMapper;
import com.hao.server.model.Node;
import com.hao.server.pojo.AudioInfoList;
import com.hao.server.service.PlayAudioService;
import com.hao.server.util.AudioInfoUtil;
import com.hao.server.util.ConfigureReader;
import com.hao.server.util.FolderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Service
public class PlayAudioServiceImpl implements PlayAudioService {
	@Autowired
	private NodeMapper fm;
	@Autowired
	private AudioInfoUtil aiu;
	@Autowired
	private Gson gson;
	@Autowired
	private FolderUtil fu;
	@Autowired
	private FolderMapper flm;

	private AudioInfoList foundAudios(final HttpServletRequest request) {
		final String fileId = request.getParameter("fileId");
		if (fileId != null && fileId.length() > 0) {
			Node targetNode = fm.queryById(fileId);
			if (targetNode != null) {
				final String account = (String) request.getSession().getAttribute("ACCOUNT");
				if (ConfigureReader.instance().authorized(account, AccountAuth.DOWNLOAD_FILES,
						fu.getAllFoldersId(targetNode.getFileParentFolder()))
						&& ConfigureReader.instance().accessFolder(flm.queryById(targetNode.getFileParentFolder()),
								account)) {
					final List<Node> blocks = (List<Node>) this.fm.queryBySomeFolder(fileId);
					return this.aiu.transformToAudioInfoList(blocks, fileId);
				}
			}
		}
		return null;
	}

	/**
	 * <h2>解析播放音频文件</h2>
	 * <p>
	 * 根据音频文件的ID查询音频文件节点，以及同级目录下所有音频文件组成播放列表，并返回节点JSON信息，以便发起播放请求。
	 * </p>
	 * 
	 * @author kohgylw
	 * @param request
	 *            javax.servlet.http.HttpServletRequest 请求对象
	 * @return String 视频节点的JSON字符串
	 */
	public String getAudioInfoListByJson(final HttpServletRequest request) {
		final AudioInfoList ail = this.foundAudios(request);
		if (ail != null) {
			return gson.toJson((Object) ail);
		}
		return "ERROR";
	}
}
