package org.niels.communityVault.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class CategoryConfig {
    private final Plugin plugin;
    private File configFile;
    private FileConfiguration config;

    public CategoryConfig(Plugin plugin) {
        this.plugin = plugin;
        createConfigFile();
    }

    private void createConfigFile() {
        configFile = new File("plugins/CommunityVault/categories.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs(); // Create directories if they don't exist

            // Create the default categories.yml file
            config = YamlConfiguration.loadConfiguration(configFile);
            addDefaultCategories();
            saveConfig(); // Save the file after adding the default values
        } else {
            config = YamlConfiguration.loadConfiguration(configFile); // Load existing config if it exists
        }
    }

    private void addDefaultCategories() {
        config.set("categories.building_blocks.name", "Building Blocks");
        config.set("categories.building_blocks.icon", "BRICKS");
        config.set("categories.building_blocks.items", Arrays.asList("STONE", "GRANITE", "BRICKS"));

        config.set("categories.decoration_blocks.name", "Decoration Blocks");
        config.set("categories.decoration_blocks.icon", "PAINTING");
        config.set("categories.decoration_blocks.items", Arrays.asList("PAINTING", "ITEM_FRAME"));

        config.set("categories.redstone.name", "Redstone");
        config.set("categories.redstone.icon", "REDSTONE");
        config.set("categories.redstone.items", Arrays.asList("REDSTONE", "REDSTONE_TORCH"));
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void saveConfig() {
        try {
            config.save(configFile); // Save the config file to disk
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save categories.yml!");
            e.printStackTrace();
        }
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile); // Reload the configuration from file
    }
}
