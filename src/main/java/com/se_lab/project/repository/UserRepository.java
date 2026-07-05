package com.se_lab.project.repository;

import com.se_lab.project.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // 이메일로 유저 전체 정보를 찾아올 때 사용 (로그인용)
    Optional<User> findByEmail(String email);

    // 이메일이 이미 존재하는지 참/거짓만 빠르게 확인할 때 사용 (회원가입 중복체크용)
    boolean existsByEmail(String email);
}