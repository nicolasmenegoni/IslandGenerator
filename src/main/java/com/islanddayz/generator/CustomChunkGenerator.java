package com.islanddayz.generator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class CustomChunkGenerator extends ChunkGenerator {
    private static final int SEA_LEVEL = 63;
    private static final int SAND_UNDERWATER_START = SEA_LEVEL - 16;

    private final IslandGenerator islandGenerator;
    private final TerrainGenerator terrainGenerator;
    private final CityGenerator cityGenerator;
    private final TreeGenerator treeGenerator;

    public CustomChunkGenerator(IslandGenerator islandGenerator, TerrainGenerator terrainGenerator, CityGenerator cityGenerator, TreeGenerator treeGenerator) {
        this.islandGenerator = islandGenerator;
        this.terrainGenerator = terrainGenerator;
        this.cityGenerator = cityGenerator;
        this.treeGenerator = treeGenerator;
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

                boolean insideCity = cityGenerator.isInsideCity(worldX, worldZ);
                int topY = terrainGenerator.computeHeight(worldX, worldZ, islandMask, SEA_LEVEL, insideCity);
                Material topMaterial = pickTopMaterial(worldX, worldZ, topY, islandMask, insideCity);

                for (int y = 40; y <= topY; y++) {
                    Material layer = pickLayerMaterial(y, topY, islandMask, insideCity, topMaterial);
                    chunkData.setBlock(localX, y, localZ, layer);
                }
            }
        }
    }

    private Material pickLayerMaterial(int y, int topY, double islandMask, boolean insideCity, Material topMaterial) {
        if (y == topY) {
            return topMaterial;
        }

        if (insideCity) {
            return y >= topY - 3 ? Material.DIRT : Material.STONE;
        }

        if (isBeachLayer(y, topY, islandMask)) {
            return Material.SAND;
        }

        if (y >= topY - 4) {
            return Material.DIRT;
        }

        return Material.STONE;
    }

    private boolean isBeachLayer(int y, int topY, double islandMask) {
        boolean coastalBand = islandMask < 0.34D || topY <= SEA_LEVEL + 4;
        if (!coastalBand) {
            return false;
        }

        if (topY <= SEA_LEVEL) {
            return y >= SAND_UNDERWATER_START;
        }

        return y >= Math.max(SAND_UNDERWATER_START, topY - 5);
    }

    private Material pickTopMaterial(int x, int z, int topY, double islandMask, boolean insideCity) {
        if (insideCity) {
            if (cityGenerator.isRoad(x, z)) {
                return cityGenerator.isRoadStripe(x, z) ? Material.YELLOW_CONCRETE : Material.GRAY_CONCRETE;
            }
            return Material.DIRT;
        }

        if (topY <= SEA_LEVEL + 2 || islandMask < 0.30D) {
            return Material.SAND;
        }

        if (topY <= SEA_LEVEL + 5) {
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
        return Collections.singletonList(new TreePopulator(treeGenerator));
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
}
