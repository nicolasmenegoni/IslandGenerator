package com.islanddayz.generator;

import org.bukkit.Material;
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

                if (cityGenerator.getRoadType(centerX, centerZ) != CityGenerator.RoadType.NONE || !cityGenerator.isInsideCity(centerX, centerZ)) {
                    continue;
                }

                if (Math.floorMod(centerX * 31 + centerZ * 17, 9) != 0) {
                    continue;
                }

                int lotW = 14 + random.nextInt(7);
                int lotL = 14 + random.nextInt(7);
                int minX = centerX - lotW / 2;
                int minZ = centerZ - lotL / 2;

                if (!isLotValid(region, minX, minZ, lotW, lotL)) {
                    continue;
                }

                int houseW = lotW - (3 + random.nextInt(4));
                int houseL = lotL - (3 + random.nextInt(4));
                int houseX = minX + 1 + random.nextInt(Math.max(1, lotW - houseW - 1));
                int houseZ = minZ + 1 + random.nextInt(Math.max(1, lotL - houseL - 1));

                int y = region.getHighestBlockYAt(centerX, centerZ);
                buildHouse(region, random, houseX, y, houseZ, houseW, houseL);
            }
        }
    }

    private boolean isLotValid(LimitedRegion region, int minX, int minZ, int w, int l) {
        for (int x = minX; x < minX + w; x++) {
            for (int z = minZ; z < minZ + l; z++) {
                int y = region.getHighestBlockYAt(x, z);
                Material ground = region.getType(x, y - 1, z);
                if (cityGenerator.getRoadType(x, z) != CityGenerator.RoadType.NONE
                        || !cityGenerator.isInsideCity(x, z)
                        || (ground != Material.GRASS_BLOCK && ground != Material.DIRT && ground != Material.COARSE_DIRT)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void buildHouse(LimitedRegion region, Random random, int x, int y, int z, int w, int l) {
        Material[] wallChoices = {Material.BRICKS, Material.SMOOTH_STONE, Material.OAK_PLANKS, Material.SPRUCE_PLANKS};
        Material[] roofChoices = {Material.DARK_OAK_STAIRS, Material.SPRUCE_STAIRS, Material.STONE_BRICK_STAIRS};
        Material[] windowChoices = {Material.GLASS, Material.TINTED_GLASS, Material.WHITE_STAINED_GLASS, Material.GRAY_STAINED_GLASS};

        Material wall = wallChoices[random.nextInt(wallChoices.length)];
        Material roof = roofChoices[random.nextInt(roofChoices.length)];
        Material window = windowChoices[random.nextInt(windowChoices.length)];

        int height = 4 + random.nextInt(2);

        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < l; dz++) {
                region.setType(x + dx, y, z + dz, Material.SMOOTH_STONE);
                for (int dy = 1; dy <= height; dy++) {
                    boolean edge = dx == 0 || dz == 0 || dx == w - 1 || dz == l - 1;
                    if (edge) {
                        region.setType(x + dx, y + dy, z + dz, wall);
                    } else {
                        region.setType(x + dx, y + dy, z + dz, Material.AIR);
                    }
                }
            }
        }

        carveWindows(region, random, x, y, z, w, l, window);
        carveDoor(region, x, y, z, w, l);
        buildRoof(region, x, y + height, z, w, l, roof);
        buildInteriorRooms(region, random, x, y, z, w, l);
    }

    private void carveWindows(LimitedRegion region, Random random, int x, int y, int z, int w, int l, Material window) {
        for (int dx = 2; dx < w - 2; dx += 3) {
            if (random.nextBoolean()) {
                region.setType(x + dx, y + 2, z, window);
                region.setType(x + dx, y + 2, z + l - 1, window);
            }
        }
        for (int dz = 2; dz < l - 2; dz += 3) {
            if (random.nextBoolean()) {
                region.setType(x, y + 2, z + dz, window);
                region.setType(x + w - 1, y + 2, z + dz, window);
            }
        }
    }

    private void carveDoor(LimitedRegion region, int x, int y, int z, int w, int l) {
        int doorX = x + w / 2;
        region.setType(doorX, y + 1, z, Material.AIR);
        region.setType(doorX, y + 2, z, Material.AIR);
        region.setType(doorX, y + 1, z + 1, Material.OAK_DOOR);
    }

    private void buildRoof(LimitedRegion region, int x, int roofY, int z, int w, int l, Material roof) {
        int max = Math.max(w, l) / 2;
        for (int layer = 0; layer <= max; layer++) {
            for (int dx = layer; dx < w - layer; dx++) {
                for (int dz = layer; dz < l - layer; dz++) {
                    if (dx == layer || dz == layer || dx == w - layer - 1 || dz == l - layer - 1) {
                        region.setType(x + dx, roofY + layer / 2, z + dz, roof);
                    }
                }
            }
        }
    }

    private void buildInteriorRooms(LimitedRegion region, Random random, int x, int y, int z, int w, int l) {
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

        // Sala
        region.setType(x + 2, y + 1, z + 2, Material.CRAFTING_TABLE);
        // Cozinha
        region.setType(x + w - 3, y + 1, z + 2, Material.FURNACE);
        region.setType(x + w - 4, y + 1, z + 2, Material.CHEST);
        // Quarto
        region.setType(x + 2, y + 1, z + l - 3, Material.RED_BED);
        // Banheiro
        region.setType(x + w - 3, y + 1, z + l - 3, Material.CAULDRON);
        if (random.nextBoolean()) {
            region.setType(x + w - 4, y + 1, z + l - 3, Material.QUARTZ_BLOCK);
        }
    }
}
