package cc.psyc0dev.bedrockBox.team;

import org.bukkit.Location;
public class TeamBase {
    private final Location spawnLocation;
    private final Location bedLocation;
    private final Location villagerLocation;

    public TeamBase(Location spawnLocation, Location bedLocation, Location villagerLocation) {
        this.spawnLocation = spawnLocation;
        this.bedLocation = bedLocation;
        this.villagerLocation = villagerLocation;
    }

    public Location getSpawnLocation() { return spawnLocation; }
    public Location getBedLocation() { return bedLocation; }
    public Location getVillagerLocation() { return villagerLocation; }
}