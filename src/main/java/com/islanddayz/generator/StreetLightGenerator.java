package com.islanddayz.generator;

import org.bukkit.Material;
import org.bukkit.generator.LimitedRegion;

public class StreetLightGenerator {
    private final CityGenerator cityGenerator;

    public StreetLightGenerator(CityGenerator cityGenerator) {
        this.cityGenerator = cityGenerator;
    }

    public void populateChunk(LimitedRegion region, int chunkX, int chunkZ) {
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = startX + localX;
                int z = startZ + localZ;

                if (!isCornerCandidate(x, z)) {
                    continue;
                }

                if (!passesSpacingRule(x, z)) {
                    continue;
                }

                int y = region.getHighestBlockYAt(x, z);
                Material ground = region.getType(x, y - 1, z);
                if (ground == Material.WATER || ground == Material.AIR) {
                    continue;
                }

                buildLight(region, x, y, z);
            }
        }
    }

    private boolean isCornerCandidate(int x, int z) {
        if (!cityGenerator.isRoad(x, z)) {
            return false;
        }

        boolean roadX = cityGenerator.isRoad(x + 1, z) || cityGenerator.isRoad(x - 1, z);
        boolean roadZ = cityGenerator.isRoad(x, z + 1) || cityGenerator.isRoad(x, z - 1);
        boolean edgeLot = !cityGenerator.isRoad(x + 2, z + 2) || !cityGenerator.isRoad(x - 2, z - 2);

        return roadX && roadZ && edgeLot && cityGenerator.isIntersectionNear(x, z);
    }

    private boolean passesSpacingRule(int x, int z) {
        int spacing = 13 + Math.floorMod(x * 7 + z * 11, 5);
        return Math.floorMod(Math.abs(x) + Math.abs(z), spacing) == 0;
    }

    private void buildLight(LimitedRegion region, int x, int y, int z) {
        region.setType(x, y - 1, z, Material.STONE_BRICKS);
        for (int i = 0; i < 4; i++) {
            region.setType(x, y + i, z, Material.POLISHED_ANDESITE_WALL);
        }

        region.setType(x, y + 4, z, Material.POLISHED_ANDESITE);
        region.setType(x + 1, y + 4, z, Material.IRON_BARS);
        region.setType(x - 1, y + 4, z, Material.IRON_BARS);
        region.setType(x, y + 4, z + 1, Material.IRON_BARS);
        region.setType(x, y + 4, z - 1, Material.IRON_BARS);

        region.setType(x + 1, y + 3, z, Material.LANTERN);
        region.setType(x - 1, y + 3, z, Material.LANTERN);
        region.setType(x, y + 3, z + 1, Material.LANTERN);
        region.setType(x, y + 3, z - 1, Material.LANTERN);
    }
}
