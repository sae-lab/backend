package com.se_lab.project.controller;

import com.se_lab.project.entity.User;
import com.se_lab.project.global.JwtUtil;
import com.se_lab.project.repository.UserRepository;
import com.se_lab.project.service.PasswordResetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetService passwordResetService;

    // 의존성 주입 (DB와 암호화 도구를 가져옵니다)
    public AuthController(JwtUtil jwtUtil, UserRepository userRepository, PasswordEncoder passwordEncoder,
                          PasswordResetService passwordResetService) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetService = passwordResetService;
    }

    // 🚀 1. 진짜 회원가입 API
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> signupData) {
        String email = signupData.get("email");
        String rawPassword = signupData.get("password");
        String name = signupData.get("name");

        if (email == null || email.isBlank() || rawPassword == null || rawPassword.isBlank()
                || name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "이메일, 비밀번호, 이름을 모두 입력해주세요."));
        }
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(409).body(Map.of("message", "이미 가입된 이메일입니다."));
        }

        // ✨ 비밀번호 암호화 (1234 -> $2a$10$ 복잡한 문자열)
        String encodedPassword = passwordEncoder.encode(rawPassword);

        User newUser = new User(email, encodedPassword, name);
        userRepository.save(newUser);

        return ResponseEntity.ok(Map.of("message", name + "님 회원가입이 완료되었습니다!"));
    }

    // 🚀 2. 진짜 로그인 API
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginData) {
        String email = loginData.get("email");
        String rawPassword = loginData.get("password");

        // 1. DB에서 이메일로 유저 찾기 (아직 이 메서드가 없으면 붉은줄이 뜹니다. 아래 UserRepository 설정 참고!)
        User user = userRepository.findByEmail(email).orElse(null);

        // 2. 유저가 존재하고, 비밀번호가 일치하는지 확인 (DB의 암호화된 비밀번호와 비교)
        if (user != null && passwordEncoder.matches(rawPassword, user.getPassword())) {

            // 로그인 성공 시 JWT 토큰 발급
            String token = jwtUtil.generateToken(user.getEmail());

            Map<String, String> response = new HashMap<>();
            response.put("token", token);
            // 앱은 이 값을 저장해두고 프로필을 불러오기 전까지 화면에 그대로 쓴다.
            // 실명(getName)을 내려주면 프로필 로딩 전까지 실명이 노출됐다가 익명 닉네임으로
            // 바뀌는 깜빡임이 생기므로, 처음부터 공개용 표시 이름만 내려준다.
            response.put("name", user.getDisplayName());
            response.put("message", "로그인 성공");

            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(401).body(Map.of("message", "이메일 또는 비밀번호가 틀렸습니다."));
        }
    }

    // 🚀 3. 비밀번호 재설정 - 코드 발송
    // 가입 여부와 무관하게 항상 200을 준다. 응답이 갈리면 어떤 이메일이 가입돼
    // 있는지 알아내는 통로가 된다.
    @PostMapping("/password-reset/request")
    public ResponseEntity<?> requestPasswordReset(@RequestBody Map<String, String> body) {
        passwordResetService.requestCode(body.get("email"));
        return ResponseEntity.ok(Map.of(
                "message", "인증코드를 메일로 보냈습니다. 받은편지함을 확인해주세요."));
    }

    // 🚀 4. 비밀번호 재설정 - 코드 확인 후 새 비밀번호 설정
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<?> confirmPasswordReset(@RequestBody Map<String, String> body) {
        passwordResetService.confirmReset(
                body.get("email"), body.get("code"), body.get("newPassword"));
        return ResponseEntity.ok(Map.of("message", "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요."));
    }
}
