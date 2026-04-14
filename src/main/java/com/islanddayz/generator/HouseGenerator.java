package com.islanddayz.generator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.generator.LimitedRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HouseGenerator {
    private final CityGenerator cityGenerator;

    public HouseGenerator(CityGenerator cityGenerator) {
        this.cityGenerator = cityGenerator;
    }

    public void populateChunk(LimitedRegion region, int chunkX, int chunkZ, Random random) {
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        int endX = startX + 15;
        int endZ = startZ + 15;
        List<int[]> occupiedLots = new ArrayList<>();

        for (int villageIndex = 0; villageIndex < cityGenerator.villageCount(); villageIndex++) {
            int cx = cityGenerator.villageCenterX(villageIndex);
            int cz = cityGenerator.villageCenterZ(villageIndex);
            if ((cx * cx) + (cz * cz) < (170 * 170)) {
                continue;
            }
            int[][] plots = villagePlots(cityGenerator.villagePattern(villageIndex));
            WoodPalette palette = woodPalette(villageIndex);
            for (int[] plot : plots) {
                int centerX = cx + plot[0];
                int centerZ = cz + plot[1];
                if (centerX < startX || centerX > endX || centerZ < startZ || centerZ > endZ) {
                    continue;
                }
                if (!isCenteredLotCandidate(centerX, centerZ)) {
                    continue;
                }

                int lotW = 11 + random.nextInt(3);
                int lotL = 11 + random.nextInt(3);
                int minX = centerX - lotW / 2;
                int minZ = centerZ - lotL / 2;
                LotInfo lotInfo = analyzeLot(region, minX, minZ, lotW, lotL, centerX, centerZ);
                if (!lotInfo.buildable()) {
                    continue;
                }
                if (intersectsOccupiedLot(occupiedLots, minX, minZ, lotW, lotL)) {
                    continue;
                }

                int houseW = lotW - (4 + random.nextInt(4));
                int houseL = lotL - (4 + random.nextInt(4));
                int houseX = minX + 2 + random.nextInt(Math.max(1, lotW - houseW - 3));
                int houseZ = minZ + 2 + random.nextInt(Math.max(1, lotL - houseL - 3));
                prepareLotSurface(region, minX, minZ, lotW, lotL, lotInfo.minY(), 20);
                buildHouse(region, random, houseX, lotInfo.minY(), houseZ, houseW, houseL, palette);
                occupiedLots.add(new int[]{minX, minZ, minX + lotW - 1, minZ + lotL - 1});
            }
        }
    }

    private int[][] villagePlots(int pattern) {
        return switch (pattern) {
            case 0 -> new int[][]{
                    {6, -26}, {8, -14}, {-8, -10}, {6, 2}, {-14, 12}, {16, 26}
            };
            case 1 -> new int[][]{
                    {-8, -22}, {8, -24}, {-8, -10}, {8, -8}, {-8, 2}, {10, 14}
            };
            default -> new int[][]{
                    {-10, -18}, {10, -18}, {-8, -4}, {8, -2}, {-12, 14}, {10, 14}
            };
        };
    }

    private boolean intersectsOccupiedLot(List<int[]> occupiedLots, int minX, int minZ, int w, int l) {
        int maxX = minX + w - 1;
        int maxZ = minZ + l - 1;
        for (int[] box : occupiedLots) {
            if (maxX < box[0] || minX > box[2] || maxZ < box[1] || minZ > box[3]) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean isCenteredLotCandidate(int x, int z) {
        if ((x * x) + (z * z) < (170 * 170)) {
            return false;
        }
        if (cityGenerator.cityInfluence(x, z) < 0.3 || cityGenerator.getRoadType(x, z) != CityGenerator.RoadType.NONE) {
            return false;
        }

        int nearest = Math.min(
                Math.min(distanceToRoad(x, z, 0, -1, 32), distanceToRoad(x, z, 0, 1, 32)),
                Math.min(distanceToRoad(x, z, -1, 0, 32), distanceToRoad(x, z, 1, 0, 32))
        );
        return nearest <= 9;
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
        boolean nearCityEdge = cityGenerator.cityInfluence(centerX, centerZ) < 0.56;
        boolean tooSteep = (maxY - minY) > 6;
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
                for (int yy = baseY + 1; yy <= topY; yy++) {
                    region.setType(x, yy, z, Material.AIR);
                }
            }
        }
    }

    private void buildHouse(LimitedRegion region, Random random, int x, int y, int z, int w, int l, WoodPalette palette) {
        Material wall = palette.wall();
        Material roofStair = palette.roofStair();
        Material floor = palette.floor();
        Material[] windowChoices = {Material.GLASS, Material.TINTED_GLASS, Material.WHITE_STAINED_GLASS, Material.GRAY_STAINED_GLASS};
        Material window = windowChoices[random.nextInt(windowChoices.length)];
        Material doorMaterial = palette.door();

        int floorHeight = 5 + random.nextInt(2);
        boolean secondFloor = random.nextDouble() < 0.35;

        clearConstructionVolume(region, x, y, z, w, l, secondFloor ? (floorHeight * 2) + 8 : floorHeight + 8);
        buildAdaptiveFloor(region, x, y, z, w, l, floor, true);
        buildOuterWalls(region, x, y, z, w, l, floorHeight, wall);
        decorateOuterWalls(region, random, x, y, z, w, l, floorHeight, wall);
        carveWindows(region, random, x, y, z, w, l, floorHeight, window);

        boolean doubleDoor = random.nextDouble() < 0.35;
        placeDoor(region, x, y, z, w, doorMaterial, doubleDoor);
        ensureFrontDoorStep(region, x, y, z, w, doubleDoor);
        buildInteriorRooms(region, x, y, z, w, l, floorHeight, wall, doorMaterial, doubleDoor);
        decorateInterior(region, random, x, y, z, w, l);
        if (random.nextDouble() < 0.28) {
            buildGarden(region, random, x, y, z, w, l);
        }

        if (secondFloor) {
            int secondY = y + floorHeight;
            buildAdaptiveFloor(region, x + 1, secondY, z + 1, w - 2, l - 2, floor, false);
            buildOuterWalls(region, x + 1, secondY, z + 1, w - 2, l - 2, floorHeight - 1, wall);
            carveWindows(region, random, x + 1, secondY, z + 1, w - 2, l - 2, floorHeight - 1, window);
            buildInteriorRooms(region, x + 1, secondY, z + 1, w - 2, l - 2, floorHeight - 1, wall, doorMaterial, false);
            decorateInterior(region, random, x + 1, secondY, z + 1, w - 2, l - 2);
            buildTriangularRoof(region, x + 1, secondY + floorHeight - 1, z + 1, w - 2, l - 2, roofStair, wall);
            if (random.nextDouble() < 0.35) {
                buildLadderBetweenFloors(region, x + 2, y + 1, z + 2, floorHeight);
            } else {
                buildStairsBetweenFloors(region, x + 2, y + 1, z + 2, floorHeight);
            }
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

    private void buildAdaptiveFloor(LimitedRegion region, int x, int y, int z, int w, int l, Material floor, boolean fillBelow) {
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < l; dz++) {
                int wx = x + dx;
                int wz = z + dz;
                if (fillBelow) {
                    int groundY = region.getHighestBlockYAt(wx, wz) - 1;
                    for (int yy = groundY + 1; yy < y; yy++) {
                        region.setType(wx, yy, wz, Material.DIRT);
                    }
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
        for (int dx = 0; dx < w; dx++) {
            region.setType(x + dx, y, z, wall);
            region.setType(x + dx, y, z + l - 1, wall);
        }
        for (int dz = 0; dz < l; dz++) {
            region.setType(x, y, z + dz, wall);
            region.setType(x + w - 1, y, z + dz, wall);
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

    private void ensureFrontDoorStep(LimitedRegion region, int x, int y, int z, int w, boolean doubleDoor) {
        int doorX = x + (w / 2);
        for (int i = 0; i < (doubleDoor ? 2 : 1); i++) {
            int stepX = doorX - i;
            int stepY = y;
            int stepZ = z - 1;
            if (region.getType(stepX, stepY, stepZ).isAir()) {
                Stairs step = (Stairs) Bukkit.createBlockData(Material.STONE_BRICK_STAIRS);
                step.setFacing(BlockFace.SOUTH);
                step.setHalf(Stairs.Half.BOTTOM);
                region.setBlockData(stepX, stepY, stepZ, step);
            }
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
            region.setType(x + i, y + i + 1, z, Material.AIR);
            region.setType(x + i, y + i + 2, z, Material.AIR);
        }
    }

    private void buildLadderBetweenFloors(LimitedRegion region, int x, int y, int z, int height) {
        for (int i = 0; i <= height; i++) {
            region.setType(x - 1, y + i, z, Material.SPRUCE_PLANKS);
            region.setType(x, y + i, z, Material.AIR);
            region.setType(x, y + i + 1, z, Material.AIR);
            Ladder ladder = (Ladder) Bukkit.createBlockData(Material.LADDER);
            ladder.setFacing(BlockFace.EAST);
            region.setBlockData(x, y + i, z, ladder);
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
        int roomY = y + 1;

        placeCornerWorkTable(region, random, x, roomY, z, w, l);
        placeKitchen(region, x, roomY, z, w, l);
        placeBedroomBeds(region, random, x, roomY, z, w, l);
        placeBookshelves(region, random, x, roomY, z, w, l);
        placeStorageVariation(region, random, x, roomY, z, w, l);

        placeCarpetPattern(region, random, x, y, z, w, l, carpetColor);

        int ceilingY = findCeilingY(region, x + w / 2, y + 2, z + l / 2, y + 9);
        if (ceilingY > y + 2) {
            region.setType(x + w / 2, ceilingY - 1, z + l / 2, Material.LANTERN);
        }
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

    private void placeCornerWorkTable(LimitedRegion region, Random random, int x, int y, int z, int w, int l) {
        int[][] corners = {
                {x + 2, z + 2},
                {x + w - 3, z + 2},
                {x + 2, z + l - 3},
                {x + w - 3, z + l - 3}
        };
        int[] pick = corners[random.nextInt(corners.length)];
        if (region.getType(pick[0], y, pick[1]).isAir()) {
            region.setType(pick[0], y, pick[1], Material.CRAFTING_TABLE);
        }
    }

    private void placeKitchen(LimitedRegion region, int x, int y, int z, int w, int l) {
        int stoveX = x + w - 3;
        int stoveZ = z + 2;
        if (region.getType(stoveX, y, stoveZ).isAir()) {
            setFacingBlock(region, stoveX, y, stoveZ, Material.FURNACE, BlockFace.WEST);
        }
        if (region.getType(stoveX, y, stoveZ + 1).isAir()) {
            region.setType(stoveX, y, stoveZ + 1, Material.CAULDRON);
        }

    }

    private void placeStorageVariation(LimitedRegion region, Random random, int x, int y, int z, int w, int l) {
        Material[] storage = {
                Material.CHEST,
                Material.BARREL,
                Material.TRAPPED_CHEST,
                fallbackMaterial("COPPER_CHEST", Material.CHEST),
                fallbackMaterial("WAXED_COPPER_CHEST", Material.BARREL)
        };

        int[][] spots = {
                {x + 2, z + l - 3},
                {x + w - 3, z + l - 3},
                {x + w - 3, z + 3},
                {x + 3, z + 2}
        };

        int count = 1 + random.nextInt(2);
        if (random.nextDouble() < 0.3) {
            count++;
        }
        int placed = 0;
        int attempts = 0;
        while (placed < count && attempts < 16) {
            int[] pos = spots[random.nextInt(spots.length)];
            Material mat = storage[random.nextInt(storage.length)];
            attempts++;
            if (!region.getType(pos[0], y, pos[1]).isAir()) {
                continue;
            }
            setFacingBlock(region, pos[0], y, pos[1], mat, BlockFace.WEST);
            placed++;

            boolean canDouble = (mat == Material.CHEST || mat == Material.TRAPPED_CHEST) && random.nextDouble() < 0.35;
            if (canDouble && region.getType(pos[0] + 1, y, pos[1]).isAir()) {
                region.setType(pos[0] + 1, y, pos[1], mat);
            }
        }

        if (placed == 0) {
            for (int[] pos : spots) {
                if (region.getType(pos[0], y, pos[1]).isAir()) {
                    setFacingBlock(region, pos[0], y, pos[1], Material.CHEST, BlockFace.WEST);
                    break;
                }
            }
        }
    }

    private Material fallbackMaterial(String name, Material fallback) {
        Material material = Material.matchMaterial(name);
        return material != null ? material : fallback;
    }

    private void placeBedroomBeds(LimitedRegion region, Random random, int x, int y, int z, int w, int l) {
        Material[] bedColors = {
                Material.RED_BED, Material.BLUE_BED, Material.GREEN_BED, Material.YELLOW_BED,
                Material.WHITE_BED, Material.GRAY_BED, Material.CYAN_BED, Material.ORANGE_BED
        };
        Material bedMaterial = bedColors[random.nextInt(bedColors.length)];
        int bx = x + 2;
        int bz = z + l - 4;
        if (canPlaceBed(region, bx, y, bz, BlockFace.SOUTH)) {
            placeBed(region, bx, y, bz, BlockFace.SOUTH, bedMaterial);
        } else if (canPlaceBed(region, x + w - 3, y, z + l - 4, BlockFace.SOUTH)) {
            placeBed(region, x + w - 3, y, z + l - 4, BlockFace.SOUTH, bedMaterial);
            bx = x + w - 3;
            bz = z + l - 4;
        }

        if (random.nextDouble() < 0.35) {
            int secondX = bx + 1;
            if (canPlaceBed(region, secondX, y, bz, BlockFace.SOUTH)) {
                placeBed(region, secondX, y, bz, BlockFace.SOUTH, bedMaterial);
            }
        }
    }

    private void placeBookshelves(LimitedRegion region, Random random, int x, int y, int z, int w, int l) {
        int[][] shelfSpots = {
                {x + 1, z + 3},
                {x + w - 2, z + 3},
                {x + 1, z + l - 4},
                {x + w - 2, z + l - 4}
        };
        int shelves = random.nextDouble() < 0.4 ? 2 : 1;
        int placed = 0;
        for (int i = 0; i < shelfSpots.length && placed < shelves; i++) {
            int[] spot = shelfSpots[(i + random.nextInt(shelfSpots.length)) % shelfSpots.length];
            if (region.getType(spot[0], y, spot[1]).isAir()) {
                region.setType(spot[0], y, spot[1], Material.BOOKSHELF);
                placed++;
            }
        }
    }

    private boolean canPlaceBed(LimitedRegion region, int x, int y, int z, BlockFace facing) {
        int hx = x + facing.getModX();
        int hz = z + facing.getModZ();
        return region.getType(x, y, z).isAir() && region.getType(hx, y, hz).isAir();
    }

    private void placeBed(LimitedRegion region, int x, int y, int z, BlockFace facing, Material bedMaterial) {
        int hx = x + facing.getModX();
        int hz = z + facing.getModZ();
        if (!canPlaceBed(region, x, y, z, facing)) {
            return;
        }

        Bed foot = (Bed) Bukkit.createBlockData(bedMaterial);
        foot.setPart(Bed.Part.FOOT);
        foot.setFacing(facing);
        Bed head = (Bed) Bukkit.createBlockData(bedMaterial);
        head.setPart(Bed.Part.HEAD);
        head.setFacing(facing);

        region.setBlockData(x, y, z, foot);
        region.setBlockData(hx, y, hz, head);
    }

    private void setFacingBlock(LimitedRegion region, int x, int y, int z, Material material, BlockFace facing) {
        if (!(Bukkit.createBlockData(material) instanceof Directional directional)) {
            region.setType(x, y, z, material);
            return;
        }
        directional.setFacing(facing);
        region.setBlockData(x, y, z, directional);
    }

    private int findCeilingY(LimitedRegion region, int x, int startY, int z, int maxY) {
        for (int yy = startY; yy <= maxY; yy++) {
            if (!region.getType(x, yy, z).isAir()) {
                return yy;
            }
        }
        return maxY;
    }

    private void buildGarden(LimitedRegion region, Random random, int x, int y, int z, int w, int l) {
        int minX = x - 2;
        int maxX = x + w + 1;
        int minZ = z - 2;
        int maxZ = z + l + 1;
        for (int xx = minX; xx <= maxX; xx++) {
            for (int zz = minZ; zz <= maxZ; zz++) {
                boolean edge = xx == minX || xx == maxX || zz == minZ || zz == maxZ;
                if (!edge) {
                    continue;
                }
                region.setType(xx, y + 1, zz, Material.OAK_FENCE);
            }
        }
        int gateX = x + (w / 2);
        region.setType(gateX, y + 1, z - 2, Material.OAK_FENCE_GATE);
        region.setType(gateX, y + 1, z - 1, Material.AIR);
    }

    private record LotInfo(boolean buildable, int minY, int maxY) {
    }

    private WoodPalette woodPalette(int villageIndex) {
        return switch (Math.floorMod(villageIndex, 4)) {
            case 0 -> new WoodPalette(Material.OAK_PLANKS, Material.OAK_PLANKS, Material.OAK_STAIRS, Material.OAK_DOOR);
            case 1 -> new WoodPalette(Material.SPRUCE_PLANKS, Material.SPRUCE_PLANKS, Material.SPRUCE_STAIRS, Material.SPRUCE_DOOR);
            case 2 -> new WoodPalette(Material.BIRCH_PLANKS, Material.BIRCH_PLANKS, Material.BIRCH_STAIRS, Material.BIRCH_DOOR);
            default -> new WoodPalette(Material.JUNGLE_PLANKS, Material.JUNGLE_PLANKS, Material.JUNGLE_STAIRS, Material.JUNGLE_DOOR);
        };
    }

    private record WoodPalette(Material wall, Material floor, Material roofStair, Material door) {
    }
}
