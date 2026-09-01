package com.se_lab.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.TimeZone;

@SpringBootApplication
public class SightseeingProjectApplication {

    public static void main(String[] args) {
        Dotenv.configure()
                .ignoreIfMissing()
                .systemProperties()
                .load();

        // 게시물/댓글 작성 시각은 LocalDateTime.now()로 저장되는데, 이 값은 JVM 기본
        // 시간대를 따른다. 배포 환경(Docker 컨테이너)은 기본이 UTC라 한국 시간보다
        // 9시간 이른 값이 저장되고, 응답 JSON에는 시간대 표기가 없어서 앱이 그대로
        // 로컬 시각으로 읽어 "9시간 전"처럼 잘못 표시된다. 어디서 실행하든 한국 시각으로
        // 통일되도록 명시적으로 고정한다.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));

        SpringApplication.run(SightseeingProjectApplication.class, args);
    }
}
