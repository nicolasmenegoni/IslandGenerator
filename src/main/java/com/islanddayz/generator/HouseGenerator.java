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
                        || cityGenerator.cityInfluence(centerX, centerZ) < 0.25
                        || Math.floorMod(centerX * 31 + centerZ * 17, 9) != 0) {
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
        return (maxY - minY) <= 3;
    }

    private void buildHouse(LimitedRegion region, Random random, int x, int y, int z, int w, int l) {
        Material[] wallChoices = {Material.BRICKS, Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.GREEN_WOOL, Material.CYAN_WOOL, Material.ORANGE_TERRACOTTA};
        Material[] roofChoices = {Material.DARK_OAK_STAIRS, Material.SPRUCE_STAIRS, Material.STONE_BRICK_STAIRS};
        Material[] floorChoices = {Material.SMOOTH_STONE, Material.STONE, Material.STONE_BRICKS, Material.POLISHED_GRANITE, Material.ANDESITE};
        Material[] windowChoices = {Material.GLASS, Material.TINTED_GLASS, Material.WHITE_STAINED_GLASS, Material.GRAY_STAINED_GLASS};
        Material[] doorChoices = {Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR, Material.DARK_OAK_DOOR, Material.JUNGLE_DOOR};

        Material wall = wallChoices[random.nextInt(wallChoices.length)];
        Material roofStair = roofChoices[random.nextInt(roofChoices.length)];
        Material floor = floorChoices[random.nextInt(floorChoices.length)];
        Material window = windowChoices[random.nextInt(windowChoices.length)];
        Material doorMaterial = doorChoices[random.nextInt(doorChoices.length)];

        int floorHeight = 5 + random.nextInt(2);
        boolean secondFloor = random.nextDouble() < 0.35;

        buildAdaptiveFloor(region, x, y, z, w, l, floor);
        buildOuterWalls(region, x, y, z, w, l, floorHeight, wall);
        carveWindows(region, random, x, y, z, w, l, floorHeight, window);

        boolean doubleDoor = random.nextDouble() < 0.35;
        placeDoor(region, x, y, z, w, doorMaterial, doubleDoor);
        buildInteriorRooms(region, x, y, z, w, l, floorHeight, wall, doubleDoor);
        decorateInterior(region, random, x, y, z, w, l);

        if (secondFloor) {
            int secondY = y + floorHeight;
            buildAdaptiveFloor(region, x + 1, secondY, z + 1, w - 2, l - 2, floorChoices[random.nextInt(floorChoices.length)]);
            buildOuterWalls(region, x + 1, secondY, z + 1, w - 2, l - 2, floorHeight - 1, wall);
            carveWindows(region, random, x + 1, secondY, z + 1, w - 2, l - 2, floorHeight - 1, window);
            buildInteriorRooms(region, x + 1, secondY, z + 1, w - 2, l - 2, floorHeight - 1, wall, false);
            decorateInterior(region, random, x + 1, secondY, z + 1, w - 2, l - 2);
            buildStairsBetweenFloors(region, x + 2, y + 1, z + 2, floorHeight - 1);
            buildTriangularRoof(region, x + 1, secondY + floorHeight - 1, z + 1, w - 2, l - 2, roofStair, wall);
        } else {
            buildTriangularRoof(region, x, y + floorHeight, z, w, l, roofStair, wall);
        }
    }

    private void buildAdaptiveFloor(LimitedRegion region, int x, int y, int z, int w, int l, Material floor) {
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < l; dz++) {
                int wx = x + dx;
                int wz = z + dz;
                int groundY = region.getHighestBlockYAt(wx, wz) - 1;
                for (int yy = groundY + 1; yy < y; yy++) {
                    region.setType(wx, yy, wz, Material.DIRT);
                }
                region.setType(wx, y, wz, floor);
            }
        }
    }

    private void buildOuterWalls(LimitedRegion region, int x, int y, int z, int w, int l, int h, Material wall) {
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < l; dz++) {
                if (dx != 0 && dz != 0 && dx != w - 1 && dz != l - 1) {
                    continue;
                }
                for (int dy = 1; dy <= h; dy++) {
                    region.setType(x + dx, y + dy, z + dz, wall);
                }
            }
        }
    }

    private void carveWindows(LimitedRegion region, Random random, int x, int y, int z, int w, int l, int h, Material window) {
        int y1 = y + 2;
        int y2 = Math.min(y + h - 1, y + 3);
        for (int dx = 2; dx < w - 2; dx += 3) {
            if (random.nextBoolean()) {
                region.setType(x + dx, y1, z, window);
                region.setType(x + dx, y2, z, window);
                region.setType(x + dx, y1, z + l - 1, window);
                region.setType(x + dx, y2, z + l - 1, window);
            }
        }
        for (int dz = 2; dz < l - 2; dz += 3) {
            if (random.nextBoolean()) {
                region.setType(x, y1, z + dz, window);
                region.setType(x, y2, z + dz, window);
                region.setType(x + w - 1, y1, z + dz, window);
                region.setType(x + w - 1, y2, z + dz, window);
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

    private void setDoor(LimitedRegion region, int x, int y, int z, Material mat, BlockFace facing, boolean hingeRight) {
        Door lower = (Door) Bukkit.createBlockData(mat);
        lower.setHalf(Bisected.Half.BOTTOM);
        lower.setFacing(facing);
        lower.setHinge(hingeRight ? Door.Hinge.RIGHT : Door.Hinge.LEFT);
        Door upper = (Door) Bukkit.createBlockData(mat);
        upper.setHalf(Bisected.Half.TOP);
        upper.setFacing(facing);
        upper.setHinge(hingeRight ? Door.Hinge.RIGHT : Door.Hinge.LEFT);
        region.setBlockData(x, y, z, lower);
        region.setBlockData(x, y + 1, z, upper);
    }

    private void buildInteriorRooms(LimitedRegion region, int x, int y, int z, int w, int l, int h, Material wall, boolean hasDoubleDoor) {
        int doorZoneStart = x + w / 2 - (hasDoubleDoor ? 1 : 0);
        int doorZoneEnd = x + w / 2;

        int wallX = x + (w / 3);
        for (int zz = z + 2; zz < z + l - 2; zz++) {
            if (zz == z + l / 2) continue;
            for (int yy = y + 1; yy <= y + h; yy++) {
                region.setType(wallX, yy, zz, wall);
            }
        }

        int wallZ = z + (l * 2 / 3);
        for (int xx = x + 2; xx < x + w - 2; xx++) {
            if (xx >= doorZoneStart && xx <= doorZoneEnd) continue;
            if (xx == wallX) continue;
            for (int yy = y + 1; yy <= y + h; yy++) {
                region.setType(xx, yy, wallZ, wall);
            }
        }
    }

    private void buildStairsBetweenFloors(LimitedRegion region, int x, int y, int z, int height) {
        for (int i = 0; i < height; i++) {
            Stairs stair = (Stairs) Bukkit.createBlockData(Material.SPRUCE_STAIRS);
            stair.setFacing(BlockFace.EAST);
            stair.setHalf(Stairs.Half.BOTTOM);
            region.setBlockData(x + i, y + i, z, stair);
        }
    }

    private void buildTriangularRoof(LimitedRegion region, int x, int roofBaseY, int z, int w, int l, Material roofStair, Material fill) {
        boolean alongX = w >= l;
        int layers = (alongX ? l : w) / 2 + 1;

        for (int layer = 0; layer < layers; layer++) {
            int y = roofBaseY + layer;
            int minX = x - 1 + (alongX ? 0 : layer);
            int maxX = x + w + (alongX ? 0 : -1 - layer);
            int minZ = z - 1 + (alongX ? layer : 0);
            int maxZ = z + l + (alongX ? -1 - layer : 0);

            if (minX > maxX || minZ > maxZ) {
                break;
            }

            for (int xx = minX; xx <= maxX; xx++) {
                for (int zz = minZ; zz <= maxZ; zz++) {
                    boolean edge = (alongX && (zz == minZ || zz == maxZ)) || (!alongX && (xx == minX || xx == maxX));
                    if (edge) {
                        BlockFace facing;
                        if (alongX) {
                            facing = (zz == minZ) ? BlockFace.NORTH : BlockFace.SOUTH;
                        } else {
                            facing = (xx == minX) ? BlockFace.WEST : BlockFace.EAST;
                        }
                        Stairs stair = (Stairs) Bukkit.createBlockData(roofStair);
                        stair.setFacing(facing);
                        stair.setHalf(Stairs.Half.BOTTOM);
                        region.setBlockData(xx, y, zz, stair);
                    } else {
                        region.setType(xx, y, zz, fill);
                    }
                }
            }
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
            region.setType(x + w - 4, y + 1, z + l - 3,
                    random.nextBoolean() ? Material.WAXED_OXIDIZED_COPPER : Material.WAXED_EXPOSED_COPPER);
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
