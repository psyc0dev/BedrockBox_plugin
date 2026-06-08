package cc.psyc0dev.bedrockBox.game;

import cc.psyc0dev.bedrockBox.team.TeamBase;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Villager;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class BoxGenerator {

    public static final int BOX_SIZE = 64;
    public static final int BOX_HEIGHT = 64;
    public static final int BOX_MIN_Y = 4;
    public static final int HALF = BOX_SIZE / 2;

    private static final int ROOM_SIZE = 7;
    private static final int ROOM_HEIGHT = 5;
    private static final int ROOM_MARGIN = ROOM_SIZE / 2 + 3;

    private static final AtomicInteger seedCounter = new AtomicInteger(0);
    private final Random random = new Random(System.nanoTime() ^ Runtime.getRuntime().freeMemory() ^ seedCounter.incrementAndGet());
    private int centerX, centerZ;

    public void setCenter(int x, int z) {
        this.centerX = x;
        this.centerZ = z;
    }

    public Map<Integer, TeamBase> generateFull(World world, int roomCount) {
        int minX = centerX - HALF, maxX = centerX + HALF - 1;
        int minZ = centerZ - HALF, maxZ = centerZ + HALF - 1;
        int maxY = BOX_MIN_Y + BOX_HEIGHT - 1;

        generateShellAndInterior(world, minX, maxX, minZ, maxZ, maxY);
        return generateRooms(world, minX, maxX, minZ, maxZ, roomCount);
    }

    public int randomCenterOffset() {
        return (random.nextInt(2001) - 1000) / 32 * 32;
    }

    private void generateShellAndInterior(World world, int minX, int maxX, int minZ, int maxZ, int maxY) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = BOX_MIN_Y; y <= maxY; y++) {
                    boolean isEdge = x == minX || x == maxX || z == minZ || z == maxZ
                            || y == BOX_MIN_Y || y == maxY;

                    Block block = world.getBlockAt(x, y, z);
                    Material target = isEdge ? Material.BEDROCK : (random.nextDouble() < 0.05 ? Material.EMERALD_ORE : Material.STONE);
                    if (block.getType() != target) {
                        block.setType(target, false);
                    }
                }
            }
        }
    }

    private Map<Integer, TeamBase> generateRooms(World world, int minX, int maxX, int minZ, int maxZ, int roomCount) {
        Map<Integer, TeamBase> bases = new HashMap<>();

        int wallUsable = BOX_SIZE - 2 * ROOM_MARGIN;
        int totalUsable = 4 * wallUsable;
        int startOffset = random.nextInt(totalUsable);

        for (int i = 0; i < roomCount; i++) {
            int pos = (startOffset + (int) ((i + 0.5) * totalUsable / roomCount)) % totalUsable;
            int wallIndex = pos / wallUsable;
            int offset = ROOM_MARGIN + (pos % wallUsable);

            int wx, wz, dir;
            BlockFace bedFacing;
            boolean useXBuilder;

            switch (wallIndex) {
                case 0:
                    wx = minX + offset; wz = minZ;
                    dir = -1; bedFacing = BlockFace.SOUTH; useXBuilder = false;
                    break;
                case 1:
                    wx = maxX; wz = minZ + offset;
                    dir = 1; bedFacing = BlockFace.WEST; useXBuilder = true;
                    break;
                case 2:
                    wx = maxX - offset; wz = maxZ;
                    dir = 1; bedFacing = BlockFace.NORTH; useXBuilder = false;
                    break;
                default:
                    wx = minX; wz = maxZ - offset;
                    dir = -1; bedFacing = BlockFace.EAST; useXBuilder = true;
                    break;
            }

            int floorY = randomRoomY();
            TeamBase base = useXBuilder
                    ? buildRoomX(world, wx, wz, dir, floorY, bedFacing)
                    : buildRoomZ(world, wx, wz, dir, floorY, bedFacing);
            bases.put(i, base);
        }

        return bases;
    }

    private int randomRoomY() {
        return BOX_MIN_Y + 4 + random.nextInt(BOX_HEIGHT - ROOM_HEIGHT - 6);
    }

    private TeamBase buildRoomZ(World world, int cx, int wallZ, int dir, int floorY, BlockFace bedFacing) {
        int startZ = wallZ + dir;
        int endZ = wallZ + dir * ROOM_SIZE;
        if (dir < 0) { int tmp = startZ; startZ = endZ; endZ = tmp; }

        int half = ROOM_SIZE / 2;
        int ceilY = floorY + ROOM_HEIGHT - 1;
        int bedY = floorY;

        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = startZ; z <= endZ; z++) {
                for (int y = floorY; y <= ceilY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }

        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = startZ; z <= endZ; z++) {
                Block b1 = world.getBlockAt(x, floorY - 1, z);
                if (b1.getType() != Material.BEDROCK) b1.setType(Material.BEDROCK, false);
                Block b2 = world.getBlockAt(x, ceilY, z);
                if (b2.getType() != Material.BEDROCK) b2.setType(Material.BEDROCK, false);
            }
        }

        for (int z = startZ; z <= endZ; z++) {
            for (int y = floorY; y < ceilY; y++) {
                Block b1 = world.getBlockAt(cx - half, y, z);
                if (b1.getType() != Material.BEDROCK) b1.setType(Material.BEDROCK, false);
                Block b2 = world.getBlockAt(cx + half, y, z);
                if (b2.getType() != Material.BEDROCK) b2.setType(Material.BEDROCK, false);
            }
        }

        int backZ = dir < 0 ? startZ : endZ;
        for (int x = cx - half; x <= cx + half; x++) {
            for (int y = floorY; y < ceilY; y++) {
                Block block = world.getBlockAt(x, y, backZ);
                if (block.getType() != Material.BEDROCK) block.setType(Material.BEDROCK, false);
            }
        }

        Block entrance1 = world.getBlockAt(cx, floorY, wallZ);
        if (entrance1.getType() != Material.AIR) entrance1.setType(Material.AIR, false);
        Block entrance2 = world.getBlockAt(cx, floorY + 1, wallZ);
        if (entrance2.getType() != Material.AIR) entrance2.setType(Material.AIR, false);

        int bedZ = dir < 0 ? startZ + 1 : endZ - 1;
        Location bedLoc = placeBed(world, cx, bedY, bedZ, bedFacing);

        int villX = cx - half + 1;
        int villZ = backZ - dir;
        Villager villager = spawnVillager(world, villX, bedY, villZ);
        Location villagerLoc = villager.getLocation();

        int spawnZ = (wallZ + bedZ) / 2;
        Location spawnLoc = new Location(world, cx + 0.5, bedY + 0.5, spawnZ + 0.5);

        return new TeamBase(spawnLoc, bedLoc, villagerLoc, villager);
    }

    private TeamBase buildRoomX(World world, int wallX, int cz, int dir, int floorY, BlockFace bedFacing) {
        int startX = wallX + dir;
        int endX = wallX + dir * ROOM_SIZE;
        if (dir < 0) { int tmp = startX; startX = endX; endX = tmp; }

        int half = ROOM_SIZE / 2;
        int ceilY = floorY + ROOM_HEIGHT - 1;
        int bedY = floorY;

        for (int x = startX; x <= endX; x++) {
            for (int z = cz - half; z <= cz + half; z++) {
                for (int y = floorY; y <= ceilY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }

        for (int x = startX; x <= endX; x++) {
            for (int z = cz - half; z <= cz + half; z++) {
                Block b1 = world.getBlockAt(x, floorY - 1, z);
                if (b1.getType() != Material.BEDROCK) b1.setType(Material.BEDROCK, false);
                Block b2 = world.getBlockAt(x, ceilY, z);
                if (b2.getType() != Material.BEDROCK) b2.setType(Material.BEDROCK, false);
            }
        }

        for (int x = startX; x <= endX; x++) {
            for (int y = floorY; y < ceilY; y++) {
                Block b1 = world.getBlockAt(x, y, cz - half);
                if (b1.getType() != Material.BEDROCK) b1.setType(Material.BEDROCK, false);
                Block b2 = world.getBlockAt(x, y, cz + half);
                if (b2.getType() != Material.BEDROCK) b2.setType(Material.BEDROCK, false);
            }
        }

        int backX = dir < 0 ? startX : endX;
        for (int z = cz - half; z <= cz + half; z++) {
            for (int y = floorY; y < ceilY; y++) {
                Block block = world.getBlockAt(backX, y, z);
                if (block.getType() != Material.BEDROCK) block.setType(Material.BEDROCK, false);
            }
        }

        Block entrance1 = world.getBlockAt(wallX, floorY, cz);
        if (entrance1.getType() != Material.AIR) entrance1.setType(Material.AIR, false);
        Block entrance2 = world.getBlockAt(wallX, floorY + 1, cz);
        if (entrance2.getType() != Material.AIR) entrance2.setType(Material.AIR, false);

        int bedX = dir < 0 ? startX + 1 : endX - 1;
        Location bedLoc = placeBed(world, bedX, bedY, cz, bedFacing);

        int villX = backX - dir;
        int villZ = cz - half + 1;
        Villager villager = spawnVillager(world, villX, bedY, villZ);
        Location villagerLoc = villager.getLocation();

        int spawnX = (wallX + bedX) / 2;
        Location spawnLoc = new Location(world, spawnX + 0.5, bedY + 0.5, cz + 0.5);

        return new TeamBase(spawnLoc, bedLoc, villagerLoc, villager);
    }

    private Location placeBed(World world, int x, int y, int z, BlockFace facing) {
        Block footBlock = world.getBlockAt(x, y, z);
        Bed footData = (Bed) Material.RED_BED.createBlockData();
        footData.setPart(Bed.Part.FOOT);
        footData.setFacing(facing);
        footBlock.setBlockData(footData);

        int headX = x + facing.getModX();
        int headZ = z + facing.getModZ();
        Block headBlock = world.getBlockAt(headX, y, headZ);
        Bed headData = (Bed) Material.RED_BED.createBlockData();
        headData.setPart(Bed.Part.HEAD);
        headData.setFacing(facing);
        headBlock.setBlockData(headData);

        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }

    private Villager spawnVillager(World world, int x, int y, int z) {
        Location loc = new Location(world, x + 0.5, y, z + 0.5);
        Villager villager = (Villager) world.spawnEntity(loc, EntityType.VILLAGER);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setCustomName("Trader");
        villager.setCustomNameVisible(false);
        villager.setCollidable(false);
        villager.setRecipes(new java.util.ArrayList<>());
        return villager;
    }

    public void clearBox(World world) {
        int minX = centerX - HALF, maxX = centerX + HALF - 1;
        int minZ = centerZ - HALF, maxZ = centerZ + HALF - 1;
        int maxY = BOX_MIN_Y + BOX_HEIGHT - 1;
        int pad = ROOM_SIZE + 2;

        for (int x = minX - pad; x <= maxX + pad; x++) {
            for (int z = minZ - pad; z <= maxZ + pad; z++) {
                for (int y = BOX_MIN_Y - 1; y <= maxY + 1; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    public int getCenterX() { return centerX; }
    public int getCenterZ() { return centerZ; }
}