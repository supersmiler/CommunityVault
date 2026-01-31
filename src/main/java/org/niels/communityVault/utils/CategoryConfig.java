package org.niels.communityVault.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        applyDefaultCategories(config);
    }

    private void applyDefaultCategories(FileConfiguration config) {
        addOrReplaceCategory(config, "Building_Blocks", "Building Blocks", "BRICKS",
                Arrays.asList("STONE", "COBBLESTONE", "BRICKS", "SAND", "SANDSTONE", "GRANITE", "DIORITE", "ANDESITE"));
        addOrReplaceCategory(config, "Decoration_Blocks", "Decoration Blocks", "PAINTING",
                Arrays.asList("PAINTING", "ITEM_FRAME", "ARMOR_STAND", "FLOWER_POT", "CAMPFIRE"));
        addOrReplaceCategory(config, "Redstone", "Redstone", "REDSTONE",
                Arrays.asList("REDSTONE", "REDSTONE_TORCH", "REPEATER", "COMPARATOR", "PISTON", "STICKY_PISTON"));
        addOrReplaceCategory(config, "Ores", "Ores", "IRON_ORE",
                Arrays.asList("IRON_ORE", "GOLD_ORE", "COAL_ORE", "COPPER_ORE", "DIAMOND_ORE", "REDSTONE_ORE"));
        addOrReplaceCategory(config, "Wood", "Wood", "OAK_LOG",
                Arrays.asList("OAK_LOG", "OAK_PLANKS", "OAK_STAIRS", "OAK_SLAB", "BIRCH_LOG", "SPRUCE_LOG"));
        addOrReplaceCategory(config, "Plants", "Plants", "OAK_SAPLING",
                Arrays.asList("OAK_SAPLING", "DANDELION", "POPPY", "FERN", "LILY_PAD"));
        addOrReplaceCategory(config, "Tools", "Tools", "DIAMOND_PICKAXE",
                Arrays.asList("DIAMOND_PICKAXE", "IRON_PICKAXE", "IRON_AXE", "SHEARS", "FLINT_AND_STEEL"));
        addOrReplaceCategory(config, "Weapons", "Weapons", "DIAMOND_SWORD",
                Arrays.asList("DIAMOND_SWORD", "IRON_SWORD", "BOW", "CROSSBOW", "STONE_SWORD"));
        addOrReplaceCategory(config, "Armor", "Armor", "DIAMOND_CHESTPLATE",
                Arrays.asList("DIAMOND_CHESTPLATE", "DIAMOND_HELMET", "IRON_CHESTPLATE", "IRON_HELMET", "LEATHER_CHESTPLATE"));
        addOrReplaceCategory(config, "Potions", "Potions", "POTION",
                Arrays.asList("POTION", "SPLASH_POTION", "GLASS_BOTTLE", "GHAST_TEAR"));
        addOrReplaceCategory(config, "Foodstuffs", "Foodstuffs", "APPLE",
                Arrays.asList("APPLE", "BREAD", "COOKED_BEEF", "CARROT", "PUMPKIN_PIE"));
        addOrReplaceCategory(config, "Transportation", "Transportation", "MINECART",
                Arrays.asList("MINECART", "OAK_BOAT", "ELYTRA", "CHEST_MINECART"));
        addOrReplaceCategory(config, "Miscellaneous", "Miscellaneous", "CLOCK",
                Arrays.asList("CLOCK", "COMPASS", "NAME_TAG", "SADDLE", "MAP"));
        addOrReplaceCategory(config, "Nether", "Nether", "NETHERRACK",
                Arrays.asList("NETHERRACK", "NETHER_BRICKS", "SOUL_SAND", "GLOWSTONE", "QUARTZ"));
        addOrReplaceCategory(config, "End", "End", "END_STONE",
                Arrays.asList("END_STONE", "PURPUR_BLOCK", "SHULKER_BOX", "SHULKER_SHELL"));
        addOrReplaceCategory(config, "Minerals", "Minerals", "IRON_INGOT",
                Arrays.asList("IRON_INGOT", "GOLD_INGOT", "DIAMOND", "EMERALD", "COPPER_INGOT"));
        addOrReplaceCategory(config, "Farming", "Farming", "WHEAT",
                Arrays.asList("WHEAT", "WHEAT_SEEDS", "POTATO", "CARROT", "SUGAR_CANE"));
        addOrReplaceCategory(config, "Enchantments", "Enchantments", "ENCHANTING_TABLE",
                Arrays.asList("ENCHANTED_BOOK", "ENCHANTING_TABLE", "BOOK", "BOOKSHELF"));
        addOrReplaceCategory(config, "Music_Discs", "Music Discs", "MUSIC_DISC_CAT",
                Arrays.asList("MUSIC_DISC_CAT", "MUSIC_DISC_13", "MUSIC_DISC_STAL", "JUKEBOX"));
        addOrReplaceCategory(config, "Dyes", "Dyes", "INK_SAC",
                Arrays.asList("INK_SAC", "RED_DYE", "BLUE_DYE", "GREEN_DYE", "YELLOW_DYE"));
        addOrReplaceCategory(config, "Wool", "Wool", "WHITE_WOOL",
                Arrays.asList("WHITE_WOOL", "RED_WOOL", "BLUE_WOOL", "BLACK_WOOL", "YELLOW_WOOL"));
        addOrReplaceCategory(config, "Glass", "Glass", "GLASS",
                Arrays.asList("GLASS", "GLASS_PANE", "WHITE_STAINED_GLASS", "RED_STAINED_GLASS", "BLUE_STAINED_GLASS"));
        addOrReplaceCategory(config, "Terracotta", "Terracotta", "TERRACOTTA",
                Arrays.asList("TERRACOTTA", "WHITE_TERRACOTTA", "RED_TERRACOTTA", "ORANGE_TERRACOTTA"));
        addOrReplaceCategory(config, "Rails", "Rails", "RAIL",
                Arrays.asList("RAIL", "POWERED_RAIL", "DETECTOR_RAIL", "MINECART"));
        addOrReplaceCategory(config, "Fireworks", "Fireworks", "FIREWORK_ROCKET",
                Arrays.asList("FIREWORK_ROCKET"));
        addOrReplaceCategory(config, "Decoration_Lighting", "Decoration Lighting", "TORCH",
                Arrays.asList("TORCH", "LANTERN", "END_ROD", "CANDLE"));
        addOrReplaceCategory(config, "Crafting", "Crafting", "CRAFTING_TABLE",
                Arrays.asList("CRAFTING_TABLE", "CHEST", "FURNACE", "ANVIL", "BARREL"));
        addOrReplaceCategory(config, "Mob_Drops", "Mob Drops", "ROTTEN_FLESH",
                Arrays.asList("ROTTEN_FLESH", "BONE", "GUNPOWDER", "STRING", "ENDER_PEARL"));
    }

    private void addOrReplaceCategory(FileConfiguration config, String key, String name, String icon, List<String> items) {
        String basePath = "categories." + key;
        if (!config.contains(basePath)) {
            config.set(basePath + ".name", name);
            config.set(basePath + ".icon", icon);
            config.set(basePath + ".items", items);
        }
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
