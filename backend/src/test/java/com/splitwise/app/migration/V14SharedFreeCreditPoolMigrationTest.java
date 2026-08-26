package com.splitwise.app.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Exercises V14__shared_free_credit_pool.sql's user-data merge directly -
 * seeding pre-migration rows in the OLD (user_id, feature_key) shape, running
 * the actual migration file against them, and asserting the resulting (user_id,
 * credit_group) row is correct.
 *
 * Each test method runs against its own freshly-created Postgres DATABASE (not
 * just a schema) within a single shared Testcontainers instance - a fresh
 * database (rather than a fresh schema in a shared database) is what actually
 * isolates the uuid-ossp extension per test: CREATE EXTENSION is
 * database-scoped in Postgres, so two tests sharing one database but different
 * schemas would fight over which schema uuid_generate_v4() ends up resolvable
 * in. A fresh database per test sidesteps that entirely. This never touches the
 * real app's database/migration history (including whatever's already deployed
 * to production) - safe to run repeatedly.
 *
 * V14 is already deployed to production, so its SQL content must never be
 * edited (Flyway tracks applied migrations by checksum - changing an
 * already-applied file breaks the next deploy). This test validates the
 * migration exactly as it already shipped, not a hypothetical improved version
 * - see the "safe to re-run" test below for what that means in practice for an
 * unmodifiable, already-applied migration.
 */
@Testcontainers
class V14SharedFreeCreditPoolMigrationTest {

    private static PostgreSQLContainer<?> postgres;
    private static final AtomicInteger DB_COUNTER = new AtomicInteger();

    private String jdbcUrl;

    @BeforeAll
    static void startContainer() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("splenza_migration_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
    }

    @BeforeEach
    void createFreshDatabase() throws Exception {
        String dbName = "v14_test_" + DB_COUNTER.incrementAndGet();
        try (Connection admin = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            admin.createStatement().execute("CREATE DATABASE " + dbName);
        }
        // Swap the database name in the container's base JDBC URL for this
        // test's own fresh database - default "public" schema, no custom
        // search_path juggling needed.
        this.jdbcUrl = postgres.getJdbcUrl().replaceAll("/[^/?]+(\\?|$)", "/" + dbName + "$1");
    }

    private void migrateTo(String targetVersion) {
        Flyway.configure()
                .dataSource(jdbcUrl, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(targetVersion))
                .load()
                .migrate();
    }

    private MigrateResult migrateToLatest() {
        return Flyway.configure()
                .dataSource(jdbcUrl, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
    }

    private UUID seedUser(Connection connection) throws Exception {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into users (id, name, email) values (?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, "Test User");
            ps.setString(3, id + "@test.com");
            ps.execute();
        }
        return id;
    }

    /**
     * Seeds a row in the OLD (user_id, feature_key) shape - only valid before
     * V14 runs, since that column is renamed away by V14 itself.
     */
    private void seedOldFeatureUsageRow(
            Connection connection, UUID userId, String featureKey, int freeUsedToday, Instant freeResetAt
    ) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into ai_feature_daily_usage (id, user_id, feature_key, free_used_today, free_reset_at) "
                + "values (?, ?, ?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, userId);
            ps.setString(3, featureKey);
            ps.setInt(4, freeUsedToday);
            ps.setTimestamp(5, java.sql.Timestamp.from(freeResetAt));
            ps.execute();
        }
    }

    @Test
    @DisplayName("Merges a user's existing RECEIPT_SCAN + VOICE_EXPENSE rows into one AI_ASSIST row, "
            + "summing usage and keeping the later reset time")
    void mergesBothRows_summingUsageAndKeepingLatestReset() throws Exception {

        migrateTo("13");

        UUID userId;
        Instant receiptScanReset = Instant.now().plusSeconds(3600);
        Instant voiceExpenseReset = Instant.now().plusSeconds(7200); // later than receiptScanReset

        try (Connection connection = connect()) {
            userId = seedUser(connection);
            seedOldFeatureUsageRow(connection, userId, "RECEIPT_SCAN", 1, receiptScanReset);
            seedOldFeatureUsageRow(connection, userId, "VOICE_EXPENSE", 2, voiceExpenseReset);
        }

        migrateTo("14");

        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(
                "select credit_group, free_used_today, free_reset_at "
                + "from ai_feature_daily_usage where user_id = ?")) {
            ps.setObject(1, userId);
            ResultSet rs = ps.executeQuery();

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("credit_group")).isEqualTo("AI_ASSIST");
            assertThat(rs.getInt("free_used_today")).isEqualTo(3); // 1 + 2 summed
            assertThat(rs.getTimestamp("free_reset_at").toInstant())
                    .isCloseTo(voiceExpenseReset, within(1, ChronoUnit.SECONDS));

            assertThat(rs.next()).as("exactly one merged row, not two").isFalse();
        }
    }

    @Test
    @DisplayName("Migrates a user who only ever used one of the two features cleanly, with no data loss")
    void migratesUserWithOnlyOneRow_noDataLoss() throws Exception {

        migrateTo("13");

        UUID userId;
        Instant resetAt = Instant.now().plusSeconds(3600);

        try (Connection connection = connect()) {
            userId = seedUser(connection);
            seedOldFeatureUsageRow(connection, userId, "RECEIPT_SCAN", 2, resetAt);
        }

        migrateTo("14");

        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(
                "select credit_group, free_used_today, free_reset_at "
                + "from ai_feature_daily_usage where user_id = ?")) {
            ps.setObject(1, userId);
            ResultSet rs = ps.executeQuery();

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("credit_group")).isEqualTo("AI_ASSIST");
            assertThat(rs.getInt("free_used_today")).isEqualTo(2); // unchanged, nothing to merge with
            assertThat(rs.getTimestamp("free_reset_at").toInstant())
                    .isCloseTo(resetAt, within(1, ChronoUnit.SECONDS));

            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    @DisplayName("Migrates a user who never used either AI feature without error or a spurious row")
    void migratesUserWithNeitherRow_noErrorNoSpuriousRow() throws Exception {

        migrateTo("13");

        UUID userId;
        try (Connection connection = connect()) {
            userId = seedUser(connection);
            // Deliberately no ai_feature_daily_usage row seeded at all.
        }

        migrateTo("14"); // must not throw

        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(
                "select count(*) from ai_feature_daily_usage where user_id = ?")) {
            ps.setObject(1, userId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    @DisplayName("Running the migration process again (the normal Flyway recovery path) is a safe "
            + "no-op that changes nothing - the actual guarantee Flyway provides, since V14's raw SQL "
            + "itself is NOT safe to hand-execute twice (already deployed, can't be retrofitted with "
            + "IF [NOT] EXISTS guards without breaking its checksum in production - see class javadoc)")
    void reRunningTheMigrationProcess_isASafeNoOp() throws Exception {

        migrateTo("13");

        UUID userId;
        try (Connection connection = connect()) {
            userId = seedUser(connection);
            seedOldFeatureUsageRow(connection, userId, "RECEIPT_SCAN", 1, Instant.now().plusSeconds(3600));
        }

        MigrateResult first = migrateToLatest();
        assertThat(first.migrationsExecuted).isGreaterThan(0);

        // The realistic "recovery scenario" is re-invoking Flyway's own
        // migrate() (e.g. on app restart, or a redeployment) - Flyway's own
        // schema history table is what actually makes this safe, not the
        // raw SQL being independently idempotent.
        MigrateResult second = migrateToLatest();
        assertThat(second.migrationsExecuted).isZero();

        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(
                "select free_used_today from ai_feature_daily_usage where user_id = ?")) {
            ps.setObject(1, userId);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("free_used_today")).isEqualTo(1); // unchanged by the second call
        }
    }
}
