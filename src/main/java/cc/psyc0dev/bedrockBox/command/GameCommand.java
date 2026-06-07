package cc.psyc0dev.bedrockBox.command;

import cc.psyc0dev.bedrockBox.BedrockBox;
import cc.psyc0dev.bedrockBox.game.GameManager;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class GameCommand implements CommandExecutor, TabCompleter {

    private final BedrockBox plugin;
    private final GameManager gameManager;

    public GameCommand(BedrockBox plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§6[BedrockBox] §eUsage: /bedrockbox <start|stop|setlobby>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> handleStart(sender, args);
            case "stop" -> handleStop(sender);
            case "setlobby" -> handleSetLobby(sender);
            default -> sender.sendMessage("§6[BedrockBox] §eUnknown subcommand. Use: start, stop, setlobby");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            for (String s : List.of("start", "stop", "setlobby")) {
                if (s.startsWith(args[0].toLowerCase())) subs.add(s);
            }
            return subs;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            List<String> modes = new ArrayList<>();
            for (String m : List.of("solo", "duo", "test")) {
                if (m.startsWith(args[1].toLowerCase())) modes.add(m);
            }
            return modes;
        }
        return List.of();
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bedrockbox.admin")) {
            sender.sendMessage("§cYou don't have permission!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§6[BedrockBox] §eUsage: /bedrockbox start <solo|duo|test>");
            return;
        }

        String modeStr = args[1].toLowerCase();
        GameManager.GameMode mode;
        switch (modeStr) {
            case "solo" -> mode = GameManager.GameMode.SOLO;
            case "duo" -> mode = GameManager.GameMode.DUO;
            case "test" -> mode = GameManager.GameMode.TEST;
            default -> {
                sender.sendMessage("§6[BedrockBox] §eUsage: /bedrockbox start <solo|duo|test>");
                return;
            }
        }

        if (gameManager.getState() != GameManager.GameState.WAITING) {
            sender.sendMessage("§cA game is already running!");
            return;
        }

        World world;
        if (sender instanceof Player player) {
            world = player.getWorld();
        } else {
            sender.sendMessage("§cConsole must specify a world or run in-game.");
            return;
        }

        boolean started = gameManager.startGame(world, mode);
        if (started) {
            sender.sendMessage("§aCountdown started! Game begins in 10 seconds.");
        } else {
            int online = plugin.getServer().getOnlinePlayers().size();
            if (mode == GameManager.GameMode.SOLO) {
                sender.sendMessage("§cNeed 2-10 players online to start solo.");
            } else if (mode == GameManager.GameMode.DUO) {
                sender.sendMessage("§cNeed 4-20 players (even) online to start duo.");
            } else {
                sender.sendMessage("§cNeed at least 1 player online to start test.");
            }
        }
    }

    private void handleStop(CommandSender sender) {
        if (!sender.hasPermission("bedrockbox.admin")) {
            sender.sendMessage("§cYou don't have permission!");
            return;
        }
        gameManager.stopGame();
    }

    private void handleSetLobby(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can set the lobby location.");
            return;
        }
        if (!sender.hasPermission("bedrockbox.admin")) {
            sender.sendMessage("§cYou don't have permission!");
            return;
        }
        gameManager.setLobbyLocation(player.getLocation());
        player.sendMessage("§a[BedrockBox] §aLobby location set!");
    }
}