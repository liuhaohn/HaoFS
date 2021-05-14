package com.hao.mc;

import com.hao.printer.Printer;
import com.hao.mc.configuration.DataAccessConfiguration;
import com.hao.mc.configuration.FabricConfiguration;
import com.hao.mc.configuration.MVCConfiguration;
import com.hao.server.util.ConfigureReader;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.Banner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

/**
 * <h2>服务器引擎控制器</h2>
 * <p>
 * 该类为服务器引擎的控制层，负责连接服务器内核与用户操作界面，用于控制服务器行为。包括启动、关闭、重启等。同时，该类也为SpringBoot框架
 * 应用入口，负责初始化SpringBoot容器。详见内置公有方法。
 * </p>
 *
 * @version 1.0
 */
@SpringBootApplication
@Import({MVCConfiguration.class, FabricConfiguration.class})
@MapperScan(basePackages = "com.hao.server.mapper")
public class MainCtl {
    private static ApplicationContext context;
    private static boolean run = false;

    // 这里负责设置一些系统参数
    static {
        // 简化SpringBoot框架本身的日志信息输出，避免控制台信息杂乱影响操作
        System.setProperty("logging.level.root", "ERROR");
    }

    /**
     * <h2>启动服务器</h2>
     * <p>
     * 该方法将启动SpringBoot服务器引擎并返回启动结果。该过程较为耗时，为了不阻塞主线程，请在额外线程中执行该方法。
     * </p>
     *
     * @return boolean 启动结果
     */
    public static void main(String[] args) {
        Printer.init(false);
        Printer.instance.print("Initializing server settings...");
        if (!MainCtl.run) {
            ConfigureReader.instance().reTestServerPropertiesAndEffect();// 启动服务器前重新检查各项设置并加载
            if (ConfigureReader.instance().getPropertiesStatus() == 0) {
                Printer.instance.print("Starting server engine...");
                SpringApplication springApplication = new SpringApplication(MainCtl.class);
                springApplication.setBannerMode(Banner.Mode.OFF);// 关闭自定义标志输出，简化日志信息
                MainCtl.context = springApplication.run(args);
                MainCtl.run = (MainCtl.context != null);
                Printer.instance.print("Server engine started.");
            }
        }
        Printer.instance.print("Server is running.");
    }



    // SpringBoot内置Tomcat引擎必要设置：端口、错误页面及HTTPS支持
    @Bean
    public ServletWebServerFactory servletContainer() {
        // 创建Tomcat容器引擎，分为开启https和不开启https两种模式
        TomcatServletWebServerFactory tomcat = null;
        if (ConfigureReader.instance().openHttps()) {
            // 对于开启https模式，则将http端口的请求全部转发至https端口处理
            tomcat = new TomcatServletWebServerFactory() {
                // 设置默认http处理转发
                @Override
                protected void customizeConnector(Connector connector) {
                    connector.setScheme("http");
                    // Connector监听的http的端口号
                    connector.setPort(ConfigureReader.instance().getPort());
                    connector.setSecure(false);
                    // 监听到http的端口号后转向到的https的端口号
                    connector.setRedirectPort(ConfigureReader.instance().getHttpsPort());
                }

                // 设置默认http处理
                @Override
                protected void postProcessContext(Context context) {
                    SecurityConstraint constraint = new SecurityConstraint();
                    constraint.setUserConstraint("CONFIDENTIAL");
                    SecurityCollection collection = new SecurityCollection();
                    collection.addPattern("/*");
                    constraint.addCollection(collection);
                    context.addConstraint(constraint);
                }
            };
            // 添加https链接处理器
            tomcat.addAdditionalTomcatConnectors(createHttpsConnector());
        } else {
            // 对于不开启https模式，以常规方法生成容器
            tomcat = new TomcatServletWebServerFactory();
            tomcat.setPort(ConfigureReader.instance().getPort());
        }
        // 设置错误处理页面
        tomcat.addErrorPages(new ErrorPage[]{new ErrorPage(HttpStatus.NOT_FOUND, "/prv/error.html"),
                new ErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, "/prv/error.html"),
                new ErrorPage(HttpStatus.UNAUTHORIZED, "/prv/error.html"),
                new ErrorPage(HttpStatus.FORBIDDEN, "/prv/forbidden.html")});
        return tomcat;
    }

    // 生成https支持配置，包括端口号、证书文件、证书密码等
    private Connector createHttpsConnector() {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        // 配置针对Https的支持
        connector.setScheme("https");// 设置请求协议头
        connector.setPort(ConfigureReader.instance().getHttpsPort());// 设置https请求端口
        Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
        protocol.setSSLEnabled(true);// 开启SSL加密通信
        protocol.setKeystoreFile(ConfigureReader.instance().getHttpsKeyFile());// 设置证书文件
        protocol.setKeystoreType(ConfigureReader.instance().getHttpsKeyType());// 设置加密类别（PKCS12/JKS）
        protocol.setKeystorePass(ConfigureReader.instance().getHttpsKeyPass());// 设置证书密码
        return connector;
    }
}
