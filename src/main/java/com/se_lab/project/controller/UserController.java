package com.se_lab.project.controller;

import com.se_lab.project.dto.UserProfileDto;
import com.se_lab.project.entity.User;
import com.se_lab.project.repository.UserRepository;
import com.se_lab.project.service.FileStorageService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile() {
        String email = currentUserEmail();
        if (email == null) return unauthorized();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다: " + email));
        return ResponseEntity.ok(toDto(user));
    }

    // 닉네임/프로필 사진을 부분적으로 수정한다. 둘 다 선택값 — 보낸 것만 바뀐다.
    @PatchMapping(value = "/me", consumes = "multipart/form-data")
    public ResponseEntity<?> updateMyProfile(
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) MultipartFile photo) {
        String email = currentUserEmail();
        if (email == null) return unauthorized();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("유저를 찾을 수 없습니다: " + email));

        if (nickname != null) {
            String trimmed = nickname.trim();
            user.setNickname(trimmed.isEmpty() ? null : trimmed);
        }
        if (photo != null && !photo.isEmpty()) {
            user.setProfileImageUrl(fileStorageService.store(photo, "profiles"));
        }
        userRepository.save(user);

        return ResponseEntity.ok(toDto(user));
    }

    private UserProfileDto toDto(User user) {
        return UserProfileDto.builder()
                .email(user.getEmail())
                .name(user.getName())
                .nickname(user.getNickname())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }

    private String currentUserEmail() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (email == null || email.isBlank() || "anonymousUser".equals(email)) return null;
        return email;
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
    }
}
