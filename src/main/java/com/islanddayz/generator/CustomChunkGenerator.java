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
    private static final int MAX_OCEAN_DEPTH = 33;
    private static final int BORDER_HALF = 768;
    private static final int SAND_UNDERWATER_START = SEA_LEVEL - 16;

    private final IslandGenerator islandGenerator;
    private final TerrainGenerator terrainGenerator;
    private final CityGenerator cityGenerator;
    private final TreeGenerator treeGenerator;
    private final HouseGenerator houseGenerator;
    private final org.bukkit.util.noise.SimplexNoiseGenerator centerFlattenNoise = new org.bukkit.util.noise.SimplexNoiseGenerator(332211L);

    public CustomChunkGenerator(IslandGenerator islandGenerator, TerrainGenerator terrainGenerator, CityGenerator cityGenerator, TreeGenerator treeGenerator) {
        this.islandGenerator = islandGenerator;
        this.terrainGenerator = terrainGenerator;
        this.cityGenerator = cityGenerator;
        this.treeGenerator = treeGenerator;
        this.houseGenerator = new HouseGenerator(cityGenerator);
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
                    continue;
                }

                double cityInfluence = cityGenerator.cityInfluence(worldX, worldZ);
                cityInfluence = Math.max(cityInfluence, centerFlattenInfluence(worldX, worldZ));
                int topY = terrainGenerator.computeHeight(worldX, worldZ, islandMask, SEA_LEVEL, cityInfluence);
                int coastStairTop = getCoastStairTop(worldX, worldZ, islandMask, cityInfluence);
                if (coastStairTop > Integer.MIN_VALUE) {
                    topY = Math.max(topY, coastStairTop);
                    if (islandMask < 0.52) {
                        topY = Math.min(topY, SEA_LEVEL + 2);
                    }
                }

                boolean insideCity = cityInfluence > 0.12;
                Material topMaterial = pickTopMaterial(worldX, worldZ, topY, islandMask, insideCity);

                for (int y = 40; y <= topY; y++) {
                    Material layer = pickLayerMaterial(worldX, worldZ, y, topY, islandMask, cityInfluence, topMaterial);
                    chunkData.setBlock(localX, y, localZ, layer);
                }
                carveMountainCaves(chunkData, localX, localZ, worldX, worldZ, topY, islandMask);
            }
        }

        // Após gerar a ilha/praia, gera por último a escada praia -> fundo do mar até a borda.
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = (chunkX << 4) + localX;
                int worldZ = (chunkZ << 4) + localZ;
                double islandMask = islandGenerator.islandMask(worldX, worldZ);
                if (islandMask > 0.02D) {
                    continue;
                }
                applyOceanTransitionFromBeach(chunkData, localX, localZ, worldX, worldZ);
            }
        }
    }

    private Material pickLayerMaterial(int worldX, int worldZ, int y, int topY, double islandMask, double cityInfluence, Material topMaterial) {
        if (y == topY) {
            return topMaterial;
        }

        if (cityInfluence > 0.12) {
            return y >= topY - 3 ? Material.GRASS_BLOCK : Material.STONE;
        }

        if (isBeachLayer(worldX, worldZ, y, topY, islandMask, cityInfluence)) {
            return Material.SAND;
        }

        if (y >= topY - 4) {
            return Material.GRASS_BLOCK;
        }

        return Material.STONE;
    }

    private boolean isBeachLayer(int worldX, int worldZ, int y, int topY, double islandMask, double cityInfluence) {
        if (cityInfluence > 0.05) {
            return false;
        }

        boolean coastalBand = isCoastalZone(worldX, worldZ, islandMask);
        if (!coastalBand) {
            return false;
        }

        int coastStairTop = getCoastStairTop(worldX, worldZ, islandMask, cityInfluence);
        if (coastStairTop == Integer.MIN_VALUE) {
            return false;
        }

        int sandFloor = Math.max(SAND_UNDERWATER_START, coastStairTop - 2);
        return y >= sandFloor && y <= topY;
    }

    private int getCoastStairTop(int worldX, int worldZ, double islandMask, double cityInfluence) {
        if (cityInfluence > 0.05 || islandMask < 0.02 || islandMask > 0.55) {
            return Integer.MIN_VALUE;
        }

        double inland = Math.max(0.0, Math.min(1.0, (islandMask - 0.02) / 0.53));
        int step = (int) Math.floor(inland * 16.0);
        int variation = Math.floorMod(worldX * 13 + worldZ * 7, 2);
        return Math.min(SEA_LEVEL, SAND_UNDERWATER_START + step + variation);
    }

    private Material pickTopMaterial(int x, int z, int topY, double islandMask, boolean insideCity) {
        if (insideCity) {
            CityGenerator.RoadType roadType = cityGenerator.getRoadType(x, z);
            if (roadType == CityGenerator.RoadType.DIRT) {
                return Material.DIRT_PATH;
            }
            if (roadType == CityGenerator.RoadType.SAND) {
                return Material.SAND;
            }
            return Material.GRASS_BLOCK;
        }

        if (topY <= SEA_LEVEL + 5 && isCoastalZone(x, z, islandMask)) {
            return Material.SAND;
        }

        if (topY <= SEA_LEVEL + 17) {
            return Material.GRASS_BLOCK;
        }

        return Material.GRASS_BLOCK;
    }

    private void fillBaseOcean(ChunkData data, int x, int z) {
        for (int y = data.getMinHeight(); y <= 39; y++) {
            data.setBlock(x, y, z, Material.STONE);
        }
        for (int y = 40; y <= SEA_LEVEL; y++) {
            data.setBlock(x, y, z, Material.WATER);
        }
    }

    private void applyOceanTransitionFromBeach(ChunkData data, int x, int z, int worldX, int worldZ) {
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

    private boolean isCoastalZone(int worldX, int worldZ, double islandMask) {
        if (islandMask > 0.62) {
            return false;
        }
        double distanceFromEdge = Math.abs(islandGenerator.distanceFromIslandEdge(worldX, worldZ));
        return distanceFromEdge <= 58.0;
    }

    private void carveMountainCaves(ChunkData data, int localX, int localZ, int worldX, int worldZ, int topY, double islandMask) {
        if (islandMask < 0.6) {
            return;
        }
        int peakX = 300;
        int peakZ = 250;
        int peakRadius = 86;
        int dx = worldX - peakX;
        int dz = worldZ - peakZ;
        double dist2 = dx * (double) dx + dz * (double) dz;
        if (dist2 <= peakRadius * peakRadius) {
            int entryX = peakX + peakRadius - 8;
            int entryZ = peakZ - 4;
            int tunnelY = SEA_LEVEL + 22;
            for (int t = 0; t <= 46; t++) {
                int cx = entryX - t;
                int cz = entryZ + (int) Math.round(Math.sin(t * 0.22) * 4.0);
                int cy = tunnelY + (int) Math.round(Math.sin(t * 0.17) * 3.0);
                int radius = (t < 8) ? 5 : 3;
                int ddx = worldX - cx;
                int ddz = worldZ - cz;
                if ((ddx * ddx) + (ddz * ddz) > radius * radius) {
                    continue;
                }
                for (int y = cy - 3; y <= cy + 3 && y < topY; y++) {
                    data.setBlock(localX, y, localZ, Material.AIR);
                }
            }
        }
    }

    private double centerFlattenInfluence(int x, int z) {
        double dist = Math.sqrt((double) x * x + (double) z * z);
        if (dist > 290) {
            return 0.0;
        }
        double irregular = centerFlattenNoise.noise(x * 0.009, z * 0.009) * 38.0;
        double edge = 290 + irregular;
        double blend = Math.max(0.0, Math.min(1.0, (edge - dist) / 145.0));
        double smoothBlend = blend * blend * (3.0 - 2.0 * blend);
        double plateau = Math.max(0.0, Math.min(1.0, (210.0 - dist) / 70.0));
        return Math.max(smoothBlend, plateau);
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(new HousePopulator(houseGenerator), new TreePopulator(treeGenerator));
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
        return true;
    }


    private static final class HousePopulator extends BlockPopulator {
        private final HouseGenerator houseGenerator;

        private HousePopulator(HouseGenerator houseGenerator) {
            this.houseGenerator = houseGenerator;
        }

        @Override
        public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion region) {
            houseGenerator.populateChunk(region, chunkX, chunkZ, random);
        }
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
