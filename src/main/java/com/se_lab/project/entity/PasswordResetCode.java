package com.se_lab.project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/// 비밀번호 재설정용 6자리 코드.
///
/// 코드 원문은 저장하지 않고 해시(BCrypt)만 남긴다. DB가 통째로 유출돼도
/// 그것만으로는 남의 비밀번호를 바꿀 수 없어야 하기 때문이다.
@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "password_reset_codes")
public class PasswordResetCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /// 한 번 쓴 코드는 다시 못 쓰도록 사용 시각을 남긴다.
    @Column
    private LocalDateTime usedAt;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /// 코드를 몇 번 틀렸는지. 무한정 찍어보는 걸 막는다.
    @Builder.Default
    @Column(nullable = false)
    private int attemptCount = 0;

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }

    public void markUsed(LocalDateTime now) {
        this.usedAt = now;
    }

    public void recordFailedAttempt() {
        this.attemptCount++;
    }
}
