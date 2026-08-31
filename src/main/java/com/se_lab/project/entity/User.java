package com.se_lab.project.entity;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    private String password;

    // 회원가입 시 받는 실명. 게시판 등 공개된 곳에는 절대 노출하지 않고,
    // 공개 표시용으로는 항상 nickname(또는 익명 기본값)만 쓴다 — 아래 getDisplayName() 참고.
    @Column(nullable = false)
    private String name;

    // 사용자가 직접 설정하는 공개 닉네임. 설정 전에는 null이며, 이 경우 getDisplayName()이 "익명" + id로 대체한다.
    @Setter
    @Column
    private String nickname;

    @Setter
    @Column
    private String profileImageUrl;

    // 기본 생성자
    public User() {}

    // 생성자에도 password가 들어가도록 수정했습니다.
    public User(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.name = name;
    }

    // 게시판/댓글 등 공개 화면에 표시할 이름. 실명(name)은 절대 여기 섞이지 않는다.
    public String getDisplayName() {
        return (nickname != null && !nickname.isBlank()) ? nickname : "익명" + id;
    }
}
