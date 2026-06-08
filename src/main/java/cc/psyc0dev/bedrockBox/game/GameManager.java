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
    private int villagerTaskId = -1;
    private boolean generationComplete = false;
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
        this.generationComplete = false;

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

        List<java.util.concurrent.CompletableFuture<Chunk>> chunkFutures = new ArrayList<>();
        for (int chunkX = chunkMinX; chunkX <= chunkMaxX; chunkX++) {
            for (int chunkZ = chunkMinZ; chunkZ <= chunkMaxZ; chunkZ++) {
                chunkFutures.add(world.getChunkAtAsync(chunkX, chunkZ));
            }
        }

        plugin.getLogger().info("Loading " + chunkFutures.size() + " chunks asynchronously...");

        java.util.concurrent.CompletableFuture.allOf(chunkFutures.toArray(new java.util.concurrent.CompletableFuture[0])).thenRun(() -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getLogger().info("Chunks loaded. Generating Bedrock Box...");
                world.setGameRule(GameRules.KEEP_INVENTORY, false);
                generator.setCenter(cx, cz);
                generator.clearBox(world);
                bases = generator.generateFull(world, roomCount);
                generationComplete = true;
                plugin.getLogger().info("Bedrock Box generation complete!");
            });
        });

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
        if (!generationComplete) {
            plugin.getLogger().info("Bedrock Box generation not complete. Postponing game start...");
            plugin.getServer().getScheduler().runTaskLater(plugin, this::finishCountdown, 10L);
            return;
        }

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
        setupGameScoreboardTeams();
        startVillagerLookTask();

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

        stopVillagerLookTask();
        cleanupScoreboardTeams();
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
            stopVillagerLookTask();
            cleanupScoreboardTeams();
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

    private void setupGameScoreboardTeams() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();

        // Clean up existing bb_ teams first to avoid conflicts/leaks
        for (org.bukkit.scoreboard.Team team : new ArrayList<>(board.getTeams())) {
            if (team.getName().startsWith("bb_")) {
                team.unregister();
            }
        }

        // Loop through all playing players
        for (Map.Entry<UUID, Integer> entry : playerTeams.entrySet()) {
            UUID playerUUID = entry.getKey();
            int teamId = entry.getValue();
            Player player = plugin.getServer().getPlayer(playerUUID);
            if (player == null || !player.isOnline()) continue;

            Team teamEnum = Team.byId(teamId);
            String teamName = "bb_team_" + teamId;
            org.bukkit.scoreboard.Team scoreboardTeam = board.getTeam(teamName);
            if (scoreboardTeam == null) {
                scoreboardTeam = board.registerNewTeam(teamName);
                scoreboardTeam.color(teamEnum.getAdventureColor());
                scoreboardTeam.setPrefix(teamEnum.getChatColor().toString());
                scoreboardTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.FOR_OWN_TEAM);
                scoreboardTeam.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
            }
            scoreboardTeam.addEntry(player.getName());

            // If player's name is colored, we can also set their display name and tab list name!
            player.displayName(net.kyori.adventure.text.Component.text(player.getName(), teamEnum.getAdventureColor()));
            player.playerListName(net.kyori.adventure.text.Component.text(player.getName(), teamEnum.getAdventureColor()));
        }

        // Also add villagers to their respective teams
        if (bases != null) {
            for (Map.Entry<Integer, TeamBase> entry : bases.entrySet()) {
                int teamId = entry.getKey();
                TeamBase base = entry.getValue();
                if (base.getVillager() != null) {
                    Team teamEnum = Team.byId(teamId);
                    String teamName = "bb_team_" + teamId;
                    org.bukkit.scoreboard.Team scoreboardTeam = board.getTeam(teamName);
                    if (scoreboardTeam != null) {
                        scoreboardTeam.addEntry(base.getVillager().getUniqueId().toString());
                    }

                    // Set custom name to show their team color and be visible
                    base.getVillager().setCustomName(teamEnum.getChatColor() + "Trader");
                    base.getVillager().setCustomNameVisible(true);
                }
            }
        }
    }

    public void cleanupScoreboardTeams() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (org.bukkit.scoreboard.Team team : new ArrayList<>(board.getTeams())) {
            if (team.getName().startsWith("bb_")) {
                team.unregister();
            }
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.displayName(null);
            player.playerListName(null);
        }
    }

    private void startVillagerLookTask() {
        stopVillagerLookTask();
        villagerTaskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (state != GameState.PLAYING || bases == null) {
                stopVillagerLookTask();
                return;
            }

            for (Map.Entry<Integer, TeamBase> entry : bases.entrySet()) {
                TeamBase base = entry.getValue();
                org.bukkit.entity.Villager villager = base.getVillager();
                if (villager == null || !villager.isValid()) continue;

                Location villLoc = villager.getLocation();
                Player nearest = null;
                double nearestDistSq = 25.0; // 5 blocks squared

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                    if (!player.getWorld().equals(villLoc.getWorld())) continue;

                    double distSq = player.getLocation().distanceSquared(villLoc);
                    if (distSq <= nearestDistSq) {
                        nearest = player;
                        nearestDistSq = distSq;
                    }
                }

                if (nearest != null) {
                    Location villLocEye = villager.getEyeLocation();
                    Location playerLoc = nearest.getEyeLocation();
                    org.bukkit.util.Vector direction = playerLoc.toVector().subtract(villLocEye.toVector());
                    Location temp = new Location(null, 0, 0, 0);
                    temp.setDirection(direction);
                    villager.setRotation(temp.getYaw(), temp.getPitch());
                }
            }
        }, 0L, 2L);
    }

    private void stopVillagerLookTask() {
        if (villagerTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(villagerTaskId);
            villagerTaskId = -1;
        }
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