package com.se_lab.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class SightseeingProjectApplication {

    public static void main(String[] args) {
        Dotenv.configure()
                .ignoreIfMissing()
                .systemProperties()
                .load();
        SpringApplication.run(SightseeingProjectApplication.class, args);
    }
}
