package com.islanddayz.generator;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

public class CustomChunkGenerator extends ChunkGenerator {
    private static final int SEA_LEVEL = 63;
    private static final int MAX_OCEAN_DEPTH = 33;
    private static final int BORDER_HALF = 1024;

    private final IslandGenerator islandGenerator;

    public CustomChunkGenerator(IslandGenerator islandGenerator) {
        this.islandGenerator = islandGenerator;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = (chunkX << 4) + localX;
                int worldZ = (chunkZ << 4) + localZ;

                double islandMask = islandGenerator.islandMask(worldX, worldZ);
                fillBaseOcean(chunkData, localX, localZ);
                if (islandMask <= 0.02D) {
                    applyOceanTransition(chunkData, localX, localZ, worldX, worldZ);
                    continue;
                }

                int topY = computeSimpleIslandHeight(islandMask);
                for (int y = 40; y <= topY; y++) {
                    chunkData.setBlock(localX, y, localZ, pickLayerMaterial(y, topY, islandMask));
                }
            }
        }
    }

    private int computeSimpleIslandHeight(double islandMask) {
        int elevation = (int) Math.round(islandMask * 16.0);
        return SEA_LEVEL + Math.max(1, elevation);
    }

    private Material pickLayerMaterial(int y, int topY, double islandMask) {
        if (y == topY) {
            return islandMask < 0.52 ? Material.SAND : Material.GRASS_BLOCK;
        }
        if (topY - y <= 3) {
            return islandMask < 0.52 ? Material.SAND : Material.DIRT;
        }
        return Material.STONE;
    }

    private void fillBaseOcean(ChunkData data, int x, int z) {
        for (int y = data.getMinHeight(); y <= 39; y++) {
            data.setBlock(x, y, z, Material.STONE);
        }
        for (int y = 40; y <= SEA_LEVEL; y++) {
            data.setBlock(x, y, z, Material.WATER);
        }
    }

    private void applyOceanTransition(ChunkData data, int x, int z, int worldX, int worldZ) {
        double edgeDistance = Math.max(0.0, islandGenerator.distanceFromIslandEdge(worldX, worldZ));
        int toBorder = Math.max(1, Math.min(BORDER_HALF - Math.abs(worldX), BORDER_HALF - Math.abs(worldZ)));
        double total = edgeDistance + toBorder;
        double progress = total <= 0.0 ? 1.0 : edgeDistance / total;

        int floorY = SEA_LEVEL - (int) Math.floor(progress * MAX_OCEAN_DEPTH);
        int sandStart = Math.max(data.getMinHeight(), floorY - 3);

        for (int y = data.getMinHeight(); y < sandStart; y++) {
            data.setBlock(x, y, z, Material.STONE);
        }
        for (int y = sandStart; y <= floorY; y++) {
            data.setBlock(x, y, z, Material.SAND);
        }
        for (int y = floorY + 1; y <= SEA_LEVEL; y++) {
            data.setBlock(x, y, z, Material.WATER);
        }
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of();
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
}
