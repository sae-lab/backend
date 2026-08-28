package com.se_lab.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    private String email;
    // 계정 소유자 본인에게만 보여주는 실명. 게시판 등 공개 화면에는 절대 쓰지 않는다.
    private String name;
    private String nickname;
    // 최종적으로 화면에 표시될 이름 (nickname이 없으면 "익명" + id로 대체)
    private String displayName;
    private String profileImageUrl;
}
