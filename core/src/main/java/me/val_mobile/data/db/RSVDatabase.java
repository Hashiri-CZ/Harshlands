package me.val_mobile.data.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.val_mobile.data.RSVScheduler;
import me.val_mobile.rsv.RSVPlugin;
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

public class RSVDatabase {

    public record TanDataRow(
        double temperature,
        int thirst,
        double thirstExhaustion,
        int thirstSaturation,
        int thirstTickTimer
    ) {}

    private HikariDataSource dataSource;
    private final RSVPlugin plugin;
    private final RSVScheduler scheduler;
    private final Logger logger;
    private boolean isMysql;

    public RSVDatabase(RSVPlugin plugin, RSVScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.logger = plugin.getLogger();
    }

    public void connect() {
        FileConfiguration config = plugin.getConfig();
        String type = config.getString("Database.Type", "H2").toUpperCase();
        this.isMysql = type.equals("MYSQL");

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("RSV-Pool");

        if (isMysql) {
            String host = config.getString("Database.MySQL.Host", "localhost");
            int port = config.getInt("Database.MySQL.Port", 3306);
            String database = config.getString("Database.MySQL.Database", "realisticsurvival");
            String username = config.getString("Database.MySQL.Username", "root");
            String password = config.getString("Database.MySQL.Password", "");

            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(
                    "[RSVDatabase] MySQL driver not found. Add mysql-connector-j to your server's /lib folder.", e);
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
            logger.info("[RSVDatabase] Connected to " + type + " database.");
        } catch (Exception e) {
            throw new RuntimeException("[RSVDatabase] Failed to connect to database: " + e.getMessage(), e);
        }
    }

    public void createTables() {
        String tanTable = "CREATE TABLE IF NOT EXISTS rsv_tan_data ("
            + "uuid VARCHAR(36) PRIMARY KEY,"
            + "temperature DOUBLE NOT NULL,"
            + "thirst INT NOT NULL,"
            + "thirst_exhaustion DOUBLE NOT NULL,"
            + "thirst_saturation INT NOT NULL,"
            + "thirst_tick_timer INT NOT NULL"
            + ")";

        String baublesTable = "CREATE TABLE IF NOT EXISTS rsv_baubles_data ("
            + "uuid VARCHAR(36) PRIMARY KEY,"
            + "items_json TEXT NOT NULL"
            + ")";

        String torchTable = "CREATE TABLE IF NOT EXISTS rsv_torch_lit ("
            + "location_key VARCHAR(120) PRIMARY KEY,"
            + "expires_at BIGINT NOT NULL"
            + ")";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(tanTable);
            stmt.execute(baublesTable);
            stmt.execute(torchTable);
            logger.info("[RSVDatabase] Tables created/verified.");
        } catch (SQLException e) {
            throw new RuntimeException("[RSVDatabase] Failed to create tables: " + e.getMessage(), e);
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
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM rsv_tan_data")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return; // Already has data
            }
        } catch (SQLException e) {
            logger.warning("[RSVDatabase] Could not check rsv_tan_data for migration: " + e.getMessage());
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(yamlFile);
        Set<String> keys = yaml.getKeys(false);
        if (keys.isEmpty()) {
            return;
        }

        String sql = isMysql
            ? "INSERT IGNORE INTO rsv_tan_data (uuid, temperature, thirst, thirst_exhaustion, thirst_saturation, thirst_tick_timer) VALUES (?, ?, ?, ?, ?, ?)"
            : "MERGE INTO rsv_tan_data (uuid, temperature, thirst, thirst_exhaustion, thirst_saturation, thirst_tick_timer) KEY(uuid) VALUES (?, ?, ?, ?, ?, ?)";

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
            logger.warning("[RSVDatabase] TAN data migration failed: " + e.getMessage());
            return;
        }

        yamlFile.renameTo(new File(yamlFile.getParent(), "playerdata.yml.migrated"));
        logger.info("[RSVDatabase] Migrated " + migrated + " TAN player records from YAML to DB.");
    }

    private void migrateBaublesData() {
        File yamlFile = new File(plugin.getDataFolder(), "resources/baubles/playerdata.yml");
        if (!yamlFile.exists()) {
            return;
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM rsv_baubles_data")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        } catch (SQLException e) {
            logger.warning("[RSVDatabase] Could not check rsv_baubles_data for migration: " + e.getMessage());
            return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(yamlFile);
        Set<String> keys = yaml.getKeys(false);
        if (keys.isEmpty()) {
            return;
        }

        String sql = isMysql
            ? "INSERT IGNORE INTO rsv_baubles_data (uuid, items_json) VALUES (?, ?)"
            : "MERGE INTO rsv_baubles_data (uuid, items_json) KEY(uuid) VALUES (?, ?)";

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
            logger.warning("[RSVDatabase] Baubles data migration failed: " + e.getMessage());
            return;
        }

        yamlFile.renameTo(new File(yamlFile.getParent(), "playerdata.yml.migrated"));
        logger.info("[RSVDatabase] Migrated " + migrated + " Baubles player records from YAML to DB.");
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
            ? "INSERT IGNORE INTO rsv_torch_lit (location_key, expires_at) VALUES (?, ?)"
            : "MERGE INTO rsv_torch_lit (location_key, expires_at) KEY(location_key) VALUES (?, ?)";

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
            logger.warning("[RSVDatabase] Torch data migration failed: " + e.getMessage());
            return;
        }

        // Clear only the LitTorches section; keep UnlitTorches for FearModule
        yaml.set("LitTorches", null);
        try {
            yaml.save(yamlFile);
        } catch (Exception e) {
            logger.warning("[RSVDatabase] Failed to clear migrated torch data from YAML: " + e.getMessage());
        }

        if (migrated > 0) {
            logger.info("[RSVDatabase] Migrated " + migrated + " lit torch records from YAML to DB.");
        }
    }

    // ── TAN Data ──────────────────────────────────────────────────────────────

    public CompletableFuture<Optional<TanDataRow>> loadTanData(UUID uuid) {
        return scheduler.supplyAsync(() -> {
            String sql = "SELECT temperature, thirst, thirst_exhaustion, thirst_saturation, thirst_tick_timer"
                + " FROM rsv_tan_data WHERE uuid = ?";
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
                logger.warning("[RSVDatabase] Failed to load TAN data for " + uuid + ": " + e.getMessage());
            }
            return Optional.empty();
        });
    }

    public CompletableFuture<Void> saveTanData(UUID uuid, TanDataRow row) {
        return scheduler.runAsync(() -> {
            String sql = isMysql
                ? "INSERT INTO rsv_tan_data (uuid, temperature, thirst, thirst_exhaustion, thirst_saturation, thirst_tick_timer)"
                    + " VALUES (?, ?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE temperature=VALUES(temperature), thirst=VALUES(thirst),"
                    + " thirst_exhaustion=VALUES(thirst_exhaustion), thirst_saturation=VALUES(thirst_saturation),"
                    + " thirst_tick_timer=VALUES(thirst_tick_timer)"
                : "MERGE INTO rsv_tan_data (uuid, temperature, thirst, thirst_exhaustion, thirst_saturation, thirst_tick_timer)"
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
                logger.warning("[RSVDatabase] Failed to save TAN data for " + uuid + ": " + e.getMessage());
            }
        });
    }

    // ── Baubles Data ──────────────────────────────────────────────────────────

    public CompletableFuture<Optional<String>> loadBaublesJson(UUID uuid) {
        return scheduler.supplyAsync(() -> {
            String sql = "SELECT items_json FROM rsv_baubles_data WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(rs.getString("items_json"));
                    }
                }
            } catch (SQLException e) {
                logger.warning("[RSVDatabase] Failed to load baubles data for " + uuid + ": " + e.getMessage());
            }
            return Optional.empty();
        });
    }

    public CompletableFuture<Void> saveBaublesJson(UUID uuid, String json) {
        return scheduler.runAsync(() -> {
            String sql = isMysql
                ? "INSERT INTO rsv_baubles_data (uuid, items_json) VALUES (?, ?)"
                    + " ON DUPLICATE KEY UPDATE items_json=VALUES(items_json)"
                : "MERGE INTO rsv_baubles_data (uuid, items_json) KEY(uuid) VALUES (?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, json);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.warning("[RSVDatabase] Failed to save baubles data for " + uuid + ": " + e.getMessage());
            }
        });
    }

    // ── Torch Data ────────────────────────────────────────────────────────────

    public CompletableFuture<Map<String, Long>> loadLitTorches() {
        return scheduler.supplyAsync(() -> {
            Map<String, Long> result = new HashMap<>();
            long now = System.currentTimeMillis();
            String sql = "SELECT location_key, expires_at FROM rsv_torch_lit WHERE expires_at > ?";
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
                logger.warning("[RSVDatabase] Failed to load lit torch data: " + e.getMessage());
            }
            return result;
        });
    }

    public CompletableFuture<Void> saveLitTorches(Map<String, Long> snapshot) {
        return scheduler.runAsync(() -> {
            long now = System.currentTimeMillis();
            try (Connection conn = dataSource.getConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("DELETE FROM rsv_torch_lit");
                }
                if (!snapshot.isEmpty()) {
                    String sql = isMysql
                        ? "INSERT IGNORE INTO rsv_torch_lit (location_key, expires_at) VALUES (?, ?)"
                        : "INSERT INTO rsv_torch_lit (location_key, expires_at) VALUES (?, ?)";
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
                logger.warning("[RSVDatabase] Failed to save lit torch data: " + e.getMessage());
            }
        });
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("[RSVDatabase] Connection pool closed.");
        }
    }
}
