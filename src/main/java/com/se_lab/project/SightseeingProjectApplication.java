package com.se_lab.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class SightseeingProjectApplication {

    public static void main(String[] args) {
        Dotenv.configure().systemProperties().load(); // 이 한 줄이 .env를 환경변수로 바꿔줌
        SpringApplication.run(SightseeingProjectApplication.class, args);
    }
}