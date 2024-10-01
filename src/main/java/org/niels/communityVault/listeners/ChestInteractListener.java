package org.niels.communityVault.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.niels.communityVault.commands.VaultCommand;
import org.niels.communityVault.utils.VaultStorage;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ChestInteractListener implements Listener {

    private final Plugin plugin;
    private final VaultCommand vaultCommand; // Add VaultCommand here
    private static Set<Location> validWithdrawalChests = new HashSet<>(); // Tracks legit withdrawal chests
    private static Set<Location> validDepositChests = new HashSet<>(); // Tracks legit deposit chests
    private static File chestFile;
    private static FileConfiguration chestConfig;


    public ChestInteractListener(Plugin plugin, VaultCommand vaultCommand) {
        this.plugin = plugin;
        this.vaultCommand = vaultCommand; // Initialize VaultCommand
    }

    public static void saveChestsToFile() {
        if (chestFile == null) {
            chestFile = new File("plugins/communityvault/chests.yml");
        }

        chestConfig = YamlConfiguration.loadConfiguration(chestFile);

        // Save deposit chests
        List<Map<String, Object>> dChestList = new ArrayList<>();
        for (Location loc : validDepositChests) {
            dChestList.add(serializeLocation(loc));  // Convert Location to a Map
        }
        chestConfig.set("dchests", dChestList);

        // Save withdrawal chests
        List<Map<String, Object>> wChestList = new ArrayList<>();
        for (Location loc : validWithdrawalChests) {
            wChestList.add(serializeLocation(loc));  // Convert Location to a Map
        }
        chestConfig.set("wchests", wChestList);

        try {
            chestConfig.save(chestFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Helper method to convert a Location to a Map
    private static Map<String, Object> serializeLocation(Location location) {
        Map<String, Object> locMap = new HashMap<>();
        locMap.put("world", location.getWorld().getName());
        locMap.put("x", location.getX());
        locMap.put("y", location.getY());
        locMap.put("z", location.getZ());
        return locMap;
    }


    public static void loadChestsFromFile() {
        chestFile = new File("plugins/communityvault/chests.yml");

        if (!chestFile.getParentFile().exists()) {
            chestFile.getParentFile().mkdirs();  // Create the directory if it doesn't exist
        }

        if (!chestFile.exists()) {
            try {
                chestFile.createNewFile(); // Create the file if it doesn't exist
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        chestConfig = YamlConfiguration.loadConfiguration(chestFile);

        List<?> loadedDItems = chestConfig.getList("dchests");
        List<?> loadedWItems = chestConfig.getList("wchests");

        if (loadedDItems != null) {
            validDepositChests.clear();
            for (Object item : loadedDItems) {
                if (item instanceof Map) {
                    validDepositChests.add(deserializeLocation((Map<String, Object>) item));
                }
            }
        }

        if (loadedWItems != null) {
            validWithdrawalChests.clear();
            for (Object item : loadedWItems) {
                if (item instanceof Map) {
                    validWithdrawalChests.add(deserializeLocation((Map<String, Object>) item));
                }
            }
        }
    }

    // Helper method to convert a Map to a Location
    private static Location deserializeLocation(Map<String, Object> locMap) {
        String worldName = (String) locMap.get("world");
        double x = (double) locMap.get("x");
        double y = (double) locMap.get("y");
        double z = (double) locMap.get("z");
        return new Location(Bukkit.getWorld(worldName), x, y, z);
    }


    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (block.getType() == Material.CHEST) {
            Chest chest = (Chest) block.getState();
            ItemStack chestItem = event.getItemInHand();

            if (chestItem.hasItemMeta()) {
                ItemMeta meta = chestItem.getItemMeta();
                if(meta.getLore() != null)
                {
                    // Check if it's a Withdrawal Chest
                    if (meta.hasLore() && meta.getLore().toString().contains("WithdrawalChest")) {
                        validWithdrawalChests.add(block.getLocation()); // Mark the chest as a valid withdrawal chest
                        player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Withdrawal Chest placed!");
                    }
                    // Check if it's a Deposit Chest
                    if (meta.hasLore() && meta.getLore().toString().contains("DepositChest")) {
                        validDepositChests.add(block.getLocation()); // Mark the chest as a valid deposit chest
                        player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Deposit Chest placed!");
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();

        if (validWithdrawalChests.contains(loc) || validDepositChests.contains(loc)) {
            event.setDropItems(false); // Prevent the chest from dropping an item
            validWithdrawalChests.remove(loc);
            validDepositChests.remove(loc);
            event.getPlayer().sendMessage("A special chest was destroyed.");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        //player.sendMessage(event.getAction().toString());

        // Cancel shift-clicking or double-clicking entirely
        if ((event.isShiftClick()) &&
                (title.contains("Community Vault") || title.contains("(Page") || title.contains("(Stacks)"))) {
            event.setCancelled(true); // Prevent shift-clicking and double-clicking
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.YELLOW + "Shift-clicking and double-clicking are disabled in the Community Vault.");
            return;
        }

        // Cancel clicking in the player's own inventory (lower half of the screen)
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory()) &&
                (title.equalsIgnoreCase("Community Vault") || title.contains("(Page") || title.contains("(Stacks)"))) {
            event.setCancelled(true); // Prevent interacting with own inventory
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.YELLOW + "You cannot move items in your inventory while in the Community Vault.");
            return;
        }

        // Handle valid chest interactions (allow picking from withdrawal chest)
        if (title.equalsIgnoreCase("Community Vault") || title.equalsIgnoreCase("Community Vault (Select Category)")) {
            boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
            vaultCommand.handleMainVaultClick(event, canWithdraw); // Allow item withdrawal if accessed via withdrawal chest
        }

        // Handle category inventory clicks (pagination)
        if (title.contains("(Page") && !title.contains("(Stacks)") && !title.contains("(Search)")) {
            boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
            vaultCommand.handleCategoryClick(event, canWithdraw); // Allow item withdrawal if accessed via withdrawal chest
        }

        // Handle category inventory clicks (pagination)
        if (title.contains("(Page") && title.contains("(Stacks)")) {
            boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
            vaultCommand.handleStacksClick(event, canWithdraw); // Allow item withdrawal if accessed via withdrawal chest
        }

        // Handle category inventory clicks (pagination)
        if (title.contains("(Page") && title.contains("(Search)")) {
                Block block = getTargetBlock(player, 5);

                if(block != null && block.getType() != Material.AIR)
                {
                    Location chestLocation = getTargetBlock(player, 5).getLocation();
                    if (validWithdrawalChests.contains(chestLocation)) {
                        vaultCommand.handleSearchClick(event, true); // Allow item withdrawal if accessed via withdrawal chest
                    }
                    else
                    {
                        vaultCommand.handleSearchClick(event, false);
                    }
                }
                else if(block == null)
                {
                    vaultCommand.handleSearchClick(event, false);
                }
        }
    }

    public static Block getTargetBlock(Player player, int range) {
        Block targetBlock = player.getTargetBlockExact(range);  // Use range for max distance
        if(targetBlock != null)
        {
            if (targetBlock.getType() != Material.AIR) {
                return targetBlock;
            } else {
                return null;  // Return null if no valid block within range is found
            }
        }
        return null;
    }
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Detect if player is opening a deposit chest
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            if (event.getClickedBlock().getType() == Material.CHEST) {
                Chest chest = (Chest) event.getClickedBlock().getState();
                Location chestLocation = chest.getBlock().getLocation();
                Player player = event.getPlayer();
                if (validWithdrawalChests.contains(chestLocation)) {
                    player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Withdrawal Chest.");
                    // Handle withdrawal logic (open inventory, allow item withdrawal, etc.)
                    event.setCancelled(true);
                    vaultCommand.openMainVaultInventory(player, true);
                } else if (validDepositChests.contains(chestLocation)) {
                    player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA +  "Deposit Chest.");
                    event.getPlayer().sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Items placed here will be sent to the community vault.");
                    // Handle deposit logic (move items from chest to vault, etc.)
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Get the inventory that was closed
        Inventory inventory = event.getInventory();
        Player player = (Player) event.getPlayer();

        // Check if the inventory holder is a Chest
        if (event.getView().getTopInventory().getHolder() instanceof Chest) {
            Chest chest = (Chest) event.getView().getTopInventory().getHolder();
            Location chestLocation = chest.getBlock().getLocation();



            // Check if it's a Deposit Chest
            if (validDepositChests.contains(chestLocation)) {
                // Move items to the vault
                for (ItemStack item : inventory.getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        VaultStorage.addItemToVault(item); // Add item to vault
                        inventory.remove(item); // Remove item from the chest
                    }
                }
                player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Items Deposited!");
            }

            // Optional: You could handle withdrawal logic for withdrawal chests similarly
            // Example:
            if (validWithdrawalChests.contains(chestLocation)) {
                // Additional logic if needed for Withdrawal Chests
            }
        }
    }
}
