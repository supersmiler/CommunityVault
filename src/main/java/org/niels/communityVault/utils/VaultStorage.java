package org.niels.communityVault.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class VaultStorage {

    private static List<ItemStack> vaultStorage = new ArrayList<>();
    private static File vaultFile;
    private static FileConfiguration vaultConfig;

    private static final Map<String, Category> categories = new HashMap<>();

    // Category class to hold category data (name and materials)
    private static class Category {
        String name;
        List<Material> materials;
        Material icon;  // Icon for the category

        Category(String name, List<Material> materials, Material icon) {
            this.name = name;
            this.materials = materials;
            this.icon = icon;
        }
    }

    // Method to load categories from the configuration
    public static void loadCategories(CategoryConfig categoryConfig) {
        FileConfiguration config = categoryConfig.getConfig();

        if (config.contains("categories")) {
            for (String key : config.getConfigurationSection("categories").getKeys(false)) {
                String categoryName = config.getString("categories." + key + ".name");  // Load category name
                String iconName = config.getString("categories." + key + ".icon", "CHEST");  // Load icon name, default to CHEST
                Material iconMaterial = Material.getMaterial(iconName.toUpperCase());  // Convert to Material

                List<String> materialNames = config.getStringList("categories." + key + ".items");
                List<Material> materials = new ArrayList<>();

                for (String materialName : materialNames) {
                    Material material = Material.getMaterial(materialName.toUpperCase());
                    if (material != null) {
                        materials.add(material);
                    }
                }

                // Store category using key, name, and icon
                categories.put(key, new Category(categoryName, materials, iconMaterial != null ? iconMaterial : Material.CHEST));
            }
        }
        else {
            // If no categories found, create some default categories
            //addDefaultCategories(config);

            // Save the updated configuration
            //categoryConfig.saveConfig();
        }
    }

    // Helper method to add default categories
    private static void addDefaultCategories(FileConfiguration config) {
        // Example default categories
        Map<String, Category> defaultCategories = new HashMap<>();

        // Add a default "Building Blocks" category
        defaultCategories.put("building_blocks", new Category(
                "Building Blocks",
                Arrays.asList(Material.STONE, Material.BRICKS, Material.GRANITE),
                Material.BRICKS
        ));

        // Add a default "Decoration Blocks" category
        defaultCategories.put("decoration_blocks", new Category(
                "Decoration Blocks",
                Arrays.asList(Material.PAINTING, Material.ITEM_FRAME),
                Material.PAINTING
        ));

        // Add a default "Redstone" category
        defaultCategories.put("redstone", new Category(
                "Redstone",
                Arrays.asList(Material.REDSTONE, Material.REDSTONE_TORCH),
                Material.REDSTONE
        ));

        // Add a default "Redstone" category
        defaultCategories.put("all", new Category(
                "All",
                Arrays.asList(Material.values()),
                Material.END_CRYSTAL
        ));

        // Store these categories into the config file
        for (Map.Entry<String, Category> entry : defaultCategories.entrySet()) {
            String key = entry.getKey();
            Category category = entry.getValue();

            // Save category name, icon, and items to the config
            config.set("categories." + key + ".name", category.name);
            config.set("categories." + key + ".icon", category.icon.name());

            List<String> materialNames = new ArrayList<>();
            for (Material material : category.materials) {
                materialNames.add(material.name());
            }
            config.set("categories." + key + ".items", materialNames);

            // Add to internal categories map
            categories.put(key, category);
        }
    }

    // Method to get all materials by category name
    public static List<Material> getMaterialsInCategory(String key) {
        Category category = categories.get(key);
        return category != null ? category.materials : new ArrayList<>();
    }

    // Method to get the name of the category by key
    public static String getCategoryName(String key) {
        Category category = categories.get(key);
        return category != null ? category.name : "Unknown";
    }

    // Method to get the icon of the category by key
    public static Material getCategoryIcon(String key) {
        Category category = categories.get(key);
        return category != null ? category.icon : Material.CHEST;  // Default icon is CHEST
    }

    public static Set<String> getCategoryKeys() {
        Set<String> sortedKeys = new TreeSet<>(categories.keySet()); // TreeSet automatically sorts alphabetically
        return sortedKeys;
    }

    // Method to get items by a partial material name
    public static List<ItemStack> getItemsByPartialName(String partialName) {
        List<ItemStack> matchedItems = new ArrayList<>();
        String searchTerm = partialName.toUpperCase().replace(" ", "_");  // Replace spaces with underscores

        for (ItemStack item : vaultStorage) {
            if (item.getType().name().contains(searchTerm)) {
                matchedItems.add(item);
            }
        }
        return matchedItems;
    }


    // Filter vault items by category key
    public static List<ItemStack> getItemsByCategoryKey(String key) {
        List<Material> categoryMaterials = getMaterialsInCategory(key);
        List<ItemStack> filteredItems = new ArrayList<>();

        for (ItemStack item : vaultStorage) {
            if (categoryMaterials.contains(item.getType())) {
                filteredItems.add(item);
            }
        }

        return filteredItems;
    }

    // Add or remove materials from categories and save to config
    public static void addItemToCategory(String key, Material material, CategoryConfig categoryConfig) {
        Category category = categories.get(key);
        if (category != null) {
            category.materials.add(material);
            saveCategoryToConfig(key, category, categoryConfig);
        }
    }

    // Add an item to the vault
    public static void addItemToVault(ItemStack item) {
        vaultStorage.add(item); vaultStorage.sort(Comparator.comparing(itemStack -> itemStack.getType().name()));
    }
    public static void removeItemFromCategory(String key, Material material, CategoryConfig categoryConfig) {
        Category category = categories.get(key);
        if (category != null) {
            category.materials.remove(material);
            saveCategoryToConfig(key, category, categoryConfig);
        }
    }

    // Save a category back to the configuration file
    private static void saveCategoryToConfig(String key, Category category, CategoryConfig categoryConfig) {
        FileConfiguration config = categoryConfig.getConfig();

        List<String> materialNames = new ArrayList<>();
        for (Material material : category.materials) {
            materialNames.add(material.name());
        }

        // Save the category name, icon, and its materials
        config.set("categories." + key + ".name", category.name);
        config.set("categories." + key + ".icon", category.icon.name());
        config.set("categories." + key + ".items", materialNames);
        categoryConfig.saveConfig();
    }


    // Remove an item from the vault by index
    public static ItemStack removeItemFromVault(int index) {
        if (index >= 0 && index < vaultStorage.size()) {
            return vaultStorage.remove(index);
        }
        return null;
    }

    // Get all items in the vault
    public static List<ItemStack> getVaultItems() {


        return vaultStorage;
    }

    // Clear the vault
    public static void clearVault() {
        vaultStorage.clear();
    }

    // Get all stacks of a specific material from the vault
    public static List<ItemStack> getItemsByMaterial(Material material) {
        List<ItemStack> materialStacks = new ArrayList<>();
        for (ItemStack item : vaultStorage) {
            if (item.getType() == material) {
                materialStacks.add(item);
            }
        }
        return materialStacks;
    }

    // Get items by a category (block type or tool type)
    public static ItemStack[] getItemsByCategory(Material... categories) {
        List<ItemStack> filteredItems = new ArrayList<>();
        for (ItemStack item : vaultStorage) {
            if (item != null && isInCategory(item, categories)) {
                filteredItems.add(item);
            }
        }
        return filteredItems.toArray(new ItemStack[0]);
    }

    public static String getCategoryKeyByMaterial(Material material) {
        for (String key : categories.keySet()) {
            List<Material> materialsInCategory = categories.get(key).materials;
            if (materialsInCategory.contains(material)) {
                return key;  // Return the key for the category that contains the material
            }
        }
        return null;  // Return null if the material is not found in any category
    }


    // Get items by a category (block type or tool type)
    public static ItemStack[] getRemainingItems(Material... categories) {
        List<ItemStack> filteredItems = new ArrayList<>();
        for (ItemStack item : vaultStorage) {
            if (item != null && !isInCategory(item, categories)) {
                filteredItems.add(item);
            }
        }

        // Debugging: Log how many items were not in the provided categories
        System.out.println("Remaining Items: " + filteredItems.size());

        return filteredItems.toArray(new ItemStack[0]);
    }

    public static ItemStack[] getRemainingItems(List<Material> filteredMaterials) {
        List<ItemStack> remainingItems = new ArrayList<>();

        // Iterate through the vault storage
        for (ItemStack item : vaultStorage) {
            if (item != null && !filteredMaterials.contains(item.getType())) {
                remainingItems.add(item);  // Add item if its material is not in the filtered list
            }
        }

        // Debugging: Log how many items were not in the provided categories
        //System.out.println("Remaining Items: " + remainingItems.size());

        return remainingItems.toArray(new ItemStack[0]);
    }


    // Helper method to check if an item belongs to a category
    private static boolean isInCategory(ItemStack item, Material[] categories) {
        for (Material category : categories) {
            if (item.getType() == category) {
                return true;
            }
        }
        return false;
    }

    // Save the vault contents to a file (vault.yml)
    public static void saveVaultToFile() {
        if (vaultFile == null) {
            vaultFile = new File("plugins/communityvault/vault.yml");
        }

        vaultConfig = YamlConfiguration.loadConfiguration(vaultFile);
        if(vaultStorage.size() <= 0)
        {
            System.out.println("ERROR!!!! VAULTSTORAGE TRIED TO SAVE AN EMPTY VAULT");

            return;
        }
        // Save the list of ItemStacks to the config file
        vaultConfig.set("vault", vaultStorage);

        try {
            vaultConfig.save(vaultFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Load the vault contents from a file (vault.yml)
    public static void loadVaultFromFile() {
        vaultFile = new File("plugins/communityvault/vault.yml");

        // Create the parent directories if they do not exist
        if (!vaultFile.getParentFile().exists()) {
            vaultFile.getParentFile().mkdirs();  // Create the directory if it doesn't exist
        }

        // Create the file if it doesn't exist
        if (!vaultFile.exists()) {
            try {
                vaultFile.createNewFile(); // Create the file if it doesn't exist
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        vaultConfig = YamlConfiguration.loadConfiguration(vaultFile);

        List<?> loadedItems = vaultConfig.getList("vault");
        if (loadedItems != null) {
            vaultStorage.clear();
            for (Object item : loadedItems) {
                if (item instanceof ItemStack) {
                    vaultStorage.add((ItemStack) item); // Add each item back to the storage
                }
            }
        }
        // Sort the vault alphabetically by Material name
        vaultStorage.sort(Comparator.comparing(itemStack -> itemStack.getType().name()));
    }

    public static boolean removeExactItemFromVault(Material material, int amount) {
        for (int i = 0; i < vaultStorage.size(); i++) {
            ItemStack item = vaultStorage.get(i);

            // Check if the item matches both the material and the amount
            if (item.getType() == material && item.getAmount() == amount) {
                // Remove the exact stack
                vaultStorage.remove(i);
                return true; // Return true indicating the item was found and removed
            }
        }
        return false; // Return false if no matching item was found
    }

    // Method to remove a certain number of items from the vault (from last stack to first)
    public static void removeItemFromVault(Material material, int amount) {
        List<ItemStack> itemsToRemove = new ArrayList<>();
        int remainingAmount = amount;

        // Traverse the vaultStorage in reverse to remove from the last stack first
        for (int i = vaultStorage.size() - 1; i >= 0; i--) {
            ItemStack item = vaultStorage.get(i);

            if (item.getType() == material) {
                int itemAmount = item.getAmount();

                if (itemAmount > remainingAmount) {
                    // Reduce the amount in this stack and break
                    item.setAmount(itemAmount - remainingAmount);
                    break;
                } else {
                    // Remove the entire stack and adjust the remaining amount to remove
                    remainingAmount -= itemAmount;
                    itemsToRemove.add(item);  // Mark item for removal
                    vaultStorage.remove(i);   // Remove from vaultStorage
                    if (remainingAmount <= 0) {
                        break;  // Stop once we have removed enough
                    }
                }
            }
        }

        // Debugging message
        //System.out.println("Removed " + (amount - remainingAmount) + " " + material + " from the vault. Remaining to remove: " + remainingAmount);
    }




    // Method to get the total count of a specific material in the vault
    public static int getItemCountFromVault(Material material) {
        int totalAmount = 0;
        for (ItemStack item : vaultStorage) {
            if (item.getType() == material) {
                totalAmount += item.getAmount();
            }
        }
        return totalAmount;
    }


}
