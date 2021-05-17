# HaoFS：基于联盟链的分布式点对点文件系统

## 项目介绍

  ![image](https://user-images.githubusercontent.com/51185162/118489300-430a9b00-b74f-11eb-9cb2-4974414d29e0.png)

联盟链分布式文件存储共享系统是一个点对点结构的分布式存储系统，总体框架上图所示。其中“组织”指的是现实中的组织实体，如企业、政府等。“联盟”指的是一些组织实体联合建立的集团。“联盟链”是一种区块链，其只允许联盟中的组织访问和修改此区块链。

联盟中组织共同维护区块链和区块链文件共享系统，每个联盟成员需要部署自己的区块链文件存储节点，由于各文件存储节点是对等关系，所以在图中称为“对等文件节点”，各组织的对等文件节点联合构成联盟链分布式文件存储共享系统。每个对等文件节点中都实现了联盟链数据接口模块、联盟文件存储模块、联盟身份认证模块、联盟权限控制模块、联盟文件检索模块和节点Web服务模块。此外每个组织都需要部署一个CA节点用于颁发数字证书，该证书标识的身份是其他组织认证身份的基础。

项目详细说明见`使用说明书.doc`

## 项目运行

- 项目测试和部署可以使用 `project.sh` 中的命令，其中使用 `./project.sh up -img` 可以将项目打包为Docker镜像，该镜像运行后即一个对等文件节点，其所属组织是 `conf/Admin@xxx` 证书文件决定的。

- 项目部署基于Hyperledger Fabric联盟链网络，启动前先要部署联盟链网络，然后各组织需要安装FileContract智能合约，联盟链测试环境和智能合约在FileChain中。项目中假设fabric在一个ip下，如果不在，自行修改`docker-compose.yaml`文件，并将`conf`目录配置好

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
