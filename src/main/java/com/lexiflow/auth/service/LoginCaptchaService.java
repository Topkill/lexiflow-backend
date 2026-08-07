package com.lexiflow.auth.service;

import com.lexiflow.auth.dto.LoginCaptchaResponse;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import com.lexiflow.infra.redis.RedisKeys;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 登录图形验证码服务。
 *
 * <p>基于 Redis 存储验证码答案，支持图形生成和校验。
 * 验证码有效期 5 分钟，同一 IP 每秒最多获取一次。
 * 图形包含随机噪点和字符旋转，增加机器识别难度。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginCaptchaService {

    /** 验证码有效期：5 分钟 */
    static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    /** 验证码获取最小间隔：1 秒 */
    static final Duration ISSUE_INTERVAL = Duration.ofSeconds(1);

    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 生成图形验证码。
     *
     * @param clientIp 客户端 IP，用于限流
     * @return 验证码响应，包含验证码 ID、图片 Data URL 和有效期
     */
    public LoginCaptchaResponse issue(String clientIp) {
        assertIssueAllowed(clientIp);
        String captchaId = UUID.randomUUID().toString();
        String code = "%04d".formatted(secureRandom.nextInt(10000));
        try {
            stringRedisTemplate.opsForValue().set(RedisKeys.authLoginCaptchaKey(captchaId), code, CAPTCHA_TTL);
        } catch (RuntimeException ex) {
            log.warn("Redis login captcha store failed, captchaId={}", captchaId, ex);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "验证码生成失败，请稍后重试");
        }
        return new LoginCaptchaResponse(captchaId, buildImageDataUrl(code), CAPTCHA_TTL.toSeconds());
    }

    /**
     * 校验验证码答案，校验后立即删除（一次性使用）。
     *
     * @param captchaId   验证码 ID
     * @param captchaCode 用户输入的验证码
     */
    public void assertValid(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BizException(ErrorCode.LOGIN_CAPTCHA_REQUIRED);
        }
        String key = RedisKeys.authLoginCaptchaKey(captchaId);
        String expected;
        try {
            expected = stringRedisTemplate.opsForValue().get(key);
            stringRedisTemplate.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Redis login captcha verify failed, captchaId={}", captchaId, ex);
            throw new BizException(ErrorCode.LOGIN_CAPTCHA_INVALID);
        }
        if (!StringUtils.hasText(expected) || !expected.equals(captchaCode.trim())) {
            throw new BizException(ErrorCode.LOGIN_CAPTCHA_INVALID);
        }
    }

    private void assertIssueAllowed(String clientIp) {
        String key = RedisKeys.authLoginCaptchaRateKey(normalizeClientIp(clientIp));
        try {
            Boolean allowed = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", ISSUE_INTERVAL);
            if (Boolean.FALSE.equals(allowed)) {
                throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "验证码获取过于频繁，请稍后再试");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Redis login captcha rate check failed, clientIp={}", normalizeClientIp(clientIp), ex);
        }
    }

    private String buildImageDataUrl(String code) {
        BufferedImage image = new BufferedImage(120, 44, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(244, 251, 245));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            drawNoise(graphics, image.getWidth(), image.getHeight());
            drawCode(graphics, code);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            String base64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            return "data:image/png;base64," + base64;
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "验证码生成失败，请稍后重试");
        }
    }

    private void drawNoise(Graphics2D graphics, int width, int height) {
        Color[] colors = {
                new Color(145, 228, 222),
                new Color(240, 182, 109),
                new Color(27, 188, 177),
                new Color(95, 116, 111)
        };
        for (int i = 0; i < 5; i++) {
            graphics.setColor(colors[secureRandom.nextInt(colors.length)]);
            int x1 = secureRandom.nextInt(width);
            int y1 = secureRandom.nextInt(height);
            int x2 = secureRandom.nextInt(width);
            int y2 = secureRandom.nextInt(height);
            graphics.drawLine(x1, y1, x2, y2);
        }
        for (int i = 0; i < 24; i++) {
            graphics.setColor(colors[secureRandom.nextInt(colors.length)]);
            graphics.fillOval(secureRandom.nextInt(width), secureRandom.nextInt(height), 2, 2);
        }
    }

    private void drawCode(Graphics2D graphics, String code) {
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 26));
        for (int i = 0; i < code.length(); i++) {
            Graphics2D digitGraphics = (Graphics2D) graphics.create();
            try {
                int x = 18 + i * 23;
                int y = 30 + secureRandom.nextInt(5) - 2;
                double angle = Math.toRadians(secureRandom.nextInt(31) - 15);
                digitGraphics.rotate(angle, x + 8, y - 10);
                digitGraphics.setColor(new Color(23, 43, 39));
                digitGraphics.drawString(String.valueOf(code.charAt(i)), x, y);
            } finally {
                digitGraphics.dispose();
            }
        }
    }

    private String normalizeClientIp(String clientIp) {
        return StringUtils.hasText(clientIp) ? clientIp.trim().toLowerCase(Locale.ROOT) : "unknown";
    }
}
