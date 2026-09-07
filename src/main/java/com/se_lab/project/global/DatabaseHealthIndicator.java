package com.se_lab.project.global;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component("dbHealthIndicator")
public class DatabaseHealthIndicator implements HealthIndicator, AutoCloseable {

    private final Duration timeout;
    private final DatabaseCheck databaseCheck;
    private final ThreadPoolExecutor executor;

    @Autowired
    public DatabaseHealthIndicator(
            DataSourceProperties dataSourceProperties,
            @Value("${app.health.database.timeout:3s}") Duration timeout
    ) {
        this(timeout, () -> checkDatabase(dataSourceProperties, timeout));
    }

    DatabaseHealthIndicator(Duration timeout, DatabaseCheck databaseCheck) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Database health timeout must be positive");
        }
        this.timeout = timeout;
        this.databaseCheck = databaseCheck;
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, "database-health-check");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(), threadFactory, new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Override
    public Health health() {
        final Future<Boolean> check;
        try {
            check = executor.submit(databaseCheck::isAvailable);
        } catch (RuntimeException exception) {
            return Health.down().build();
        }

        try {
            return check.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    ? Health.up().build()
                    : Health.down().build();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Health.down().build();
        } catch (ExecutionException | TimeoutException exception) {
            check.cancel(true);
            return Health.down().build();
        }
    }

    private static boolean checkDatabase(DataSourceProperties dataSourceProperties, Duration timeout)
            throws Exception {
        int timeoutSeconds = Math.max(1, Math.toIntExact((timeout.toMillis() + 999) / 1_000));
        Properties properties = new Properties();
        properties.setProperty("user", dataSourceProperties.determineUsername());
        properties.setProperty("password", dataSourceProperties.determinePassword());
        properties.setProperty("connectTimeout", Integer.toString(timeoutSeconds));
        properties.setProperty("socketTimeout", Integer.toString(timeoutSeconds));
        properties.setProperty("loginTimeout", Integer.toString(timeoutSeconds));

        try (Connection connection = DriverManager.getConnection(dataSourceProperties.determineUrl(), properties);
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(timeoutSeconds);
            return statement.execute("SELECT 1");
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    @FunctionalInterface
    interface DatabaseCheck {
        boolean isAvailable() throws Exception;
    }
}
