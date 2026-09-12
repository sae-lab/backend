package com.se_lab.project.global;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = HealthEndpointSecurityTest.TestApplication.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, HealthEndpointSecurityTest.HealthTestConfiguration.class})
class HealthEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtUtil jwtUtil;

    @Autowired
    private TestDatabaseHealthIndicator databaseHealthIndicator;

    @BeforeEach
    void databaseIsAvailable() {
        databaseHealthIndicator.available = true;
    }

    @Test
    void allowsAnonymousHealthChecksWithoutExposingDetails() throws Exception {
        for (String path : new String[]{"/livez", "/readyz", "/healthz"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist());
        }
    }

    @Test
    void routesSimulatedDatabaseOutageWithoutFailingLiveness() throws Exception {
        // Health group의 장애 계약을 검증한다. 실제 Supabase/network 장애 시험은
        // scripts/verify-health-endpoints.sh를 테스트 환경에서 별도로 실행한다.
        databaseHealthIndicator.available = false;

        mockMvc.perform(get("/readyz"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components").doesNotExist());

        mockMvc.perform(get("/healthz"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components").doesNotExist());

        mockMvc.perform(get("/livez"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HealthTestConfiguration {

        @Bean(name = "db")
        TestDatabaseHealthIndicator databaseHealthIndicator() {
            return new TestDatabaseHealthIndicator();
        }
    }

    static class TestDatabaseHealthIndicator implements HealthIndicator {

        private boolean available = true;

        @Override
        public Health health() {
            return available ? Health.up().build() : Health.down().build();
        }
    }
}
