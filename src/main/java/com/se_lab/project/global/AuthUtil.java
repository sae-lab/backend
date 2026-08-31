package com.se_lab.project.global;

import org.springframework.security.core.context.SecurityContextHolder;

// 컨트롤러마다 반복되던 "SecurityContext에서 로그인 이메일 꺼내기" 보일러플레이트를 한 곳에 모은다.
public final class AuthUtil {

    private AuthUtil() {
    }

    public static String currentUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    // 로그인 상태가 아니면(anonymousUser 등) null.
    public static String requireLoggedIn() {
        String email = currentUserEmail();
        if (email == null || email.isBlank() || "anonymousUser".equals(email)) return null;
        return email;
    }
}
