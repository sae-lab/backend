package com.se_lab.project.service;

import com.se_lab.project.entity.PasswordResetCode;
import com.se_lab.project.entity.User;
import com.se_lab.project.repository.PasswordResetCodeRepository;
import com.se_lab.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/// 비밀번호 재설정. 가입할 때 등록한 이메일로 6자리 코드를 보내고,
/// 그 코드로 새 비밀번호를 설정하게 한다.
///
/// 비밀번호는 BCrypt로 저장되어 원본을 되돌릴 수 없으므로 "찾아주기"는 불가능하고
/// 재설정만 가능하다.
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    public static final int CODE_TTL_MINUTES = 10;

    /// 확인 실패는 원인을 구분해서 알려주지 않는다. "코드가 틀렸다"와 "그런 요청이 없다"를
    /// 나눠서 응답하면, 남의 이메일로 찔러봤을 때 가입 여부를 알아낼 수 있다.
    private static final String INVALID_CODE_MESSAGE = "인증코드가 올바르지 않거나 만료되었습니다.";

    private static final int MAX_SENDS_PER_HOUR = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailSender mailSender;

    private final SecureRandom random = new SecureRandom();

    /// 코드 발송 요청.
    ///
    /// 가입되지 않은 이메일이어도 성공으로 응답한다. 응답이 갈리면 어떤 이메일이
    /// 가입돼 있는지 확인하는 통로가 되기 때문이다. 호출하는 쪽은 언제나 200을 준다.
    @Transactional
    public void requestCode(String email) {
        if (email == null || email.isBlank()) {
            throw new PasswordResetException(HttpStatus.BAD_REQUEST, "이메일을 입력해주세요.");
        }

        // 사용자 조회보다 먼저 확인한다. 순서를 바꾸면 가입된 이메일일 때만 503이 나가서
        // 응답 차이로 가입 여부가 드러난다.
        if (!mailSender.isConfigured()) {
            throw new PasswordResetException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "메일 발송이 아직 설정되지 않았습니다. 잠시 후 다시 시도해주세요.");
        }

        User user = userRepository.findByEmail(email.trim()).orElse(null);
        if (user == null) {
            log.info("가입되지 않은 이메일로 비밀번호 재설정 요청이 들어왔다. 조용히 무시한다.");
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // 도배 방지. 한도를 넘겨도 오류를 내지 않고 조용히 넘긴다.
        // 429를 돌려주면 그 자체가 "이 이메일은 가입돼 있다"는 신호가 된다.
        if (codeRepository.countByUserAndCreatedAtAfter(user, now.minusHours(1)) >= MAX_SENDS_PER_HOUR) {
            log.warn("비밀번호 재설정 코드 발송 한도를 넘겼다. userId={}", user.getId());
            return;
        }

        // 이전에 보낸 코드는 모두 무효로 만든다. 살아 있는 코드가 여러 개면
        // 그만큼 맞혀볼 기회가 늘어난다.
        codeRepository.invalidateAllUnused(user, now);

        String code = generateCode();
        codeRepository.save(PasswordResetCode.builder()
                .user(user)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(now.plusMinutes(CODE_TTL_MINUTES))
                .createdAt(now)
                .build());

        mailSender.sendCode(user.getEmail(), code, CODE_TTL_MINUTES);
    }

    /// 코드 확인 후 새 비밀번호 설정.
    ///
    /// 코드를 틀렸을 때 시도 횟수를 남겨야 하므로 롤백하지 않는다.
    /// 롤백되면 몇 번을 틀려도 카운터가 0으로 돌아가 무한정 찍어볼 수 있다.
    @Transactional(noRollbackFor = PasswordResetException.class)
    public void confirmReset(String email, String code, String newPassword) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            throw new PasswordResetException(HttpStatus.BAD_REQUEST, "이메일과 인증코드를 입력해주세요.");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new PasswordResetException(
                    HttpStatus.BAD_REQUEST,
                    "비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
        }

        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> invalidCode());

        PasswordResetCode saved = codeRepository
                .findTopByUserAndUsedAtIsNullOrderByCreatedAtDesc(user)
                .orElseThrow(() -> invalidCode());

        LocalDateTime now = LocalDateTime.now();
        if (saved.isExpired(now)) {
            throw invalidCode();
        }
        if (saved.getAttemptCount() >= MAX_ATTEMPTS) {
            // 이 코드는 태워버리고 다시 요청하게 한다.
            saved.markUsed(now);
            throw new PasswordResetException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "인증코드를 너무 여러 번 틀렸습니다. 코드를 다시 요청해주세요.");
        }
        if (!passwordEncoder.matches(code.trim(), saved.getCodeHash())) {
            saved.recordFailedAttempt();
            throw invalidCode();
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        saved.markUsed(now);
        log.info("비밀번호 재설정 완료. userId={}", user.getId());
    }

    private PasswordResetException invalidCode() {
        return new PasswordResetException(HttpStatus.BAD_REQUEST, INVALID_CODE_MESSAGE);
    }

    /// 100000~999999 범위의 6자리 코드. 앞자리가 0이면 사용자가 빠뜨리기 쉬워 제외한다.
    private String generateCode() {
        return String.valueOf(100_000 + random.nextInt(900_000));
    }
}
