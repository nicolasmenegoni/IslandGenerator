package com.islanddayz.generator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.generator.LimitedRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HouseGenerator {
    private final CityGenerator cityGenerator;
    private static final HouseStyle[] STYLE_ROTATION = {
            HouseStyle.SINGLE_STORY,
            HouseStyle.TWO_STORY,
            HouseStyle.FENCED,
            HouseStyle.CHIMNEY
    };

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
            if (isMountainVillage(cx, cz)) {
                continue;
            }
            int[][] plots = villagePlots(cityGenerator.villagePattern(villageIndex));
            if (cityGenerator.villagePattern(villageIndex) == 3) {
                populateCentralFarm(region, cx, cz, startX, endX, startZ, endZ);
            }
            WoodPalette palette = woodPalette(villageIndex);
            populateVillageTrees(region, palette, cx, cz, startX, endX, startZ, endZ);
            for (int plotIndex = 0; plotIndex < plots.length; plotIndex++) {
                int[] plot = plots[plotIndex];
                int centerX = cx + plot[0];
                int centerZ = cz + plot[1];
                if (centerX < startX || centerX > endX || centerZ < startZ || centerZ > endZ) {
                    continue;
                }
                boolean mandatoryVillageHouse = plotIndex < 5;
                if ((!mandatoryVillageHouse && !isCenteredLotCandidate(centerX, centerZ)) || isMountainVillage(centerX, centerZ)) {
                    continue;
                }

                int lotW = 18;
                int lotL = 18;
                int minX = centerX - lotW / 2;
                int minZ = centerZ - lotL / 2;
                LotInfo lotInfo = analyzeLot(region, minX, minZ, lotW, lotL, centerX, centerZ);
                if (!mandatoryVillageHouse && intersectsOccupiedLot(occupiedLots, minX, minZ, minX + lotW - 1, minZ + lotL - 1)) {
                    continue;
                }
                if ((!mandatoryVillageHouse && !lotInfo.buildable()) || (!mandatoryVillageHouse && (lotInfo.maxY() - lotInfo.minY()) > 8)) {
                    continue;
                }

                BlockFace entryFacing = nearestRoadDirection(centerX, centerZ);
                if (entryFacing == null && mandatoryVillageHouse) {
                    entryFacing = BlockFace.NORTH;
                }
                if (entryFacing == null) {
                    continue;
                }
                int[] lotCenter = offsetLotFromRoad(centerX, centerZ, entryFacing, random);
                centerX = lotCenter[0];
                centerZ = lotCenter[1];

                HouseStyle style = houseStyleForPlot(villageIndex, plotIndex);
                boolean secondFloor = style == HouseStyle.TWO_STORY || (style == HouseStyle.CHIMNEY && random.nextDouble() < 0.35);
                boolean hasGarden = style == HouseStyle.FENCED || (style == HouseStyle.SINGLE_STORY && random.nextDouble() < 0.28);
                boolean hasChimney = style == HouseStyle.CHIMNEY;

                int houseW = secondFloor ? 14 + random.nextInt(4) : 12 + random.nextInt(4);
                int houseL = secondFloor ? 14 + random.nextInt(4) : 12 + random.nextInt(4);
                if (mandatoryVillageHouse) {
                    houseW = secondFloor ? 13 + random.nextInt(2) : 11 + random.nextInt(2);
                    houseL = secondFloor ? 13 + random.nextInt(2) : 11 + random.nextInt(2);
                }
                int houseX = centerX - (houseW / 2);
                int houseZ = centerZ - (houseL / 2);
                int enclosureOffset = hasGarden ? 4 : 0;
                int houseSpacing = mandatoryVillageHouse ? 3 : 5 + random.nextInt(2);
                int occMinX = houseX - enclosureOffset - houseSpacing;
                int occMinZ = houseZ - enclosureOffset - houseSpacing;
                int occMaxX = houseX + houseW - 1 + enclosureOffset + houseSpacing;
                int occMaxZ = houseZ + houseL - 1 + enclosureOffset + houseSpacing;
                if (houseOverlapsRoad(houseX, houseZ, houseW, houseL)) {
                    continue;
                }
                if (!mandatoryVillageHouse && intersectsOccupiedLot(occupiedLots, occMinX, occMinZ, occMaxX, occMaxZ)) {
                    continue;
                }
                int minRoadDistance = mandatoryVillageHouse ? 1 : (hasGarden ? 3 : 2);
                if (!mandatoryVillageHouse && houseTooCloseToRoad(houseX, houseZ, houseW, houseL, minRoadDistance)) {
                    continue;
                }
                int prepMinX = houseX - enclosureOffset - 1;
                int prepMinZ = houseZ - enclosureOffset - 1;
                int prepW = houseW + (enclosureOffset * 2) + 2;
                int prepL = houseL + (enclosureOffset * 2) + 2;
                prepareLotSurface(region, prepMinX, prepMinZ, prepW, prepL, lotInfo.minY(), 20);
                buildHouse(region, random, houseX, lotInfo.minY(), houseZ, houseW, houseL, palette, entryFacing, secondFloor, hasGarden, hasChimney);
                occupiedLots.add(new int[]{occMinX, occMinZ, occMaxX, occMaxZ});
            }
        }
    }

    private int[][] villagePlots(int pattern) {
        return switch (pattern) {
            case 0 -> new int[][]{
                    {-22, -36}, {0, -36}, {22, -36}, {-22, -14}, {22, -14}, {-24, 8}, {24, 8}, {-22, 30}, {0, 34}, {22, 30}
            };
            case 1 -> new int[][]{
                    {-24, -32}, {0, -32}, {24, -32}, {-24, -10}, {24, -10}, {-24, 12}, {24, 12}, {-16, 34}, {0, 36}, {16, 34}
            };
            case 2 -> new int[][]{
                    {-26, -30}, {0, -30}, {26, -30}, {-24, -6}, {24, -6}, {-24, 16}, {24, 16}, {-14, 36}, {0, 38}, {14, 36}
            };
            default -> new int[][]{
                    {-12, -30}, {12, -30}, {-30, -2}, {-30, 16}, {30, -2}, {30, 16}, {-12, 38}, {12, 38}
            };
        };
    }

    private HouseStyle houseStyleForPlot(int villageIndex, int plotIndex) {
        int base = Math.floorMod(villageIndex, STYLE_ROTATION.length);
        int styleIndex = (base + (plotIndex / 2)) % STYLE_ROTATION.length;
        return STYLE_ROTATION[styleIndex];
    }

    private boolean intersectsOccupiedLot(List<int[]> occupiedLots, int minX, int minZ, int maxX, int maxZ) {
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
        if (cityGenerator.cityInfluence(x, z) < 0.28 || cityGenerator.getRoadType(x, z) != CityGenerator.RoadType.NONE) {
            return false;
        }

        int nearest = Math.min(
                Math.min(distanceToRoad(x, z, 0, -1, 32), distanceToRoad(x, z, 0, 1, 32)),
                Math.min(distanceToRoad(x, z, -1, 0, 32), distanceToRoad(x, z, 1, 0, 32))
        );
        return nearest >= 5 && nearest <= 12;
    }

    private int[] offsetLotFromRoad(int x, int z, BlockFace entryFacing, Random random) {
        int targetDistance = 4 + random.nextInt(3); // 4-6 blocos
        int currentDistance = distanceToRoad(x, z, entryFacing.getModX(), entryFacing.getModZ(), 32);
        if (currentDistance > 32) {
            return new int[]{x, z};
        }
        int shift = Math.max(-4, Math.min(4, targetDistance - currentDistance));
        int newX = x - (entryFacing.getModX() * shift);
        int newZ = z - (entryFacing.getModZ() * shift);
        return new int[]{newX, newZ};
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
                int y = region.getHighestBlockYAt(x, z);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        boolean nearCityEdge = cityGenerator.cityInfluence(centerX, centerZ) < 0.25;
        boolean tooSteep = (maxY - minY) > 9;
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

    private void buildHouse(LimitedRegion region, Random random, int x, int y, int z, int w, int l, WoodPalette palette, BlockFace entryFacing, boolean secondFloor, boolean hasGarden, boolean hasChimney) {
        Material wall = palette.wall();
        Material roofStair = palette.roofStair();
        Material floor = palette.floor();
        Material[] windowChoices = {Material.GLASS, Material.TINTED_GLASS, Material.WHITE_STAINED_GLASS, Material.GRAY_STAINED_GLASS};
        Material window = windowChoices[random.nextInt(windowChoices.length)];
        Material doorMaterial = palette.door();

        int floorHeight = 5 + random.nextInt(2);

        clearConstructionVolume(region, x, y, z, w, l, secondFloor ? (floorHeight * 2) + 8 : floorHeight + 8);
        buildAdaptiveFloor(region, x, y, z, w, l, floor, true);
        buildOuterWalls(region, x, y, z, w, l, floorHeight, wall);
        decorateOuterWalls(region, random, x, y, z, w, l, floorHeight, wall);
        carveWindows(region, random, x, y, z, w, l, floorHeight, window);

        boolean doubleDoor = random.nextDouble() < 0.35;
        placeDoor(region, x, y, z, w, l, doorMaterial, doubleDoor, entryFacing);
        ensureFrontDoorStep(region, x, y, z, w, l, doubleDoor, entryFacing);
        buildInteriorRooms(region, x, y, z, w, l, floorHeight, wall, doorMaterial, doubleDoor);
        decorateInterior(region, random, x, y, z, w, l, true, true, false);
        if (hasGarden) {
            buildGarden(region, random, x, y, z, w, l, entryFacing);
        }

        if (secondFloor) {
            int secondY = y + floorHeight;
            buildAdaptiveFloor(region, x + 1, secondY, z + 1, w - 2, l - 2, floor, false);
            buildOuterWalls(region, x + 1, secondY, z + 1, w - 2, l - 2, floorHeight - 1, wall);
            carveWindows(region, random, x + 1, secondY, z + 1, w - 2, l - 2, floorHeight - 1, window);
            buildInteriorRooms(region, x + 1, secondY, z + 1, w - 2, l - 2, floorHeight - 1, wall, doorMaterial, false);
            decorateInterior(region, random, x + 1, secondY, z + 1, w - 2, l - 2, false, false, true);
            buildTriangularRoof(region, x + 1, secondY + floorHeight - 1, z + 1, w - 2, l - 2, roofStair, wall);
            if (hasChimney || random.nextDouble() < 0.22) {
                buildChimney(region, x + 1, secondY + floorHeight - 1, z + 1, w - 2, l - 2);
            }
            if (random.nextDouble() < 0.35) {
                buildLadderBetweenFloors(region, x + 2, y + 1, z + 2, floorHeight);
            } else {
                clearSecondFloorAccess(region, x + 2, y + 1, z + 2, floorHeight);
                buildStairsBetweenFloors(region, x + 2, y + 1, z + 2, floorHeight);
            }
        } else {
            buildTriangularRoof(region, x, y + floorHeight, z, w, l, roofStair, wall);
            if (hasChimney || random.nextDouble() < 0.18) {
                buildChimney(region, x, y + floorHeight, z, w, l);
            }
        }
    }

    private void buildChimney(LimitedRegion region, int x, int roofBaseY, int z, int w, int l) {
        int chimneyX = x + 2;
        int chimneyZ = z + 2;
        int topY = roofBaseY + 5;
        for (int yy = roofBaseY; yy <= topY; yy++) {
            region.setType(chimneyX, yy, chimneyZ, Material.BRICKS);
        }
        region.setType(chimneyX, topY + 1, chimneyZ, Material.CAMPFIRE);
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

    private void placeDoor(LimitedRegion region, int x, int y, int z, int w, int l, Material doorMaterial, boolean doubleDoor, BlockFace facing) {
        int doorX = x + (w / 2);
        int doorZ = z + (l / 2);
        if (facing == BlockFace.NORTH) {
            setDoor(region, doorX, y + 1, z, doorMaterial, BlockFace.NORTH, false);
            if (doubleDoor) setDoor(region, doorX - 1, y + 1, z, doorMaterial, BlockFace.NORTH, true);
            return;
        }
        if (facing == BlockFace.SOUTH) {
            setDoor(region, doorX, y + 1, z + l - 1, doorMaterial, BlockFace.SOUTH, false);
            if (doubleDoor) setDoor(region, doorX - 1, y + 1, z + l - 1, doorMaterial, BlockFace.SOUTH, true);
            return;
        }
        if (facing == BlockFace.WEST) {
            setDoor(region, x, y + 1, doorZ, doorMaterial, BlockFace.WEST, false);
            if (doubleDoor) setDoor(region, x, y + 1, doorZ - 1, doorMaterial, BlockFace.WEST, true);
            return;
        }
        setDoor(region, x + w - 1, y + 1, doorZ, doorMaterial, BlockFace.EAST, false);
        if (doubleDoor) setDoor(region, x + w - 1, y + 1, doorZ - 1, doorMaterial, BlockFace.EAST, true);
    }

    private void ensureFrontDoorStep(LimitedRegion region, int x, int y, int z, int w, int l, boolean doubleDoor, BlockFace facing) {
        int doorX = x + (w / 2);
        int doorZ = z + (l / 2);
        for (int i = 0; i < (doubleDoor ? 2 : 1); i++) {
            int stepX = doorX;
            int stepY = y;
            int stepZ = z - 1;
            if (facing == BlockFace.SOUTH) {
                stepX = doorX - i;
                stepZ = z + l;
            } else if (facing == BlockFace.NORTH) {
                stepX = doorX - i;
                stepZ = z - 1;
            } else if (facing == BlockFace.WEST) {
                stepX = x - 1;
                stepZ = doorZ - i;
            } else if (facing == BlockFace.EAST) {
                stepX = x + w;
                stepZ = doorZ - i;
            }
            if (region.getType(stepX, stepY, stepZ).isAir()) {
                Stairs step = (Stairs) Bukkit.createBlockData(Material.STONE_BRICK_STAIRS);
                step.setFacing(facing.getOppositeFace());
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
            region.setType(x + i + 1, y + i + 1, z, Material.AIR);
            region.setType(x + i + 1, y + i + 2, z, Material.AIR);
        }
        region.setType(x + height, y + height + 1, z, Material.AIR);
        region.setType(x + height, y + height + 2, z, Material.AIR);
    }

    private void clearSecondFloorAccess(LimitedRegion region, int x, int y, int z, int height) {
        for (int i = 0; i <= height + 1; i++) {
            int baseX = x + i;
            for (int yy = y + i; yy <= y + i + 3; yy++) {
                region.setType(baseX, yy, z, Material.AIR);
            }
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
            int maxX = alongX ? x + w : x + w - layer;
            int minZ = alongX ? z - 1 + layer : z - 1;
            int maxZ = alongX ? z + l - layer : z + l;

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

    private void decorateInterior(LimitedRegion region, Random random, int x, int y, int z, int w, int l, boolean includeKitchen, boolean allowWallTorch, boolean singleLanternFloor) {
        Material[] carpets = {Material.RED_CARPET, Material.GREEN_CARPET, Material.CYAN_CARPET, Material.GRAY_CARPET};
        Material carpetColor = carpets[random.nextInt(carpets.length)];
        int roomY = y + 1;

        placeCornerWorkTable(region, random, x, roomY, z, w, l);
        if (includeKitchen) {
            placeKitchen(region, x, roomY, z, w, l);
        }
        placeBedroomBeds(region, random, x, roomY, z, w, l);
        placeBookshelves(region, random, x, roomY, z, w, l);
        placeStorageVariation(region, random, x, roomY, z, w, l);

        placeCarpetPattern(region, random, x, y, z, w, l, carpetColor);

        int ceilingY = findCeilingY(region, x + w / 2, y + 2, z + l / 2, y + 9);
        int lanternY;
        if (ceilingY > y + 2) {
            lanternY = ceilingY - 1;
        } else {
            lanternY = y + 4;
        }
        if (singleLanternFloor) {
            for (int xx = x + 1; xx <= x + w - 2; xx++) {
                for (int zz = z + 1; zz <= z + l - 2; zz++) {
                    for (int yy = y + 1; yy <= y + 8; yy++) {
                        Material current = region.getType(xx, yy, zz);
                        if (current == Material.LANTERN || current == Material.SOUL_LANTERN) {
                            region.setType(xx, yy, zz, Material.AIR);
                        }
                    }
                }
            }
        }
        region.setType(x + w / 2, lanternY, z + l / 2, Material.AIR);
        Lantern lantern = (Lantern) Bukkit.createBlockData(Material.LANTERN);
        lantern.setHanging(true);
        region.setBlockData(x + w / 2, lanternY, z + l / 2, lantern);
        if (allowWallTorch && random.nextBoolean()) {
            if (!region.getType(x + 1, y + 3, z + l / 2 - 1).isAir()) {
                setFacingBlock(region, x + 1, y + 3, z + l / 2, Material.WALL_TORCH, BlockFace.SOUTH);
            } else if (!region.getType(x + 1, y + 3, z + l / 2 + 1).isAir()) {
                setFacingBlock(region, x + 1, y + 3, z + l / 2, Material.WALL_TORCH, BlockFace.NORTH);
            }
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
        int[][] wallSpots = {
                {x + 1, z + 2},
                {x + w - 2, z + 2},
                {x + 1, z + l - 3},
                {x + w - 2, z + l - 3},
                {x + 2, z + 1},
                {x + w - 3, z + 1}
        };
        int[] pick = wallSpots[random.nextInt(wallSpots.length)];
        if (region.getType(pick[0], y, pick[1]).isAir()) {
            region.setType(pick[0], y, pick[1], Material.CRAFTING_TABLE);
        }
    }

    private void placeKitchen(LimitedRegion region, int x, int y, int z, int w, int l) {
        int stoveX = x + w - 2;
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
                {x + 1, z + l - 3},
                {x + w - 2, z + l - 3},
                {x + w - 2, z + 3},
                {x + w - 3, z + l / 2}
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
        int shape = random.nextInt(4);
        int width = (shape == 0 || shape == 2) ? 2 : 1;
        int height = (shape <= 1) ? 2 : 3;
        int baseX = random.nextBoolean() ? x + 1 : x + w - 2;
        int baseZ = random.nextBoolean() ? z + 2 : z + l - 3;
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                int shelfX = baseX;
                int shelfZ = baseZ;
                if (baseX == x + 1 || baseX == x + w - 2) {
                    shelfZ = Math.max(z + 1, Math.min(z + l - 2, baseZ + dx));
                } else {
                    shelfX = Math.max(x + 1, Math.min(x + w - 2, baseX + dx));
                }
                if (region.getType(shelfX, y + dy, shelfZ).isAir()) {
                    region.setType(shelfX, y + dy, shelfZ, Material.BOOKSHELF);
                }
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

    private void buildGarden(LimitedRegion region, Random random, int x, int y, int z, int w, int l, BlockFace entryFacing) {
        int fenceOffset = 4;
        int minX = x - fenceOffset;
        int maxX = x + w - 1 + fenceOffset;
        int minZ = z - fenceOffset;
        int maxZ = z + l - 1 + fenceOffset;
        for (int xx = minX; xx <= maxX; xx++) {
            for (int zz = minZ; zz <= maxZ; zz++) {
                boolean edge = xx == minX || xx == maxX || zz == minZ || zz == maxZ;
                if (!edge) {
                    continue;
                }
                region.setType(xx, y, zz, Material.OAK_FENCE);
            }
        }
        int gateX = x + (w / 2);
        int gateZ = z + (l / 2);
        if (entryFacing == BlockFace.NORTH) {
            gateZ = minZ;
        } else if (entryFacing == BlockFace.SOUTH) {
            gateZ = maxZ;
        } else if (entryFacing == BlockFace.WEST) {
            gateX = minX;
        } else if (entryFacing == BlockFace.EAST) {
            gateX = maxX;
        }

        org.bukkit.block.data.type.Gate gate = (org.bukkit.block.data.type.Gate) Bukkit.createBlockData(Material.OAK_FENCE_GATE);
        gate.setFacing(entryFacing);
        gate.setOpen(true);
        gate.setInWall(true);
        region.setBlockData(gateX, y, gateZ, gate);
        if (entryFacing == BlockFace.NORTH || entryFacing == BlockFace.SOUTH) {
            int pathStart = Math.min(gateZ, z + (entryFacing == BlockFace.NORTH ? -1 : l));
            int pathEnd = Math.max(gateZ, z + (entryFacing == BlockFace.NORTH ? -1 : l));
            for (int pathZ = pathStart; pathZ <= pathEnd; pathZ++) {
                region.setType(gateX, y, pathZ, Material.AIR);
            }
        } else {
            int pathStart = Math.min(gateX, x + (entryFacing == BlockFace.WEST ? -1 : w));
            int pathEnd = Math.max(gateX, x + (entryFacing == BlockFace.WEST ? -1 : w));
            for (int pathX = pathStart; pathX <= pathEnd; pathX++) {
                region.setType(pathX, y, gateZ, Material.AIR);
            }
        }
    }

    private void populateCentralFarm(LimitedRegion region, int centerX, int centerZ, int chunkMinX, int chunkMaxX, int chunkMinZ, int chunkMaxZ) {
        int farmMinX = centerX - 6;
        int farmMaxX = centerX + 6;
        int farmMinZ = centerZ - 10;
        int farmMaxZ = centerZ + 18;
        if (farmMaxX < chunkMinX || farmMinX > chunkMaxX || farmMaxZ < chunkMinZ || farmMinZ > chunkMaxZ) {
            return;
        }

        int minX = Math.max(farmMinX, chunkMinX);
        int maxX = Math.min(farmMaxX, chunkMaxX);
        int minZ = Math.max(farmMinZ, chunkMinZ);
        int maxZ = Math.min(farmMaxZ, chunkMaxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int y = region.getHighestBlockYAt(x, z);
                boolean waterLane = Math.floorMod(z - centerZ, 5) == 0 && x >= centerX - 1 && x <= centerX + 1;
                boolean edge = x == farmMinX || x == farmMaxX || z == farmMinZ || z == farmMaxZ;
                if (edge) {
                    region.setType(x, y, z, Material.GRASS_BLOCK);
                    continue;
                }
                if (waterLane) {
                    region.setType(x, y - 1, z, Material.WATER);
                    region.setType(x, y, z, Material.AIR);
                    continue;
                }
                region.setType(x, y - 1, z, Material.FARMLAND);
                Material crop = (Math.floorMod(x + z, 3) == 0) ? Material.WHEAT : Material.CARROTS;
                if (region.getType(x, y, z).isAir()) {
                    region.setType(x, y, z, crop);
                }
            }
        }
    }

    private void populateVillageTrees(LimitedRegion region, WoodPalette palette, int centerX, int centerZ, int chunkMinX, int chunkMaxX, int chunkMinZ, int chunkMaxZ) {
        Material log = switch (palette.wall()) {
            case SPRUCE_PLANKS -> Material.SPRUCE_LOG;
            case BIRCH_PLANKS -> Material.BIRCH_LOG;
            case JUNGLE_PLANKS -> Material.JUNGLE_LOG;
            default -> Material.OAK_LOG;
        };
        Material leaves = switch (log) {
            case SPRUCE_LOG -> Material.SPRUCE_LEAVES;
            case BIRCH_LOG -> Material.BIRCH_LEAVES;
            case JUNGLE_LOG -> Material.JUNGLE_LEAVES;
            default -> Material.OAK_LEAVES;
        };
        int[][] offsets = {
                {-44, -36}, {0, -46}, {44, -36}, {-52, -6}, {52, -6}, {-52, 22}, {52, 22}, {-30, 48}, {0, 54}, {30, 48}
        };
        for (int[] offset : offsets) {
            int x = centerX + offset[0];
            int z = centerZ + offset[1];
            if (x < chunkMinX || x > chunkMaxX || z < chunkMinZ || z > chunkMaxZ) {
                continue;
            }
            int y = region.getHighestBlockYAt(x, z);
            for (int yy = y + 1; yy <= y + 4; yy++) {
                region.setType(x, yy, z, log);
            }
            for (int xx = x - 2; xx <= x + 2; xx++) {
                for (int zz = z - 2; zz <= z + 2; zz++) {
                    if (xx < chunkMinX || xx > chunkMaxX || zz < chunkMinZ || zz > chunkMaxZ) {
                        continue;
                    }
                    for (int yy = y + 3; yy <= y + 5; yy++) {
                        if (Math.abs(xx - x) + Math.abs(zz - z) > 3) {
                            continue;
                        }
                        if (region.getType(xx, yy, zz).isAir()) {
                            region.setType(xx, yy, zz, leaves);
                        }
                    }
                }
            }
        }
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

    private BlockFace nearestRoadDirection(int x, int z) {
        int north = distanceToRoad(x, z, 0, -1, 24);
        int south = distanceToRoad(x, z, 0, 1, 24);
        int west = distanceToRoad(x, z, -1, 0, 24);
        int east = distanceToRoad(x, z, 1, 0, 24);
        int min = Math.min(Math.min(north, south), Math.min(west, east));
        if (min > 24) {
            return null;
        }
        if (min == north) return BlockFace.NORTH;
        if (min == south) return BlockFace.SOUTH;
        if (min == west) return BlockFace.WEST;
        return BlockFace.EAST;
    }

    private boolean houseTooCloseToRoad(int x, int z, int w, int l, int minDistance) {
        int minX = x - minDistance;
        int maxX = x + w - 1 + minDistance;
        int minZ = z - minDistance;
        int maxZ = z + l - 1 + minDistance;
        for (int xx = minX; xx <= maxX; xx++) {
            for (int zz = minZ; zz <= maxZ; zz++) {
                if (cityGenerator.getRoadType(xx, zz) != CityGenerator.RoadType.NONE) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean houseOverlapsRoad(int x, int z, int w, int l) {
        for (int xx = x; xx < x + w; xx++) {
            for (int zz = z; zz < z + l; zz++) {
                if (cityGenerator.getRoadType(xx, zz) != CityGenerator.RoadType.NONE) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isMountainVillage(int x, int z) {
        int dx = x - 300;
        int dz = z - 250;
        return (dx * dx) + (dz * dz) <= 220 * 220;
    }

    private enum HouseStyle {
        SINGLE_STORY,
        TWO_STORY,
        FENCED,
        CHIMNEY
    }
}
