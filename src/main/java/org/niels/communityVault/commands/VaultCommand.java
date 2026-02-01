package org.niels.communityVault.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.niels.communityVault.CommunityVault;
import org.niels.communityVault.listeners.ChestInteractListener;
import org.niels.communityVault.utils.BackupManager;
import org.niels.communityVault.utils.CategoryConfig;
import org.niels.communityVault.utils.VaultStorage;
import org.niels.communityVault.ui.CategoryMenu;
import org.niels.communityVault.ui.VaultMenuHolder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Date;

import static org.niels.communityVault.utils.MaterialUtils.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class VaultCommand implements CommandExecutor {

    private final Plugin plugin;
    private final CategoryConfig categoryConfig;
    private final CategoryMenu categoryMenu;

    private void markVaultTransition(Player player) {
        player.setMetadata("vaultTransition", new FixedMetadataValue(plugin, true));
        Bukkit.getScheduler().runTask(plugin, () -> player.removeMetadata("vaultTransition", plugin));
    }

    private void openVaultInventory(Player player, Inventory inventory) {
        markVaultTransition(player);
        player.openInventory(inventory);
    }

    private Location getWithdrawalKey(Player player) {
        MetadataValue keyValue = player.getMetadata("withdrawalChestKey").isEmpty()
                ? null : player.getMetadata("withdrawalChestKey").get(0);
        Object rawKey = keyValue != null ? keyValue.value() : null;
        if (rawKey instanceof Location) {
            return (Location) rawKey;
        }
        return ChestInteractListener.getWithdrawalKeyForPlayer(player.getUniqueId());
    }

    public VaultCommand(Plugin plugin, CategoryConfig categoryConfig, CategoryMenu categoryMenu) {
        this.plugin = plugin;
        this.categoryConfig = categoryConfig;
        this.categoryMenu = categoryMenu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return false;
        }

        Player player = (Player) sender;

        if (label.equalsIgnoreCase("searchvault") || label.equalsIgnoreCase("sv")) {
            searchVault(player, args);
            return true;
        }
        if (label.equalsIgnoreCase("cvaultcompact")) {
            if (!player.isOp() && !player.hasPermission("communityvault.compact")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to compact the vault.");
                return true;
            }
            synchronized (VaultStorage.class) {
                VaultStorage.compactVault();
                VaultStorage.saveVaultToFile();
            }
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Vault compacted and saved.");
            return true;
        }
        if (label.equalsIgnoreCase("cvaultstatus")) {
            if (!player.isOp() && !player.hasPermission("communityvault.status")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to view status.");
                return true;
            }
            int totalStacks = VaultStorage.getVaultItems().size();
            int totalItems = VaultStorage.getTotalItemCount();
            int categories = VaultStorage.getCategoryKeys().size();
            String backupInfo = "never";
            if (BackupManager.getLastBackupTime() != null) {
                backupInfo = Date.from(BackupManager.getLastBackupTime()).toString();
            }
            String saveVaultTask = CommunityVault.saveVault != null && CommunityVault.saveVault.isSync() ? "running" : "unknown";
            String saveChestsTask = CommunityVault.saveChests != null && CommunityVault.saveChests.isSync() ? "running" : "unknown";
            boolean capacityEnabled = CommunityVault.configManager.getBoolean("maxVaultCapacityEnabled");
            int maxCapacity = CommunityVault.configManager.getInt("maxVaultCapacity");
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Stacks: " + totalStacks + ", Items: " + totalItems + ", Categories: " + categories);
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Last backup: " + backupInfo);
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Saves - vault: " + saveVaultTask + ", chests: " + saveChestsTask);
            if (capacityEnabled) {
                player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Capacity: enabled, max " + maxCapacity);
            } else {
                player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Capacity: disabled");
            }
            return true;
        }
        if (label.equalsIgnoreCase("cvaultreload")) {
            if (!player.isOp() && !player.hasPermission("communityvault.reload")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to reload.");
                return true;
            }
            CommunityVault.configManager.reloadConfig();
            categoryConfig.reloadConfig();
            VaultStorage.loadCategories(categoryConfig);
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Configuration reloaded.");
            return true;
        }

        // Open the main vault inventory with categories
        openMainVaultInventory(player, false);
        return true;
    }

    public void searchVault(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Please provide an item name to search for.");
            return;
        }

        // Join the args array to get the full search term
        String searchTerm = String.join(" ", args);

        // Get matching items from the vault based on the search term
        List<ItemStack> matchedItems = VaultStorage.getItemsByPartialName(searchTerm);

        if (matchedItems.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.RED + "No items found matching '" + searchTerm + "'.");
            player.getInventory().close();
        } else {
            // Open an inventory displaying all matched items, starting from page 1
            player.setMetadata("searchString", new FixedMetadataValue(plugin, searchTerm));
            openSearchResultsInventory(player, searchTerm, matchedItems, 1);
        }
    }

    public void searchVault(Player player, String searchString, int page) {

        // Get matching items from the vault based on the search term
        List<ItemStack> matchedItems = VaultStorage.getItemsByPartialName(searchString);

        if (matchedItems.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.RED + "No more items found matching '" + searchString + "'.");
            player.getInventory().close();
        } else {
            // Open an inventory displaying all matched items, starting from page 1
            player.setMetadata("searchString", new FixedMetadataValue(plugin, searchString));
            openSearchResultsInventory(player, searchString, matchedItems, page);
        }
    }

    public void openSearchResultsInventory(Player player, String searchTerm, List<ItemStack> items, int page) {
        // Define page size (45 items per page, reserving the last row for navigation)
        int pageSize = 45;
        int totalPages = (int) Math.ceil(items.size() / (double) pageSize);

        // Ensure page is within the valid range
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        // Calculate start and end indices for the current page
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, items.size());

        // Create the inventory for the current page
        Inventory searchInventory = Bukkit.createInventory(new VaultMenuHolder(VaultMenuHolder.Type.SEARCH, null, 1, null, page), 54,
                "Search: " + searchTerm + " (Page " + page + ")");

        // Add items for the current page
        for (int i = start; i < end; i++) {
            searchInventory.setItem(i - start, items.get(i)); // Place items in the inventory slots
        }

        // Add navigation buttons
        searchInventory.setItem(45, createNavigationItem(Material.BARRIER, "Back"));
        if (page > 1) {
            searchInventory.setItem(48, createNavigationItem(Material.ARROW, "Previous Page"));
        }
        if (page < totalPages) {
            searchInventory.setItem(50, createNavigationItem(Material.ARROW, "Next Page"));
        }

        // Open the inventory for the player
        openVaultInventory(player, searchInventory);
    }

    // Handle category clicks (including pagination)
    public void handleSearchClick(InventoryClickEvent event, boolean isValid) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        String searchString = null;
        if (player.hasMetadata("searchString") && !player.getMetadata("searchString").isEmpty()) {
            searchString = player.getMetadata("searchString").get(0).asString();
        } else {
            // Fallback: cancel if search context is missing
            event.setCancelled(true);
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.RED + "Search context missing. Reopen the search.");
            return;
        }

        // Check if the player clicked in a category inventory
        VaultMenuHolder holder = event.getView().getTopInventory().getHolder() instanceof VaultMenuHolder
                ? (VaultMenuHolder) event.getView().getTopInventory().getHolder() : null;
        if (holder != null && holder.getType() == VaultMenuHolder.Type.SEARCH) {
            int currentPage = holder.getPage();
            if (!isValid) {
                event.setCancelled(true); // Prevent item withdrawal
            }
            boolean isWithdrawSelecting = player.hasMetadata("withdrawSelect");
            if (isWithdrawSelecting && clickedItem != null && clickedItem.getType() != Material.AIR) {
                String displayName = (clickedItem.getItemMeta() != null && clickedItem.getItemMeta().hasDisplayName())
                        ? clickedItem.getItemMeta().getDisplayName() : "";
                if (!"Next Page".equals(displayName) && !"Previous Page".equals(displayName)
                        && !"Back".equals(displayName)) {
                    Location key = getWithdrawalKey(player);
                    if (key != null) {
                        ChestInteractListener.updateWithdrawalSelection(key, clickedItem.getType());
                        player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA
                                + "Withdrawal output set to " + clickedItem.getType().name() + ".");
                        player.removeMetadata("withdrawSelect", plugin);
                        boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
                        openMainVaultInventory(player, canWithdraw);
                    } else {
                        player.sendMessage(ChatColor.RED + "No withdrawal chest selected.");
                        player.removeMetadata("withdrawSelect", plugin);
                    }
                    event.setCancelled(true);
                    return;
                }
            }
            if (clickedItem != null && clickedItem.getType() == Material.BARRIER && clickedItem.getItemMeta() != null && "Back".equals(clickedItem.getItemMeta().getDisplayName())) {
                // Back button
                event.setCancelled(false);
                openMainVaultInventory(player, isValid);
                return;
            }


            if (clickedItem != null && clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta() != null && clickedItem.getItemMeta().hasDisplayName()) {
                // Determine if it's "Next Page" or "Previous Page"
                if (clickedItem.getItemMeta() != null) {
                    String displayName = clickedItem.getItemMeta().getDisplayName();
                    if (displayName.equals("Next Page")) {
                        searchVault(player, searchString, currentPage + 1);
                    } else if (displayName.equals("Previous Page")) {
                        searchVault(player, searchString, currentPage - 1);
                    }
                }
            }
            else if (clickedItem != null && clickedItem.getType() != Material.AIR && isValid) {
                // Prevent concurrent modifications and ensure vault consistency
                synchronized (VaultStorage.class) {
                    Material material = clickedItem.getType();
                    int amountToWithdraw = clickedItem.getAmount();

                    // Get the amount currently in the vault
                    int currentAmountInVault = VaultStorage.getItemCountFromVault(material);
                    //player.sendMessage("Attempting to withdraw " + amountToWithdraw + " " + material.toString() + ". Current in vault: " + currentAmountInVault);

                    if (!canFitInInventory(player, clickedItem)) {
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.RED + "You do not have space in your inventory!");
                        return;
                    }

                    if (currentAmountInVault >= amountToWithdraw) {
                        // Ensure to update the vault storage before allowing the item to be taken out
                        if(!VaultStorage.removeExactItemFromVault(clickedItem))
                        {
                            //Cancel if stack cannot be found
                            event.setCancelled(true);
                            return;
                        }

                        // Add the item to the player's inventory
                        ItemStack withdrawItem = clickedItem.clone();
                        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(withdrawItem);
                        if (!leftovers.isEmpty()) {
                            // Roll back removal if somehow inventory rejected the item
                            VaultStorage.addItemToVault(withdrawItem);
                            event.setCancelled(true);
                            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.RED + "You do not have space in your inventory!");
                            return;
                        }
                        currentAmountInVault = VaultStorage.getItemCountFromVault(material);
                        player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "You have withdrawn " + amountToWithdraw + " " + material.toString() + " from the vault.");
                    } else {
                        player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.RED + "Not enough " + material.toString() + " in the vault. Available: " + currentAmountInVault);
                    }
                }

                // Cancel the event and refresh the vault page
                event.setCancelled(true);
                searchVault(player, searchString, currentPage);
            } else {
                player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.RED + "You cannot withdraw anything because you are not looking at a withdrawal chest.");
                event.setCancelled(true); // Prevent withdrawal in view-only mode
            }
        }
    }



    public void openMainVaultInventory(Player player, boolean isValid) {
        openMainVaultInventoryPage(player, isValid, 1);
    }

    public void openMainVaultInventoryPage(Player player, boolean isValid, int page) {
        player.removeMetadata("categoryStacksParent", plugin);
        player.removeMetadata("categoryStacksParentPage", plugin);
        if (!isValid) {
            ChestInteractListener.clearWithdrawalKeyForPlayer(player.getUniqueId());
        }
        boolean isSelecting = player.hasMetadata("categorySelect") && player.getMetadata("categorySelect").get(0).asBoolean();
        String title = isSelecting ? "Select Category For Item" : "Community Vault";
        VaultMenuHolder.Type holderType = isSelecting ? VaultMenuHolder.Type.MAIN_SELECT : VaultMenuHolder.Type.MAIN;

        // Paginate categories (45 slots, last row for nav/buttons)
        List<String> categoryKeys = new ArrayList<>(VaultStorage.getCategoryKeys());
        categoryKeys.sort(Comparator.comparing(VaultStorage::getCategoryName));
        int pageSize = 45;
        int totalPages = (int) Math.ceil(categoryKeys.size() / (double) pageSize);
        if (totalPages < 1) totalPages = 1;
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        Inventory mainVaultInventory = Bukkit.createInventory(
                new VaultMenuHolder(holderType, null, 1, null, page),
                54,
                title
        );

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, categoryKeys.size());
        int index = 0;
        for (int i = start; i < end; i++) {
            String categoryKey = categoryKeys.get(i);
            String displayName = VaultStorage.getCategoryName(categoryKey);
            Material iconMaterial = VaultStorage.getCategoryIcon(categoryKey);
            mainVaultInventory.setItem(index, createCategoryItem(iconMaterial, displayName));
            index++;
        }

        if (totalPages > 1 && page > 1) {
            mainVaultInventory.setItem(45, createNavigationItem(Material.ARROW, "Previous Page"));
        }
        if (totalPages > 1 && page < totalPages) {
            mainVaultInventory.setItem(46, createNavigationItem(Material.ARROW, "Next Page"));
        }

//        // Add categories to the vault inventory
//        mainVaultInventory.setItem(0, createCategoryItem(Material.BRICKS, "Building Blocks"));      // Building Blocks
//        mainVaultInventory.setItem(1, createCategoryItem(Material.PAINTING, "Decoration Blocks"));  // Decoration Blocks
//        mainVaultInventory.setItem(2, createCategoryItem(Material.REDSTONE, "Redstone"));           // Redstone
//        mainVaultInventory.setItem(3, createCategoryItem(Material.MINECART, "Transportation"));     // Transportation
//        mainVaultInventory.setItem(4, createCategoryItem(Material.CLOCK, "Miscellaneous"));         // Miscellaneous
//        mainVaultInventory.setItem(5, createCategoryItem(Material.APPLE, "Foodstuffs"));            // Foodstuffs
//        mainVaultInventory.setItem(6, createCategoryItem(Material.DIAMOND_PICKAXE, "Tools"));       // Tools
//        mainVaultInventory.setItem(7, createCategoryItem(Material.IRON_SWORD, "Combat"));           // Combat
//        mainVaultInventory.setItem(8, createCategoryItem(Material.BREWING_STAND, "Brewing"));       // Brewing
//        mainVaultInventory.setItem(9, createCategoryItem(Material.EMERALD, "Materials"));           // Materials
//        mainVaultInventory.setItem(10, createCategoryItem(Material.CHEST, "Remaining Items"));            // All Items
//        mainVaultInventory.setItem(11, createCategoryItem(Material.END_CRYSTAL, "All Items"));            // All Items

        isSelecting = player.hasMetadata("categorySelect") && player.getMetadata("categorySelect").get(0).asBoolean();
        if(isSelecting)
        {
            mainVaultInventory.setItem(52, createNavigationItem(Material.RED_DYE, "Stop Selecting"));
            if(player.hasMetadata("categorySelectType"))
            {
                String type =  player.getMetadata("categorySelectType").get(0).asString();
                if(type != null)
                {
                    mainVaultInventory.setItem(53, createNavigationItem(Material.getMaterial(type), "Selected Item"));
                }
            }


        }
        else{
            if (hasAnyCategoryPermission(player, "communityvault.categories.view")) {
                mainVaultInventory.setItem(48, createNavigationItem(Material.WRITABLE_BOOK, "Manage Categories"));
            }
            mainVaultInventory.setItem(49, createNavigationItem(Material.END_CRYSTAL, "All items"));
            mainVaultInventory.setItem(50, createNavigationItem(Material.TRAPPED_CHEST, "Uncategorized Items"));
            if (isValid && CommunityVault.configManager.getBoolean("allowHopperWithdrawal")) {
                ItemStack hopperItem = createNavigationItem(Material.HOPPER, "Set Withdrawal Output");
                Location key = getWithdrawalKey(player);
                if (key != null) {
                    Material selected = ChestInteractListener.getWithdrawalSelection(key);
                    ItemMeta meta = hopperItem.getItemMeta();
                    if (meta != null) {
                        String outputName = selected != null ? selected.name() : "None";
                        meta.setLore(Collections.singletonList(ChatColor.YELLOW + "Output: " + outputName));
                        hopperItem.setItemMeta(meta);
                    }
                }
                mainVaultInventory.setItem(53, hopperItem);
            }
        }

        // Save the player's vault access type (withdrawal allowed or not)
        player.setMetadata("canWithdraw", new FixedMetadataValue(plugin, isValid));
        // Open the inventory for the player
        openVaultInventory(player, mainVaultInventory);
    }


    // Helper method to create a category item in the main vault inventory
    private ItemStack createCategoryItem(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            item.setItemMeta(meta);
        }
        return item;
    }

    // Handle clicks in the main vault inventory
    public void handleMainVaultClick(InventoryClickEvent event, boolean isValid) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return; // Ignore empty slots
        }

        event.setCancelled(true); // Prevent taking items
        VaultMenuHolder holder = event.getView().getTopInventory().getHolder() instanceof VaultMenuHolder
                ? (VaultMenuHolder) event.getView().getTopInventory().getHolder() : null;
        int currentPage = holder != null ? holder.getPage() : 1;
        if (clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta() != null
                && clickedItem.getItemMeta().hasDisplayName()
                && (holder != null && (holder.getType() == VaultMenuHolder.Type.MAIN
                || holder.getType() == VaultMenuHolder.Type.MAIN_SELECT))) {
            String displayName = clickedItem.getItemMeta().getDisplayName();
            boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
            if ("Next Page".equals(displayName)) {
                openMainVaultInventoryPage(player, canWithdraw, currentPage + 1);
                return;
            }
            if ("Previous Page".equals(displayName)) {
                openMainVaultInventoryPage(player, canWithdraw, currentPage - 1);
                return;
            }
        }
        String categoryDisplayName = clickedItem.getItemMeta().getDisplayName();
        String categoryKey = getCategoryKeyByDisplayName(categoryDisplayName); // Helper to retrieve the key by name
            if(clickedItem.getType() == Material.RED_DYE)
            {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if(displayName.equals("Stop Selecting"))
                {
                    player.setMetadata("categorySelect", new FixedMetadataValue(plugin, 0));
                    player.removeMetadata("categorySelectType", plugin);
                    boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
                    openMainVaultInventory(player, canWithdraw);
                    return;
                }


            }
            if(clickedItem.getType() == Material.END_CRYSTAL)
            {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if(displayName.equals("All items"))
                {
                    openCategoryInventory(player, "All items", VaultStorage.getItemsByCategory(Material.values()));
                    return;
                }


            }
            if(clickedItem.getType() == Material.TRAPPED_CHEST)
            {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if(displayName.equals("Uncategorized Items"))
                {
                    openCategoryInventory(player, "Uncategorized Items", getRemainingItems());
                    return;
                }


            }
            if (clickedItem.getType() == Material.HOPPER) {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if (displayName.equals("Set Withdrawal Output") && isValid
                        && CommunityVault.configManager.getBoolean("allowHopperWithdrawal")) {
                    player.setMetadata("withdrawSelect", new FixedMetadataValue(plugin, 1));
                    openCategoryInventory(player, "All items", VaultStorage.getItemsByCategory(Material.values()));
                    return;
                }
            }
            if (clickedItem.getType() == Material.BOOK) {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if (displayName.equals("Search Vault")) {
                    categoryMenu.startVaultSearch(player);
                    return;
                }
            }
            if (clickedItem.getType() == Material.BARRIER) {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if (displayName.equals("Clear Withdrawal Output") && isValid
                        && CommunityVault.configManager.getBoolean("allowHopperWithdrawal")) {
                    Location key = getWithdrawalKey(player);
                    if (key != null) {
                        ChestInteractListener.updateWithdrawalSelection(key, null);
                        player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA
                                + "Withdrawal output cleared.");
                        boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
                        openMainVaultInventory(player, canWithdraw);
                    } else {
                        player.sendMessage(ChatColor.RED + "No withdrawal chest selected.");
                    }
                    return;
                }
            }
            if (clickedItem.getType() == Material.WRITABLE_BOOK) {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if (displayName.equals("Manage Categories")) {
                    categoryMenu.openManager(player);
                    return;
                }
            }
            boolean isSelecting = player.hasMetadata("categorySelect") && player.getMetadata("categorySelect").get(0).asBoolean();
            if(isSelecting)
            {
                boolean canEdit = hasAnyCategoryPermission(player, "communityvault.categories.additem")
                        || player.hasMetadata("categorySelectUncategorized");
                if (!canEdit) {
                    player.sendMessage(ChatColor.RED + "You need communityvault.categories.additem to assign categories.");
                    player.setMetadata("categorySelect", new FixedMetadataValue(plugin, 0));
                    player.removeMetadata("categorySelectType", plugin);
                    player.removeMetadata("categorySelectUncategorized", plugin);
                    return;
                }
                if(player.hasMetadata("categorySelectType"))
                {
                    String type =  player.getMetadata("categorySelectType").get(0).asString();
                    String oldCategoryKey = VaultStorage.getCategoryKeyByMaterial(Material.getMaterial(type));
                    if(categoryKey == null)
                    {
                        return;
                    }
                    player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Moved item: " + type + " from category: " + VaultStorage.getCategoryKeyByMaterial(Material.getMaterial(type)) + " to: " + categoryKey );
                    if (oldCategoryKey != null) {
                        VaultStorage.removeItemFromCategory(oldCategoryKey, Material.getMaterial(type), categoryConfig);
                    }
                    VaultStorage.addItemToCategory(categoryKey, Material.getMaterial(type), categoryConfig);
                    player.setMetadata("categorySelect", new FixedMetadataValue(plugin, 0));
                    player.removeMetadata("categorySelectType", plugin);
                    player.removeMetadata("categorySelectUncategorized", plugin);
                    openCategoryInventory(player, categoryKey, VaultStorage.getItemsByCategoryKey(categoryKey).toArray(new ItemStack[0]));

                }

            }
            else if (categoryKey != null) {
                openCategoryInventory(player, categoryKey, VaultStorage.getItemsByCategoryKey(categoryKey).toArray(new ItemStack[0]));
            }
    }

    private ItemStack[] getRemainingItems()
    {
        List<Material> filteredItems = new ArrayList<>();
        for(String key : VaultStorage.getCategoryKeys())
        {
            filteredItems.addAll(VaultStorage.getMaterialsInCategory(key));
        }

        return VaultStorage.getRemainingItems(filteredItems);
    }

    private ItemStack[] getItemsForCategoryName(String categoryName) {
        if (categoryName.contains("All items")) {
            return VaultStorage.getItemsByCategory(Material.values());
        }
        if (categoryName.contains("Uncategorized Items")) {
            return getRemainingItems();
        }
        String categoryKey = getCategoryKeyByDisplayName(categoryName);
        if (categoryKey == null) {
            categoryKey = categoryName;
        }
        return VaultStorage.getItemsByCategoryKey(categoryKey).toArray(new ItemStack[0]);
    }

    private int extractPageFromTitle(String title) {
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(title);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return 1;
    }

    private String getStacksParentName(Inventory inventory) {
        if (inventory != null && inventory.getHolder() instanceof VaultMenuHolder) {
            VaultMenuHolder holder = (VaultMenuHolder) inventory.getHolder();
            if (holder.getType() == VaultMenuHolder.Type.STACKS) {
                return holder.getParentName();
            }
        }
        return null;
    }

    private int getStacksParentPage(Inventory inventory) {
        if (inventory != null && inventory.getHolder() instanceof VaultMenuHolder) {
            VaultMenuHolder holder = (VaultMenuHolder) inventory.getHolder();
            if (holder.getType() == VaultMenuHolder.Type.STACKS) {
                return holder.getParentPage();
            }
        }
        return 1;
    }

    private boolean hasAnyCategoryPermission(Player player, String... permissions) {
        if (player.isOp() || player.hasPermission("communityvault.categories")) {
            return true;
        }
        for (String permission : permissions) {
            if (player.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    // Helper method to get the category key based on its display name
    private String getCategoryKeyByDisplayName(String displayName) {
        for (String key : VaultStorage.getCategoryKeys()) {
            if (VaultStorage.getCategoryName(key).equals(displayName)) {
                return key;
            }
        }
        return null;
    }

    // Open a category inventory with pagination
    public void openCategoryInventory(Player player, String categoryName, ItemStack[] items) {
        player.removeMetadata("categoryStacksParent", plugin);
        player.removeMetadata("categoryStacksParentPage", plugin);
        openCategoryInventoryPage(player, categoryName, items, 1); // Start from page 1
    }

    public void openCategoryInventoryPage(Player player, String categoryName, ItemStack[] items, int page) {
        // Sort items alphabetically by Material name
        Arrays.sort(items, Comparator.comparing(itemStack -> itemStack.getType().name()));
        Inventory categoryInventory = Bukkit.createInventory(new VaultMenuHolder(VaultMenuHolder.Type.CATEGORY, null, 1, null, page), 54, categoryName + " (Page " + page + ")");

        Map<Material, Integer> materialCountMap = new HashMap<>();

        // Count the total amount of each material type
        for (ItemStack item : items) {
            if (item != null) {
                Material material = item.getType();
                int count = item.getAmount();
                materialCountMap.put(material, materialCountMap.getOrDefault(material, 0) + count);
            }
        }

        // Convert the Map to a List of Map.Entry objects
        List<Map.Entry<Material, Integer>> materialCountList = new ArrayList<>(materialCountMap.entrySet());

        // Sort the list by Material name
        materialCountList.sort(Comparator.comparing(entry -> entry.getKey().name()));

        // Define page size
        int pageSize = 45; // Reserve last row for navigation buttons
        int totalPages = (int) Math.ceil((double) materialCountList.size() / pageSize);

        // Ensure totalPages is at least 1
        if (totalPages < 1) totalPages = 1;

        // Ensure page is within valid range
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;


        // Calculate start and end indices for the current page
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, materialCountList.size());

        // Debugging to check the start and end indices
        //player.sendMessage("Start Index: " + start + ", End Index: " + end);

        // Display items for the current page
        for (int i = start; i < end; i++) {
            Map.Entry<Material, Integer> entry = materialCountList.get(i);
            ItemStack itemStack = new ItemStack(entry.getKey());
            ItemMeta meta = itemStack.getItemMeta();

            // Add the total count of the material as a tooltip (lore)
            List<String> lore = new ArrayList<>();
            lore.add("Total: " + entry.getValue());
            meta.setLore(lore);
            itemStack.setItemMeta(meta);

            // Add the item to the inventory
            categoryInventory.setItem(i - start, itemStack);
        }

        // Add back button
        categoryInventory.setItem(45, createNavigationItem(Material.BARRIER, "Back"));
        boolean isSelecting = player.hasMetadata("categorySelect") && player.getMetadata("categorySelect").get(0).asBoolean();
        boolean isWithdrawSelecting = player.hasMetadata("withdrawSelect");
        if(isSelecting)
        {
            categoryInventory.setItem(52, createNavigationItem(Material.RED_DYE, "Stop Selecting"));
            String type =  player.getMetadata("categorySelectType").get(0).asString();
            categoryInventory.setItem(53, createNavigationItem(Material.getMaterial(type), "Selected Item"));
        }
        else if (isWithdrawSelecting) {
            categoryInventory.setItem(52, createNavigationItem(Material.BARRIER, "Clear Withdrawal Output"));
            categoryInventory.setItem(53, createNavigationItem(Material.BOOK, "Search Vault"));
        }
        else
        {
            if (categoryName.equals("Uncategorized Items")) {
                categoryInventory.setItem(52, createNavigationItem(Material.EMERALD, "Assign Category"));
            } else if (!categoryName.equals("All items")) {
                if (hasAnyCategoryPermission(player, "communityvault.categories.additem")) {
                    categoryInventory.setItem(51, createNavigationItem(Material.EMERALD, "Add To Category"));
                }
                categoryInventory.setItem(52, createNavigationItem(Material.RED_DYE, "Remove From Category"));
            }
        }
        // Add category select button

        // Add pagination buttons
        if (totalPages > 1 && page > 1) {
            categoryInventory.setItem(48, createNavigationItem(Material.ARROW, "Previous Page"));
        }
        if (totalPages > 1 && page < totalPages) {
            categoryInventory.setItem(50, createNavigationItem(Material.ARROW, "Next Page"));
        }

        // Open the inventory for the player
        openVaultInventory(player, categoryInventory);
    }





    // Helper method to create navigation buttons for paging
    private ItemStack createNavigationItem(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(Collections.singletonList(ChatColor.YELLOW + "Click to navigate"));
            item.setItemMeta(meta);
        }
        return item;
    }


    // Handle category clicks (including pagination)
    public void handleCategoryClick(InventoryClickEvent event, boolean isValid) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        String title = event.getView().getTitle();

        // Check if the player clicked in a category inventory
        VaultMenuHolder holder = event.getView().getTopInventory().getHolder() instanceof VaultMenuHolder
                ? (VaultMenuHolder) event.getView().getTopInventory().getHolder() : null;
        if (holder != null && holder.getType() == VaultMenuHolder.Type.CATEGORY) {
            event.setCancelled(true); // Prevent item withdrawal
            boolean isWithdrawSelecting = player.hasMetadata("withdrawSelect");

            if (isWithdrawSelecting && clickedItem != null && clickedItem.getType() != Material.AIR) {
                String displayName = (clickedItem.getItemMeta() != null && clickedItem.getItemMeta().hasDisplayName())
                        ? clickedItem.getItemMeta().getDisplayName() : "";
                if (!"Next Page".equals(displayName) && !"Previous Page".equals(displayName)
                        && !"Assign Category".equals(displayName) && !"Stop Selecting".equals(displayName)
                        && !"Remove From Category".equals(displayName) && !"Add To Category".equals(displayName)
                        && !"Back".equals(displayName) && !"Search Vault".equals(displayName)
                        && !"Clear Withdrawal Output".equals(displayName)) {
                    Location key = getWithdrawalKey(player);
                    if (key != null) {
                        ChestInteractListener.updateWithdrawalSelection(key, clickedItem.getType());
                        player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA
                                + "Withdrawal output set to " + clickedItem.getType().name() + ".");
                        player.removeMetadata("withdrawSelect", plugin);
                        boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
                        openMainVaultInventory(player, canWithdraw);
                    } else {
                        player.sendMessage(ChatColor.RED + "No withdrawal chest selected.");
                        player.removeMetadata("withdrawSelect", plugin);
                    }
                    return;
                }
            }

            if (isWithdrawSelecting && clickedItem != null && clickedItem.getType() == Material.BOOK) {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if ("Search Vault".equals(displayName)) {
                    categoryMenu.startVaultSearch(player);
                    return;
                }
            }
            if (isWithdrawSelecting && clickedItem != null && clickedItem.getType() == Material.BARRIER) {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if ("Clear Withdrawal Output".equals(displayName)) {
                    Location key = getWithdrawalKey(player);
                    if (key != null) {
                        ChestInteractListener.updateWithdrawalSelection(key, null);
                        player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA
                                + "Withdrawal output cleared.");
                        player.removeMetadata("withdrawSelect", plugin);
                        boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
                        openMainVaultInventory(player, canWithdraw);
                    } else {
                        player.sendMessage(ChatColor.RED + "No withdrawal chest selected.");
                        player.removeMetadata("withdrawSelect", plugin);
                    }
                    return;
                }
            }

            if (clickedItem != null && (clickedItem.getType() == Material.ARROW || clickedItem.getType() == Material.EMERALD || clickedItem.getType() == Material.RED_DYE)) {
                String displayName = (clickedItem.getItemMeta() != null && clickedItem.getItemMeta().hasDisplayName()) ? clickedItem.getItemMeta().getDisplayName() : "";
                if(!"Next Page".equals(displayName) && !"Previous Page".equals(displayName) && !"Assign Category".equals(displayName) && !"Stop Selecting".equals(displayName) && !"Remove From Category".equals(displayName) && !"Add To Category".equals(displayName) && clickedItem.getType() != Material.AIR && (player.hasMetadata("categorySelect") && player.getMetadata("categorySelect").get(0).asBoolean()) )
                {
                    String itemType = clickedItem.getType().toString();

                    player.setMetadata("categorySelectType", new FixedMetadataValue(plugin, itemType));
                    boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
                    openMainVaultInventory(player, canWithdraw);
                }
                else if (!"Next Page".equals(displayName) && !"Previous Page".equals(displayName) && !"Assign Category".equals(displayName) && !"Stop Selecting".equals(displayName) && !"Remove From Category".equals(displayName) && !"Add To Category".equals(displayName) && clickedItem.getType() != Material.AIR) {
                    Material clickedMaterial = clickedItem.getType();
                    String categoryName = title.split(" \\(Page")[0];
                    int parentPage = extractPageFromTitle(title);
                    player.setMetadata("categoryStacksParent", new FixedMetadataValue(plugin, categoryName));
                    player.setMetadata("categoryStacksParentPage", new FixedMetadataValue(plugin, parentPage));
                    openMaterialStacksInventory(player, clickedMaterial, categoryName, parentPage); // Open material stacks
                }

            }
            if (clickedItem != null && clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta() != null && clickedItem.getItemMeta().hasDisplayName()) {
                // Determine if it's "Next Page" or "Previous Page"
                String categoryName = title.split(" \\(Page")[0]; // Extract category name
                String displayName = clickedItem.getItemMeta().getDisplayName();

                int currentPage = holder.getPage();
                ItemStack[] items = getItemsForCategoryName(categoryName);




                if (clickedItem.getItemMeta() != null && items != null) {

                    if (displayName.equals("Next Page")) {
                        boolean isSelecting = player.hasMetadata("categorySelect") && player.getMetadata("categorySelect").get(0).asBoolean();
                        if(isSelecting)
                        {
                            player.setMetadata("categorySelect", new FixedMetadataValue(plugin, 0));
                        }
                        openCategoryInventoryPage(player, categoryName, items, currentPage + 1);
                    } else if (displayName.equals("Previous Page")) {
                        boolean isSelecting = player.hasMetadata("categorySelect") && player.getMetadata("categorySelect").get(0).asBoolean();
                        if(isSelecting)
                        {
                            player.setMetadata("categorySelect", new FixedMetadataValue(plugin, 0));
                        }
                        openCategoryInventoryPage(player, categoryName, items, currentPage - 1);
                    }
                }
            } else if (clickedItem != null && clickedItem.getType() == Material.BARRIER && clickedItem.getItemMeta() != null && "Back".equals(clickedItem.getItemMeta().getDisplayName())) {
                // Back button
                player.removeMetadata("categorySelectUncategorized", plugin);
                player.removeMetadata("categoryRemoveTarget", plugin);
                player.removeMetadata("withdrawSelect", plugin);
                boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
                Bukkit.getScheduler().runTask(plugin, () -> openMainVaultInventory(player, canWithdraw));
            }
            else if(clickedItem != null && clickedItem.getType() == Material.RED_DYE)
            {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if(displayName.equals("Stop Selecting"))
                {
                    player.setMetadata("categorySelect", new FixedMetadataValue(plugin, 0));
                    player.removeMetadata("categorySelectType", plugin);
                    player.removeMetadata("categorySelectUncategorized", plugin);
                    ItemStack categorySelectInProgress = createCategoryItem(Material.EMERALD, "Assign Category");
                    event.getInventory().setItem(52, categorySelectInProgress);
                }
                if(displayName.equals("Remove From Category"))
                {
                    if (!hasAnyCategoryPermission(player, "communityvault.categories.removeitem")) {
                        player.sendMessage(ChatColor.RED + "You need communityvault.categories.removeitem to remove items from categories.");
                        return;
                    }
                    String categoryName = title.split(" \\(Page")[0];
                    String categoryKey = getCategoryKeyByDisplayName(categoryName);
                    if (categoryKey == null) {
                        categoryKey = categoryName;
                    }
                    if (categoryKey != null) {
                        player.setMetadata("categoryRemoveTarget", new FixedMetadataValue(plugin, categoryKey));
                        player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.YELLOW + "Click an item to remove it from this category.");
                    }
                }
            }
            else if(clickedItem != null && clickedItem.getType() == Material.EMERALD)
            {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if(displayName.equals("Assign Category"))
                {
                    player.setMetadata("categorySelect", new FixedMetadataValue(plugin, 1));
                    player.setMetadata("categorySelectUncategorized", new FixedMetadataValue(plugin, 1));
                    ItemStack categorySelectInProgress = createCategoryItem(Material.RED_DYE, "Stop Selecting");
                    event.getInventory().setItem(52, categorySelectInProgress);
                }
                if(displayName.equals("Add To Category"))
                {
                    if (!hasAnyCategoryPermission(player, "communityvault.categories.additem")) {
                        player.sendMessage(ChatColor.RED + "You need communityvault.categories.additem to add items.");
                        return;
                    }
                    String categoryName = title.split(" \\(Page")[0];
                    String categoryKey = getCategoryKeyByDisplayName(categoryName);
                    if (categoryKey == null) {
                        categoryKey = categoryName;
                    }
                    categoryMenu.openAddItemsForCategory(player, categoryKey);
                }
            }
            //If categoryselect is enabled and item is clicked
            else if(clickedItem != null && clickedItem.getType() != Material.AIR && (player.hasMetadata("categorySelect") && player.getMetadata("categorySelect").get(0).asBoolean()))
            {
                boolean canEdit = hasAnyCategoryPermission(player, "communityvault.categories.additem")
                        || player.hasMetadata("categorySelectUncategorized");
                if (!canEdit) {
                    player.sendMessage(ChatColor.RED + "You need communityvault.categories.additem to assign categories.");
                    player.setMetadata("categorySelect", new FixedMetadataValue(plugin, 0));
                    player.removeMetadata("categorySelectType", plugin);
                    player.removeMetadata("categorySelectUncategorized", plugin);
                    return;
                }
                String itemType = clickedItem.getType().toString();

                player.setMetadata("categorySelectType", new FixedMetadataValue(plugin, itemType));
                boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
                openMainVaultInventory(player, canWithdraw);
            }
            else if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                if (player.hasMetadata("categoryRemoveTarget")) {
                    if (!hasAnyCategoryPermission(player, "communityvault.categories.removeitem")) {
                        player.sendMessage(ChatColor.RED + "You need communityvault.categories.removeitem to remove items.");
                        player.removeMetadata("categoryRemoveTarget", plugin);
                        return;
                    }
                    String categoryKey = player.getMetadata("categoryRemoveTarget").get(0).asString();
                    if (VaultStorage.removeItemFromCategory(categoryKey, clickedItem.getType(), categoryConfig)) {
                        player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Removed " + clickedItem.getType() + " from category.");
                    }
                    player.removeMetadata("categoryRemoveTarget", plugin);
                    String categoryName = title.split(" \\(Page")[0];
                    ItemStack[] items = VaultStorage.getItemsByCategoryKey(categoryName).toArray(new ItemStack[0]);
                    openCategoryInventoryPage(player, categoryName, items, extractPageFromTitle(title));
                    return;
                }
                Material clickedMaterial = clickedItem.getType();
                String categoryName = title.split(" \\(Page")[0];
                int parentPage = extractPageFromTitle(title);
                player.setMetadata("categoryStacksParent", new FixedMetadataValue(plugin, categoryName));
                player.setMetadata("categoryStacksParentPage", new FixedMetadataValue(plugin, parentPage));
                openMaterialStacksInventory(player, clickedMaterial, categoryName, parentPage); // Open material stacks
            }
        }
    }

    // Open a new inventory showing all stacks of a specific material
    public void openMaterialStacksInventory(Player player, Material material) {
        openMaterialStacksInventoryPage(player, material, 1, null, 1);
    }

    public void openMaterialStacksInventory(Player player, Material material, String parentName, int parentPage) {
        openMaterialStacksInventoryPage(player, material, 1, parentName, parentPage);
    }

    public void openMaterialStacksInventoryPage(Player player, Material material, int page, String parentName, int parentPage) {
        List<ItemStack> materialStacks = VaultStorage.getItemsByMaterial(material);
        // Check if there are any items to display
        if (materialStacks.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.RED + "No items of this material in the vault.");
        }
        Collections.sort(materialStacks, Comparator.comparing(itemStack -> itemStack.getType().name()));


        // Define page size (45 items per page, reserving bottom row for navigation)
        int pageSize = 45;
        int totalPages = (int) Math.ceil(materialStacks.size() / (double) pageSize);

        // Calculate start and end indices for the current page
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, materialStacks.size());

        // Create the inventory for the current page
        Inventory materialInventory = Bukkit.createInventory(
                new VaultMenuHolder(VaultMenuHolder.Type.STACKS, parentName, parentPage, material.name(), page),
                54,
                material.name() + " (Page " + page + ")");

        // Add the stacks of the current material to the inventory
        for (int i = start; i < end; i++) {
            materialInventory.setItem(i - start, materialStacks.get(i)); // Use `i - start` to place correctly in inventory slots
        }

        // Add back button
        materialInventory.setItem(45, createNavigationItem(Material.BARRIER, "Back"));

        // Add pagination buttons
        if (page > 1) {
            materialInventory.setItem(48, createNavigationItem(Material.ARROW, "Previous Page"));
        }
        if (page < totalPages) {
            materialInventory.setItem(50, createNavigationItem(Material.ARROW, "Next Page"));
        }

        // Open the inventory for the player
        openVaultInventory(player, materialInventory);
    }


    // Handle category clicks (including pagination)
    public void handleStacksClick(InventoryClickEvent event, boolean isValid) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        // Check if the player clicked in a category inventory
        VaultMenuHolder holder = event.getView().getTopInventory().getHolder() instanceof VaultMenuHolder
                ? (VaultMenuHolder) event.getView().getTopInventory().getHolder() : null;
        if (holder != null && holder.getType() == VaultMenuHolder.Type.STACKS) {
            int currentPage = holder.getPage();
            if (!isValid) {
                event.setCancelled(true); // Prevent item withdrawal
            }
            boolean isWithdrawSelecting = player.hasMetadata("withdrawSelect");
            if (clickedItem != null && clickedItem.getType() == Material.BARRIER && clickedItem.getItemMeta() != null && "Back".equals(clickedItem.getItemMeta().getDisplayName())) {
                // Back button
                event.setCancelled(false);
                String parentName = getStacksParentName(event.getView().getTopInventory());
                int parentPage = getStacksParentPage(event.getView().getTopInventory());
                if (parentName != null) {
                    ItemStack[] items = getItemsForCategoryName(parentName);
                    player.removeMetadata("categoryStacksParent", plugin);
                    player.removeMetadata("categoryStacksParentPage", plugin);
                    openCategoryInventoryPage(player, parentName, items, parentPage);
                    return;
                }
                boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
                openMainVaultInventory(player, canWithdraw);
                return;
            }

            if (isWithdrawSelecting && clickedItem != null && clickedItem.getType() != Material.AIR) {
                String displayName = (clickedItem.getItemMeta() != null && clickedItem.getItemMeta().hasDisplayName())
                        ? clickedItem.getItemMeta().getDisplayName() : "";
                if (!"Next Page".equals(displayName) && !"Previous Page".equals(displayName)
                        && !"Back".equals(displayName)) {
                    Location key = getWithdrawalKey(player);
                    if (key != null) {
                        ChestInteractListener.updateWithdrawalSelection(key, clickedItem.getType());
                        player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA
                                + "Withdrawal output set to " + clickedItem.getType().name() + ".");
                        player.removeMetadata("withdrawSelect", plugin);
                        boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
                        openMainVaultInventory(player, canWithdraw);
                    } else {
                        player.sendMessage(ChatColor.RED + "No withdrawal chest selected.");
                        player.removeMetadata("withdrawSelect", plugin);
                    }
                    event.setCancelled(true);
                    return;
                }
            }


            if (clickedItem != null && clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta() != null && clickedItem.getItemMeta().hasDisplayName()) {
                // Determine if it's "Next Page" or "Previous Page"
                if (clickedItem.getItemMeta() != null) {
                    String displayName = clickedItem.getItemMeta().getDisplayName();
                    if (displayName.equals("Next Page")) {
                        openMaterialStacksInventoryPage(player, event.getInventory().getItem(0).getType(), currentPage + 1,
                                getStacksParentName(event.getView().getTopInventory()),
                                getStacksParentPage(event.getView().getTopInventory()));
                    } else if (displayName.equals("Previous Page")) {
                        openMaterialStacksInventoryPage(player, event.getInventory().getItem(0).getType(), currentPage - 1,
                                getStacksParentName(event.getView().getTopInventory()),
                                getStacksParentPage(event.getView().getTopInventory()));
                    }
                }
            }
             else if (clickedItem != null && clickedItem.getType() != Material.AIR && isValid) {
                // Prevent concurrent modifications and ensure vault consistency
                synchronized (VaultStorage.class) {
                    Material material = clickedItem.getType();
                    int amountToWithdraw = clickedItem.getAmount();

                    // Get the amount currently in the vault
                    int currentAmountInVault = VaultStorage.getItemCountFromVault(material);
                    //player.sendMessage("Attempting to withdraw " + amountToWithdraw + " " + material.toString() + ". Current in vault: " + currentAmountInVault);

                    if (!canFitInInventory(player, clickedItem)) {
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.RED + "You do not have space in your inventory!");
                        return;
                    }

                    if (currentAmountInVault >= amountToWithdraw) {
                        // Ensure to update the vault storage before allowing the item to be taken out
                        if(!VaultStorage.removeExactItemFromVault(clickedItem))
                        {
                            //Cancel if stack cannot be found
                            event.setCancelled(true);
                            return;
                        }

                        // Add the item to the player's inventory
                        ItemStack withdrawItem = clickedItem.clone();
                        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(withdrawItem);
                        if (!leftovers.isEmpty()) {
                            // Roll back removal if inventory rejected the item
                            VaultStorage.addItemToVault(withdrawItem);
                            event.setCancelled(true);
                            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.RED + "You do not have space in your inventory!");
                            return;
                        }
                        currentAmountInVault = VaultStorage.getItemCountFromVault(material);
                        player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "You have withdrawn " + amountToWithdraw + " " + material.toString() + " from the vault.");


                    } else {
                        player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.RED + "Not enough " + material.toString() + " in the vault. Available: " + currentAmountInVault);
                    }
                }

                // Cancel the event and refresh the vault page
                event.setCancelled(true);
                openMaterialStacksInventoryPage(player, event.getInventory().getItem(0).getType(), currentPage,
                        getStacksParentName(event.getView().getTopInventory()),
                        getStacksParentPage(event.getView().getTopInventory()));
            } else {
                event.setCancelled(true); // Prevent withdrawal in view-only mode
                player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.RED + "You cannot withdraw items from the vault.");
            }
        }
    }




    // Vault view should be view-only (cannot withdraw items)
    public void handleVaultClick(InventoryClickEvent event) {
        // Holder routing is handled elsewhere
    }
    private Material[] concat(Material[] first, Material[] second) {
        // Use a Set to automatically remove duplicates
        Set<Material> combinedSet = new HashSet<>(Arrays.asList(first)); // Add all elements from the first array
        combinedSet.addAll(Arrays.asList(second)); // Add all elements from the second array (duplicates will be ignored)

        // Convert the Set back to an array
        return combinedSet.toArray(new Material[0]);
    }

    // Check if the player's inventory can fully fit the item (respecting stack sizes and similarity)
    private boolean canFitInInventory(Player player, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return true;
        }

        int remaining = itemStack.getAmount();
        int maxStack = itemStack.getMaxStackSize();
        ItemStack[] contents = player.getInventory().getStorageContents();

        for (ItemStack slot : contents) {
            if (slot == null || slot.getType() == Material.AIR) {
                remaining -= maxStack;
            } else if (slot.isSimilar(itemStack) && slot.getAmount() < slot.getMaxStackSize()) {
                remaining -= (slot.getMaxStackSize() - slot.getAmount());
            }

            if (remaining <= 0) {
                return true;
            }
        }

        return remaining <= 0;
    }

}



