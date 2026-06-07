package cc.psyc0dev.bedrockBox;

import cc.psyc0dev.bedrockBox.command.GameCommand;
import cc.psyc0dev.bedrockBox.listener.GameListener;
import cc.psyc0dev.bedrockBox.game.GameManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class BedrockBox extends JavaPlugin {

    private GameManager gameManager;
    private GameCommand gameCommand;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        gameManager = new GameManager(this);
        gameManager.loadLobbyLocation();
        gameCommand = new GameCommand(this, gameManager);

        getCommand("bedrockbox").setExecutor(gameCommand);
        getCommand("bedrockbox").setTabCompleter(gameCommand);
        getServer().getPluginManager().registerEvents(new GameListener(this, gameManager), this);

        getLogger().info("BedrockBox enabled!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null && gameManager.getState() != GameManager.GameState.WAITING) {
            gameManager.stopGame();
        }
        getLogger().info("BedrockBox disabled!");
    }

    public GameManager getGameManager() {
        return gameManager;
    }
}