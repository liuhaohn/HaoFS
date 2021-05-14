package com.hao.server.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hao.server.enumeration.VCLevel;
import com.hao.server.fabric.FabricDao;
import com.hao.server.fabric.MSP;
import com.hao.server.fabric.RPCHttpClient;
import com.hao.server.fabric.Wallet;
import com.hao.server.pojo.ChangePasswordInfoPojo;
import com.hao.server.pojo.PublicKeyInfo;
import com.hao.server.pojo.SignUpInfoPojo;
import com.hao.server.service.AccountService;
import com.hao.server.util.*;
import org.hyperledger.fabric.gateway.Identities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;

@Service
public class AccountServiceImpl implements AccountService {
    @Autowired
    private RSAKeyUtil ku;
    @Autowired
    private LogUtil lu;
    @Autowired
    private Gson gson;
    @Autowired
    private MSP msp;
    @Autowired
    ApplicationContext applicationContext;

    // 登录密钥有效期
    private static final long TIME_OUT = 30000L;

    private VerificationCodeFactory vcf;

    private CharsetEncoder ios8859_1Encoder;

    {
        ios8859_1Encoder = Charset.forName("ISO-8859-1").newEncoder();
        if (!ConfigureReader.instance().getVCLevel().equals(VCLevel.Close)) {
            int line = 0;
            int oval = 0;
            switch (ConfigureReader.instance().getVCLevel()) {
                case Standard: {
                    line = 6;
                    oval = 2;
                    break;
                }
                case Simplified: {
                    line = 1;
                    oval = 0;
                    break;
                }
                default:
                    break;
            }
            // 验证码生成工厂，包含了一些不太容易误认的字符
            vcf = new VerificationCodeFactory(45, line, oval, 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'j', 'k', 'm',
                    'n', 'p', 'q', 'r', 's', 't', 'w', 'x', 'y', 'z', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B',
                    'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'M', 'N', 'P', 'Q', 'R', 'S', 'T', 'W', 'X', 'Y', 'Z');
        }
    }

    // 关注账户，当任意一个账户登录失败后将加入至该集合中，登录成功则移除。登录集合中的账户必须进行验证码验证
    private static final Set<String> focusAccount = new HashSet<>();

    public String checkLoginRequest(final HttpServletRequest request, final HttpSession session) {
        String encrypted = request.getParameter("encrypted");
        try {
            String loginInfoStr = RSACipherUtil.decryption(encrypted, ku.getPrivateKey());
            JsonElement parse = JsonParser.parseString(loginInfoStr);
            JsonObject credentials = parse.getAsJsonObject().get("credentials").getAsJsonObject();
            String certificate = credentials.get("certificate").getAsString();
            X509Certificate x509Certificate = Identities.readX509Certificate(certificate);
            // 验证证书是否正确，如果证书正确则visitor用户就已经可以登录了，这里认为其证书不会泄密
            // TODO 验证登录方是否是此用户，就是发送随机文本让其签名（或者用此公钥加密文本让其解密）
            if (!msp.verifyCertificate(x509Certificate)) {
                return "error";
            }
            // 用户OU
            String organization = msp.parseOrganization(x509Certificate);
            String organizationUnion = msp.parseOrganizationUnion(x509Certificate);
            if (organizationUnion.equals("client")) {     // 如果是本地用户则验证私钥正确性
                String privateKey = credentials.get("privateKey").getAsString();
                if (!organization.equals(applicationContext.getBean(FabricDao.class).getLocalMspId()) || !msp.verifyPrivateKey(privateKey, x509Certificate.getPublicKey())) {    // 本地用户不合法
                    return "accountnotfound";
                }
                session.setAttribute("ACCOUNT", organization);
                session.setAttribute("RPCHttpClient", applicationContext.getBean("RPCHttpClient"));
                return "permitlogin";
            } else if (organizationUnion.equals("visitor")) {
                session.setAttribute("VISITOR", organization);
                return "permitlogin";
            } else {
                return "accountnotfound";
            }
        } catch (Exception e) {
            return "error";
        }
    }

    public void logout(final HttpSession session) {
        session.invalidate();
    }

    public String getPublicKey() {
        PublicKeyInfo pki = new PublicKeyInfo();
        pki.setPublicKey(ku.getPublicKey());
        pki.setTime(System.currentTimeMillis());
        return gson.toJson(pki);
    }

    @Override
    public void getNewLoginVerCode(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
        try {
            if (ConfigureReader.instance().getVCLevel().equals(VCLevel.Close)) {
                response.sendError(404);
            } else {
                VerificationCode vc = vcf.next(4);
                session.setAttribute("VERCODE", vc.getCode());
                response.setContentType("image/png");
                OutputStream out = response.getOutputStream();
                vc.saveTo(out);
                out.flush();
                out.close();
            }
        } catch (IOException e) {
            try {
                response.sendError(500);
            } catch (IOException e1) {

            }
        }
    }

    @Override
    public String doPong(HttpServletRequest request) {
        if (request.getSession().getAttribute("ACCOUNT") != null) {
            return "pong";// 只有登录了的账户才有必要进行应答
        } else {
            return "";// 未登录则不返回标准提示，但也做应答（此时，前端应停止后续应答以节省线程开支）
        }
    }

    @Override
    public String changePassword(HttpServletRequest request) {
        // 验证是否开启了用户修改密码功能
        if (!ConfigureReader.instance().isAllowChangePassword()) {
            return "illegal";
        }
        // 必须登录了一个账户
        HttpSession session = request.getSession();
        final String account = (String) session.getAttribute("ACCOUNT");
        if (account == null) {
            return "mustlogin";
        }
        // 解析修改密码请求
        final String encrypted = request.getParameter("encrypted");
        try {
            final String changePasswordInfoStr = RSACipherUtil.decryption(encrypted, ku.getPrivateKey());
            final ChangePasswordInfoPojo info = gson.fromJson(changePasswordInfoStr.replaceAll("\\\\", "\\\\\\\\"),
                    ChangePasswordInfoPojo.class);
            if (System.currentTimeMillis() - Long.parseLong(info.getTime()) > TIME_OUT) {
                return "error";
            }
            // 如果验证码开启且该账户已被关注，则要求提供验证码
            if (!ConfigureReader.instance().getVCLevel().equals(VCLevel.Close)) {
                synchronized (focusAccount) {
                    if (focusAccount.contains(account)) {
                        String reqVerCode = request.getParameter("vercode");
                        String trueVerCode = (String) session.getAttribute("VERCODE");
                        session.removeAttribute("VERCODE");// 确保一个验证码只会生效一次，无论对错
                        if (reqVerCode == null || trueVerCode == null
                                || !trueVerCode.equals(reqVerCode.toLowerCase())) {
                            return "needsubmitvercode";
                        }
                    }
                }
            }
            if (ConfigureReader.instance().checkAccountPwd(account, info.getOldPwd())) {
                // 如果该账户输入正确且是一个被关注的账户，则解除该账户的关注，释放空间
                if (!ConfigureReader.instance().getVCLevel().equals(VCLevel.Close)) {
                    synchronized (focusAccount) {
                        focusAccount.remove(account);
                    }
                }
                String newPassword = info.getNewPwd();
                // 新密码合法性检查
                if (newPassword != null && newPassword.length() >= 3 && newPassword.length() <= 32
                        && ios8859_1Encoder.canEncode(newPassword)) {
                    if (ConfigureReader.instance().changePassword(account, newPassword)) {
                        lu.writeChangePasswordEvent(request, account, newPassword);
                        return "success";
                    }
                }
                return "invalidnewpwd";
            } else {
                // 如果账户密码不匹配，则将该账户加入到关注账户集合，避免对方进一步破解
                synchronized (focusAccount) {
                    if (!ConfigureReader.instance().getVCLevel().equals(VCLevel.Close)) {
                        focusAccount.add(account);
                    }
                }
                return "oldpwderror";
            }
        } catch (Exception e) {
            lu.writeException(e);
            return "cannotchangepwd";
        }
    }

    @Override
    public String isAllowSignUp() {
        return ConfigureReader.instance().isAllowSignUp() ? "true" : "false";
    }

    @Override
    public String doSignUp(HttpServletRequest request) {
        // 验证是否开启了注册功能
        if (!ConfigureReader.instance().isAllowSignUp()) {
            return "illegal";
        }
        HttpSession session = request.getSession();
        // 如果已经登入一个账户了，必须先注销
        if (session.getAttribute("ACCOUNT") != null) {
            return "mustlogout";
        }
        // 如果开启了验证码则必须输入
        String reqVerCode = request.getParameter("vercode");
        if (!ConfigureReader.instance().getVCLevel().equals(VCLevel.Close)) {
            String trueVerCode = (String) session.getAttribute("VERCODE");
            session.removeAttribute("VERCODE");// 确保一个验证码只会生效一次，无论对错
            if (reqVerCode == null || trueVerCode == null || !trueVerCode.equals(reqVerCode.toLowerCase())) {
                return "needvercode";
            }
        }
        // 解析注册请求
        final String encrypted = request.getParameter("encrypted");
        try {
            final String signUpInfoStr = RSACipherUtil.decryption(encrypted, ku.getPrivateKey());
            final SignUpInfoPojo info = gson.fromJson(signUpInfoStr.replaceAll("\\\\", "\\\\\\\\"),
                    SignUpInfoPojo.class);
            if (System.currentTimeMillis() - Long.parseLong(info.getTime()) > TIME_OUT) {
                return "error";
            }
            if (ConfigureReader.instance().foundAccount(info.getAccount())) {
                return "accountexists";
            }
            String account = info.getAccount();
            String password = info.getPwd();
            // ls

            if (account != null && account.length() >= 3 && account.length() <= 32
                    && ios8859_1Encoder.canEncode(account)) {
                if (account.indexOf("=") < 0 && account.indexOf(":") < 0 && account.indexOf("#") != 0) {
                    if (password != null && password.length() >= 3 && password.length() <= 32
                            && ios8859_1Encoder.canEncode(password)) {
                        if (ConfigureReader.instance().createNewAccount(account, password)) {
                            lu.writeSignUpEvent(request, account, password);
                            session.setAttribute("ACCOUNT", account);
                            return "success";
                        } else {
                            return "cannotsignup";
                        }
                    } else {
                        return "invalidpwd";
                    }
                } else {
                    return "illegalaccount";
                }
            } else {
                return "invalidaccount";
            }
        } catch (Exception e) {
            lu.writeException(e);
            return "cannotsignup";
        }
    }
}
