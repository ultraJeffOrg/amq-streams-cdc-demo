package com.redhat.demo.producer;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Health check for MySQL database connectivity.
 */
@Readiness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    @Inject
    AgroalDataSource dataSource;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("MySQL connection");
        
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(5)) {
                return builder.up()
                        .withData("database", "MySQL")
                        .withData("status", "connected")
                        .build();
            } else {
                return builder.down()
                        .withData("error", "Connection invalid")
                        .build();
            }
        } catch (SQLException e) {
            return builder.down()
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}

