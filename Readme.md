- 项目用`project.sh`中的命令就可以了，项目打包用`./project.sh up -img`
  
- 项目使用说明间 `使用说明书.doc`

- 项目启动前要启动fabric网络，项目中假设fabric在一个ip下，如果不在，自行修改`docker-compose.yaml`文件，并将`conf`目录配置好

  `conf`目录结构（以org1组织的为例）：
  
  ```
      conf
      ├── account.properties            # 历史遗留问题，不要删，保留原样就可以
      ├── server.properties             # 可以配置port
      ├── Admin@org1.example.com.id     # 当前组织的admin用户证书
      ├── ca.org1.example.com-cert.pem  # 各org的ca根证书
      ├── ca.org2.example.com-cert.pem
      ├── ca.org3.example.com-cert.pem
      ├── connection-org1.yaml          # 当前组织连接到fabric的配置
      ├── User1@org1.example.com.id     # 当前组织的user用户证书
      ├── Visitor@org1.example.com.id   # 当前组织的visitor用户证书
      ├── Visitor@org2.example.com.id   # org2的visitor身份的公钥
      └── Visitor@org3.example.com.id   # org3的visitor身份的公钥
  ```