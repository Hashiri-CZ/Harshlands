package cz.hashiri.harshlands.data.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import cz.hashiri.harshlands.data.HLScheduler;
import cz.hashiri.harshlands.rsv.HLPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class HLDatabase {

    public record TanDataRow(
        double temperature,
        int thirst,
        double thirstExhaustion,
        int thirstSaturation,
        int thirstTickTimer
    ) {}

    private HikariDataSource dataSource;
    private final HLPlugin plugin;
    private final HLScheduler scheduler;
    private final Logger logger;
    private boolean isMysql;

    public HLDatabase(HLPlugin plugin, HLScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.logger = plugin.getLogger();
    }

    public void connect() {
        FileConfiguration config = plugin.getConfig();
        String type = config.getString("Database.Type", "H2").toUpperCase();
        this.isMysql = type.equals("MYSQL");

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("HL-Pool");

        if (isMysql) {
            String host = config.getString("Database.MySQL.Host", "localhost");
            int port = config.getInt("Database.MySQL.Port", 3306);
            String database = config.getString("Database.MySQL.Database", "harshlands");
            String username = config.getString("Database.MySQL.Username", "root");
            String password = config.getString("Database.MySQL.Password", "");

            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(
                    "[HLDatabase] MySQL driver not found. Add mysql-connector-j to your server's /lib folder.", e);
            }
            hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikariConfig.setMaximumPoolSize(config.getInt("Database.MySQL.Pool.MaximumPoolSize", 10));
            hikariConfig.setMinimumIdle(config.getInt("Database.MySQL.Pool.MinimumIdle", 2));
            hikariConfig.setConnectionTimeout(config.getLong("Database.MySQL.Pool.ConnectionTimeout", 30000));
            hikariConfig.setIdleTimeout(config.getLong("Database.MySQL.Pool.IdleTimeout", 600000));
            hikariConfig.setMaxLifetime(config.getLong("Database.MySQL.Pool.MaxLifetime", 1800000));
        } else {
            File dbFile = new File(plugin.getDataFolder(), "data");
            hikariConfig.setJdbcUrl("jdbc:h2:file:" + dbFile.getAbsolutePath() + ";TRACE_LEVEL_FILE=0");
            hikariConfig.setDriverClassName("org.h2.Driver");
            hikariConfig.setMaximumPoolSize(2);
            hikariConfig.setMinimumIdle(1);
        }

        try {
            dataSource = new HikariDataSource(hikariConfig);
            // Test connection
            try (Connection conn = dataSource.getConnection()) {
                conn.isValid(1);
            }
            logger.info("[HLDatabase] Connected to " + type + " database.");
        } catch (Exception e) {
            throw new RuntimeException("[HLDatabase] Failed to connect to database: " + e.getMessage(), e);
        }
    }

    /**
     * Renames old rsv_* tables to hl_* if they exist (one-time migration from pre-Harshlands DB).
     * Must be called after connect() and before createTables().
     */
    public void migrateTableNames() {
        try (Connection conn = dataSource.getConnection()) {
            if (isMysql) {
                // MySQL: check and rename each table if it still has the old name
                String[][] migrations = {
                    {"rsv_tan_data",     "hl_tan_data"},
                    {"rsv_baubles_data", "hl_baubles_data"},
                    {"rsv_torch_lit",    "hl_torch_lit"}
                };
                for (String[] pair : migrations) {
                    String oldTable = pair[0];
                    String newTable = pair[1];
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT COUNT(*) FROM information_schema.tables"
                            + " WHERE table_schema = DATABASE() AND table_name = ?")) {
                        ps.setString(1, oldTable);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next() && rs.getInt(1) > 0) {
                                conn.createStatement().execute(
                                    "RENAME TABLE " + oldTable + " TO " + newTable);
                                logger.info("[HLDatabase] Renamed table " + oldTable + " -> " + newTable);
                            }
                        }
                    }
                }
            } else {
                // H2: table names are stored uppercased in INFORMATION_SCHEMA
                String[][] migrations = {
                    {"RSV_TAN_DATA",     "HL_TAN_DATA",     "hl_tan_data"},
                    {"RSV_BAUBLES_DATA", "HL_BAUBLES_DATA", "hl_baubles_data"},
                    {"RSV_TORCH_LIT",    "HL_TORCH_LIT",    "hl_torch_lit"}
                };
                for (String[] triple : migrations) {
                    String oldUpper = triple[0];
                    String newUpper = triple[1];
                    String newTable = triple[2];
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES"
                            + " WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = ?")) {
                        ps.setString(1, oldUpper);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next() && rs.getInt(1) > 0) {
                                // Also check the new name doesn't already exist
                                boolean newExists;
                                try (PreparedStatement ps2 = conn.prepareStatement(
                                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES"
                                        + " WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = ?")) {
                                    ps2.setString(1, newUpper);
                                    try (ResultSet rs2 = ps2.executeQuery()) {
                                        newExists = rs2.next() && rs2.getInt(1) > 0;
                                    }
                                }
                                if (!newExists) {
                                    conn.createStatement().execute(
                                        "ALTER TABLE " + oldUpper + " RENAME TO " + newTable);
                                    logger.info("[HLDatabase] Renamed table " + oldUpper + " -> " + newTable);
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.warning("[HLDatabase] Table name migration failed: " + e.getMessage());
        }
    }

    public void createTables() {
        String tanTable = "CREATE TABLE IF NOT EXISTS hl_tan_data ("
            + "uuid VARCHAR(36) PRIMARY KEY,"
            + "temperature DOUBLE NOT NULL,"
            + "thirst INT NOT NULL,"
            + "thirst_exhaustion DOUBLE NOT NULL,"
            + "thirst_saturation INT NOT NULL,"
            + "thirst_tick_timer INT NOT NULL"
            + ")";

        String baublesTable = "CREATE TABLE IF NOT EXISTS hl_baubles_data ("
            + "uuid VARCHAR(36) PRIMARY KEY,"
            + "items_json TEXT NOT NULL"
            + ")";

        String torchTable = "CREATE TABLE IF NOT EXISTS hl_torch_lit ("
            + "location_key VARCHAR(120) PRIMARY KEY,"
            + "expires_at BIGINT NOT NULL"
            + ")";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(tanTable);
            stmt.execute(baublesTable);
            stmt.execute(torchTable);
            logger.info("[HLDatabase] Tables created/verified.");
        } catch (SQLException e) {
            throw new RuntimeException("[HLDatabase] Failed to create tables: " + e.getMessage(), e);
        }

        migrateYamlData();
    }

    // ── YAML Migration ────────────────────────────────────────────────────────

    private void migrateYamlData() {
        migrateTanData();
        migrateBaublesData();
        migrateTorchData();
    }

    private void migrateTanData() {
        File yamlFile = new File(plugin.getDataFolder(), "resources/toughasnails/playerdata.yml");
        if (!yamlFile.exists()) {
            return;
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM hl_tan_data")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return; // Already has data
            }
        } catch (SQLException e) {
            logger.warning("[HLDatabase] Could not check hl_tan_data for migration: " + e.getMessage());
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(yamlFile);
        Set<String> keys = yaml.getKeys(false);
        if (keys.isEmpty()) {
            return;
        }

        String sql = isMysql
            ? "INSERT IGNORE INTO hl_tan_data (uuid, temperature, thirst, thirst_exhaustion, thirst_saturation, thirst_tick_timer) VALUES (?, ?, ?, ?, ?, ?)"
            : "MERGE INTO hl_tan_data (uuid, temperature, thirst, thirst_exhaustion, thirst_saturation, thirst_tick_timer) KEY(uuid) VALUES (?, ?, ?, ?, ?, ?)";

        int migrated = 0;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String uuid : keys) {
                ps.setString(1, uuid);
                ps.setDouble(2, yaml.getDouble(uuid + ".Temperature", 0.0));
                ps.setInt(3, yaml.getInt(uuid + ".Thirst", 20));
                ps.setDouble(4, yaml.getDouble(uuid + ".ThirstExhaustion", 0.0));
                ps.setInt(5, yaml.getInt(uuid + ".ThirstSaturation", 5));
                ps.setInt(6, yaml.getInt(uuid + ".ThirstTickTimer", 0));
                ps.addBatch();
                migrated++;
            }
            ps.executeBatch();
        } catch (SQLException e) {
            logger.warning("[HLDatabase] TAN data migration failed: " + e.getMessage());
            return;
        }

        yamlFile.renameTo(new File(yamlFile.getParent(), "playerdata.yml.migrated"));
        logger.info("[HLDatabase] Migrated " + migrated + " TAN player records from YAML to DB.");
    }

    private void migrateBaublesData() {
        File yamlFile = new File(plugin.getDataFolder(), "resources/baubles/playerdata.yml");
        if (!yamlFile.exists()) {
            return;
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM hl_baubles_data")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        } catch (SQLException e) {
            logger.warning("[HLDatabase] Could not check hl_baubles_data for migration: " + e.getMessage());
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(yamlFile);
        Set<String> keys = yaml.getKeys(false);
        if (keys.isEmpty()) {
            return;
        }

        String sql = isMysql
            ? "INSERT IGNORE INTO hl_baubles_data (uuid, items_json) VALUES (?, ?)"
            : "MERGE INTO hl_baubles_data (uuid, items_json) KEY(uuid) VALUES (?, ?)";

        int migrated = 0;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String uuid : keys) {
                String json = yaml.getString(uuid + ".Items");
                if (json != null && !json.isEmpty()) {
                    ps.setString(1, uuid);
                    ps.setString(2, json);
                    ps.addBatch();
                    migrated++;
                }
            }
            ps.executeBatch();
        } catch (SQLException e) {
            logger.warning("[HLDatabase] Baubles data migration failed: " + e.getMessage());
            return;
        }

        yamlFile.renameTo(new File(yamlFile.getParent(), "playerdata.yml.migrated"));
        logger.info("[HLDatabase] Migrated " + migrated + " Baubles player records from YAML to DB.");
    }

    private void migrateTorchData() {
        File yamlFile = new File(plugin.getDataFolder(), "resources/fear/torchdata.yml");
        if (!yamlFile.exists()) {
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(yamlFile);
        ConfigurationSection section = yaml.getConfigurationSection("LitTorches");
        if (section == null || section.getKeys(false).isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        String sql = isMysql
            ? "INSERT IGNORE INTO hl_torch_lit (location_key, expires_at) VALUES (?, ?)"
            : "MERGE INTO hl_torch_lit (location_key, expires_at) KEY(location_key) VALUES (?, ?)";

        int migrated = 0;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String key : section.getKeys(false)) {
                long remaining = section.getLong(key);
                if (remaining > 0) {
                    ps.setString(1, key);
                    ps.setLong(2, now + remaining);
                    ps.addBatch();
                    migrated++;
                }
            }
            ps.executeBatch();
        } catch (SQLException e) {
            logger.warning("[HLDatabase] Torch data migration failed: " + e.getMessage());
            return;
        }

        // Clear only the LitTorches section; keep UnlitTorches for FearModule
        yaml.set("LitTorches", null);
        try {
            yaml.save(yamlFile);
        } catch (Exception e) {
            logger.warning("[HLDatabase] Failed to clear migrated torch data from YAML: " + e.getMessage());
        }

        if (migrated > 0) {
            logger.info("[HLDatabase] Migrated " + migrated + " lit torch records from YAML to DB.");
        }
    }

    // ── TAN Data ──────────────────────────────────────────────────────────────

    public CompletableFuture<Optional<TanDataRow>> loadTanData(UUID uuid) {
        return scheduler.supplyAsync(() -> {
            String sql = "SELECT temperature, thirst, thirst_exhaustion, thirst_saturation, thirst_tick_timer"
                + " FROM hl_tan_data WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new TanDataRow(
                            rs.getDouble("temperature"),
                            rs.getInt("thirst"),
                            rs.getDouble("thirst_exhaustion"),
                            rs.getInt("thirst_saturation"),
                            rs.getInt("thirst_tick_timer")
                        ));
                    }
                }
            } catch (SQLException e) {
                logger.warning("[HLDatabase] Failed to load TAN data for " + uuid + ": " + e.getMessage());
            }
            return Optional.empty();
        });
    }

    public CompletableFuture<Void> saveTanData(UUID uuid, TanDataRow row) {
        return scheduler.runAsync(() -> {
            String sql = isMysql
                ? "INSERT INTO hl_tan_data (uuid, temperature, thirst, thirst_exhaustion, thirst_saturation, thirst_tick_timer)"
                    + " VALUES (?, ?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE temperature=VALUES(temperature), thirst=VALUES(thirst),"
                    + " thirst_exhaustion=VALUES(thirst_exhaustion), thirst_saturation=VALUES(thirst_saturation),"
                    + " thirst_tick_timer=VALUES(thirst_tick_timer)"
                : "MERGE INTO hl_tan_data (uuid, temperature, thirst, thirst_exhaustion, thirst_saturation, thirst_tick_timer)"
                    + " KEY(uuid)"
                    + " VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setDouble(2, row.temperature());
                ps.setInt(3, row.thirst());
                ps.setDouble(4, row.thirstExhaustion());
                ps.setInt(5, row.thirstSaturation());
                ps.setInt(6, row.thirstTickTimer());
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.warning("[HLDatabase] Failed to save TAN data for " + uuid + ": " + e.getMessage());
            }
        });
    }

    // ── Baubles Data ──────────────────────────────────────────────────────────

    public CompletableFuture<Optional<String>> loadBaublesJson(UUID uuid) {
        return scheduler.supplyAsync(() -> {
            String sql = "SELECT items_json FROM hl_baubles_data WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(rs.getString("items_json"));
                    }
                }
            } catch (SQLException e) {
                logger.warning("[HLDatabase] Failed to load baubles data for " + uuid + ": " + e.getMessage());
            }
            return Optional.empty();
        });
    }

    public CompletableFuture<Void> saveBaublesJson(UUID uuid, String json) {
        return scheduler.runAsync(() -> {
            String sql = isMysql
                ? "INSERT INTO hl_baubles_data (uuid, items_json) VALUES (?, ?)"
                    + " ON DUPLICATE KEY UPDATE items_json=VALUES(items_json)"
                : "MERGE INTO hl_baubles_data (uuid, items_json) KEY(uuid) VALUES (?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, json);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.warning("[HLDatabase] Failed to save baubles data for " + uuid + ": " + e.getMessage());
            }
        });
    }

    // ── Torch Data ────────────────────────────────────────────────────────────

    public CompletableFuture<Map<String, Long>> loadLitTorches() {
        return scheduler.supplyAsync(() -> {
            Map<String, Long> result = new HashMap<>();
            long now = System.currentTimeMillis();
            String sql = "SELECT location_key, expires_at FROM hl_torch_lit WHERE expires_at > ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, now);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long remaining = rs.getLong("expires_at") - now;
                        if (remaining > 0) {
                            result.put(rs.getString("location_key"), remaining);
                        }
                    }
                }
            } catch (SQLException e) {
                logger.warning("[HLDatabase] Failed to load lit torch data: " + e.getMessage());
            }
            return result;
        });
    }

    public CompletableFuture<Void> saveLitTorches(Map<String, Long> snapshot) {
        return scheduler.runAsync(() -> {
            long now = System.currentTimeMillis();
            try (Connection conn = dataSource.getConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("DELETE FROM hl_torch_lit");
                }
                if (!snapshot.isEmpty()) {
                    String sql = isMysql
                        ? "INSERT IGNORE INTO hl_torch_lit (location_key, expires_at) VALUES (?, ?)"
                        : "INSERT INTO hl_torch_lit (location_key, expires_at) VALUES (?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
                            ps.setString(1, entry.getKey());
                            ps.setLong(2, now + entry.getValue());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
            } catch (SQLException e) {
                logger.warning("[HLDatabase] Failed to save lit torch data: " + e.getMessage());
            }
        });
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("[HLDatabase] Connection pool closed.");
        }
    }
}
