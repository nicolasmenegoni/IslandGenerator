package com.islanddayz.generator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.generator.LimitedRegion;

import java.util.Random;

public class HouseGenerator {
    private final CityGenerator cityGenerator;

    public HouseGenerator(CityGenerator cityGenerator) {
        this.cityGenerator = cityGenerator;
    }

    public void populateChunk(LimitedRegion region, int chunkX, int chunkZ, Random random) {
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;

        for (int localX = 2; localX < 14; localX += 6) {
            for (int localZ = 2; localZ < 14; localZ += 6) {
                int centerX = startX + localX;
                int centerZ = startZ + localZ;

                if (cityGenerator.getRoadType(centerX, centerZ) != CityGenerator.RoadType.NONE
                        || cityGenerator.cityInfluence(centerX, centerZ) < 0.25) {
                    continue;
                }

                if (Math.floorMod(centerX * 31 + centerZ * 17, 9) != 0) {
                    continue;
                }

                int lotW = 18 + random.nextInt(8);
                int lotL = 18 + random.nextInt(8);
                int minX = centerX - lotW / 2;
                int minZ = centerZ - lotL / 2;

                if (!isLotValid(region, minX, minZ, lotW, lotL)) {
                    continue;
                }

                int houseW = lotW - (4 + random.nextInt(4));
                int houseL = lotL - (4 + random.nextInt(4));
                int houseX = minX + 2 + random.nextInt(Math.max(1, lotW - houseW - 3));
                int houseZ = minZ + 2 + random.nextInt(Math.max(1, lotL - houseL - 3));

                int y = region.getHighestBlockYAt(centerX, centerZ);
                buildHouse(region, random, houseX, y, houseZ, houseW, houseL);
            }
        }
    }

    private boolean isLotValid(LimitedRegion region, int minX, int minZ, int w, int l) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int x = minX; x < minX + w; x++) {
            for (int z = minZ; z < minZ + l; z++) {
                int y = region.getHighestBlockYAt(x, z);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);

                Material ground = region.getType(x, y - 1, z);
                if (cityGenerator.getRoadType(x, z) != CityGenerator.RoadType.NONE
                        || cityGenerator.cityInfluence(x, z) < 0.22
                        || (ground != Material.GRASS_BLOCK && ground != Material.DIRT && ground != Material.COARSE_DIRT)) {
                    return false;
                }
            }
        }

        return (maxY - minY) <= 2;
    }

    private void buildHouse(LimitedRegion region, Random random, int x, int y, int z, int w, int l) {
        Material[] wallChoices = {
                Material.BRICKS, Material.SMOOTH_STONE, Material.OAK_PLANKS, Material.SPRUCE_PLANKS,
                Material.GREEN_WOOL, Material.CYAN_WOOL, Material.ORANGE_TERRACOTTA
        };
        Material[] roofChoices = {Material.DARK_OAK_STAIRS, Material.SPRUCE_STAIRS, Material.STONE_BRICK_STAIRS};
        Material[] windowChoices = {Material.GLASS, Material.TINTED_GLASS, Material.WHITE_STAINED_GLASS, Material.GRAY_STAINED_GLASS};
        Material[] doorChoices = {Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR, Material.DARK_OAK_DOOR, Material.JUNGLE_DOOR};

        Material wall = wallChoices[random.nextInt(wallChoices.length)];
        Material roof = roofChoices[random.nextInt(roofChoices.length)];
        Material window = windowChoices[random.nextInt(windowChoices.length)];
        Material doorMaterial = doorChoices[random.nextInt(doorChoices.length)];

        int floorHeight = 5 + random.nextInt(2);
        boolean secondFloor = random.nextDouble() < 0.35;

        buildShell(region, x, y, z, w, l, floorHeight, wall);
        carveWindows(region, random, x, y, z, w, l, floorHeight, window);

        boolean doubleDoor = random.nextDouble() < 0.35;
        placeDoor(region, x, y, z, w, doorMaterial, doubleDoor);

        if (secondFloor) {
            int secondY = y + floorHeight;
            buildFloor(region, x + 1, secondY, z + 1, w - 2, l - 2, Material.SMOOTH_STONE);
            buildShell(region, x + 1, secondY, z + 1, w - 2, l - 2, floorHeight - 1, wall);
            carveWindows(region, random, x + 1, secondY, z + 1, w - 2, l - 2, floorHeight - 1, window);
            buildRoof(region, x + 1, secondY + floorHeight - 1, z + 1, w - 2, l - 2, roof);
            buildStairsBetweenFloors(region, x + 2, y + 1, z + 2, floorHeight - 1);
            decorateInterior(region, random, x + 1, secondY, z + 1, w - 2, l - 2);
        } else {
            buildRoof(region, x, y + floorHeight, z, w, l, roof);
        }

        buildInteriorRooms(region, x, y, z, w, l);
        decorateInterior(region, random, x, y, z, w, l);
    }

    private void buildShell(LimitedRegion region, int x, int y, int z, int w, int l, int h, Material wall) {
        buildFloor(region, x, y, z, w, l, Material.SMOOTH_STONE);
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < l; dz++) {
                for (int dy = 1; dy <= h; dy++) {
                    boolean edge = dx == 0 || dz == 0 || dx == w - 1 || dz == l - 1;
                    region.setType(x + dx, y + dy, z + dz, edge ? wall : Material.AIR);
                }
            }
        }
    }

    private void buildFloor(LimitedRegion region, int x, int y, int z, int w, int l, Material floor) {
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < l; dz++) {
                region.setType(x + dx, y, z + dz, floor);
            }
        }
    }

    private void carveWindows(LimitedRegion region, Random random, int x, int y, int z, int w, int l, int h, Material window) {
        int windowY1 = y + 2;
        int windowY2 = Math.min(y + h - 1, y + 3);

        for (int dx = 2; dx < w - 2; dx += 3) {
            if (random.nextBoolean()) {
                region.setType(x + dx, windowY1, z, window);
                region.setType(x + dx, windowY2, z, window);
                region.setType(x + dx, windowY1, z + l - 1, window);
                region.setType(x + dx, windowY2, z + l - 1, window);
            }
        }
        for (int dz = 2; dz < l - 2; dz += 3) {
            if (random.nextBoolean()) {
                region.setType(x, windowY1, z + dz, window);
                region.setType(x, windowY2, z + dz, window);
                region.setType(x + w - 1, windowY1, z + dz, window);
                region.setType(x + w - 1, windowY2, z + dz, window);
            }
        }
    }

    private void placeDoor(LimitedRegion region, int x, int y, int z, int w, Material doorMaterial, boolean doubleDoor) {
        int doorX = x + (w / 2);
        setDoor(region, doorX, y + 1, z, doorMaterial, BlockFace.SOUTH, false);
        if (doubleDoor) {
            setDoor(region, doorX - 1, y + 1, z, doorMaterial, BlockFace.SOUTH, true);
        }
    }

    private void setDoor(LimitedRegion region, int x, int y, int z, Material doorMaterial, BlockFace facing, boolean hingeRight) {
        Door lower = (Door) Bukkit.createBlockData(doorMaterial);
        lower.setHalf(Bisected.Half.BOTTOM);
        lower.setFacing(facing);
        lower.setHinge(hingeRight ? Door.Hinge.RIGHT : Door.Hinge.LEFT);

        Door upper = (Door) Bukkit.createBlockData(doorMaterial);
        upper.setHalf(Bisected.Half.TOP);
        upper.setFacing(facing);
        upper.setHinge(hingeRight ? Door.Hinge.RIGHT : Door.Hinge.LEFT);

        region.setBlockData(x, y, z, lower);
        region.setBlockData(x, y + 1, z, upper);
    }

    private void buildRoof(LimitedRegion region, int x, int roofY, int z, int w, int l, Material roofStair) {
        // Overhang 1 bloco para frente das paredes.
        int minX = x - 1;
        int maxX = x + w;
        int minZ = z - 1;
        int maxZ = z + l;

        for (int xx = minX; xx <= maxX; xx++) {
            placeStair(region, xx, roofY, minZ, roofStair, BlockFace.NORTH);
            placeStair(region, xx, roofY, maxZ, roofStair, BlockFace.SOUTH);
        }
        for (int zz = minZ + 1; zz < maxZ; zz++) {
            placeStair(region, minX, roofY, zz, roofStair, BlockFace.WEST);
            placeStair(region, maxX, roofY, zz, roofStair, BlockFace.EAST);
        }

        for (int xx = x; xx < x + w; xx++) {
            for (int zz = z; zz < z + l; zz++) {
                region.setType(xx, roofY + 1, zz, Material.SPRUCE_SLAB);
            }
        }
    }

    private void placeStair(LimitedRegion region, int x, int y, int z, Material stairMat, BlockFace facing) {
        Stairs stair = (Stairs) Bukkit.createBlockData(stairMat);
        stair.setFacing(facing);
        stair.setHalf(Stairs.Half.BOTTOM);
        region.setBlockData(x, y, z, stair);
    }

    private void buildStairsBetweenFloors(LimitedRegion region, int x, int y, int z, int height) {
        for (int i = 0; i < height; i++) {
            region.setType(x + i, y + i, z, Material.SPRUCE_STAIRS);
        }
    }

    private void buildInteriorRooms(LimitedRegion region, int x, int y, int z, int w, int l) {
        int splitX = x + w / 2;
        int splitZ = z + l / 2;

        for (int dz = 1; dz < l - 1; dz++) {
            if (dz == l / 3 || dz == (l * 2) / 3) {
                continue;
            }
            region.setType(splitX, y + 1, z + dz, Material.SPRUCE_PLANKS);
            region.setType(splitX, y + 2, z + dz, Material.SPRUCE_PLANKS);
        }

        for (int dx = 1; dx < w - 1; dx++) {
            if (dx == w / 3 || dx == (w * 2) / 3) {
                continue;
            }
            region.setType(x + dx, y + 1, splitZ, Material.SPRUCE_PLANKS);
            region.setType(x + dx, y + 2, splitZ, Material.SPRUCE_PLANKS);
        }
    }

    private void decorateInterior(LimitedRegion region, Random random, int x, int y, int z, int w, int l) {
        Material[] carpets = {Material.RED_CARPET, Material.GREEN_CARPET, Material.CYAN_CARPET, Material.GRAY_CARPET};

        region.setType(x + 2, y + 1, z + 2, Material.CRAFTING_TABLE);
        region.setType(x + w - 3, y + 1, z + 2, Material.FURNACE);
        region.setType(x + w - 4, y + 1, z + 2, random.nextBoolean() ? Material.CHEST : Material.BARREL);

        placeBed(region, x + 2, y + 1, z + l - 4, BlockFace.SOUTH);

        region.setType(x + w - 3, y + 1, z + l - 3, Material.CAULDRON);
        if (random.nextBoolean()) {
            region.setType(x + w - 4, y + 1, z + l - 3, random.nextBoolean() ? Material.WAXED_OXIDIZED_COPPER : Material.WAXED_EXPOSED_COPPER);
        }

        for (int dx = 3; dx < w - 3; dx += 3) {
            for (int dz = 3; dz < l - 3; dz += 3) {
                if (random.nextDouble() < 0.35) {
                    region.setType(x + dx, y + 1, z + dz, carpets[random.nextInt(carpets.length)]);
                }
            }
        }

        region.setType(x + w / 2, y + 4, z + l / 2, Material.LANTERN);
        if (random.nextBoolean()) {
            region.setType(x + 1, y + 3, z + l / 2, Material.WALL_TORCH);
        }
    }

    private void placeBed(LimitedRegion region, int x, int y, int z, BlockFace facing) {
        int hx = x + facing.getModX();
        int hz = z + facing.getModZ();

        if (!region.getType(x, y, z).isAir() || !region.getType(hx, y, hz).isAir()) {
            return;
        }

        Bed foot = (Bed) Bukkit.createBlockData(Material.RED_BED);
        foot.setPart(Bed.Part.FOOT);
        foot.setFacing(facing);

        Bed head = (Bed) Bukkit.createBlockData(Material.RED_BED);
        head.setPart(Bed.Part.HEAD);
        head.setFacing(facing);

        region.setBlockData(x, y, z, foot);
        region.setBlockData(hx, y, hz, head);
    }
}
