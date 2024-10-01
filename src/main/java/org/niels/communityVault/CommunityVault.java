package org.niels.communityVault;

import org.bukkit.Bukkit;
import org.bukkit.block.Chest;
import org.bukkit.scheduler.BukkitTask;
import org.niels.communityVault.commands.ChestCommand;
import org.niels.communityVault.commands.VaultCommand;
import org.niels.communityVault.listeners.ChestInteractListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.niels.communityVault.utils.CategoryConfig;
import org.niels.communityVault.utils.ConfigManager;
import org.niels.communityVault.utils.VaultStorage;
import org.bstats.bukkit.Metrics;


public class CommunityVault extends JavaPlugin {
    public static BukkitTask saveVault;
    public static BukkitTask saveChests;
    public static CategoryConfig categoryConfig;
    public static ConfigManager configManager;


    @Override
    public void onEnable() {

        int pluginId = 25689; // <-- Replace with the id of your plugin!
        Metrics metrics = new Metrics(this, pluginId);

        categoryConfig = new CategoryConfig(this);
        configManager = new ConfigManager(this);

        VaultStorage.loadCategories(categoryConfig);
        VaultCommand vaultCommand = new VaultCommand(this, categoryConfig);
        ChestCommand chestCommand = new ChestCommand(configManager);
        VaultStorage.loadVaultFromFile();

        ChestInteractListener.loadChestsFromFile();

        if (this.getCommand("cvault") != null) {
            this.getCommand("cvault").setExecutor(vaultCommand);
        }
        if (this.getCommand("searchvault") != null) {
            this.getCommand("searchvault").setExecutor(vaultCommand);
        }
        if (this.getCommand("buywc") != null) {
            this.getCommand("buywc").setExecutor(chestCommand);
        }
        if (this.getCommand("buydc") != null) {
            this.getCommand("buydc").setExecutor(chestCommand);
        }

        getServer().getPluginManager().registerEvents(new ChestInteractListener(this, vaultCommand), this);
        saveVault = getServer().getScheduler().runTaskTimer(this, VaultStorage::saveVaultToFile, 2000, 2000);
        saveChests = getServer().getScheduler().runTaskTimer(this, ChestInteractListener::saveChestsToFile, 2000, 2000);

        getLogger().info("CommunityVault is enabled");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTask(saveVault.getTaskId());
        getServer().getScheduler().cancelTask(saveChests.getTaskId());
        VaultStorage.saveVaultToFile();
        ChestInteractListener.saveChestsToFile();
        //configManager.saveConfig();
        getLogger().info("CommunityVault plugin has been safely shut down");
    }

}
