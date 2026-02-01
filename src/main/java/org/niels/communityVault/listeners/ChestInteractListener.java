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
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.niels.communityVault.commands.VaultCommand;
import org.niels.communityVault.CommunityVault;
import org.niels.communityVault.ui.VaultMenuHolder;
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
            chestFile = new File("plugins/CommunityVault/chests.yml");
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
        chestFile = new File("plugins/CommunityVault/chests.yml");

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


    private boolean isRegisteredDepositChest(Location location) {
        if (validDepositChests.contains(location)) return true;
        
        Block block = location.getBlock();
        if (block.getType() == Material.CHEST) {
            Chest chest = (Chest) block.getState();
            Inventory inventory = chest.getInventory();
            if (inventory instanceof DoubleChestInventory) {
                DoubleChest doubleChest = (DoubleChest) inventory.getHolder();
                if (doubleChest != null) {
                    Location leftLoc = ((Chest) doubleChest.getLeftSide()).getBlock().getLocation();
                    Location rightLoc = ((Chest) doubleChest.getRightSide()).getBlock().getLocation();
                    return validDepositChests.contains(leftLoc) || validDepositChests.contains(rightLoc);
                }
            }
        }
        return false;
    }

    private boolean isRegisteredWithdrawalChest(Location location) {
        if (validWithdrawalChests.contains(location)) return true;

        Block block = location.getBlock();
        if (block.getType() == Material.CHEST) {
            Chest chest = (Chest) block.getState();
            Inventory inventory = chest.getInventory();
            if (inventory instanceof DoubleChestInventory) {
                DoubleChest doubleChest = (DoubleChest) inventory.getHolder();
                if (doubleChest != null) {
                    Location leftLoc = ((Chest) doubleChest.getLeftSide()).getBlock().getLocation();
                    Location rightLoc = ((Chest) doubleChest.getRightSide()).getBlock().getLocation();
                    return validWithdrawalChests.contains(leftLoc) || validWithdrawalChests.contains(rightLoc);
                }
            }
        }
        return false;
    }

    private boolean isRegisteredDepositHolder(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        if (inventory.getHolder() instanceof Chest) {
            Chest chest = (Chest) inventory.getHolder();
            return isRegisteredDepositChest(chest.getBlock().getLocation());
        }
        if (inventory instanceof DoubleChestInventory) {
            DoubleChest doubleChest = (DoubleChest) inventory.getHolder();
            if (doubleChest == null) {
                return false;
            }
            Location leftLoc = ((Chest) doubleChest.getLeftSide()).getBlock().getLocation();
            Location rightLoc = ((Chest) doubleChest.getRightSide()).getBlock().getLocation();
            return validDepositChests.contains(leftLoc) || validDepositChests.contains(rightLoc);
        }
        return false;
    }

    private Location getRegisteredDepositLocation(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        if (inventory.getHolder() instanceof Chest) {
            Chest chest = (Chest) inventory.getHolder();
            Location loc = chest.getBlock().getLocation();
            return isRegisteredDepositChest(loc) ? loc : null;
        }
        if (inventory instanceof DoubleChestInventory) {
            DoubleChest doubleChest = (DoubleChest) inventory.getHolder();
            if (doubleChest == null) {
                return null;
            }
            Location leftLoc = ((Chest) doubleChest.getLeftSide()).getBlock().getLocation();
            if (validDepositChests.contains(leftLoc)) {
                return leftLoc;
            }
            Location rightLoc = ((Chest) doubleChest.getRightSide()).getBlock().getLocation();
            if (validDepositChests.contains(rightLoc)) {
                return rightLoc;
            }
        }
        return null;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (block.getType() == Material.CHEST) {
            Chest chest = (Chest) block.getState();
            ItemStack chestItem = event.getItemInHand();
            boolean isDepositChestItem = false;
            boolean isWithdrawalChestItem = false;

            if (chestItem.hasItemMeta()) {
                ItemMeta meta = chestItem.getItemMeta();
                if (meta != null && meta.hasLore()) {
                    String lore = meta.getLore().toString();
                    isDepositChestItem = lore.contains("DepositChest");
                    isWithdrawalChestItem = lore.contains("WithdrawalChest");
                }
            }

            boolean adjacentDeposit = false;
            boolean adjacentNonDeposit = false;
            boolean adjacentWithdrawal = false;
            boolean adjacentNonWithdrawal = false;
            Block[] adjacentBlocks = new Block[] {
                    block.getRelative(1, 0, 0),
                    block.getRelative(-1, 0, 0),
                    block.getRelative(0, 0, 1),
                    block.getRelative(0, 0, -1)
            };
            for (Block adjacent : adjacentBlocks) {
                if (adjacent.getType() != Material.CHEST) {
                    continue;
                }
                if (isRegisteredDepositChest(adjacent.getLocation())) {
                    adjacentDeposit = true;
                } else {
                    adjacentNonDeposit = true;
                }
                if (isRegisteredWithdrawalChest(adjacent.getLocation())) {
                    adjacentWithdrawal = true;
                } else {
                    adjacentNonWithdrawal = true;
                }
            }

            if (isDepositChestItem && adjacentNonDeposit) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Deposit chests can only pair with other deposit chests.");
                return;
            }

            if (!isDepositChestItem && adjacentDeposit) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You must place a deposit chest to expand a deposit chest.");
                return;
            }

            if (isWithdrawalChestItem && adjacentNonWithdrawal) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Withdrawal chests can only pair with other withdrawal chests.");
                return;
            }

            if (!isWithdrawalChestItem && adjacentWithdrawal) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You must place a withdrawal chest to expand a withdrawal chest.");
                return;
            }

            if (isWithdrawalChestItem) {
                validWithdrawalChests.add(block.getLocation()); // Mark the chest as a valid withdrawal chest
                player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Withdrawal Chest placed!");
            }
            if (isDepositChestItem) {
                validDepositChests.add(block.getLocation()); // Mark the chest as a valid deposit chest
                player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Deposit Chest placed!");
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();

        if (isRegisteredWithdrawalChest(loc) || isRegisteredDepositChest(loc)) {
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

        VaultMenuHolder holder = event.getView().getTopInventory().getHolder() instanceof VaultMenuHolder
                ? (VaultMenuHolder) event.getView().getTopInventory().getHolder() : null;
        if (holder != null) {
            switch (holder.getType()) {
                case MAIN:
                case MAIN_SELECT:
                case CATEGORY:
                case STACKS:
                case SEARCH:
                    break;
                default:
                    return;
            }
        }

        // Cancel shift-clicking or double-clicking entirely
        if ((event.isShiftClick()) && holder != null) {
            event.setCancelled(true); // Prevent shift-clicking and double-clicking
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.YELLOW + "Shift-clicking and double-clicking are disabled in the Community Vault.");
            return;
        }

        // Cancel clicking in the player's own inventory (lower half of the screen)
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory()) && holder != null) {
            event.setCancelled(true); // Prevent interacting with own inventory
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.YELLOW + "You cannot move items in your inventory while in the Community Vault.");
            return;
        }

        // Handle valid chest interactions (allow picking from withdrawal chest)
        if (holder != null && (holder.getType() == VaultMenuHolder.Type.MAIN || holder.getType() == VaultMenuHolder.Type.MAIN_SELECT)) {
            boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
            vaultCommand.handleMainVaultClick(event, canWithdraw); // Allow item withdrawal if accessed via withdrawal chest
        }

        // Handle category inventory clicks (pagination)
        if (holder != null && holder.getType() == VaultMenuHolder.Type.CATEGORY) {
            boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
            vaultCommand.handleCategoryClick(event, canWithdraw); // Allow item withdrawal if accessed via withdrawal chest
        }

        // Handle category inventory clicks (pagination)
        if (holder != null && holder.getType() == VaultMenuHolder.Type.STACKS) {
            boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
            vaultCommand.handleStacksClick(event, canWithdraw); // Allow item withdrawal if accessed via withdrawal chest
        }

        // Handle category inventory clicks (pagination)
        if (holder != null && holder.getType() == VaultMenuHolder.Type.SEARCH) {
                Block block = getTargetBlock(player, 5);

                if(block != null && block.getType() != Material.AIR)
                {
                    Location chestLocation = getTargetBlock(player, 5).getLocation();
                    if (isRegisteredWithdrawalChest(chestLocation)) {
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
                if (isRegisteredWithdrawalChest(chestLocation)) {
                    //player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Withdrawal Chest.");
                    // Handle withdrawal logic (open inventory, allow item withdrawal, etc.)
                    event.setCancelled(true);
                    vaultCommand.openMainVaultInventory(player, true);
                } else if (isRegisteredDepositChest(chestLocation)) {
                    //player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA +  "Deposit Chest.");
                    //event.getPlayer().sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Items placed here will be sent to the community vault.");
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
        String title = event.getView().getTitle();

        VaultMenuHolder holder = event.getView().getTopInventory().getHolder() instanceof VaultMenuHolder
                ? (VaultMenuHolder) event.getView().getTopInventory().getHolder() : null;
        if (holder != null) {
            boolean selecting = player.hasMetadata("categorySelect") && player.getMetadata("categorySelect").get(0).asBoolean();
            if (!selecting) {
                player.removeMetadata("categorySelect", plugin);
                player.removeMetadata("categorySelectType", plugin);
                player.removeMetadata("categorySelectUncategorized", plugin);
            }
            player.removeMetadata("categoryRemoveTarget", plugin);
        }

        // Check if the inventory holder is a Chest
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory.getHolder() instanceof Chest || topInventory instanceof DoubleChestInventory) {
            Location registeredDeposit = getRegisteredDepositLocation(topInventory);
            if (registeredDeposit != null) {
                // Move items to the vault
                int totalItems = 0;
                int totalAdded = 0;
                for (int i = 0; i < inventory.getSize(); i++) {
                    ItemStack item = inventory.getItem(i);
                    if (item != null && item.getType() != Material.AIR) {
                        totalItems += item.getAmount();
                        int added = VaultStorage.addItemToVault(item); // Add item to vault
                        totalAdded += added;
                        if (added >= item.getAmount()) {
                            inventory.setItem(i, null); // Remove item from the chest
                        } else if (added > 0) {
                            item.setAmount(item.getAmount() - added);
                        }
                    }
                }
                if (totalItems > 0) {
                    boolean limitEnabled = CommunityVault.configManager.getBoolean("maxVaultCapacityEnabled");
                    if (limitEnabled && totalAdded < totalItems) {
                        if (totalAdded == 0) {
                            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.RED + "Vault is full. No items were deposited.");
                        } else {
                            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.YELLOW +
                                    "Vault is full. Deposited " + totalAdded + "/" + totalItems + " items.");
                        }
                    } else {
                        player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Items Deposited!");
                    }
                }
            }

            // Optional: You could handle withdrawal logic for withdrawal chests similarly
            // Example:
            if (topInventory.getHolder() instanceof Chest) {
                Chest chest = (Chest) topInventory.getHolder();
                Location chestLocation = chest.getBlock().getLocation();
                if (isRegisteredWithdrawalChest(chestLocation)) {
                    // Additional logic if needed for Withdrawal Chests
                }
            } else if (topInventory instanceof DoubleChestInventory) {
                DoubleChest doubleChest = (DoubleChest) topInventory.getHolder();
                if (doubleChest != null) {
                    Location leftLoc = ((Chest) doubleChest.getLeftSide()).getBlock().getLocation();
                    Location rightLoc = ((Chest) doubleChest.getRightSide()).getBlock().getLocation();
                    if (validWithdrawalChests.contains(leftLoc) || validWithdrawalChests.contains(rightLoc)) {
                        // Additional logic if needed for Withdrawal Chests
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Inventory destination = event.getDestination();
        if (destination == null) {
            return;
        }

        if (!(destination.getHolder() instanceof Chest || destination instanceof DoubleChestInventory)) {
            return;
        }
        if (!isRegisteredDepositHolder(destination)) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        // Check vault capacity — cancel the transfer so the item stays in the source block
        boolean limitEnabled = CommunityVault.configManager.getBoolean("maxVaultCapacityEnabled");
        if (limitEnabled) {
            int maxCapacity = CommunityVault.configManager.getInt("maxVaultCapacity");
            if (VaultStorage.getTotalItemCount() + item.getAmount() > maxCapacity) {
                event.setCancelled(true);
                return;
            }
        }

        InventoryType sourceType = event.getSource().getType();

        if (sourceType == InventoryType.HOPPER) {
            // Hoppers: setting item to AIR lets the hopper decrement its slot normally
            // while nothing actually enters the chest
            VaultStorage.addItemToVault(item);
            event.setItem(new ItemStack(Material.AIR));

        } else if (sourceType == InventoryType.DROPPER) {
            // Droppers: cancelling prevents the dropper from removing the item,
            // so we cancel, add to vault, and manually remove from the dropper
            event.setCancelled(true);
            int added = VaultStorage.addItemToVault(item);
            if (added > 0) {
                ItemStack toRemove = item.clone();
                toRemove.setAmount(added);
                event.getSource().removeItem(toRemove);
            }

        } else if (sourceType == InventoryType.CRAFTER) {
            // Crafters: do not cancel (cancelling causes a drop), instead consume
            // the moved item by swapping it to AIR and store it in the vault.
            VaultStorage.addItemToVault(item);
            event.setItem(new ItemStack(Material.AIR));

        } else {
            // Unknown source type — cancel to be safe and avoid dupes
            event.setCancelled(true);
        }
    }
}
