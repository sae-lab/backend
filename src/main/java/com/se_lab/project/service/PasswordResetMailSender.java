package com.se_lab.project.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/// 비밀번호 재설정 코드를 가입 시 등록한 이메일로 보낸다.
///
/// 발송 경로가 두 가지다 (`MAIL_PROVIDER`로 고른다).
///
/// - `smtp`  : 평범한 SMTP. 로컬 개발에서 쓴다.
/// - `brevo` : HTTPS(443) API. Render 같은 호스팅은 스팸 방지로 SMTP 아웃바운드
///             포트(25/587/465)를 막아둬서 SMTP로는 연결 자체가 안 된다.
///             443은 막히지 않으므로 배포 환경에서는 이쪽을 쓴다.
///
/// 어느 쪽이든 설정이 없으면 발송 기능만 꺼지고 앱은 정상 부팅해야 한다.
/// 배포된 서버에 아직 값이 없는 상태로 이 코드가 올라가도 부팅이 깨지면 안 된다.
@Slf4j
@Component
public class PasswordResetMailSender {

    private static final String BREVO_SEND_URL = "https://api.brevo.com/v3/smtp/email";
    private static final String SENDER_NAME = "강원의 길";

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final RestTemplate restTemplate;

    /// smtp | brevo
    @Value("${app.password-reset.provider:smtp}")
    private String provider;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.password-reset.mail-from:}")
    private String configuredFrom;

    @Value("${app.password-reset.brevo-api-key:}")
    private String brevoApiKey;

    public PasswordResetMailSender(ObjectProvider<JavaMailSender> mailSenderProvider, RestTemplate restTemplate) {
        this.mailSenderProvider = mailSenderProvider;
        this.restTemplate = restTemplate;
    }

    private boolean useBrevo() {
        return "brevo".equalsIgnoreCase(provider);
    }

    /// 보내는 사람 주소. Brevo에서는 이 주소가 발신자로 인증돼 있어야 한다.
    private String senderAddress() {
        return configuredFrom.isBlank() ? mailUsername : configuredFrom;
    }

    /// 메일을 보낼 수 있는 상태인지. 사용자 조회보다 **먼저** 확인해야 한다.
    /// 나중에 확인하면 가입된 이메일일 때만 503이 나가서, 응답 차이로
    /// 가입 여부가 드러난다.
    public boolean isConfigured() {
        if (useBrevo()) {
            return !brevoApiKey.isBlank() && !senderAddress().isBlank();
        }
        return !mailHost.isBlank() && mailSenderProvider.getIfAvailable() != null;
    }

    public void sendCode(String toEmail, String code, int ttlMinutes) {
        String subject = "[강원의 길] 비밀번호 재설정 인증코드";
        String body = buildBody(code, ttlMinutes);

        if (useBrevo()) {
            sendViaBrevo(toEmail, subject, body);
        } else {
            sendViaSmtp(toEmail, subject, body);
        }
    }

    private String buildBody(String code, int ttlMinutes) {
        return "비밀번호 재설정 인증코드입니다.\n\n"
                + "    " + code + "\n\n"
                + "앱의 비밀번호 재설정 화면에 이 코드를 입력해주세요.\n"
                + "코드는 " + ttlMinutes + "분 뒤에 만료됩니다.\n\n"
                + "본인이 요청한 것이 아니라면 이 메일은 무시하셔도 됩니다.\n"
                + "비밀번호는 그대로 유지됩니다.";
    }

    // ── HTTPS API (Brevo) ────────────────────────────────────────────────
    private void sendViaBrevo(String toEmail, String subject, String body) {
        if (brevoApiKey.isBlank() || senderAddress().isBlank()) {
            throw notConfigured();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("api-key", brevoApiKey);

        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", SENDER_NAME, "email", senderAddress()),
                "to", List.of(Map.of("email", toEmail)),
                "subject", subject,
                "textContent", body);

        try {
            restTemplate.postForEntity(BREVO_SEND_URL, new HttpEntity<>(payload, headers), String.class);
        } catch (RestClientException e) {
            // 코드는 절대 로그에 남기지 않는다. 로그를 보는 사람이 계정을 가져갈 수 있게 된다.
            // 예외 메시지에는 요청 본문이 들어가지 않으므로 그대로 남겨도 안전하다.
            log.error("비밀번호 재설정 메일 발송 실패 (brevo): {}", e.getMessage());
            throw sendFailed();
        }
    }

    // ── SMTP ─────────────────────────────────────────────────────────────
    private void sendViaSmtp(String toEmail, String subject, String body) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || mailHost.isBlank()) {
            throw notConfigured();
        }

        String from = senderAddress();

        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isBlank()) message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);

        try {
            sender.send(message);
        } catch (MailException e) {
            log.error("비밀번호 재설정 메일 발송 실패 (smtp): {}", e.getMessage());
            throw sendFailed();
        }
    }

    private PasswordResetException notConfigured() {
        return new PasswordResetException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "메일 발송이 아직 설정되지 않았습니다. 잠시 후 다시 시도해주세요.");
    }

    private PasswordResetException sendFailed() {
        return new PasswordResetException(
                HttpStatus.BAD_GATEWAY,
                "메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
    }
}
