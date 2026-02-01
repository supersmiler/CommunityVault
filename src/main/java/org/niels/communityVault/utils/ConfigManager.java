package org.niels.communityVault.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        loadDefaultConfig();
    }

    // Load and set default configuration values
    private void loadDefaultConfig() {
        // Add default values
        config.addDefault("diamondCostDepositChest", 5);
        config.addDefault("diamondCostWithdrawalChest", 5);
        config.addDefault("maxVaultCapacityEnabled", true);
        config.addDefault("maxVaultCapacity", 1000000); // Default high limit
        config.addDefault("allowHopperWithdrawal", true);
        config.addDefault("allowHopperDeposit", true);
        config.addDefault("allowDropperDeposit", true);
        config.addDefault("allowCrafterDeposit", true);

        // Copy defaults to config file if they don't exist
        config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    // Reload configuration from file
    public void reloadConfig() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    // Save current configuration to file
    public void saveConfig() {
        plugin.saveConfig();
    }

    // Generic get methods
    public boolean getBoolean(String path) {
        return config.getBoolean(path);
    }

    public int getInt(String path) {
        return config.getInt(path);
    }

    public double getDouble(String path) {
        return config.getDouble(path);
    }

    public String getString(String path) {
        return config.getString(path);
    }

    // Generic set methods
    public void setBoolean(String path, boolean value) {
        config.set(path, value);
        saveConfig();
    }

    public void setInt(String path, int value) {
        config.set(path, value);
        saveConfig();
    }

    public void setDouble(String path, double value) {
        config.set(path, value);
        saveConfig();
    }

    public void setString(String path, String value) {
        config.set(path, value);
        saveConfig();
    }

    // Example specific getters for common settings
    public boolean isFeatureEnabled() {
        return getBoolean("enableFeature");
    }

    public int getMaxPlayers() {
        return getInt("maxPlayers");
    }

    public String getWelcomeMessage() {
        return getString("welcomeMessage");
    }

    public double getSpawnX() {
        return getDouble("spawnLocation.x");
    }

    public double getSpawnY() {
        return getDouble("spawnLocation.y");
    }

    public double getSpawnZ() {
        return getDouble("spawnLocation.z");
    }

    public int getCooldownSeconds() {
        return getInt("cooldownSeconds");
    }
}
