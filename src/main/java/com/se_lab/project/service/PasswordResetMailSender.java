package com.se_lab.project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/// 비밀번호 재설정 코드를 가입 시 등록한 이메일로 보낸다.
///
/// SMTP 설정(MAIL_HOST 등)이 없으면 스프링이 JavaMailSender 빈을 아예 만들지 않는다.
/// 그래서 ObjectProvider로 받아, 설정이 없어도 앱이 뜨는 데는 지장이 없게 한다.
/// 배포된 서버에 아직 SMTP 값이 없는 상태에서 이 기능만 올려도 부팅이 깨지지 않아야 한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetMailSender {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.password-reset.mail-from:}")
    private String configuredFrom;

    /// 메일을 보낼 수 있는 상태인지. 사용자 조회보다 **먼저** 확인해야 한다.
    /// 나중에 확인하면 가입된 이메일일 때만 503이 나가서, 응답 차이로
    /// 가입 여부가 드러난다.
    public boolean isConfigured() {
        return !mailHost.isBlank() && mailSenderProvider.getIfAvailable() != null;
    }

    public void sendCode(String toEmail, String code, int ttlMinutes) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || mailHost.isBlank()) {
            throw new PasswordResetException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "메일 발송이 아직 설정되지 않았습니다. 잠시 후 다시 시도해주세요.");
        }

        String from = configuredFrom.isBlank() ? mailUsername : configuredFrom;

        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isBlank()) message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("[강원의 길] 비밀번호 재설정 인증코드");
        message.setText(
                "비밀번호 재설정 인증코드입니다.\n\n"
                        + "    " + code + "\n\n"
                        + "앱의 비밀번호 재설정 화면에 이 코드를 입력해주세요.\n"
                        + "코드는 " + ttlMinutes + "분 뒤에 만료됩니다.\n\n"
                        + "본인이 요청한 것이 아니라면 이 메일은 무시하셔도 됩니다.\n"
                        + "비밀번호는 그대로 유지됩니다.");

        try {
            sender.send(message);
        } catch (MailException e) {
            // 코드는 절대 로그에 남기지 않는다. 로그를 보는 사람이 계정을 가져갈 수 있게 된다.
            log.error("비밀번호 재설정 메일 발송 실패: {}", e.getMessage());
            throw new PasswordResetException(
                    HttpStatus.BAD_GATEWAY,
                    "메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }
    }
}
