package com.se_lab.project.controller;

import com.se_lab.project.entity.User;
import com.se_lab.project.repository.UserRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/test")
public class TestController {

    private final UserRepository userRepository;

    public TestController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // 1. 데이터 입력 테스트 (POST)
    @PostMapping("/insert")
    public String insertTestUser(
            @RequestParam String email,
            @RequestParam String password, // ✨ 비밀번호 파라미터 추가!
            @RequestParam String name) {

        // ✨ new User(...) 형태로 객체를 생성해야 합니다!
        User newUser = new User(email, password, name);
        userRepository.save(newUser);

        return name + "님의 데이터가 Supabase DB에 성공적으로 저장되었습니다!";
    }

    // 2. 데이터 조회 테스트 (GET)
    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}