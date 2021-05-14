package com.hao.server.service.impl;

import com.hao.server.service.ServerInfoService;
import com.hao.server.service.*;
import org.springframework.stereotype.*;
import java.util.*;
import java.text.*;

@Service
public class ServerInfoServiceImpl implements ServerInfoService
{
    @Override
    public String getOSName() {
        return System.getProperty("os.name");
    }
    
    @Override
    public String getServerTime() {
        final Date d = new Date();
        final DateFormat df = new SimpleDateFormat("yyyy.MM.dd hh:mm");
        return df.format(d);
    }
}
