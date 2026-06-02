package com.lexiflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexiflow.auth.dto.LoginCaptchaResponse;
import com.lexiflow.common.error.ErrorCode;
import com.lexiflow.common.exception.BizException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class LoginCaptchaServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void issueShouldStoreCaptchaAndReturnImageDataUrl() {
        LoginCaptchaService service = new LoginCaptchaService(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(LoginCaptchaService.ISSUE_INTERVAL))).thenReturn(true);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);

        LoginCaptchaResponse response = service.issue("127.0.0.1");

        assertThat(response.captchaId()).isNotBlank();
        assertThat(response.imageDataUrl()).startsWith("data:image/png;base64,");
        assertThat(response.expiresInSeconds()).isEqualTo(LoginCaptchaService.CAPTCHA_TTL.toSeconds());
        byte[] imageBytes = Base64.getDecoder().decode(response.imageDataUrl().substring("data:image/png;base64,".length()));
        assertThat(imageBytes).startsWith(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        verify(valueOperations).set(anyString(), codeCaptor.capture(), eq(LoginCaptchaService.CAPTCHA_TTL));
        assertThat(codeCaptor.getValue()).matches("\\d{4}");
    }

    @Test
    void issueShouldLimitFrequentRequests() {
        LoginCaptchaService service = new LoginCaptchaService(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(LoginCaptchaService.ISSUE_INTERVAL))).thenReturn(false);

        assertThatThrownBy(() -> service.issue("127.0.0.1"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    void assertValidShouldPassAndDeleteCaptcha() {
        LoginCaptchaService service = new LoginCaptchaService(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("1234");

        assertThatCode(() -> service.assertValid("captcha-id", "1234"))
                .doesNotThrowAnyException();

        verify(stringRedisTemplate).delete(anyString());
    }

    @Test
    void assertValidShouldRejectMissingCaptcha() {
        LoginCaptchaService service = new LoginCaptchaService(stringRedisTemplate);

        assertThatThrownBy(() -> service.assertValid(null, null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_CAPTCHA_REQUIRED);
    }

    @Test
    void assertValidShouldRejectWrongCaptcha() {
        LoginCaptchaService service = new LoginCaptchaService(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("1234");

        assertThatThrownBy(() -> service.assertValid("captcha-id", "0000"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_CAPTCHA_INVALID);
    }
}
