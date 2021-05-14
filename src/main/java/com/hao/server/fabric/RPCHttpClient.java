package com.hao.server.fabric;

import com.google.gson.JsonParser;
import com.hao.server.fabric.NodeState;
import com.hao.server.fabric.Wallet;
import com.hao.server.util.RSACipherUtil;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

@Component
@Scope("prototype")
public class RPCHttpClient {
    private final Map<NodeState, HttpClient> httpClients = new HashMap<>();
    @Autowired
    private Wallet wallet;

    /**
     * 保证登录
     */
    private void login(CloseableHttpClient httpClient, NodeState nodeState) {
        try {
            for (int i = 0; i < 2; i++) {
                // 获取公钥
                HttpUriRequest uriRequest = RequestBuilder.post(String.format("http://%s:%s/homeController/getPublicKey.ajax",
                        nodeState.getIpAddress(), nodeState.getPort())).build();
                CloseableHttpResponse rsaPub = httpClient.execute(uriRequest);
                String publicKey = JsonParser.parseReader(new InputStreamReader(rsaPub.getEntity().getContent()))
                        .getAsJsonObject().get("publicKey").getAsString();

                // 加密登录，都是使用本visitor的身份登录的
                String org1 = wallet.getStringIdentity("visitor");  // 使用本地visitor身份登录到其他node
                String encryption = RSACipherUtil.encryption(org1, publicKey);
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("encrypted", encryption));
                UrlEncodedFormEntity urlEncodedFormEntity = new UrlEncodedFormEntity(params);
                HttpUriRequest request = RequestBuilder.post(String.format("http://%s:%s/homeController/doLogin.ajax",
                        nodeState.getIpAddress(), nodeState.getPort())).setEntity(urlEncodedFormEntity).build();
                CloseableHttpResponse response = httpClient.execute(request);

                if (response != null && EntityUtils.toString(response.getEntity()).equals("permitlogin")) {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 目前要求获取的stateList中的节点必须都存活
     * TODO 如果只有部分节点上传成功怎么办，应该提前检测那些节点存活
     */
    public boolean distributeBlocks(List<NodeState> stateList, String fileHash, File... files) {
        HttpUriRequest[] requests = new HttpUriRequest[files.length];
        String[] suffix = new String[]{"-k01", "-k02", "-m01"};
        for (int i = 0; i < files.length; i++) {
            HttpEntity entity = MultipartEntityBuilder.create().addBinaryBody("block", files[i],
                    ContentType.APPLICATION_OCTET_STREAM, files[i].getName()).build();
            NodeState nodeState = stateList.get(i);
            // 文件直接分发到选出来的Node中
            String target = String.format("http://%s:%s/rpcController/uploadBlock.rpc",
                    nodeState.getIpAddress(), nodeState.getPort());
            requests[i] = RequestBuilder.post(target).setEntity(entity)
                    .addParameter("fHash", fileHash + suffix[i]).build();
        }

        // TODO 并行异步
        for (int i = 0; i < files.length; i++) {
            NodeState nodeState = stateList.get(i);
            HttpClient httpClient = httpClients.get(nodeState);
            if (httpClient == null) {
                httpClient = HttpClients.createDefault();
                login((CloseableHttpClient) httpClient, nodeState);
                httpClients.put(nodeState, httpClient);
            }

            boolean isSuccess = false;
            for (int j = 0; j < 2; j++) {   // 尝试2次
                try {
                    HttpResponse response = httpClient.execute(requests[i]);
                    if ("SUCCESS".equals(EntityUtils.toString(response.getEntity()))) {
                        isSuccess = true;
                        break;
                    }
                    login((CloseableHttpClient) httpClient, nodeState);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (!isSuccess) return false;
        }
        return true;
    }

    /**
     * fileHash是原文件的Hash
     */
    public boolean deleteDistributedBlocks(List<NodeState> nodeStates, String fileHash) {
        HttpUriRequest[] requests = new HttpUriRequest[nodeStates.size()];
        String[] suffix = new String[]{"-k01", "-k02", "-m01"};
        for (int i = 0; i < nodeStates.size(); i++) {
            NodeState nodeState = nodeStates.get(i);
            requests[i] = RequestBuilder.post(String.format("http://%s:%s/rpcController/deleteBlock.rpc",
                    nodeState.getIpAddress(), nodeState.getPort()))
                    .addParameter("fileId", fileHash + suffix[i]).build();
        }

        for (int i = 0; i < nodeStates.size(); i++) {
            NodeState nodeState = nodeStates.get(i);
            HttpClient httpClient = httpClients.get(nodeState);
            if (httpClient == null) {
                httpClient = HttpClients.createDefault();
                login((CloseableHttpClient) httpClient, nodeState);
                httpClients.put(nodeState, httpClient);
            }

            boolean isSuccess = false;
            for (int j = 0; j < 2; j++) {   // 尝试2次
                try {
                    HttpResponse response = httpClient.execute(requests[i]);
                    if ("SUCCESS".equals(EntityUtils.toString(response.getEntity()))) {
                        isSuccess = true;
                        break;
                    }
                    login((CloseableHttpClient) httpClient, nodeState);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (!isSuccess && i == 0) return false;   // 第一次不成功，就不删了
        }
        return true;
    }

    /**
     * 只要从三个中下载两个就行
     */
    public List<InputStream> downloadDistributedBlocks(List<NodeState> nodeStates, String fileHash) {
        HttpUriRequest[] requests = new HttpUriRequest[nodeStates.size()];
        String[] suffix = new String[]{"-k01", "-k02", "-m01"};
        for (int i = 0; i < nodeStates.size(); i++) {
            NodeState nodeState = nodeStates.get(i);
            requests[i] = RequestBuilder.post(String.format("http://%s:%s/rpcController/downloadBlock.rpc",
                    nodeState.getIpAddress(), nodeState.getPort()))
                    .addParameter("fileId", fileHash + suffix[i]).build();
        }

        List<InputStream> res = Arrays.asList(new InputStream[3]);
        int size = 0;
        for (int i = 0; i < nodeStates.size(); i++) {
            NodeState nodeState = nodeStates.get(i);
            HttpClient httpClient = httpClients.get(nodeState);
            if (httpClient == null) {
                httpClient = HttpClients.createDefault();
                login((CloseableHttpClient) httpClient, nodeState);
                httpClients.put(nodeState, httpClient);
            }
            for (int j = 0; j < 3; j++) {   // 尝试3次
                try {
                    HttpResponse response = httpClient.execute(requests[i]);
                    if (response.getEntity() != null) {
                        res.set(i, response.getEntity().getContent());
                        size++;
                        break;
                    }
                    login((CloseableHttpClient) httpClient, nodeState);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (size == 2) return res;
        }
        return null;
    }

    public boolean requestAuthorization(NodeState nodeState, String fileHash) {
        HttpUriRequest request = RequestBuilder.post(String.format("http://%s:%s/rpcController/requestAuthorization.rpc",
                nodeState.getIpAddress(), nodeState.getPort()))
                .addParameter("fileHash", fileHash).build();
        HttpClient httpClient = httpClients.get(nodeState);
        if (httpClient == null) {
            httpClient = HttpClients.createDefault();
            login((CloseableHttpClient) httpClient, nodeState);
            httpClients.put(nodeState, httpClient);
        }

        for (int j = 0; j < 3; j++) {   // 尝试2次
            try {
                HttpResponse response = httpClient.execute(request);
                if (response.getEntity() != null && EntityUtils.toString(response.getEntity()).equals("success")) {
                    return true;
                }
                login((CloseableHttpClient) httpClient, nodeState);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return false;
    }
}