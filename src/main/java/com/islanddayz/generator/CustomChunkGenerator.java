package com.islanddayz.generator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

public class CustomChunkGenerator extends ChunkGenerator {
    private static final int SEA_LEVEL = 63;
    private static final int SAND_UNDERWATER_START = SEA_LEVEL - 16;

    private final IslandGenerator islandGenerator;
    private final TerrainGenerator terrainGenerator;
    private final CityGenerator cityGenerator;
    private final TreeGenerator treeGenerator;
    private final StreetLightGenerator streetLightGenerator;

    public CustomChunkGenerator(IslandGenerator islandGenerator, TerrainGenerator terrainGenerator, CityGenerator cityGenerator, TreeGenerator treeGenerator) {
        this.islandGenerator = islandGenerator;
        this.terrainGenerator = terrainGenerator;
        this.cityGenerator = cityGenerator;
        this.treeGenerator = treeGenerator;
        this.streetLightGenerator = new StreetLightGenerator(cityGenerator);
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = (chunkX << 4) + localX;
                int worldZ = (chunkZ << 4) + localZ;

                double islandMask = islandGenerator.islandMask(worldX, worldZ);
                fillOcean(chunkData, localX, localZ);
                if (islandMask <= 0.02D) {
                    continue;
                }

                double cityInfluence = cityGenerator.cityInfluence(worldX, worldZ);
                int topY = terrainGenerator.computeHeight(worldX, worldZ, islandMask, SEA_LEVEL, cityInfluence);
                boolean insideCity = cityInfluence > 0.12;
                Material topMaterial = pickTopMaterial(worldX, worldZ, topY, islandMask, insideCity);

                for (int y = 40; y <= topY; y++) {
                    Material layer = pickLayerMaterial(worldX, worldZ, y, topY, islandMask, cityInfluence, topMaterial);
                    chunkData.setBlock(localX, y, localZ, layer);
                }
            }
        }
    }

    private Material pickLayerMaterial(int worldX, int worldZ, int y, int topY, double islandMask, double cityInfluence, Material topMaterial) {
        if (y == topY) {
            return topMaterial;
        }

        if (cityInfluence > 0.12) {
            return y >= topY - 3 ? Material.DIRT : Material.STONE;
        }

        if (isBeachLayer(worldX, worldZ, y, topY, islandMask, cityInfluence)) {
            return Material.SAND;
        }

        if (y >= topY - 4) {
            return Material.DIRT;
        }

        return Material.STONE;
    }

    private boolean isBeachLayer(int worldX, int worldZ, int y, int topY, double islandMask, double cityInfluence) {
        if (cityInfluence > 0.05) {
            return false;
        }

        boolean coastalBand = islandMask < 0.58D || topY <= SEA_LEVEL + 18;
        if (!coastalBand) {
            return false;
        }

        int step = Math.max(0, Math.min(20, (int) Math.floor((islandMask - 0.02) * 42.0)));
        int variation = Math.floorMod(worldX * 29 + worldZ * 11, 4) - 1;
        int sandFloor = SAND_UNDERWATER_START + step + variation;

        int sandCap = SEA_LEVEL + Math.max(1, step / 4);
        if (topY <= SEA_LEVEL) {
            return y >= sandFloor;
        }

        int inlandDepth = 8 - Math.min(5, step / 3);
        int inlandCap = Math.max(sandFloor, topY - inlandDepth);
        return y >= inlandCap && y <= sandCap + 6;
    }

    private Material pickTopMaterial(int x, int z, int topY, double islandMask, boolean insideCity) {
        if (insideCity) {
            if (cityGenerator.isRoad(x, z)) {
                return cityGenerator.isRoadStripe(x, z) ? Material.YELLOW_CONCRETE : Material.GRAY_CONCRETE;
            }
            return Material.GRASS_BLOCK;
        }

        if (topY <= SEA_LEVEL + 5 || islandMask < 0.40D) {
            return Material.SAND;
        }

        if (topY <= SEA_LEVEL + 10) {
            return Material.COARSE_DIRT;
        }

        return Material.GRASS_BLOCK;
    }

    private void fillOcean(ChunkData data, int x, int z) {
        for (int y = data.getMinHeight(); y <= 39; y++) {
            data.setBlock(x, y, z, Material.STONE);
        }
        for (int y = 40; y <= SEA_LEVEL; y++) {
            data.setBlock(x, y, z, Material.WATER);
        }
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(new TreePopulator(treeGenerator), new StreetLightPopulator(streetLightGenerator));
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    private static final class TreePopulator extends BlockPopulator {
        private final TreeGenerator treeGenerator;

        private TreePopulator(TreeGenerator treeGenerator) {
            this.treeGenerator = treeGenerator;
        }

        @Override
        public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion region) {
            treeGenerator.populateChunk(region, chunkX, chunkZ, random);
        }
    }

    private static final class StreetLightPopulator extends BlockPopulator {
        private final StreetLightGenerator streetLightGenerator;

        private StreetLightPopulator(StreetLightGenerator streetLightGenerator) {
            this.streetLightGenerator = streetLightGenerator;
        }

        @Override
        public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion region) {
            streetLightGenerator.populateChunk(region, chunkX, chunkZ);
        }
    }
}
