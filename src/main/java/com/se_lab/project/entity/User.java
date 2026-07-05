package com.se_lab.project.entity;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "users") // Supabase DB에 'users'라는 테이블로 매핑됩니다.
public class User {

    // Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이메일은 로그인 아이디로 쓸 거니까 중복을 막기 위해 unique = true를 추가했습니다!
    @Column(nullable = false, unique = true)
    private String email;

    // ✨ Getter 추가
    // ✨ 진짜 로그인을 위한 비밀번호 필드 추가!
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    // 기본 생성자
    public User() {}

    // 생성자에도 password가 들어가도록 수정했습니다.
    public User(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.name = name;
    }

}