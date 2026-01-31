package org.niels.communityVault.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.plugin.Plugin;
import org.niels.communityVault.CommunityVault;
import org.niels.communityVault.utils.BackupManager;
import org.niels.communityVault.utils.CategoryConfig;
import org.niels.communityVault.utils.VaultStorage;

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

    public VaultCommand(Plugin plugin, CategoryConfig categoryConfig) {
        this.plugin = plugin;
        this.categoryConfig = categoryConfig;
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
        Inventory searchInventory = Bukkit.createInventory(null, 54, "Search: " + searchTerm + " (Page " + page + ") (Search)");

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
        player.openInventory(searchInventory);
    }

    // Handle category clicks (including pagination)
    public void handleSearchClick(InventoryClickEvent event, boolean isValid) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        String title = event.getView().getTitle();
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
        if (title.contains("(Page") && title.contains("(Search)") ) {
            Pattern pattern = Pattern.compile("\\d+");
            Matcher matcher = pattern.matcher(title);
            int currentPage = 1;
            // If a match is found, parse it as the current page
            if (matcher.find()) {
                currentPage = Integer.parseInt(matcher.group());
            }
            if (!isValid) {
                event.setCancelled(true); // Prevent item withdrawal
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
        boolean isSelecting = player.hasMetadata("categorySelect") && player.getMetadata("categorySelect").get(0).asBoolean();
        Inventory mainVaultInventory;
        if(isSelecting)
        {
            mainVaultInventory = Bukkit.createInventory(null, 54, "Community Vault (Select Category)");
        }
        else {
            mainVaultInventory = Bukkit.createInventory(null, 54, "Community Vault");
        }

        // Add categories dynamically from VaultStorage
        int index = 0;
        for (String categoryKey : VaultStorage.getCategoryKeys()) {
            String displayName = VaultStorage.getCategoryName(categoryKey);
            Material iconMaterial = VaultStorage.getCategoryIcon(categoryKey); // Get the icon for the category
            mainVaultInventory.setItem(index, createCategoryItem(iconMaterial, displayName));
            index++;
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
            mainVaultInventory.setItem(49, createNavigationItem(Material.END_CRYSTAL, "All items"));
            mainVaultInventory.setItem(50, createNavigationItem(Material.TRAPPED_CHEST, "Remaining Items"));
        }

        // Save the player's vault access type (withdrawal allowed or not)
        player.setMetadata("canWithdraw", new FixedMetadataValue(plugin, isValid));
        // Open the inventory for the player
        player.openInventory(mainVaultInventory);
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

        String title = event.getView().getTitle();

        // Check if the player clicked in the main vault inventory
        if (title.contains("Community Vault")) {
            event.setCancelled(true); // Prevent taking items
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
                if(displayName.equals("Remaining Items"))
                {
                    openCategoryInventory(player, "Remaining Items", getRemainingItems());
                    return;
                }


            }
            boolean isSelecting = player.hasMetadata("categorySelect") && player.getMetadata("categorySelect").get(0).asBoolean();
            if(isSelecting)
            {
                if(player.hasMetadata("categorySelectType"))
                {
                    String type =  player.getMetadata("categorySelectType").get(0).asString();
                    String oldCategoryKey = VaultStorage.getCategoryKeyByMaterial(Material.getMaterial(type));
                    if(categoryKey == null)
                    {
                        return;
                    }
                    player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Moved item: " + type + " from category: " + VaultStorage.getCategoryKeyByMaterial(Material.getMaterial(type)) + " to: " + categoryKey );
                    VaultStorage.removeItemFromCategory(oldCategoryKey, Material.getMaterial(type), categoryConfig);
                    VaultStorage.addItemToCategory(categoryKey, Material.getMaterial(type), categoryConfig);
                    player.setMetadata("categorySelect", new FixedMetadataValue(plugin, 0));
                    player.removeMetadata("categorySelectType", plugin);
                    openCategoryInventory(player, categoryKey, VaultStorage.getItemsByCategoryKey(categoryKey).toArray(new ItemStack[0]));

                }

            }
            else if (categoryKey != null) {
                openCategoryInventory(player, categoryKey, VaultStorage.getItemsByCategoryKey(categoryKey).toArray(new ItemStack[0]));
            }

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
        openCategoryInventoryPage(player, categoryName, items, 1); // Start from page 1
    }

    public void openCategoryInventoryPage(Player player, String categoryName, ItemStack[] items, int page) {
        // Sort items alphabetically by Material name
        Arrays.sort(items, Comparator.comparing(itemStack -> itemStack.getType().name()));
        Inventory categoryInventory = Bukkit.createInventory(null, 54, categoryName + " (Page " + page + ")");

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
        if(isSelecting)
        {
            categoryInventory.setItem(52, createNavigationItem(Material.RED_DYE, "Stop Selecting"));
            String type =  player.getMetadata("categorySelectType").get(0).asString();
            categoryInventory.setItem(53, createNavigationItem(Material.getMaterial(type), "Selected Item"));
        }
        else
        {
            categoryInventory.setItem(52, createNavigationItem(Material.EMERALD, "Select Category"));
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
        player.openInventory(categoryInventory);
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
        if (title.contains("(Page")) {
            event.setCancelled(true); // Prevent item withdrawal

            if (clickedItem != null && (clickedItem.getType() == Material.ARROW || clickedItem.getType() == Material.EMERALD || clickedItem.getType() == Material.RED_DYE)) {
                String displayName = (clickedItem.getItemMeta() != null && clickedItem.getItemMeta().hasDisplayName()) ? clickedItem.getItemMeta().getDisplayName() : "";
                if(!"Next Page".equals(displayName) && !"Previous Page".equals(displayName) && !"Select Category".equals(displayName) && !"Stop Selecting".equals(displayName) && clickedItem.getType() != Material.AIR && (player.hasMetadata("categorySelect") && player.getMetadata("categorySelect").get(0).asBoolean()) )
                {
                    String itemType = clickedItem.getType().toString();

                    player.setMetadata("categorySelectType", new FixedMetadataValue(plugin, itemType));
                    boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
                    openMainVaultInventory(player, canWithdraw);
                }
                else if (!"Next Page".equals(displayName) && !"Previous Page".equals(displayName) && !"Select Category".equals(displayName) && !"Stop Selecting".equals(displayName) && clickedItem.getType() != Material.AIR) {
                    Material clickedMaterial = clickedItem.getType();
                    openMaterialStacksInventory(player, clickedMaterial); // Open material stacks
                }

            }
            if (clickedItem != null && clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta() != null && clickedItem.getItemMeta().hasDisplayName()) {
                // Determine if it's "Next Page" or "Previous Page"
                String categoryName = title.split(" \\(Page")[0]; // Extract category name
                String displayName = clickedItem.getItemMeta().getDisplayName();

                Pattern pattern = Pattern.compile("\\d+");
                Matcher matcher = pattern.matcher(title);
                int currentPage = 1;
                // If a match is found, parse it as the current page
                if (matcher.find()) {
                    currentPage = Integer.parseInt(matcher.group());
                }
                ItemStack[] items = null;
                if(categoryName.contains("All items"))
                {
                    items = VaultStorage.getItemsByCategory(Material.values());
                }
                else if(categoryName.contains("Remaining Items"))
                {
                    items = getRemainingItems();
                }
                else
                {
                    //player.sendMessage("cpage"+ Integer.toString(currentPage));
                     items = VaultStorage.getItemsByCategoryKey(categoryName).toArray(new ItemStack[0]);
                }




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
                boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
                openMainVaultInventory(player, canWithdraw);
            }
            else if(clickedItem != null && clickedItem.getType() == Material.EMERALD)
            {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if(displayName.equals("Select Category"))
                {
                    player.setMetadata("categorySelect", new FixedMetadataValue(plugin, 1));
                    ItemStack categorySelectInProgress = createCategoryItem(Material.RED_DYE, "Stop Selecting");
                    event.getInventory().setItem(52, categorySelectInProgress);
                }
            }
            else if(clickedItem != null && clickedItem.getType() == Material.RED_DYE)
            {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                if(displayName.equals("Stop Selecting"))
                {
                    player.setMetadata("categorySelect", new FixedMetadataValue(plugin, 0));
                    player.removeMetadata("categorySelectType", plugin);
                    ItemStack categorySelectInProgress = createCategoryItem(Material.EMERALD, "Select Category");
                    event.getInventory().setItem(52, categorySelectInProgress);
                }
            }
            //If categoryselect is enabled and item is clicked
            else if(clickedItem != null && clickedItem.getType() != Material.AIR && (player.hasMetadata("categorySelect") && player.getMetadata("categorySelect").get(0).asBoolean()))
            {
                String itemType = clickedItem.getType().toString();

                player.setMetadata("categorySelectType", new FixedMetadataValue(plugin, itemType));
                boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
                openMainVaultInventory(player, canWithdraw);
            }
            else if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                Material clickedMaterial = clickedItem.getType();
                openMaterialStacksInventory(player, clickedMaterial); // Open material stacks
            }
        }
    }

    // Open a new inventory showing all stacks of a specific material
    public void openMaterialStacksInventory(Player player, Material material) {
        openMaterialStacksInventoryPage(player, material, 1);
    }

    public void openMaterialStacksInventoryPage(Player player, Material material, int page) {
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
        Inventory materialInventory = Bukkit.createInventory(null, 54, material.name() + " (Page " + page + ") (Stacks)");

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
        player.openInventory(materialInventory);
    }


    // Handle category clicks (including pagination)
    public void handleStacksClick(InventoryClickEvent event, boolean isValid) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        String title = event.getView().getTitle();

        // Check if the player clicked in a category inventory
        if (title.contains("(Page")) {
            Pattern pattern = Pattern.compile("\\d+");
            Matcher matcher = pattern.matcher(title);
            int currentPage = 1;
            // If a match is found, parse it as the current page
            if (matcher.find()) {
                currentPage = Integer.parseInt(matcher.group());
            }
            if (!isValid) {
                event.setCancelled(true); // Prevent item withdrawal
            }
            if (clickedItem != null && clickedItem.getType() == Material.BARRIER && clickedItem.getItemMeta() != null && "Back".equals(clickedItem.getItemMeta().getDisplayName())) {
                // Back button
                event.setCancelled(false);
                boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
                openMainVaultInventory(player, canWithdraw);
                return;
            }


            if (clickedItem != null && clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta() != null && clickedItem.getItemMeta().hasDisplayName()) {
                // Determine if it's "Next Page" or "Previous Page"
                String categoryName = title.split(" \\(Page")[0]; // Extract category name
                if (clickedItem.getItemMeta() != null) {
                    String displayName = clickedItem.getItemMeta().getDisplayName();
                    if (displayName.equals("Next Page")) {
                        openMaterialStacksInventoryPage(player, event.getInventory().getItem(0).getType(), currentPage + 1);
                    } else if (displayName.equals("Previous Page")) {
                        openMaterialStacksInventoryPage(player, event.getInventory().getItem(0).getType(), currentPage - 1);
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
                openMaterialStacksInventoryPage(player, event.getInventory().getItem(0).getType(), currentPage);
            } else {
                event.setCancelled(true); // Prevent withdrawal in view-only mode
                player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.RED + "You cannot withdraw items from the vault.");
            }
        }
    }




    // Vault view should be view-only (cannot withdraw items)
    public void handleVaultClick(InventoryClickEvent event) {
        // Ensure all clicks in the vault inventory are canceled
        if (event.getView().getTitle().equalsIgnoreCase("Community Vault")) {
            event.setCancelled(true); // Prevent item withdrawal or manipulation
        }
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



