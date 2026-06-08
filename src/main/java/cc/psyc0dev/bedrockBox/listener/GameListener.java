package cc.psyc0dev.bedrockBox.listener;

import cc.psyc0dev.bedrockBox.BedrockBox;
import cc.psyc0dev.bedrockBox.game.GameManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.entity.Villager;
import cc.psyc0dev.bedrockBox.game.ShopManager;
import cc.psyc0dev.bedrockBox.game.ShopHolder;

public class GameListener implements Listener {

    private final BedrockBox plugin;
    private final GameManager gameManager;

    public GameListener(BedrockBox plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (gameManager.getState() == GameManager.GameState.PLAYING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (gameManager.getState() != GameManager.GameState.PLAYING) return;
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Integer damagerTeam = gameManager.getPlayerTeam(damager);
        Integer victimTeam = gameManager.getPlayerTeam(victim);
        if (damagerTeam != null && damagerTeam.equals(victimTeam)) {
            event.setCancelled(true);
            damager.sendMessage("§cYou cannot hit your teammate!");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();

        if (type == Material.BEDROCK) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                event.setCancelled(true);
                return;
            }
        }

        if (type == Material.RED_BED) {
            if (gameManager.getState() != GameManager.GameState.PLAYING) {
                event.setCancelled(true);
                return;
            }

            Player owner = getBedOwner(block);
            boolean isOwnBed = owner != null && owner.equals(player);

            if (isOwnBed) {
                player.sendMessage("§7You broke your bed. Place and claim it again or you're out on death!");
                gameManager.destroyPlayerBed(player, player);
            } else if (owner != null) {
                gameManager.destroyPlayerBed(owner, player);
            }
            return;
        }

        if (!event.isCancelled() && !player.isSneaking()) {
            breakAdjacent3x3(player, block);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (gameManager.getState() != GameManager.GameState.PLAYING) return;

        if (event.getBlockPlaced().getType() == Material.BEDROCK) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (gameManager.getState() != GameManager.GameState.PLAYING) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.RED_BED) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);
        gameManager.setPlayerSpawn(event.getPlayer(), event.getClickedBlock().getLocation());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (gameManager.getState() != GameManager.GameState.PLAYING) return;

        gameManager.handlePlayerDeath(player);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (gameManager.getState() != GameManager.GameState.PLAYING) return;

        Location spawn = gameManager.getPlayerSpawn(player);
        if (spawn != null && gameManager.isBedIntact(player)) {
            event.setRespawnLocation(spawn);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.teleport(spawn);
                gameManager.giveStartingGear(player);
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (gameManager.getState() == GameManager.GameState.WAITING
                || gameManager.getState() == GameManager.GameState.COUNTDOWN) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        if (!gameManager.isInGameBounds(player)) {
            player.teleport(event.getFrom());
            player.sendMessage("§cYou cannot leave the game area!");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (gameManager.getState() == GameManager.GameState.WAITING) {
            player.setGameMode(GameMode.ADVENTURE);
            if (gameManager.getLobbyLocation() != null) {
                player.teleport(gameManager.getLobbyLocation());
            }
        }
    }

    private Player getBedOwner(Block bedBlock) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            Location spawn = gameManager.getPlayerSpawn(p);
            if (spawn == null) continue;
            Block foot = spawn.getBlock();
            if (foot.equals(bedBlock)) return p;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) != 1) continue;
                    if (foot.getRelative(dx, 0, dz).equals(bedBlock)) return p;
                }
            }
        }
        return null;
    }

    private boolean isPickaxe3x3(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(GameManager.PICKAXE_3X3_KEY, PersistentDataType.BOOLEAN);
    }

    private void breakAdjacent3x3(Player player, Block origin) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isPickaxe3x3(held)) return;

        org.bukkit.util.Vector dir = player.getLocation().getDirection();
        double ax = Math.abs(dir.getX());
        double ay = Math.abs(dir.getY());
        double az = Math.abs(dir.getZ());

        if (ay >= ax && ay >= az) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    breakIfValid(origin.getRelative(dx, 0, dz), held);
                }
            }
        } else if (az >= ax) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    breakIfValid(origin.getRelative(dx, dy, 0), held);
                }
            }
        } else {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dy == 0 && dz == 0) continue;
                    breakIfValid(origin.getRelative(0, dy, dz), held);
                }
            }
        }
    }

    private void breakIfValid(Block block, ItemStack held) {
        if (block.getType() == Material.BEDROCK) return;
        if (block.getType() == Material.RED_BED) return;
        block.breakNaturally(held);
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (gameManager.getState() != GameManager.GameState.PLAYING) return;
        if (!(event.getRightClicked() instanceof Villager)) return;

        event.setCancelled(true); // Cancel vanilla trade inventory opening
        ShopManager.openShop(event.getPlayer());
    }

    @EventHandler
    public void onPlayerInteractAtEntity(org.bukkit.event.player.PlayerInteractAtEntityEvent event) {
        if (gameManager.getState() != GameManager.GameState.PLAYING) return;
        if (!(event.getRightClicked() instanceof Villager)) return;

        event.setCancelled(true); // Cancel vanilla trade inventory opening
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof ShopHolder) {
            event.setCancelled(true); // block picking up the item
            if (!(event.getWhoClicked() instanceof Player player)) return;

            // Process click inside shop inventory
            if (event.getRawSlot() < event.getInventory().getSize()) {
                ShopManager.handleShopClick(player, event.getSlot());
            }
        }
    }
}