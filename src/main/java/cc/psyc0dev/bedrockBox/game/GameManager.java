package cc.psyc0dev.bedrockBox.game;

import cc.psyc0dev.bedrockBox.BedrockBox;
import cc.psyc0dev.bedrockBox.team.Team;
import cc.psyc0dev.bedrockBox.team.TeamBase;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;

public class GameManager {

    public static final NamespacedKey PICKAXE_3X3_KEY = new NamespacedKey("bedrockbox", "pickaxe_3x3");

    public enum GameState {
        WAITING, COUNTDOWN, PLAYING, ENDING
    }

    public enum GameMode {
        SOLO, DUO, TEST
    }

    private final BedrockBox plugin;
    private GameState state = GameState.WAITING;
    private GameMode gameMode = GameMode.SOLO;
    private Map<Integer, TeamBase> bases;
    private Map<UUID, Integer> playerTeams;
    private Map<UUID, Location> playerSpawns;
    private Map<UUID, Boolean> bedIntact;
    private Location lobbyLocation;
    private World gameWorld;
    private BossBar countdownBar;
    private int countdownTaskId = -1;
    private int gameCenterX, gameCenterZ;

    private static final int BOX_BOUNDARY = BoxGenerator.HALF + 8;

    public GameManager(BedrockBox plugin) {
        this.plugin = plugin;
        this.playerTeams = new HashMap<>();
        this.playerSpawns = new HashMap<>();
        this.bedIntact = new HashMap<>();
    }

    public GameState getState() { return state; }
    public GameMode getGameMode() { return gameMode; }
    public Map<Integer, TeamBase> getBases() { return bases; }
    public Integer getPlayerTeam(Player player) { return playerTeams.get(player.getUniqueId()); }
    public Location getLobbyLocation() { return lobbyLocation; }
    public World getGameWorld() { return gameWorld; }
    public boolean isBedIntact(Player player) { return bedIntact.getOrDefault(player.getUniqueId(), false); }
    public Location getPlayerSpawn(Player player) { return playerSpawns.get(player.getUniqueId()); }

    public void setLobbyLocation(Location location) {
        this.lobbyLocation = location;
        plugin.getConfig().set("lobby.world", location.getWorld().getName());
        plugin.getConfig().set("lobby.x", location.getX());
        plugin.getConfig().set("lobby.y", location.getY());
        plugin.getConfig().set("lobby.z", location.getZ());
        plugin.getConfig().set("lobby.yaw", (double) location.getYaw());
        plugin.getConfig().set("lobby.pitch", (double) location.getPitch());
        plugin.saveConfig();
    }

    public void loadLobbyLocation() {
        if (plugin.getConfig().contains("lobby.world")) {
            String worldName = plugin.getConfig().getString("lobby.world");
            World world = plugin.getServer().getWorld(worldName);
            if (world != null) {
                double x = plugin.getConfig().getDouble("lobby.x");
                double y = plugin.getConfig().getDouble("lobby.y");
                double z = plugin.getConfig().getDouble("lobby.z");
                float yaw = (float) plugin.getConfig().getDouble("lobby.yaw");
                float pitch = (float) plugin.getConfig().getDouble("lobby.pitch");
                this.lobbyLocation = new Location(world, x, y, z, yaw, pitch);
            }
        }
    }

    public boolean startGame(World world, GameMode mode) {
        if (state != GameState.WAITING) return false;

        List<Player> players = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        int count = players.size();

        int roomCount;
        if (mode == GameMode.SOLO) {
            if (count < 2 || count > 10) return false;
            roomCount = count;
        } else if (mode == GameMode.DUO) {
            if (count < 4 || count > 20 || count % 2 != 0) return false;
            roomCount = count / 2;
        } else {
            if (count < 1) return false;
            roomCount = count;
        }

        this.gameMode = mode;
        this.gameWorld = world;

        BoxGenerator generator = new BoxGenerator();

        int cx = generator.randomCenterOffset();
        int cz = generator.randomCenterOffset();
        this.gameCenterX = cx;
        this.gameCenterZ = cz;
        plugin.getLogger().info("Generating box at center: " + cx + ", " + cz);

        world.getEntities().removeIf(e -> e.getCustomName() != null && e.getCustomName().contains("Trader"));

        int pad = BoxGenerator.HALF + 7;
        int chunkMinX = (cx - pad) >> 4;
        int chunkMaxX = (cx + pad) >> 4;
        int chunkMinZ = (cz - pad) >> 4;
        int chunkMaxZ = (cz + pad) >> 4;
        for (int chunkX = chunkMinX; chunkX <= chunkMaxX; chunkX++) {
            for (int chunkZ = chunkMinZ; chunkZ <= chunkMaxZ; chunkZ++) {
                world.getChunkAt(chunkX, chunkZ).load(true);
            }
        }

        plugin.getLogger().info("Generating 64x64x64 bedrock box...");

        world.setGameRule(GameRule.KEEP_INVENTORY, false);

        generator.setCenter(cx, cz);
        generator.clearBox(world);
        bases = generator.generateFull(world, getDefaultTrades(), roomCount);

        state = GameState.COUNTDOWN;
        startCountdown();
        return true;
    }

    private void startCountdown() {
        String modeName = switch (gameMode) {
            case SOLO -> "§eSolo";
            case DUO -> "§6Duo";
            case TEST -> "§bTest";
        };
        countdownBar = Bukkit.createBossBar(
                "§6§lBedrockBox " + modeName, BarColor.YELLOW, BarStyle.SOLID);
        countdownBar.setVisible(true);

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            countdownBar.addPlayer(player);
        }

        plugin.getServer().broadcastMessage("§6[BedrockBox] §eGame starting in 10 seconds!");

        countdownTaskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            int secondsLeft = 10;
            @Override
            public void run() {
                if (secondsLeft <= 0) {
                    finishCountdown();
                    return;
                }

                countdownBar.setTitle("§6§lGame starts in §e§l" + secondsLeft + " §6§lsecond" + (secondsLeft != 1 ? "s" : ""));
                countdownBar.setProgress(secondsLeft / 10.0);

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                }

                secondsLeft--;
            }
        }, 0L, 20L);
    }

    private void finishCountdown() {
        if (countdownTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(countdownTaskId);
            countdownTaskId = -1;
        }
        if (countdownBar != null) {
            countdownBar.removeAll();
            countdownBar.setVisible(false);
            countdownBar = null;
        }

        state = GameState.PLAYING;

        List<Player> players = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        assignTeams(players);
        assignInitialSpawns();
        teleportPlayersToBases();
        giveStartingGear();
        hidePlayerNametags(players);

        for (Player player : players) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            player.sendTitle("§6§lGO!", "§eFight for your bed!", 5, 30, 10);
        }

        plugin.getServer().broadcastMessage("§6[BedrockBox] §aGame started!");
    }

    private void assignInitialSpawns() {
        for (Map.Entry<UUID, Integer> entry : playerTeams.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null) continue;
            Integer teamId = entry.getValue();
            Location bedLoc = bases.get(teamId).getBedLocation();
            setPlayerSpawn(player, bedLoc);
        }
    }

    public void setPlayerSpawn(Player player, Location bedLocation) {
        playerSpawns.put(player.getUniqueId(), bedLocation.clone());
        bedIntact.put(player.getUniqueId(), true);
        player.sendMessage("§aBed claimed! This is now your spawn point.");
    }

    public void destroyPlayerBed(Player victim, Player breaker) {
        UUID victimId = victim.getUniqueId();
        if (!bedIntact.getOrDefault(victimId, false)) return;

        bedIntact.put(victimId, false);
        victim.sendMessage("§cYour bed was destroyed! If you die, you're out!");
    }

    public void stopGame() {
        if (state == GameState.WAITING) return;

        if (countdownTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(countdownTaskId);
            countdownTaskId = -1;
        }
        if (countdownBar != null) {
            countdownBar.removeAll();
            countdownBar.setVisible(false);
            countdownBar = null;
        }

        state = GameState.ENDING;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.setHealth(20);
            player.setFoodLevel(20);
            player.getInventory().clear();
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            if (lobbyLocation != null) {
                player.teleport(lobbyLocation);
            }
        }

        playerTeams.clear();
        playerSpawns.clear();
        bedIntact.clear();
        bases = null;
        gameWorld = null;
        state = GameState.WAITING;

        plugin.getServer().broadcastMessage("§6[BedrockBox] §cGame stopped!");
    }

    public void teleportToLobby(Player player) {
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        player.getInventory().clear();
        player.setHealth(20);
        player.setFoodLevel(20);
        if (lobbyLocation != null) {
            player.teleport(lobbyLocation);
        }
    }

    public void handlePlayerDeath(Player player) {
        UUID id = player.getUniqueId();
        if (playerTeams.containsKey(id) && !bedIntact.getOrDefault(id, false)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                player.sendMessage("§cYou have been eliminated!");
            }, 1L);
            plugin.getServer().broadcastMessage("§c" + player.getName() + " has been eliminated!");
            playerTeams.remove(id);
            playerSpawns.remove(id);
            bedIntact.remove(id);
            checkWinCondition();
        }
    }

    private void checkWinCondition() {
        if (gameMode == GameMode.SOLO || gameMode == GameMode.TEST) {
            List<UUID> alive = new ArrayList<>();
            for (Map.Entry<UUID, Integer> entry : playerTeams.entrySet()) {
                if (bedIntact.getOrDefault(entry.getKey(), false)) {
                    alive.add(entry.getKey());
                }
            }
            if (alive.size() <= 1) {
                if (alive.isEmpty()) {
                    endGame((Player) null);
                } else {
                    Player winner = plugin.getServer().getPlayer(alive.get(0));
                    endGame(winner);
                }
            }
        } else {
            Set<Integer> teamsWithBed = new HashSet<>();
            for (Map.Entry<UUID, Integer> entry : playerTeams.entrySet()) {
                if (bedIntact.getOrDefault(entry.getKey(), false)) {
                    teamsWithBed.add(entry.getValue());
                }
            }
            if (teamsWithBed.size() <= 1) {
                if (teamsWithBed.isEmpty()) {
                    endGame((Integer) null);
                } else {
                    endGame(teamsWithBed.iterator().next());
                }
            }
        }
    }

    private void endGame(Player winner) {
        state = GameState.ENDING;

        plugin.getServer().broadcastMessage("");
        plugin.getServer().broadcastMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        plugin.getServer().broadcastMessage("§6§l        GAME OVER!");
        if (winner != null) {
            plugin.getServer().broadcastMessage("§e§l    Winner: §6§l" + winner.getName());
        } else {
            plugin.getServer().broadcastMessage("§c§l        No one wins!");
        }
        plugin.getServer().broadcastMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        plugin.getServer().broadcastMessage("");

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            if (winner != null) {
                player.sendTitle("§6§l" + winner.getName() + " §6§lWINS!", "§eCongratulations!", 10, 60, 20);
            } else {
                player.sendTitle("§c§lGAME OVER", "§eNo one wins!", 10, 60, 20);
            }
        }

        scheduleReset();
    }

    private void endGame(Integer winnerTeamId) {
        state = GameState.ENDING;

        String winnerName = winnerTeamId != null
                ? Team.byId(winnerTeamId).getDisplayName() + " Team"
                : null;

        plugin.getServer().broadcastMessage("");
        plugin.getServer().broadcastMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        plugin.getServer().broadcastMessage("§6§l        GAME OVER!");
        if (winnerName != null) {
            plugin.getServer().broadcastMessage("§e§l    Winner: " + winnerName);
        } else {
            plugin.getServer().broadcastMessage("§c§l        No one wins!");
        }
        plugin.getServer().broadcastMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        plugin.getServer().broadcastMessage("");

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            if (winnerName != null) {
                player.sendTitle("§6§l" + winnerName + " §6§lWINS!", "§eCongratulations!", 10, 60, 20);
            } else {
                player.sendTitle("§c§lGAME OVER", "§eNo one wins!", 10, 60, 20);
            }
        }

        scheduleReset();
    }

    private void scheduleReset() {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                teleportToLobby(player);
            }
            playerTeams.clear();
            playerSpawns.clear();
            bedIntact.clear();
            bases = null;
            gameWorld = null;
            state = GameState.WAITING;
        }, 200L);
    }

    public boolean isInGameBounds(Player player) {
        if (gameWorld == null || player.getWorld() != gameWorld) return false;
        Location loc = player.getLocation();
        return Math.abs(loc.getBlockX() - gameCenterX) <= BOX_BOUNDARY
                && Math.abs(loc.getBlockZ() - gameCenterZ) <= BOX_BOUNDARY;
    }

    private void assignTeams(List<Player> players) {
        if (gameMode == GameMode.TEST) {
            for (Player player : players) {
                playerTeams.put(player.getUniqueId(), 0);
            }
            return;
        }

        if (gameMode == GameMode.SOLO) {
            for (int i = 0; i < players.size(); i++) {
                playerTeams.put(players.get(i).getUniqueId(), i);
            }
        } else {
            for (int i = 0; i < players.size(); i++) {
                playerTeams.put(players.get(i).getUniqueId(), i / 2);
            }
        }
    }

    private void teleportPlayersToBases() {
        for (Map.Entry<UUID, Integer> entry : playerTeams.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                Location spawnLoc = bases.get(entry.getValue()).getSpawnLocation();
                if (spawnLoc != null) player.teleport(spawnLoc);
                player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                player.setHealth(20);
                player.setFoodLevel(20);
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false));
            }
        }
    }

    private void giveStartingGear() {
        for (Map.Entry<UUID, Integer> entry : playerTeams.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                giveStartingGear(player);
            }
        }
    }

    public void giveStartingGear(Player player) {
        player.getInventory().clear();
        player.getInventory().addItem(new ItemStack(Material.WOODEN_PICKAXE, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false));
    }

    private void hidePlayerNametags(List<Player> players) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team team = board.getTeam("bb_players");
        if (team == null) team = board.registerNewTeam("bb_players");
        team.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
        team.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
        for (Player p : players) {
            team.addEntry(p.getName());
        }
    }

    private List<MerchantRecipe> getDefaultTrades() {
        List<MerchantRecipe> defaults = new ArrayList<>();
        addTrade(defaults, Material.COBBLESTONE, 16, Material.STONE_PICKAXE, 1);
        addTrade(defaults, Material.COBBLESTONE, 32, Material.IRON_PICKAXE, 1);
        addTrade(defaults, Material.COBBLESTONE, 64, Material.DIAMOND_PICKAXE, 1);
        addTrade(defaults, Material.COBBLESTONE, 64, Material.GOLD_INGOT, 1);
        addEnchantedTrade(defaults, Material.GOLD_INGOT, 4, Material.DIAMOND_PICKAXE, 1, Enchantment.EFFICIENCY, 5);
        addTrade3x3Pickaxe(defaults);
        addTrade(defaults, Material.COBBLESTONE, 64, Material.IRON_SWORD, 1);
        addTrade(defaults, Material.COBBLESTONE, 64, Material.IRON_HELMET, 1);
        addTrade(defaults, Material.COBBLESTONE, 64, Material.IRON_CHESTPLATE, 1);
        addTrade(defaults, Material.COBBLESTONE, 64, Material.IRON_LEGGINGS, 1);
        addTrade(defaults, Material.COBBLESTONE, 64, Material.IRON_BOOTS, 1);
        addTrade(defaults, Material.EMERALD, 32, Material.DIAMOND_SWORD, 1);
        addTrade(defaults, Material.EMERALD, 32, Material.DIAMOND_HELMET, 1);
        addTrade(defaults, Material.EMERALD, 32, Material.DIAMOND_CHESTPLATE, 1);
        addTrade(defaults, Material.EMERALD, 32, Material.DIAMOND_LEGGINGS, 1);
        addTrade(defaults, Material.EMERALD, 32, Material.DIAMOND_BOOTS, 1);
        addEnchantedTrade(defaults, Material.EMERALD, 64, Material.DIAMOND_SWORD, 1, Enchantment.SHARPNESS, 1);

        return defaults;
    }

    private void addTrade(List<MerchantRecipe> list, Material ing, int ingAmt, Material result, int resultAmt) {
        MerchantRecipe recipe = new MerchantRecipe(new ItemStack(result, resultAmt), 9999999);
        recipe.addIngredient(new ItemStack(ing, ingAmt));
        list.add(recipe);
    }

    private void addEnchantedTrade(List<MerchantRecipe> list, Material ing, int ingAmt, Material result, int resultAmt, Enchantment enchant, int level) {
        ItemStack item = new ItemStack(result, resultAmt);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(enchant, level, true);
            item.setItemMeta(meta);
        }

        MerchantRecipe recipe = new MerchantRecipe(item, 9999999);
        recipe.addIngredient(new ItemStack(ing, ingAmt));
        list.add(recipe);
    }

    private void addTrade3x3Pickaxe(List<MerchantRecipe> list) {
        ItemStack pickaxe = createPickaxe3x3();
        MerchantRecipe recipe = new MerchantRecipe(pickaxe, 9999999);
        recipe.addIngredient(new ItemStack(Material.EMERALD, 64));
        list.add(recipe);
    }

    public static ItemStack createPickaxe3x3() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
            meta.displayName(MiniMessage.miniMessage().deserialize("<aqua><bold>3x3 <blue><bold>Pickaxe"));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(PICKAXE_3X3_KEY, PersistentDataType.BOOLEAN, true);
            item.setItemMeta(meta);
        }
        return item;
    }
}