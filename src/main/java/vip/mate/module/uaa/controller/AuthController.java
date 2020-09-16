package vip.mate.module.uaa.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xkcoding.justauth.AuthRequestFactory;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.utils.AuthStateUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.provider.token.ConsumerTokenServices;
import org.springframework.web.bind.annotation.*;
import vip.mate.core.common.api.Result;
import vip.mate.core.common.entity.LoginUser;
import vip.mate.core.common.util.SecurityUtil;
import vip.mate.core.common.util.StringUtil;
import vip.mate.module.system.dto.UserInfo;
import vip.mate.module.system.entity.SysUser;
import vip.mate.module.system.service.ISysRolePermissionService;
import vip.mate.module.system.service.ISysUserService;
import vip.mate.module.uaa.config.SocialConfig;
import vip.mate.module.uaa.service.ValidateService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 认证控制类
 *
 * @author pangu
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/mate-uaa/auth")
@Api(tags = "认证管理")
public class AuthController {

    @Qualifier("consumerTokenServices")
    private final ConsumerTokenServices consumerTokenServices;

    private final ValidateService validateService;

    private final ISysUserService sysUserService;

    private final ISysRolePermissionService sysRolePermissionService;

    private final AuthRequestFactory factory;

    private final SocialConfig socialConfig;

    @GetMapping("/get/user")
    @ApiOperation(value = "用户信息", notes = "用户信息")
    public Result<?> getUser(HttpServletRequest request) {

        LoginUser loginUser = SecurityUtil.getUsername(request);
        UserInfo userInfo = null;
        /**
         * 根据type来判断调用哪个接口登录，待扩展社交登录模式
         * type 1:用户名和密码登录　2：手机号码登录
         */
        if (loginUser.getType() == 2) {
            SysUser sysUser = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getTelephone, loginUser.getAccount()));
            userInfo = sysUserService.getUserInfo(sysUser);
        } else {
            SysUser sysUser = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getAccount, loginUser.getAccount()));
            userInfo = sysUserService.getUserInfo(sysUser);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("userName", loginUser.getAccount());
        data.put("avatar", userInfo.getSysUser().getAvatar());
        data.put("roleId", userInfo.getSysUser().getRoleId());
        data.put("departId", userInfo.getSysUser().getDepartId());
        data.put("tenantId", userInfo.getSysUser().getTenantId());
        List<String> stringList = sysRolePermissionService.getMenuIdByRoleId(String.valueOf(userInfo.getSysUser().getRoleId()));
        data.put("permissions", stringList);
        return Result.data(data);
    }

    @GetMapping("/code")
    @ApiOperation(value = "验证码获取", notes = "验证码获取")
    public Result<?> authCode() {
        return validateService.getCode();
    }

    @PostMapping("/logout")
    @ApiOperation(value = "退出登录", notes = "退出登录")
    public Result<?> logout(HttpServletRequest request) {
        if (StringUtil.isNotBlank(SecurityUtil.getHeaderToken(request))) {
            consumerTokenServices.revokeToken(SecurityUtil.getToken(request));
        }
        return Result.success("操作成功");
    }

    /**
     * 验证码下发
     * @param mobile 手机号码
     * @return Result
     */
    @ApiOperation(value = "手机验证码下发", notes = "手机验证码下发")
    @GetMapping("/sms-code")
    public Result<?> smsCode(String mobile) {
        return validateService.getSmsCode(mobile);
    }


    /**
     * 登录类型
     */
    @GetMapping("/list")
    @ApiOperation(value = "登录类型", notes = "登录类型")
    public Map<String, String> loginType() {
        List<String> oauthList = factory.oauthList();
        return oauthList.stream().collect(Collectors.toMap(oauth -> oauth.toLowerCase() + "登录", oauth -> "http://localhost:10001/mate-uaa/auth/login/" + oauth.toLowerCase()));
    }

    /**
     * 登录
     *
     * @param oauthType 第三方登录类型
     * @param response  response
     * @throws IOException
     */
    @ApiOperation(value = "第三方登录", notes = "第三方登录")
    @PostMapping("/login/{oauthType}")
    public void login(@PathVariable String oauthType, HttpServletResponse response) throws IOException {
        AuthRequest authRequest = factory.get(oauthType);
        response.sendRedirect(authRequest.authorize(oauthType + "::" + AuthStateUtils.createState()));
    }

    /**
     * 登录成功后的回调
     *
     * @param oauthType 第三方登录类型
     * @param callback  携带返回的信息
     * @return 登录成功后的信息
     */
    @ApiOperation(value = "第三方登录回调", notes = "第三方登录回调")
    @GetMapping("/callback/{oauthType}")
    public void callback(@PathVariable String oauthType, AuthCallback callback, HttpServletResponse httpServletResponse) throws IOException {
        String url = socialConfig.getUrl() + "?code=" + oauthType + "-" + callback.getCode() + "&state=" + callback.getState();
        log.debug("url:{}", url);
        //跳转到指定页面
        httpServletResponse.sendRedirect(url);
    }
}
