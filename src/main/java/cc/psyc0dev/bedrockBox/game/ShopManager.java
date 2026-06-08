package cc.psyc0dev.bedrockBox.game;

import cc.psyc0dev.bedrockBox.BedrockBox;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShopManager {

    private static final Map<Integer, ShopItem> shopItems = new HashMap<>();

    public static class ShopItem {
        private final ItemStack result;
        private final Material currency;
        private final int cost;

        public ShopItem(ItemStack result, Material currency, int cost) {
            this.result = result;
            this.currency = currency;
            this.cost = cost;
        }

        public ItemStack getResult() { return result; }
        public Material getCurrency() { return currency; }
        public int getCost() { return cost; }
    }

    static {
        // Row 1: Pickaxes, Gold, and Sword
        registerItem(10, new ItemStack(Material.STONE_PICKAXE), Material.COBBLESTONE, 16);
        registerItem(11, new ItemStack(Material.IRON_PICKAXE), Material.COBBLESTONE, 32);
        registerItem(12, new ItemStack(Material.DIAMOND_PICKAXE), Material.COBBLESTONE, 64);
        
        ItemStack eff5Pick = new ItemStack(Material.DIAMOND_PICKAXE);
        eff5Pick.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        registerItem(13, eff5Pick, Material.GOLD_INGOT, 4);

        registerItem(14, GameManager.createPickaxe3x3(), Material.EMERALD, 64);
        registerItem(15, new ItemStack(Material.GOLD_INGOT), Material.COBBLESTONE, 64);
        registerItem(16, new ItemStack(Material.IRON_SWORD), Material.COBBLESTONE, 64);

        // Row 2: Iron Armor and Diamond Swords
        registerItem(19, new ItemStack(Material.IRON_HELMET), Material.COBBLESTONE, 64);
        registerItem(20, new ItemStack(Material.IRON_CHESTPLATE), Material.COBBLESTONE, 64);
        registerItem(21, new ItemStack(Material.IRON_LEGGINGS), Material.COBBLESTONE, 64);
        registerItem(22, new ItemStack(Material.IRON_BOOTS), Material.COBBLESTONE, 64);
        registerItem(24, new ItemStack(Material.DIAMOND_SWORD), Material.EMERALD, 32);

        ItemStack sharpSword = new ItemStack(Material.DIAMOND_SWORD);
        sharpSword.addUnsafeEnchantment(Enchantment.SHARPNESS, 1);
        registerItem(25, sharpSword, Material.EMERALD, 64);

        // Row 3: Diamond Armor
        registerItem(28, new ItemStack(Material.DIAMOND_HELMET), Material.EMERALD, 32);
        registerItem(29, new ItemStack(Material.DIAMOND_CHESTPLATE), Material.EMERALD, 32);
        registerItem(30, new ItemStack(Material.DIAMOND_LEGGINGS), Material.EMERALD, 32);
        registerItem(31, new ItemStack(Material.DIAMOND_BOOTS), Material.EMERALD, 32);
    }

    private static void registerItem(int slot, ItemStack result, Material currency, int cost) {
        shopItems.put(slot, new ShopItem(result, currency, cost));
    }

    public static void openShop(Player player) {
        Inventory inv = Bukkit.createInventory(new ShopHolder(), 45, Component.text("Trader Shop", NamedTextColor.DARK_GRAY));

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty());
            filler.setItemMeta(fillerMeta);
        }

        for (int i = 0; i < inv.getSize(); i++) {
            if (shopItems.containsKey(i)) {
                ShopItem shopItem = shopItems.get(i);
                ItemStack display = shopItem.getResult().clone();
                ItemMeta meta = display.getItemMeta();
                if (meta != null) {
                    List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
                    if (lore == null) lore = new ArrayList<>();
                    
                    lore.add(Component.empty());
                    lore.add(Component.text("Cost: ", NamedTextColor.GRAY)
                            .append(Component.text(shopItem.getCost() + " " + formatCurrencyName(shopItem.getCurrency()), getCurrencyColor(shopItem.getCurrency())))
                            .decoration(TextDecoration.ITALIC, false));
                    
                    meta.lore(lore);
                    display.setItemMeta(meta);
                }
                inv.setItem(i, display);
            } else {
                inv.setItem(i, filler);
            }
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
    }

    public static void handleShopClick(Player player, int slot) {
        ShopItem shopItem = shopItems.get(slot);
        if (shopItem == null) return;

        if (!hasResource(player, shopItem.getCurrency(), shopItem.getCost())) {
            player.sendMessage(Component.text("You do not have enough resources!", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        deductResource(player, shopItem.getCurrency(), shopItem.getCost());
        player.getInventory().addItem(shopItem.getResult().clone());
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    private static boolean hasResource(Player player, Material currency, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == currency) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    private static void deductResource(Player player, Material currency, int amount) {
        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == currency) {
                if (item.getAmount() > left) {
                    item.setAmount(item.getAmount() - left);
                    left = 0;
                    break;
                } else {
                    left -= item.getAmount();
                    contents[i] = null;
                }
            }
        }
        player.getInventory().setContents(contents);
    }

    private static String formatCurrencyName(Material currency) {
        return switch (currency) {
            case COBBLESTONE -> "Cobblestone";
            case GOLD_INGOT -> "Gold Ingot";
            case EMERALD -> "Emerald";
            default -> currency.name();
        };
    }

    private static NamedTextColor getCurrencyColor(Material currency) {
        return switch (currency) {
            case COBBLESTONE -> NamedTextColor.GRAY;
            case GOLD_INGOT -> NamedTextColor.GOLD;
            case EMERALD -> NamedTextColor.GREEN;
            default -> NamedTextColor.WHITE;
        };
    }
}
