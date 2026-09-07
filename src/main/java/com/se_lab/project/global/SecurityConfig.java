package com.se_lab.project.global;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor; // 1. 필수!
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;

    @Value("${app.cors.allowed-origin-patterns:http://localhost:*}")
    private List<String> allowedOriginPatterns;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/livez", "/readyz", "/healthz").permitAll()
                        .requestMatchers("/api/v1/admin/**").denyAll()
                        .requestMatchers("/api/v1/places/**").permitAll()
                        .requestMatchers("/api/v1/home").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/routes/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/pilgrimages/generate").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/pilgrimages/**").permitAll()
                        .requestMatchers("/api/v1/trails/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/api/v1/images/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/user-routes/**").permitAll()
                        .anyRequest().authenticated()
                )
                // 로그인이 안 된(또는 토큰이 만료된) 요청은 403이 아니라 401로 답한다.
                // 스프링 기본값은 둘 다 403이라, 앱에서 "로그인이 필요하다"와
                // "권한이 없다"(예: 남의 글 수정)를 구분할 수 없다. 구분이 안 되면
                // 만료된 세션을 정리할 수 없어, 앱이 로그인된 줄 알면서 모든 요청이
                // 실패하는 상태에 갇힌다.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"로그인이 필요합니다.\"}");
                        })
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // ✨ 비밀번호 암호화 도구(BCrypt)를 Bean으로 등록합니다!
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
