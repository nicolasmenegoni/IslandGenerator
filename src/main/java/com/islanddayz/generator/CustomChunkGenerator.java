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
                fillBaseOcean(chunkData, localX, localZ);
                if (islandMask <= 0.02D) {
                    continue;
                }

                double cityInfluence = cityGenerator.cityInfluence(worldX, worldZ);
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
            if (roadType == CityGenerator.RoadType.MAIN) {
                return cityGenerator.isRoadStripe(x, z) ? Material.YELLOW_CONCRETE : Material.GRAY_CONCRETE;
            }
            if (roadType == CityGenerator.RoadType.DIRT) {
                return Material.DIRT_PATH;
            }
            return Material.GRASS_BLOCK;
        }

        if (topY <= SEA_LEVEL + 6 || islandMask < 0.48D) {
            return Material.SAND;
        }

        if (topY <= SEA_LEVEL + 12) {
            return Material.COARSE_DIRT;
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
