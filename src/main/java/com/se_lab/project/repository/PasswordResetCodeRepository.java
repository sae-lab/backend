package com.se_lab.project.repository;

import com.se_lab.project.entity.PasswordResetCode;
import com.se_lab.project.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    /// 확인 단계에서 쓸, 아직 사용하지 않은 가장 최근 코드.
    Optional<PasswordResetCode> findTopByUserAndUsedAtIsNullOrderByCreatedAtDesc(User user);

    /// 발송 요청 도배를 막기 위한 최근 발송 횟수.
    long countByUserAndCreatedAtAfter(User user, LocalDateTime since);

    /// 새 코드를 낼 때 이전에 보낸 코드는 모두 무효로 만든다.
    /// 옛 코드가 살아 있으면 그만큼 맞혀볼 기회가 늘어난다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordResetCode c set c.usedAt = :now where c.user = :user and c.usedAt is null")
    void invalidateAllUnused(@Param("user") User user, @Param("now") LocalDateTime now);
}
