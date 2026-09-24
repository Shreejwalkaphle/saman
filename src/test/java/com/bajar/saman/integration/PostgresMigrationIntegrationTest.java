package com.bajar.saman.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("saman_integration")
                    .withUsername("saman_test")
                    .withPassword("saman_test");

    @BeforeAll
    static void migrateSchema() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        assertEquals(21, flyway.migrate().migrationsExecuted,
                "A clean PostgreSQL database must apply the complete migration chain");
        assertEquals(0, flyway.migrate().migrationsExecuted,
                "Re-running Flyway against the same schema must be idempotent");
    }

    @Test
    void migrationChainCreatesCurrentShopSchema() throws SQLException {
        try (Connection connection = connection()) {
            assertEquals("21", scalar(connection,
                    "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"));
            assertEquals("NO", scalar(connection,
                    "SELECT is_nullable FROM information_schema.columns " +
                            "WHERE table_schema='public' AND table_name='products' AND column_name='shop_id'"));
            assertEquals("shops", scalar(connection,
                    "SELECT table_name FROM information_schema.tables " +
                            "WHERE table_schema='public' AND table_name='shops'"));
            assertEquals("shop_members", scalar(connection,
                    "SELECT table_name FROM information_schema.tables " +
                            "WHERE table_schema='public' AND table_name='shop_members'"));
        }
    }

    @Test
    void postgresEnforcesShopCoordinateConstraint() throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO shops (
                         id, name, slug, application_key, phone, address_line1,
                         city, district, latitude, longitude, status,
                         created_at, updated_at, version
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), 0)
                     """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, "Invalid Coordinate Shop");
            statement.setString(3, "invalid-coordinate-shop");
            statement.setObject(4, UUID.randomUUID());
            statement.setString(5, "9800000000");
            statement.setString(6, "Main Road");
            statement.setString(7, "Biratnagar");
            statement.setString(8, "Morang");
            statement.setBigDecimal(9, new java.math.BigDecimal("91.000000"));
            statement.setBigDecimal(10, new java.math.BigDecimal("87.271800"));
            statement.setString(11, "PENDING_APPROVAL");

            SQLException exception = assertThrows(SQLException.class, statement::executeUpdate);
            assertEquals("23514", exception.getSQLState(),
                    "PostgreSQL must reject latitude outside -90..90 via CHECK constraint");
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next(), "Query returned no rows: " + sql);
            return result.getString(1);
        }
    }
}
