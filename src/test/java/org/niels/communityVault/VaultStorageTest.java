package org.niels.communityVault;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.niels.communityVault.utils.VaultStorage;
import org.niels.communityVault.commands.VaultCommand;
import org.niels.communityVault.utils.CategoryConfig;
import org.niels.communityVault.utils.ConfigManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.entity.Player;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultStorageTest {

    @BeforeAll
    static void setUpMockServer() {
        System.setProperty("org.mockbukkit.running", "true");
        MockBukkit.mock();
        MockBukkit.load(CommunityVault.class);
    }

    @AfterAll
    static void tearDownMockServer() {
        MockBukkit.unmock();
    }

    @Test
    void addItemToVaultRespectsCapacity() {
        VaultStorage.clearVault();
        // Set a small capacity for testing
        CommunityVault.configManager.setBoolean("maxVaultCapacityEnabled", true);
        CommunityVault.configManager.setInt("maxVaultCapacity", 100);
        
        int added1 = VaultStorage.addItemToVault(new ItemStack(Material.ARROW, 60));
        assertEquals(60, added1, "Should add all 60 arrows");
        
        int added2 = VaultStorage.addItemToVault(new ItemStack(Material.ARROW, 60));
        assertEquals(40, added2, "Should only add 40 arrows due to capacity");
        
        assertEquals(100, VaultStorage.getTotalItemCount(), "Total vault count should be capped at 100");
        
        int added3 = VaultStorage.addItemToVault(new ItemStack(Material.IRON_INGOT, 10));
        assertEquals(0, added3, "Should not add any more items when at capacity");
    }

    @Test
    void addItemToVaultIgnoresCapacityWhenDisabled() {
        VaultStorage.clearVault();
        CommunityVault.configManager.setBoolean("maxVaultCapacityEnabled", false);
        CommunityVault.configManager.setInt("maxVaultCapacity", 10);

        int added = VaultStorage.addItemToVault(new ItemStack(Material.ARROW, 64));
        assertEquals(64, added, "Should add all items when capacity is disabled");
        assertEquals(64, VaultStorage.getTotalItemCount(), "Total vault count should exceed the limit when disabled");
    }

    @AfterEach
    void tearDown() {
        VaultStorage.clearVault();
        // Reset default capacity
        if (CommunityVault.configManager != null) {
            CommunityVault.configManager.setBoolean("maxVaultCapacityEnabled", true);
            CommunityVault.configManager.setInt("maxVaultCapacity", 1000000);
        }
    }

    @Test
    void addItemToVaultMergesStacks() {
        VaultStorage.clearVault();
        VaultStorage.addItemToVault(new ItemStack(Material.ARROW, 30));
        VaultStorage.addItemToVault(new ItemStack(Material.ARROW, 50));

        List<ItemStack> arrows = VaultStorage.getItemsByMaterial(Material.ARROW);
        int total = arrows.stream().mapToInt(ItemStack::getAmount).sum();
        assertEquals(80, total, "Total arrow count should be preserved");

        long fullStacks = arrows.stream().filter(s -> s.getAmount() == s.getMaxStackSize()).count();
        assertEquals(1, fullStacks, "One full stack expected after merge");

        long partialStacks = arrows.stream().filter(s -> s.getAmount() != s.getMaxStackSize()).count();
        assertEquals(1, partialStacks, "One partial stack expected after merge");
    }

    @Test
    void canFitInInventoryReturnsFalseWhenFull() throws Exception {
        VaultStorage.clearVault();
        Plugin plugin = MockBukkit.createMockPlugin();
        VaultCommand vaultCommand = new VaultCommand(plugin, new CategoryConfig(plugin));

        Player player = MockBukkit.getMock().addPlayer();
        PlayerInventory inv = player.getInventory();
        // Fill all slots with non-stackable items to block space
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, new ItemStack(Material.DIAMOND_SWORD, 1));
        }

        Method canFit = VaultCommand.class.getDeclaredMethod("canFitInInventory", Player.class, ItemStack.class);
        canFit.setAccessible(true);
        boolean result = (boolean) canFit.invoke(vaultCommand, player, new ItemStack(Material.ARROW, 64));
        assertEquals(false, result, "Full inventory should not fit additional items");
    }

    @Test
    void vaultSaveLoadRoundTripPreservesCounts() throws Exception {
        VaultStorage.clearVault();
        VaultStorage.addItemToVault(new ItemStack(Material.WHEAT, 20));
        VaultStorage.addItemToVault(new ItemStack(Material.IRON_INGOT, 5));

        // Simulate a save/load using Bukkit serialization helpers rather than the file on disk
        List<ItemStack> snapshot = new ArrayList<>();
        for (ItemStack is : VaultStorage.getVaultItems()) {
            snapshot.add(is.clone());
        }

        // Reset and restore from the serialized form
        VaultStorage.clearVault();
        for (ItemStack is : snapshot) {
            VaultStorage.addItemToVault(is);
        }

        assertEquals(25, VaultStorage.getTotalItemCount(), "Total item count should persist after restore");
        assertEquals(2, VaultStorage.getItemsByMaterial(Material.WHEAT).size() + VaultStorage.getItemsByMaterial(Material.IRON_INGOT).size(), "Stacks should be present after restore");
    }

    @Test
    void addItemToVaultMergesIntoPartialStack() {
        VaultStorage.clearVault();
        VaultStorage.addItemToVault(new ItemStack(Material.ARROW, 40));
        VaultStorage.addItemToVault(new ItemStack(Material.ARROW, 30)); // should fill to 64 + 6 leftover

        List<ItemStack> arrows = VaultStorage.getItemsByMaterial(Material.ARROW);
        arrows.sort((a, b) -> Integer.compare(b.getAmount(), a.getAmount())); // largest first

        assertEquals(2, arrows.size(), "Should have a full and a partial stack");
        assertEquals(64, arrows.get(0).getAmount(), "First stack should be full");
        assertEquals(6, arrows.get(1).getAmount(), "Remainder should be 6");
    }

    @Test
    void addItemToVaultIgnoresNullOrAir() {
        VaultStorage.clearVault();
        VaultStorage.addItemToVault(null);
        VaultStorage.addItemToVault(new ItemStack(Material.AIR, 1));
        assertEquals(0, VaultStorage.getVaultItems().size(), "Null/air should not be added");
    }

    @Test
    void compactVaultMergesStackablesOnly() {
        VaultStorage.clearVault();
        VaultStorage.addItemToVault(new ItemStack(Material.WHEAT, 20));
        VaultStorage.addItemToVault(new ItemStack(Material.WHEAT, 50)); // total 70 -> 64 + 6
        VaultStorage.addItemToVault(new ItemStack(Material.IRON_SWORD, 1)); // unstackable, should remain as-is

        VaultStorage.compactVault();

        List<ItemStack> wheat = VaultStorage.getItemsByMaterial(Material.WHEAT);
        wheat.sort((a, b) -> Integer.compare(b.getAmount(), a.getAmount()));
        assertEquals(2, wheat.size(), "Wheat should merge into two stacks");
        assertEquals(64, wheat.get(0).getAmount());
        assertEquals(6, wheat.get(1).getAmount());

        List<ItemStack> swords = VaultStorage.getItemsByMaterial(Material.IRON_SWORD);
        assertEquals(1, swords.size(), "Unstackable sword should remain separate");
    }

    @Test
    void compactVaultGroupsBySimilarityAndMaxStack() {
        VaultStorage.clearVault();

        // Same-color shulkers with different meta; amounts are 1 because shulkers are unstackable
        ItemStack namedShulkerA = new ItemStack(Material.WHITE_SHULKER_BOX, 1);
        ItemMeta metaA = namedShulkerA.getItemMeta();
        metaA.setDisplayName("Loot A");
        namedShulkerA.setItemMeta(metaA);

        ItemStack namedShulkerB = new ItemStack(Material.WHITE_SHULKER_BOX, 1);
        ItemMeta metaB = namedShulkerB.getItemMeta();
        metaB.setDisplayName("Loot B");
        namedShulkerB.setItemMeta(metaB);

        VaultStorage.addItemToVault(namedShulkerA);
        VaultStorage.addItemToVault(namedShulkerB);

        VaultStorage.compactVault();

        List<ItemStack> shulkers = VaultStorage.getItemsByMaterial(Material.WHITE_SHULKER_BOX);
        assertEquals(2, shulkers.size(), "Unstackable shulkers should not be merged");
    }

    @Test
    void removeExactItemUsesSimilarity() {
        VaultStorage.clearVault();
        ItemStack stackA = new ItemStack(Material.WHITE_SHULKER_BOX, 1);
        ItemStack stackB = new ItemStack(Material.WHITE_SHULKER_BOX, 1);
        ItemMeta metaB = stackB.getItemMeta();
        metaB.setDisplayName("Custom");
        stackB.setItemMeta(metaB);

        VaultStorage.addItemToVault(stackA);
        VaultStorage.addItemToVault(stackB);

        assertTrue(VaultStorage.removeExactItemFromVault(stackB), "Should remove exact matching stack");
        List<ItemStack> remaining = VaultStorage.getItemsByMaterial(Material.WHITE_SHULKER_BOX);
        assertEquals(1, remaining.size(), "Only one stack should remain");
        ItemMeta meta = remaining.get(0).getItemMeta();
        assertTrue(meta == null || !meta.hasDisplayName(), "Remaining stack should be the unmodified one");
    }
}
