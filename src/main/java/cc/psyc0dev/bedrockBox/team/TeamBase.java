package cc.psyc0dev.bedrockBox.team;

import org.bukkit.Location;
import org.bukkit.entity.Villager;

public class TeamBase {
    private final Location spawnLocation;
    private final Location bedLocation;
    private final Location villagerLocation;
    private final Villager villager;

    public TeamBase(Location spawnLocation, Location bedLocation, Location villagerLocation, Villager villager) {
        this.spawnLocation = spawnLocation;
        this.bedLocation = bedLocation;
        this.villagerLocation = villagerLocation;
        this.villager = villager;
    }

    public Location getSpawnLocation() { return spawnLocation; }
    public Location getBedLocation() { return bedLocation; }
    public Location getVillagerLocation() { return villagerLocation; }
    public Villager getVillager() { return villager; }
}