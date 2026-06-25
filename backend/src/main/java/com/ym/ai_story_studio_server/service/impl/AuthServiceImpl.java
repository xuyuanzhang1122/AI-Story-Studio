package com.ym.ai_story_studio_server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ym.ai_story_studio_server.common.ResultCode;
import com.ym.ai_story_studio_server.dto.auth.PhoneLoginVO;
import com.ym.ai_story_studio_server.entity.User;
import com.ym.ai_story_studio_server.entity.UserAuth;
import com.ym.ai_story_studio_server.entity.Wallet;
import com.ym.ai_story_studio_server.exception.BusinessException;
import com.ym.ai_story_studio_server.mapper.UserAuthMapper;
import com.ym.ai_story_studio_server.mapper.UserMapper;
import com.ym.ai_story_studio_server.mapper.WalletMapper;
import com.ym.ai_story_studio_server.service.AuthService;
import com.ym.ai_story_studio_server.service.SmsService;
import com.ym.ai_story_studio_server.util.JwtUtil;
import com.ym.ai_story_studio_server.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 认证服务实现类
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SmsService smsService;
    private final RedisUtil redisUtil;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final UserAuthMapper userAuthMapper;
    private final WalletMapper walletMapper;

    /**
     * 验证码有效期（秒）
     */
    private static final long CODE_EXPIRE_SECONDS = 300;

    /**
     * 防刷锁定时长（秒）
     */
    private static final long LOCK_SECONDS = 60;

    /**
     * 初始赠送积分
     */
    private static final int INITIAL_BALANCE = 20;

    @Override
    public void sendVerificationCode(String phone) {
        // 非商业自部署版本：跳过实际 SMS 调用，前端可输入任意 6 位数字直接登录
        log.info("[DEV] 跳过验证码发送: phone={}", maskPhone(phone));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PhoneLoginVO phoneLogin(String phone, String code, String inviteCode) {
        // 非商业自部署版本：跳过验证码校验，任意 code 均放行
        log.info("[DEV] 跳过验证码校验直接登录: phone={}", maskPhone(phone));

        // 3. 查询用户
        UserAuth userAuth = userAuthMapper.selectOne(
                new LambdaQueryWrapper<UserAuth>()
                        .eq(UserAuth::getAuthType, "PHONE")
                        .eq(UserAuth::getIdentifier, phone)
        );

        User user;
        Wallet wallet;

        if (userAuth == null) {
            // 首次登录：自动注册
            log.info("首次登录，开始注册用户: phone={}, inviteCode={}", maskPhone(phone), inviteCode);
            user = createNewUser(phone, inviteCode);
            wallet = walletMapper.selectById(user.getId());
        } else {
            // 已注册用户
            user = userMapper.selectById(userAuth.getUserId());
            if (user == null) {
                log.error("用户认证记录存在但用户不存在: userId={}", userAuth.getUserId());
                throw new BusinessException(ResultCode.USER_NOT_FOUND);
            }
            wallet = walletMapper.selectById(user.getId());
            log.info("用户登录成功: userId={}, phone={}", user.getId(), maskPhone(phone));
        }

        // 4. 生成JWT Token
        String token = jwtUtil.generateToken(user.getId());

        // 5. 返回登录信息
        return new PhoneLoginVO(
                token,
                user.getId(),
                user.getNickname(),
                user.getAvatarUrl(),
                wallet != null ? wallet.getBalance() : 0
        );
    }

    @Override
    public PhoneLoginVO wechatLogin(String code) {
        // TODO: V2版本实现微信登录
        // 实现步骤：
        // 1. 使用微信授权码换取access_token和openid
        //    - 调用微信API: https://api.weixin.qq.com/sns/oauth2/access_token
        //    - 参数: appid, secret, code, grant_type=authorization_code
        //
        // 2. 使用access_token获取用户信息
        //    - 调用微信API: https://api.weixin.qq.com/sns/userinfo
        //    - 获取: openid, nickname, headimgurl
        //
        // 3. 根据openid查询user_auth表
        //    - auth_type='WECHAT', identifier=openid
        //
        // 4. 如果不存在则自动注册
        //    - 创建users记录（nickname使用微信昵称）
        //    - 创建user_auth记录（auth_type='WECHAT', identifier=openid）
        //    - 创建wallets记录（初始积分20）
        //
        // 5. 生成JWT Token并返回

        log.warn("微信登录功能尚未实现，请使用手机号登录");
        throw new BusinessException(ResultCode.SYSTEM_ERROR, "微信登录功能开发中，敬请期待");
    }

    /**
     * 创建新用户（首次登录自动注册）
     *
     * @param phone 手机号
     * @param inviteCode 邀请码（可选）
     * @return 新创建的用户
     */
    private User createNewUser(String phone, String inviteCode) {
        // 1. 创建用户记录
        User user = new User();
        user.setNickname(generateNickname());
        user.setStatus(1); // 正常状态
        userMapper.insert(user);
        log.info("创建用户成功: userId={}, nickname={}", user.getId(), user.getNickname());

        // 2. 创建认证记录
        UserAuth userAuth = new UserAuth();
        userAuth.setUserId(user.getId());
        userAuth.setAuthType("PHONE");
        userAuth.setIdentifier(phone);
        userAuth.setVerified(1); // 已验证
        userAuthMapper.insert(userAuth);
        log.info("创建认证记录成功: userId={}, authType=PHONE", user.getId());

        // 3. 创建钱包（赠送初始积分）
        Wallet wallet = new Wallet();
        wallet.setUserId(user.getId());
        wallet.setBalance(INITIAL_BALANCE);
        walletMapper.insert(wallet);
        log.info("创建钱包成功: userId={}, 初始积分={}", user.getId(), INITIAL_BALANCE);

        // 4. TODO: 处理邀请码逻辑（如果有）
        if (inviteCode != null && !inviteCode.isBlank()) {
            log.info("用户使用邀请码注册: userId={}, inviteCode={}", user.getId(), inviteCode);
            // TODO: 验证邀请码并处理奖励逻辑
        }

        return user;
    }

    /**
     * 生成默认昵称
     * <p>格式: 用户{5位随机数}
     *
     * @return 昵称
     */
    private String generateNickname() {
        int randomNum = ThreadLocalRandom.current().nextInt(10000, 100000);
        return "用户" + randomNum;
    }

    /**
     * 手机号脱敏
     * <p>格式: 138****8000
     *
     * @param phone 手机号
     * @return 脱敏后的手机号
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
