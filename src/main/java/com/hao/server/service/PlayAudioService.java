package com.hao.server.service;

import javax.servlet.http.*;

public interface PlayAudioService
{
    String getAudioInfoListByJson(final HttpServletRequest request);
}
