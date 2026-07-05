package com.se_lab.project.controller;

import com.se_lab.project.entity.User;
import com.se_lab.project.global.JwtUtil;
import com.se_lab.project.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*")  // ⭐️ 이거 딱 한 줄 추가!! (모든 프론트엔드 접근 허용)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // 의존성 주입 (DB와 암호화 도구를 가져옵니다)
    public AuthController(JwtUtil jwtUtil, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 🚀 1. 진짜 회원가입 API
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> signupData) {
        String email = signupData.get("email");
        String rawPassword = signupData.get("password");
        String name = signupData.get("name");

        // ✨ 비밀번호 암호화 (1234 -> $2a$10$ 복잡한 문자열)
        String encodedPassword = passwordEncoder.encode(rawPassword);

        User newUser = new User(email, encodedPassword, name);
        userRepository.save(newUser);

        return ResponseEntity.ok(name + "님 회원가입이 완료되었습니다!");
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
            response.put("name", user.getName());
            response.put("message", "로그인 성공");

            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(401).body("이메일 또는 비밀번호가 틀렸습니다.");
        }
    }
}