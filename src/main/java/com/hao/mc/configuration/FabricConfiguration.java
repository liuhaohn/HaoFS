package com.hao.mc.configuration;

import com.hao.server.fabric.Wallet;
import org.hyperledger.fabric.gateway.*;
import org.springframework.context.annotation.*;

import java.io.*;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

@Configuration
@ComponentScan({"com.hao.server.fabric"})
public class FabricConfiguration {
    @Bean
    public Gateway gateway(Wallet wallet) {
        for (int i = 0; i < 3; i++) {
            try {
                return Gateway.createBuilder()
                        .networkConfig(new FileInputStream(wallet.getConnectionConfig()))
                        .identity(wallet.getIdentity("admin")).discovery(true).connect();
            } catch (IOException ignored) {
            }
        }
        throw new Error("connect fabric failed");
    }

    @Bean
    public Contract contract(Gateway gateway) throws UnknownHostException, InterruptedException, TimeoutException, ContractException {
        return gateway.getNetwork("filenet").getContract("fncc");
    }

}

