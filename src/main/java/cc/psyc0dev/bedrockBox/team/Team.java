package cc.psyc0dev.bedrockBox.team;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;

public enum Team {
    RED(NamedTextColor.RED, ChatColor.RED, "§cRed"),
    BLUE(NamedTextColor.BLUE, ChatColor.BLUE, "§9Blue"),
    GREEN(NamedTextColor.GREEN, ChatColor.GREEN, "§2Green"),
    YELLOW(NamedTextColor.YELLOW, ChatColor.YELLOW, "§eYellow");

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