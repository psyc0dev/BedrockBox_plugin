package cc.psyc0dev.bedrockBox.team;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;

public enum Team {
    RED(NamedTextColor.RED, ChatColor.RED, "§cRed"),
    BLUE(NamedTextColor.BLUE, ChatColor.BLUE, "§9Blue"),
    GREEN(NamedTextColor.GREEN, ChatColor.GREEN, "§2Green"),
    YELLOW(NamedTextColor.YELLOW, ChatColor.YELLOW, "§eYellow"),
    AQUA(NamedTextColor.AQUA, ChatColor.AQUA, "§bAqua"),
    PINK(NamedTextColor.LIGHT_PURPLE, ChatColor.LIGHT_PURPLE, "§dPink"),
    GOLD(NamedTextColor.GOLD, ChatColor.GOLD, "§6Gold"),
    DARK_AQUA(NamedTextColor.DARK_AQUA, ChatColor.DARK_AQUA, "§3Cyan"),
    DARK_PURPLE(NamedTextColor.DARK_PURPLE, ChatColor.DARK_PURPLE, "§5Purple"),
    GRAY(NamedTextColor.GRAY, ChatColor.GRAY, "§7Gray");

    private final NamedTextColor adventureColor;
    private final ChatColor chatColor;
    private final String displayName;

    Team(NamedTextColor adventureColor, ChatColor chatColor, String displayName) {
        this.adventureColor = adventureColor;
        this.chatColor = chatColor;
        this.displayName = displayName;
    }

    public NamedTextColor getAdventureColor() { return adventureColor; }
    public ChatColor getChatColor() { return chatColor; }
    public String getDisplayName() { return displayName; }

    public static Team byId(int id) {
        Team[] values = values();
        return values[id % values.length];
    }
}