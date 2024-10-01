package org.niels.communityVault.commands;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.niels.communityVault.listeners.ChestInteractListener;
import org.bukkit.ChatColor;
import org.niels.communityVault.utils.CategoryConfig;
import org.niels.communityVault.utils.ConfigManager;

import java.util.ArrayList;
import java.util.List;

public class ChestCommand implements CommandExecutor {
    private final ConfigManager configManager;

    public ChestCommand(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return false;
        }

        Player player = (Player) sender;

        if (label.equalsIgnoreCase("buywc")) {
            buyWithdrawalChest(player);
            return true;
        }

        if (label.equalsIgnoreCase("buydc")) {
            buyDepositChest(player);
            return true;
        }

        return false;
    }



    public void buyWithdrawalChest(Player player) {
        int buyAmount = configManager.getInt("diamondCostWithdrawalChest");
        if (player.getInventory().contains(Material.DIAMOND, buyAmount)) {
            player.getInventory().removeItem(new ItemStack(Material.DIAMOND, buyAmount)); // Charge diamonds
            ItemStack chestItem = new ItemStack(Material.CHEST);

            // Add custom metadata to identify it as a withdrawal chest
            ItemMeta meta = chestItem.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add("WithdrawalChest");
            meta.setLore(lore);
            meta.setDisplayName("Withdrawal Chest");
            chestItem.setItemMeta(meta);

            player.getInventory().addItem(chestItem); // Give chest to player
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.GREEN + "You bought a Withdrawal Chest! Place it to activate.");

        } else {
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.RED + "You need "+ buyAmount +" diamonds to buy a Withdrawal Chest.");
        }
    }

    public void buyDepositChest(Player player) {
        int buyAmount = configManager.getInt("diamondCostDepositChest");
        if (player.getInventory().contains(Material.DIAMOND, buyAmount)) {
            player.getInventory().removeItem(new ItemStack(Material.DIAMOND, buyAmount)); // Charge diamonds
            ItemStack chestItem = new ItemStack(Material.CHEST);

            // Add custom metadata to identify it as a deposit chest
            ItemMeta meta = chestItem.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add("DepositChest");
            meta.setLore(lore);
            meta.setDisplayName("Deposit Chest");
            chestItem.setItemMeta(meta);

            player.getInventory().addItem(chestItem); // Give chest to player
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.GREEN + "You bought a Deposit Chest! Place it to activate.");
        } else {
            player.sendMessage(ChatColor.GOLD + "[CommunityVault] " + ChatColor.RED + "You need "+ buyAmount +" diamonds to buy a Deposit Chest.");
        }
    }
}