package com.maksonlee.gerrit.reflog.model;

import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.Calendar;
import java.util.Optional;
import java.util.TimeZone;

@Singleton
public class ReflogRepository {
    private static final String JDBC_URL = "jdbc:h2:/srv/gerrit/db/reflog";
    private static final Logger logger = LoggerFactory.getLogger(ReflogRepository.class);
    private static boolean initialized = false;

    public ReflogRepository() {
        initDatabaseIfNeeded();
    }

    private synchronized void initDatabaseIfNeeded() {
        if (initialized) return;

        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "")) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet tables = meta.getTables(null, null, "REFLOG", null);
            if (!tables.next()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("""
                                CREATE TABLE reflog (
                                  id IDENTITY PRIMARY KEY,
                                  project VARCHAR(255),
                                  ref VARCHAR(255),
                                  old_rev VARCHAR(40),
                                  new_rev VARCHAR(40),
                                  username VARCHAR(255),
                                  timestamp TIMESTAMP
                                )
                            """);
                }
            }
            initialized = true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize H2 database", e);
        }
    }

    public void save(String project, String ref, String oldRev, String newRev, String user, Instant timestamp) {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "")) {
            conn.setAutoCommit(false);
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO reflog (project, ref, old_rev, new_rev, username, timestamp) VALUES (?, ?, ?, ?, ?, ?)"
            );
            stmt.setString(1, project);
            stmt.setString(2, ref);
            stmt.setString(3, oldRev);
            stmt.setString(4, newRev);
            stmt.setString(5, user);
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            stmt.setTimestamp(6, Timestamp.from(timestamp), utc);
            stmt.executeUpdate();
            conn.commit();

            logger.info("Successfully inserted reflog entry for {} {}", project, ref);
        } catch (SQLException e) {
            logger.error("Failed to insert reflog entry: {}", e.getMessage());
        }
    }

    public Optional<RevisionAtTime> findLastRevisionBefore(String project, String ref, Instant timestamp) {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "")) {
            PreparedStatement stmt = conn.prepareStatement("""
                        SELECT new_rev, timestamp FROM reflog
                        WHERE project = ? AND ref = ? AND timestamp <= ?
                        ORDER BY timestamp DESC LIMIT 1
                    """);

            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            stmt.setString(1, project);
            stmt.setString(2, ref);
            stmt.setTimestamp(3, Timestamp.from(timestamp), utc);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(new RevisionAtTime(
                        project,
                        ref,
                        rs.getString("new_rev"),
                        rs.getTimestamp("timestamp", utc).toInstant()
                ));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Database error in findLastRevisionBefore", e);
        }
    }

    public boolean exists(String project, String ref, String newRev) {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "")) {
            PreparedStatement stmt = conn.prepareStatement("""
                        SELECT 1 FROM reflog
                        WHERE project = ? AND ref = ? AND new_rev = ?
                        LIMIT 1
                    """);
            stmt.setString(1, project);
            stmt.setString(2, ref);
            stmt.setString(3, newRev);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check reflog existence", e);
        }
    }

    public record RevisionAtTime(String project, String ref, String revision, Instant timestamp) {
    }
}
