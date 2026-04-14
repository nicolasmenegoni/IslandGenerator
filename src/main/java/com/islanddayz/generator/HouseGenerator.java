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
        int[] slots = {4, 12};
        for (int localX : slots) {
            for (int localZ : slots) {
                int centerX = startX + localX;
                int centerZ = startZ + localZ;
                if (!isCenteredLotCandidate(centerX, centerZ)) {
                    continue;
                }

                int lotW = 18 + random.nextInt(6);
                int lotL = 18 + random.nextInt(6);
                int minX = centerX - lotW / 2;
                int minZ = centerZ - lotL / 2;
                LotInfo lotInfo = analyzeLot(region, minX, minZ, lotW, lotL, centerX, centerZ);
                if (!lotInfo.buildable()) {
                    continue;
                }

                int houseW = lotW - (4 + random.nextInt(4));
                int houseL = lotL - (4 + random.nextInt(4));
                int houseX = minX + 2 + random.nextInt(Math.max(1, lotW - houseW - 3));
                int houseZ = minZ + 2 + random.nextInt(Math.max(1, lotL - houseL - 3));
                prepareLotSurface(region, minX, minZ, lotW, lotL, lotInfo.minY(), 20);
                buildHouse(region, random, houseX, lotInfo.minY(), houseZ, houseW, houseL);
            }
        }
    }

    private boolean isCenteredLotCandidate(int x, int z) {
        if (cityGenerator.cityInfluence(x, z) < 0.55 || cityGenerator.getRoadType(x, z) != CityGenerator.RoadType.NONE) {
            return false;
        }

        int north = distanceToRoad(x, z, 0, -1, 48);
        int south = distanceToRoad(x, z, 0, 1, 48);
        int west = distanceToRoad(x, z, -1, 0, 48);
        int east = distanceToRoad(x, z, 1, 0, 48);

        boolean hasRoads = north > 3 && south > 3 && west > 3 && east > 3;
        boolean balanced = Math.abs(north - south) <= 8 && Math.abs(west - east) <= 8;
        return hasRoads && balanced;
    }

    private int distanceToRoad(int x, int z, int stepX, int stepZ, int maxDist) {
        for (int dist = 1; dist <= maxDist; dist++) {
            if (cityGenerator.getRoadType(x + stepX * dist, z + stepZ * dist) != CityGenerator.RoadType.NONE) {
                return dist;
            }
        }
        return maxDist + 1;
    }

    private LotInfo analyzeLot(LimitedRegion region, int minX, int minZ, int w, int l, int centerX, int centerZ) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int x = minX; x < minX + w; x++) {
            for (int z = minZ; z < minZ + l; z++) {
                if (cityGenerator.getRoadType(x, z) != CityGenerator.RoadType.NONE) {
                    return new LotInfo(false, 0, 0);
                }
                int y = region.getHighestBlockYAt(x, z);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        boolean nearCityEdge = cityGenerator.cityInfluence(centerX, centerZ) < 0.62;
        boolean tooSteep = (maxY - minY) > 4;
        return new LotInfo(!nearCityEdge && !tooSteep, minY, maxY);
    }

    private void prepareLotSurface(LimitedRegion region, int minX, int minZ, int w, int l, int baseY, int extraHeight) {
        int topY = baseY + extraHeight;
        for (int x = minX; x < minX + w; x++) {
            for (int z = minZ; z < minZ + l; z++) {
                topY = Math.max(topY, region.getHighestBlockYAt(x, z) + 2);
            }
        }

        for (int x = minX; x < minX + w; x++) {
            for (int z = minZ; z < minZ + l; z++) {
                region.setType(x, baseY, z, Material.DIRT);
                for (int yy = baseY + 1; yy <= topY; yy++) {
                    region.setType(x, yy, z, Material.AIR);
                }
            }
        }
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

        clearConstructionVolume(region, x, y, z, w, l, secondFloor ? (floorHeight * 2) + 8 : floorHeight + 8);
        buildAdaptiveFloor(region, x, y, z, w, l, floor);
        buildOuterWalls(region, x, y, z, w, l, floorHeight, wall);
        decorateOuterWalls(region, random, x, y, z, w, l, floorHeight, wall);
        carveWindows(region, random, x, y, z, w, l, floorHeight, window);

        boolean doubleDoor = random.nextDouble() < 0.35;
        placeDoor(region, x, y, z, w, doorMaterial, doubleDoor);
        buildInteriorRooms(region, x, y, z, w, l, floorHeight, wall, doorMaterial, doubleDoor);
        decorateInterior(region, random, x, y, z, w, l);

        if (secondFloor) {
            int secondY = y + floorHeight;
            buildAdaptiveFloor(region, x + 1, secondY, z + 1, w - 2, l - 2, floorChoices[random.nextInt(floorChoices.length)]);
            buildOuterWalls(region, x + 1, secondY, z + 1, w - 2, l - 2, floorHeight - 1, wall);
            carveWindows(region, random, x + 1, secondY, z + 1, w - 2, l - 2, floorHeight - 1, window);
            buildInteriorRooms(region, x + 1, secondY, z + 1, w - 2, l - 2, floorHeight - 1, wall, doorMaterial, false);
            decorateInterior(region, random, x + 1, secondY, z + 1, w - 2, l - 2);
            buildStairsBetweenFloors(region, x + 2, y + 1, z + 2, floorHeight - 1);
            buildTriangularRoof(region, x + 1, secondY + floorHeight - 1, z + 1, w - 2, l - 2, roofStair, wall);
        } else {
            buildTriangularRoof(region, x, y + floorHeight, z, w, l, roofStair, wall);
        }
    }

    private void clearConstructionVolume(LimitedRegion region, int x, int y, int z, int w, int l, int minClearance) {
        int topY = y + minClearance;
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < l; dz++) {
                int wx = x + dx;
                int wz = z + dz;
                topY = Math.max(topY, region.getHighestBlockYAt(wx, wz) + 2);
            }
        }

        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < l; dz++) {
                int wx = x + dx;
                int wz = z + dz;
                for (int yy = y + 1; yy <= topY; yy++) {
                    region.setType(wx, yy, wz, Material.AIR);
                }
            }
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

    private void decorateOuterWalls(LimitedRegion region, Random random, int x, int y, int z, int w, int l, int h, Material wall) {
        Material beam = random.nextBoolean() ? Material.SPRUCE_LOG : Material.DARK_OAK_LOG;
        Material base = (wall == Material.BRICKS || wall == Material.ORANGE_TERRACOTTA) ? Material.STONE_BRICKS : Material.COBBLESTONE;

        for (int dx = 0; dx < w; dx++) {
            region.setType(x + dx, y + 1, z, base);
            region.setType(x + dx, y + 1, z + l - 1, base);
        }
        for (int dz = 0; dz < l; dz++) {
            region.setType(x, y + 1, z + dz, base);
            region.setType(x + w - 1, y + 1, z + dz, base);
        }

        int[] cornerX = {x, x + w - 1, x, x + w - 1};
        int[] cornerZ = {z, z, z + l - 1, z + l - 1};
        for (int i = 0; i < cornerX.length; i++) {
            for (int yy = y + 1; yy <= y + h; yy++) {
                region.setType(cornerX[i], yy, cornerZ[i], beam);
            }
        }

        for (int dx = 2; dx < w - 2; dx += 4) {
            for (int yy = y + 2; yy <= y + h - 1; yy += 2) {
                region.setType(x + dx, yy, z, base);
                region.setType(x + dx, yy, z + l - 1, base);
            }
        }
        for (int dz = 2; dz < l - 2; dz += 4) {
            for (int yy = y + 2; yy <= y + h - 1; yy += 2) {
                region.setType(x, yy, z + dz, base);
                region.setType(x + w - 1, yy, z + dz, base);
            }
        }
    }

    private void carveWindows(LimitedRegion region, Random random, int x, int y, int z, int w, int l, int h, Material window) {
        int y1 = y + 2;
        int y2 = Math.min(y + h - 1, y + 3);
        int wallX = x + (w / 3);
        int wallZ = z + (l * 2 / 3);

        for (int dx = 2; dx <= w - 3; dx += 4) {
            int wx = x + dx;
            if (wx == wallX) {
                continue;
            }
            placeWindowVariant(region, random, wx, y1, y2, z, window, true);
            placeWindowVariant(region, random, wx, y1, y2, z + l - 1, window, true);
        }

        for (int dz = 2; dz <= l - 3; dz += 4) {
            int wz = z + dz;
            if (wz == wallZ) {
                continue;
            }
            placeWindowVariant(region, random, x, y1, y2, wz, window, false);
            placeWindowVariant(region, random, x + w - 1, y1, y2, wz, window, false);
        }
    }

    private void placeWindowVariant(LimitedRegion region, Random random, int x, int y1, int y2, int z, Material window, boolean alongX) {
        boolean twoByTwo = random.nextDouble() < 0.35;
        if (twoByTwo) {
            int off = alongX ? 1 : 0;
            int offZ = alongX ? 0 : 1;
            region.setType(x, y1, z, window);
            region.setType(x, y2, z, window);
            region.setType(x + off, y1, z + offZ, window);
            region.setType(x + off, y2, z + offZ, window);
            return;
        }

        region.setType(x, y1, z, window);
        region.setType(x, y2, z, window);
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

    private void buildInteriorRooms(LimitedRegion region, int x, int y, int z, int w, int l, int h, Material wall, Material doorMaterial, boolean hasDoubleDoor) {
        int wallX = x + (w / 3);
        int doorZ = z + l / 2;
        for (int zz = z + 1; zz <= z + l - 2; zz++) {
            for (int yy = y + 1; yy <= y + h; yy++) {
                region.setType(wallX, yy, zz, wall);
            }
        }
        setDoor(region, wallX, y + 1, doorZ, doorMaterial, BlockFace.EAST, false);

        int wallZ = z + (l * 2 / 3);
        int doorX = x + w / 2;
        for (int xx = x + 1; xx <= x + w - 2; xx++) {
            for (int yy = y + 1; yy <= y + h; yy++) {
                region.setType(xx, yy, wallZ, wall);
            }
        }
        setDoor(region, doorX, y + 1, wallZ, doorMaterial, BlockFace.SOUTH, false);
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
            int minX = alongX ? x - 1 : x - 1 + layer;
            int maxX = alongX ? x + w : x + w - 1 - layer;
            int minZ = alongX ? z - 1 + layer : z - 1;
            int maxZ = alongX ? z + l - 1 - layer : z + l;

            if (minX > maxX || minZ > maxZ) {
                break;
            }

            for (int xx = minX; xx <= maxX; xx++) {
                for (int zz = minZ; zz <= maxZ; zz++) {
                    boolean edge = (alongX && (zz == minZ || zz == maxZ)) || (!alongX && (xx == minX || xx == maxX));
                    if (edge) {
                        BlockFace facing;
                        if (alongX) {
                            facing = (zz == minZ) ? BlockFace.SOUTH : BlockFace.NORTH;
                        } else {
                            facing = (xx == minX) ? BlockFace.EAST : BlockFace.WEST;
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
        Material carpetColor = carpets[random.nextInt(carpets.length)];

        region.setType(x + 2, y + 1, z + 2, Material.CRAFTING_TABLE);
        region.setType(x + w - 3, y + 1, z + 2, Material.FURNACE);
        region.setType(x + w - 4, y + 1, z + 2, random.nextBoolean() ? Material.CHEST : Material.BARREL);

        placeBed(region, x + 2, y + 1, z + l - 4, BlockFace.SOUTH);

        region.setType(x + w - 3, y + 1, z + l - 3, Material.CAULDRON);
        if (random.nextBoolean()) {
            region.setType(x + w - 4, y + 1, z + l - 3,
                    random.nextBoolean() ? Material.WAXED_OXIDIZED_COPPER : Material.WAXED_EXPOSED_COPPER);
        }

        placeCarpetPattern(region, random, x, y, z, w, l, carpetColor);

        region.setType(x + w / 2, y + 4, z + l / 2, Material.LANTERN);
        if (random.nextBoolean()) {
            region.setType(x + 1, y + 3, z + l / 2, Material.WALL_TORCH);
        }
    }

    private void placeCarpetPattern(LimitedRegion region, Random random, int x, int y, int z, int w, int l, Material carpetColor) {
        int centerX = x + w / 2;
        int centerZ = z + l / 2;
        int shape = random.nextInt(3);

        if (shape == 0) { // quadrado
            int half = Math.max(2, Math.min(w, l) / 4);
            for (int xx = centerX - half; xx <= centerX + half; xx++) {
                for (int zz = centerZ - half; zz <= centerZ + half; zz++) {
                    placeCarpetIfAir(region, xx, y + 1, zz, carpetColor);
                }
            }
            return;
        }

        if (shape == 1) { // retângulo
            int halfW = Math.max(2, w / 4);
            int halfL = Math.max(2, l / 5);
            for (int xx = centerX - halfW; xx <= centerX + halfW; xx++) {
                for (int zz = centerZ - halfL; zz <= centerZ + halfL; zz++) {
                    placeCarpetIfAir(region, xx, y + 1, zz, carpetColor);
                }
            }
            return;
        }

        int radius = Math.max(2, Math.min(w, l) / 4); // circular
        int r2 = radius * radius;
        for (int xx = centerX - radius; xx <= centerX + radius; xx++) {
            for (int zz = centerZ - radius; zz <= centerZ + radius; zz++) {
                int dx = xx - centerX;
                int dz = zz - centerZ;
                if (dx * dx + dz * dz <= r2) {
                    placeCarpetIfAir(region, xx, y + 1, zz, carpetColor);
                }
            }
        }
    }

    private void placeCarpetIfAir(LimitedRegion region, int x, int y, int z, Material carpetColor) {
        if (region.getType(x, y, z).isAir()) {
            region.setType(x, y, z, carpetColor);
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

    private record LotInfo(boolean buildable, int minY, int maxY) {
    }
}
