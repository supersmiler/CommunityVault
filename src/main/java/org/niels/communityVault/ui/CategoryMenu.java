package org.niels.communityVault.ui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.niels.communityVault.commands.VaultCommand;
import org.niels.communityVault.utils.CategoryConfig;
import org.niels.communityVault.utils.VaultStorage;
import org.niels.communityVault.ui.VaultMenuHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CategoryMenu implements Listener {

    private static final String MANAGER_TITLE = "Category Manager";
    private static final String EDIT_TITLE_PREFIX = "Edit Category: ";
    private static final String ICON_TITLE_PREFIX = "Pick Icon: ";
    private static final String DELETE_TITLE_PREFIX = "Delete Category: ";
    private static final String ITEMS_TITLE_PREFIX = "Category Items: ";
    private static final String ADD_TITLE_PREFIX = "Add Items: ";

    private final JavaPlugin plugin;
    private final CategoryConfig categoryConfig;
    private VaultCommand vaultCommand;

    private final Map<UUID, PendingInput> pendingInputs = new HashMap<>();
    private final Map<UUID, String> iconSearch = new HashMap<>();
    private final Map<UUID, String> itemSearch = new HashMap<>();
    private final Map<UUID, String> addSearch = new HashMap<>();
    private final Map<UUID, String> editingCategory = new HashMap<>();

    public CategoryMenu(JavaPlugin plugin, CategoryConfig categoryConfig) {
        this.plugin = plugin;
        this.categoryConfig = categoryConfig;
    }

    public void setVaultCommand(VaultCommand vaultCommand) {
        this.vaultCommand = vaultCommand;
    }

    public void openManager(Player player) {
        openManager(player, 1);
    }

    private void openManager(Player player, int page) {
        if (!hasAnyPermission(player, "communityvault.categories.view")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to manage categories.");
            return;
        }

        List<String> keys = new ArrayList<>(VaultStorage.getCategoryKeys());
        keys.sort(Comparator.comparing(VaultStorage::getCategoryName));
        int pageSize = 45;
        int totalPages = Math.max(1, (int) Math.ceil(keys.size() / (double) pageSize));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        Inventory inventory = Bukkit.createInventory(new VaultMenuHolder(VaultMenuHolder.Type.CATEGORY_MANAGER), 54,
                MANAGER_TITLE + " (Page " + page + ")");
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, keys.size());

        for (int i = start; i < end; i++) {
            String key = keys.get(i);
            Material icon = VaultStorage.getCategoryIcon(key);
            String name = VaultStorage.getCategoryName(key);
            ItemStack item = new ItemStack(icon);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + name);
                meta.setLore(Arrays.asList(
                        ChatColor.DARK_GRAY + "key:" + key,
                        ChatColor.YELLOW + "Click to edit"
                ));
                item.setItemMeta(meta);
            }
            inventory.setItem(i - start, item);
        }

        inventory.setItem(45, navigationItem(Material.BARRIER, "Back"));
        if (hasAnyPermission(player, "communityvault.categories.create")) {
            inventory.setItem(49, navigationItem(Material.EMERALD_BLOCK, "Create Category"));
        } else {
            inventory.setItem(49, navigationItem(Material.BARRIER, "Create Category (No Permission)"));
        }
        if (page > 1) {
            inventory.setItem(48, navigationItem(Material.ARROW, "Previous Page"));
        }
        if (page < totalPages) {
            inventory.setItem(50, navigationItem(Material.ARROW, "Next Page"));
        }

        player.openInventory(inventory);
    }

    private void openEditor(Player player, String key) {
        if (!hasAnyPermission(player, "communityvault.categories.view")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to manage categories.");
            return;
        }

        editingCategory.put(player.getUniqueId(), key);
        String name = VaultStorage.getCategoryName(key);
        Inventory inventory = Bukkit.createInventory(new VaultMenuHolder(VaultMenuHolder.Type.CATEGORY_EDITOR), 27,
                EDIT_TITLE_PREFIX + name);

        inventory.setItem(10, navigationItem(Material.NAME_TAG,
                hasAnyPermission(player, "communityvault.categories.rename") ? "Rename" : "Rename (No Permission)"));
        inventory.setItem(12, navigationItem(Material.ITEM_FRAME,
                hasAnyPermission(player, "communityvault.categories.icon") ? "Change Icon" : "Change Icon (No Permission)"));
        inventory.setItem(14, navigationItem(Material.CHEST,
                hasAnyPermission(player, "communityvault.categories.additem", "communityvault.categories.removeitem")
                        ? "Edit Items" : "Edit Items (No Permission)"));
        inventory.setItem(16, navigationItem(Material.BARRIER,
                hasAnyPermission(player, "communityvault.categories.delete") ? "Delete Category" : "Delete Category (No Permission)"));
        inventory.setItem(22, navigationItem(Material.ARROW, "Back"));

        player.openInventory(inventory);
    }

    private void openIconPicker(Player player, String key, int page) {
        if (!hasAnyPermission(player, "communityvault.categories.icon")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to manage categories.");
            return;
        }

        editingCategory.put(player.getUniqueId(), key);
        String search = iconSearch.getOrDefault(player.getUniqueId(), "");
        List<Material> materials = getFilteredMaterials(search);
        int pageSize = 45;
        int totalPages = Math.max(1, (int) Math.ceil(materials.size() / (double) pageSize));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        String name = VaultStorage.getCategoryName(key);
        Inventory inventory = Bukkit.createInventory(new VaultMenuHolder(VaultMenuHolder.Type.ICON_PICKER), 54,
                ICON_TITLE_PREFIX + name + " (Page " + page + ")");
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, materials.size());

        for (int i = start; i < end; i++) {
            Material material = materials.get(i);
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + material.name());
                meta.setLore(Collections.singletonList(ChatColor.YELLOW + "Click to set icon"));
                item.setItemMeta(meta);
            }
            inventory.setItem(i - start, item);
        }

        inventory.setItem(45, navigationItem(Material.BOOK, "Search Icons"));
        if (!search.isBlank()) {
            inventory.setItem(46, navigationItem(Material.BARRIER, "Clear Search"));
        }
        inventory.setItem(49, navigationItem(Material.BARRIER, "Back"));
        if (page > 1) {
            inventory.setItem(48, navigationItem(Material.ARROW, "Previous Page"));
        }
        if (page < totalPages) {
            inventory.setItem(50, navigationItem(Material.ARROW, "Next Page"));
        }

        player.openInventory(inventory);
    }

    private void openDeleteConfirm(Player player, String key) {
        if (!hasAnyPermission(player, "communityvault.categories.delete")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to manage categories.");
            return;
        }

        editingCategory.put(player.getUniqueId(), key);
        String name = VaultStorage.getCategoryName(key);
        Inventory inventory = Bukkit.createInventory(new VaultMenuHolder(VaultMenuHolder.Type.DELETE_CONFIRM), 27,
                DELETE_TITLE_PREFIX + name);
        inventory.setItem(11, navigationItem(Material.LIME_WOOL, "Confirm Delete"));
        inventory.setItem(15, navigationItem(Material.RED_WOOL, "Cancel"));
        player.openInventory(inventory);
    }

    private void openCategoryItems(Player player, String key, int page) {
        if (!hasAnyPermission(player, "communityvault.categories.additem", "communityvault.categories.removeitem")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to manage categories.");
            return;
        }

        editingCategory.put(player.getUniqueId(), key);
        String search = itemSearch.getOrDefault(player.getUniqueId(), "");
        List<Material> materials = getCategoryMaterialsFiltered(key, search);

        int pageSize = 45;
        int totalPages = Math.max(1, (int) Math.ceil(materials.size() / (double) pageSize));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        String name = VaultStorage.getCategoryName(key);
        Inventory inventory = Bukkit.createInventory(new VaultMenuHolder(VaultMenuHolder.Type.CATEGORY_ITEMS), 54,
                ITEMS_TITLE_PREFIX + name + " (Page " + page + ")");
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, materials.size());

        for (int i = start; i < end; i++) {
            Material material = materials.get(i);
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + material.name());
                meta.setLore(Collections.singletonList(ChatColor.YELLOW + "Click to remove"));
                item.setItemMeta(meta);
            }
            inventory.setItem(i - start, item);
        }

        inventory.setItem(45, navigationItem(Material.BARRIER, "Back"));
        if (hasAnyPermission(player, "communityvault.categories.additem")) {
            inventory.setItem(49, navigationItem(Material.EMERALD_BLOCK, "Add Items"));
        } else {
            inventory.setItem(49, navigationItem(Material.BARRIER, "Add Items (No Permission)"));
        }
        inventory.setItem(53, navigationItem(Material.BOOK, "Search"));
        if (!search.isBlank()) {
            inventory.setItem(46, navigationItem(Material.BARRIER, "Clear Search"));
        }
        if (page > 1) {
            inventory.setItem(48, navigationItem(Material.ARROW, "Previous Page"));
        }
        if (page < totalPages) {
            inventory.setItem(50, navigationItem(Material.ARROW, "Next Page"));
        }

        player.openInventory(inventory);
    }

    private void openAddItems(Player player, String key, int page) {
        if (!hasAnyPermission(player, "communityvault.categories.additem")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to manage categories.");
            return;
        }

        editingCategory.put(player.getUniqueId(), key);
        String search = addSearch.getOrDefault(player.getUniqueId(), "");
        List<Material> materials = getFilteredMaterials(search);
        int pageSize = 45;
        int totalPages = Math.max(1, (int) Math.ceil(materials.size() / (double) pageSize));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        String name = VaultStorage.getCategoryName(key);
        Inventory inventory = Bukkit.createInventory(new VaultMenuHolder(VaultMenuHolder.Type.CATEGORY_ADD_ITEMS), 54,
                ADD_TITLE_PREFIX + name + " (Page " + page + ")");
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, materials.size());

        List<Material> current = VaultStorage.getMaterialsInCategory(key);
        for (int i = start; i < end; i++) {
            Material material = materials.get(i);
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + material.name());
                if (current.contains(material)) {
                    meta.setLore(Collections.singletonList(ChatColor.GRAY + "Already in category"));
                } else {
                    meta.setLore(Collections.singletonList(ChatColor.YELLOW + "Click to add"));
                }
                item.setItemMeta(meta);
            }
            inventory.setItem(i - start, item);
        }

        inventory.setItem(45, navigationItem(Material.BARRIER, "Back"));
        inventory.setItem(53, navigationItem(Material.BOOK, "Search"));
        if (!search.isBlank()) {
            inventory.setItem(46, navigationItem(Material.BARRIER, "Clear Search"));
        }
        if (page > 1) {
            inventory.setItem(48, navigationItem(Material.ARROW, "Previous Page"));
        }
        if (page < totalPages) {
            inventory.setItem(50, navigationItem(Material.ARROW, "Next Page"));
        }

        player.openInventory(inventory);
    }

    public void openAddItemsForCategory(Player player, String key) {
        openAddItems(player, key, 1);
    }

    private void startRename(Player player) {
        String key = editingCategory.get(player.getUniqueId());
        if (key == null) {
            return;
        }
        pendingInputs.put(player.getUniqueId(), new PendingInput(InputType.RENAME_CATEGORY, key));
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Type the new category name in chat.");
    }

    private void startCreate(Player player) {
        pendingInputs.put(player.getUniqueId(), new PendingInput(InputType.CREATE_CATEGORY, null));
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Type the new category name in chat.");
    }

    private void startIconSearch(Player player) {
        String key = editingCategory.get(player.getUniqueId());
        if (key == null) {
            return;
        }
        pendingInputs.put(player.getUniqueId(), new PendingInput(InputType.ICON_SEARCH, key));
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Type a search term for icons (e.g. stone, glass).");
    }

    private void startItemSearch(Player player) {
        String key = editingCategory.get(player.getUniqueId());
        if (key == null) {
            return;
        }
        pendingInputs.put(player.getUniqueId(), new PendingInput(InputType.ITEM_SEARCH, key));
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Type a search term for category items.");
    }

    private void startAddSearch(Player player) {
        String key = editingCategory.get(player.getUniqueId());
        if (key == null) {
            return;
        }
        pendingInputs.put(player.getUniqueId(), new PendingInput(InputType.ADD_SEARCH, key));
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Type a search term to add items.");
    }

    public void startVaultSearch(Player player) {
        if (player == null) {
            return;
        }
        player.setMetadata("vaultTransition", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        Bukkit.getScheduler().runTask(plugin, () -> player.removeMetadata("vaultTransition", plugin));
        pendingInputs.put(player.getUniqueId(), new PendingInput(InputType.VAULT_SEARCH, null));
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Type a search term for the vault.");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        VaultMenuHolder holder = event.getView().getTopInventory().getHolder() instanceof VaultMenuHolder
                ? (VaultMenuHolder) event.getView().getTopInventory().getHolder() : null;
        if (holder == null) {
            return;
        }

        String title = event.getView().getTitle();

        if (holder.getType() == VaultMenuHolder.Type.CATEGORY_MANAGER) {
            event.setCancelled(true);
            handleManagerClick(player, event.getCurrentItem(), title);
            return;
        }

        if (holder.getType() == VaultMenuHolder.Type.CATEGORY_EDITOR) {
            event.setCancelled(true);
            handleEditorClick(player, event.getCurrentItem());
            return;
        }

        if (holder.getType() == VaultMenuHolder.Type.ICON_PICKER) {
            event.setCancelled(true);
            handleIconPickerClick(player, event.getCurrentItem(), title);
            return;
        }

        if (holder.getType() == VaultMenuHolder.Type.DELETE_CONFIRM) {
            event.setCancelled(true);
            handleDeleteClick(player, event.getCurrentItem());
            return;
        }

        if (holder.getType() == VaultMenuHolder.Type.CATEGORY_ITEMS) {
            event.setCancelled(true);
            handleItemsClick(player, event.getCurrentItem(), title);
            return;
        }

        if (holder.getType() == VaultMenuHolder.Type.CATEGORY_ADD_ITEMS) {
            event.setCancelled(true);
            handleAddItemsClick(player, event.getCurrentItem(), title);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PendingInput pending = pendingInputs.remove(uuid);
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (pending.type == InputType.CREATE_CATEGORY) {
                handleCreate(player, message);
                return;
            }
            if (pending.type == InputType.RENAME_CATEGORY) {
                handleRename(player, pending.key, message);
                return;
            }
            if (pending.type == InputType.ICON_SEARCH) {
                iconSearch.put(uuid, message);
                if (pending.key != null) {
                    openIconPicker(player, pending.key, 1);
                }
                return;
            }
            if (pending.type == InputType.ITEM_SEARCH) {
                itemSearch.put(uuid, message);
                if (pending.key != null) {
                    openCategoryItems(player, pending.key, 1);
                }
                return;
            }
            if (pending.type == InputType.ADD_SEARCH) {
                addSearch.put(uuid, message);
                if (pending.key != null) {
                    openAddItems(player, pending.key, 1);
                }
                return;
            }
            if (pending.type == InputType.VAULT_SEARCH) {
                if (vaultCommand != null) {
                    vaultCommand.searchVault(player, new String[] { message });
                }
            }
        });
    }

    private void handleManagerClick(Player player, ItemStack clicked, String title) {
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        String name = getDisplayName(clicked);
        if ("Back".equals(name)) {
            if (vaultCommand != null) {
                boolean canWithdraw = player.hasMetadata("canWithdraw") && player.getMetadata("canWithdraw").get(0).asBoolean();
                vaultCommand.openMainVaultInventory(player, canWithdraw);
            } else {
                player.closeInventory();
            }
            return;
        }
        if ("Create Category".equals(name) || "Create Category (No Permission)".equals(name)) {
            if (!hasAnyPermission(player, "communityvault.categories.create")) {
                player.sendMessage(ChatColor.RED + "You need communityvault.categories.create to create categories.");
                return;
            }
            startCreate(player);
            return;
        }
        if ("Next Page".equals(name) || "Previous Page".equals(name)) {
            int page = extractPage(title);
            if ("Next Page".equals(name)) {
                openManager(player, page + 1);
            } else {
                openManager(player, page - 1);
            }
            return;
        }

        String key = getCategoryKeyFromLore(clicked);
        if (key != null) {
            openEditor(player, key);
        }
    }

    private void handleEditorClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        String name = getDisplayName(clicked);
        String key = editingCategory.get(player.getUniqueId());
        if (key == null) {
            return;
        }
        if ("Rename".equals(name) || "Rename (No Permission)".equals(name)) {
            if (!hasAnyPermission(player, "communityvault.categories.rename")) {
                player.sendMessage(ChatColor.RED + "You need communityvault.categories.rename to rename categories.");
                return;
            }
            startRename(player);
        } else if ("Change Icon".equals(name) || "Change Icon (No Permission)".equals(name)) {
            if (!hasAnyPermission(player, "communityvault.categories.icon")) {
                player.sendMessage(ChatColor.RED + "You need communityvault.categories.icon to change icons.");
                return;
            }
            openIconPicker(player, key, 1);
        } else if ("Edit Items".equals(name) || "Edit Items (No Permission)".equals(name)) {
            if (!hasAnyPermission(player, "communityvault.categories.additem", "communityvault.categories.removeitem")) {
                player.sendMessage(ChatColor.RED + "You need category item permissions to edit items.");
                return;
            }
            openCategoryItems(player, key, 1);
        } else if ("Delete Category".equals(name) || "Delete Category (No Permission)".equals(name)) {
            if (!hasAnyPermission(player, "communityvault.categories.delete")) {
                player.sendMessage(ChatColor.RED + "You need communityvault.categories.delete to delete categories.");
                return;
            }
            openDeleteConfirm(player, key);
        } else if ("Back".equals(name)) {
            openManager(player, 1);
        }
    }

    private void handleIconPickerClick(Player player, ItemStack clicked, String title) {
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        String name = getDisplayName(clicked);
        String key = editingCategory.get(player.getUniqueId());
        if (key == null) {
            return;
        }
        if ("Search Icons".equals(name)) {
            startIconSearch(player);
            return;
        }
        if ("Clear Search".equals(name)) {
            iconSearch.remove(player.getUniqueId());
            openIconPicker(player, key, 1);
            return;
        }
        if ("Back".equals(name)) {
            openEditor(player, key);
            return;
        }
        if ("Next Page".equals(name) || "Previous Page".equals(name)) {
            int page = extractPage(title);
            if ("Next Page".equals(name)) {
                openIconPicker(player, key, page + 1);
            } else {
                openIconPicker(player, key, page - 1);
            }
            return;
        }

        Material icon = clicked.getType();
        if (VaultStorage.setCategoryIcon(key, icon, categoryConfig)) {
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Icon updated.");
        }
        openEditor(player, key);
    }

    private void handleItemsClick(Player player, ItemStack clicked, String title) {
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        String name = getDisplayName(clicked);
        String key = editingCategory.get(player.getUniqueId());
        if (key == null) {
            return;
        }
        if ("Back".equals(name)) {
            openEditor(player, key);
            return;
        }
        if ("Add Items".equals(name)) {
            if (!hasAnyPermission(player, "communityvault.categories.additem")) {
                player.sendMessage(ChatColor.RED + "You need communityvault.categories.additem to add items.");
                return;
            }
            openAddItems(player, key, 1);
            return;
        }
        if ("Search".equals(name)) {
            startItemSearch(player);
            return;
        }
        if ("Clear Search".equals(name)) {
            itemSearch.remove(player.getUniqueId());
            openCategoryItems(player, key, 1);
            return;
        }
        if ("Next Page".equals(name) || "Previous Page".equals(name)) {
            int page = extractPage(title);
            if ("Next Page".equals(name)) {
                openCategoryItems(player, key, page + 1);
            } else {
                openCategoryItems(player, key, page - 1);
            }
            return;
        }

        Material material = clicked.getType();
        if (!hasAnyPermission(player, "communityvault.categories.removeitem")) {
            player.sendMessage(ChatColor.RED + "You need communityvault.categories.removeitem to remove items.");
            return;
        }
        if (VaultStorage.removeItemFromCategory(key, material, categoryConfig)) {
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Removed " + material + ".");
        }
        openCategoryItems(player, key, extractPage(title));
    }

    private void handleAddItemsClick(Player player, ItemStack clicked, String title) {
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        String name = getDisplayName(clicked);
        String key = editingCategory.get(player.getUniqueId());
        if (key == null) {
            return;
        }
        if ("Back".equals(name)) {
            openCategoryItems(player, key, 1);
            return;
        }
        if ("Search".equals(name)) {
            startAddSearch(player);
            return;
        }
        if ("Clear Search".equals(name)) {
            addSearch.remove(player.getUniqueId());
            openAddItems(player, key, 1);
            return;
        }
        if ("Next Page".equals(name) || "Previous Page".equals(name)) {
            int page = extractPage(title);
            if ("Next Page".equals(name)) {
                openAddItems(player, key, page + 1);
            } else {
                openAddItems(player, key, page - 1);
            }
            return;
        }

        Material material = clicked.getType();
        if (!hasAnyPermission(player, "communityvault.categories.additem")) {
            player.sendMessage(ChatColor.RED + "You need communityvault.categories.additem to add items.");
            return;
        }
        if (!VaultStorage.getMaterialsInCategory(key).contains(material)) {
            VaultStorage.addItemToCategory(key, material, categoryConfig);
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Added " + material + ".");
        } else {
            player.sendMessage(ChatColor.RED + "That item is already in this category.");
        }
        openAddItems(player, key, extractPage(title));
    }

    private void handleDeleteClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        String name = getDisplayName(clicked);
        String key = editingCategory.get(player.getUniqueId());
        if (key == null) {
            return;
        }
        if ("Confirm Delete".equals(name)) {
            if (VaultStorage.deleteCategory(key, categoryConfig)) {
                player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Category deleted.");
            }
            editingCategory.remove(player.getUniqueId());
            openManager(player, 1);
        } else if ("Cancel".equals(name)) {
            openEditor(player, key);
        }
    }

    private void handleCreate(Player player, String name) {
        if (name.isBlank()) {
            player.sendMessage(ChatColor.RED + "Category name cannot be empty.");
            openManager(player, 1);
            return;
        }
        String key = makeUniqueKey(slugify(name));
        boolean created = VaultStorage.createCategory(key, name, Material.CHEST, categoryConfig);
        if (!created) {
            player.sendMessage(ChatColor.RED + "Could not create category. Try a different name.");
            openManager(player, 1);
            return;
        }
        player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Category created.");
        openEditor(player, key);
    }

    private void handleRename(Player player, String key, String name) {
        if (name.isBlank()) {
            player.sendMessage(ChatColor.RED + "Category name cannot be empty.");
            openEditor(player, key);
            return;
        }
        boolean renamed = VaultStorage.renameCategory(key, name, categoryConfig);
        if (!renamed) {
            player.sendMessage(ChatColor.RED + "Could not rename category.");
        } else {
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA + "Category renamed.");
        }
        openEditor(player, key);
    }

    private boolean hasAnyPermission(Player player, String... permissions) {
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

    private ItemStack navigationItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Collections.singletonList(ChatColor.YELLOW + "Click"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getDisplayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? ChatColor.stripColor(meta.getDisplayName()) : "";
    }

    private String getCategoryKeyFromLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return null;
        }
        for (String line : meta.getLore()) {
            String stripped = ChatColor.stripColor(line);
            if (stripped != null && stripped.startsWith("key:")) {
                return stripped.substring(4);
            }
        }
        return null;
    }

    private int extractPage(String title) {
        Matcher matcher = Pattern.compile("\\d+").matcher(title);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        return 1;
    }

    private List<Material> getFilteredMaterials(String filter) {
        List<Material> materials = new ArrayList<>();
        String normalized = filter == null ? "" : filter.trim().toUpperCase();
        for (Material material : Material.values()) {
            if (!material.isItem()) {
                continue;
            }
            if (normalized.isEmpty() || material.name().contains(normalized)) {
                materials.add(material);
            }
        }
        materials.sort(Comparator.comparing(Material::name));
        return materials;
    }

    private List<Material> getCategoryMaterialsFiltered(String key, String filter) {
        List<Material> materials = new ArrayList<>(VaultStorage.getMaterialsInCategory(key));
        String normalized = filter == null ? "" : filter.trim().toUpperCase();
        if (!normalized.isEmpty()) {
            materials.removeIf(material -> !material.name().contains(normalized));
        }
        materials.sort(Comparator.comparing(Material::name));
        return materials;
    }

    private String slugify(String name) {
        String slug = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        slug = slug.replaceAll("^_+", "").replaceAll("_+$", "");
        if (slug.isBlank()) {
            slug = "category";
        }
        return slug;
    }

    private String makeUniqueKey(String base) {
        String key = base;
        int index = 2;
        while (VaultStorage.getCategoryKeys().contains(key)) {
            key = base + "_" + index;
            index++;
        }
        return key;
    }

    private enum InputType {
        CREATE_CATEGORY,
        RENAME_CATEGORY,
        ICON_SEARCH,
        ITEM_SEARCH,
        ADD_SEARCH,
        VAULT_SEARCH
    }

    private static final class PendingInput {
        private final InputType type;
        private final String key;

        private PendingInput(InputType type, String key) {
            this.type = type;
            this.key = key;
        }
    }
}
