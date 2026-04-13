package com.islanddayz.generator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.generator.LimitedRegion;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class CustomChunkGenerator extends ChunkGenerator {
    private static final int SEA_LEVEL = 63;

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

                int topY = terrainGenerator.computeHeight(worldX, worldZ, islandMask, SEA_LEVEL);
                Material topMaterial = pickTopMaterial(worldX, worldZ, topY, islandMask);

                for (int y = 40; y <= topY; y++) {
                    Material layer = Material.STONE;
                    if (y >= topY - 4 && y < topY) {
                        layer = Material.DIRT;
                    }
                    if (y == topY) {
                        layer = topMaterial;
                    }
                    chunkData.setBlock(localX, y, localZ, layer);
                }

                chunkData.setBiome(localX, localZ, Biome.PLAINS);
            }
        }
    }

    private Material pickTopMaterial(int x, int z, int topY, double islandMask) {
        if (cityGenerator.isInsideCity(x, z)) {
            if (cityGenerator.isRoad(x, z)) {
                return cityGenerator.isRoadStripe(x, z) ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE;
            }
            return Material.DIRT;
        }

        if (topY <= SEA_LEVEL + 2 || islandMask < 0.28D) {
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
