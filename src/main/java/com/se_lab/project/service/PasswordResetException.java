package com.se_lab.project.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/// 비밀번호 재설정 과정에서 사용자에게 그대로 보여줄 수 있는 실패.
///
/// 코드가 틀렸는지 / 만료됐는지 / 그런 요청 자체가 없었는지를 구분해서 알려주면
/// 남의 계정을 상대로 찔러볼 때 정보가 된다. 확인 단계의 실패 메시지는
/// 일부러 한 가지로 통일한다 ([PasswordResetService.INVALID_CODE_MESSAGE]).
@Getter
public class PasswordResetException extends RuntimeException {

    private final HttpStatus status;

    public PasswordResetException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
