package com.islanddayz.generator;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import java.util.List;
import java.util.Random;

public class CustomChunkGenerator extends ChunkGenerator {
    private static final int SEA_LEVEL = 63;
    private static final int MAX_OCEAN_DEPTH = 33;
    private static final int BORDER_HALF = 256;

    private final IslandGenerator islandGenerator;
    private final SimplexNoiseGenerator hillsNoise = new SimplexNoiseGenerator(918273L);
    private final SimplexNoiseGenerator detailNoise = new SimplexNoiseGenerator(123987L);
    private final SimplexNoiseGenerator ridgeNoise = new SimplexNoiseGenerator(778821L);

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

                int topY = computeSimpleIslandHeight(worldX, worldZ, islandMask);
                for (int y = 40; y <= topY; y++) {
                    chunkData.setBlock(localX, y, localZ, pickLayerMaterial(y, topY, islandMask));
                }

                tryPlacePalm(chunkData, worldX, worldZ, localX, localZ, topY, islandMask);
            }
        }
    }

    private int computeSimpleIslandHeight(int worldX, int worldZ, double islandMask) {
        double coastalFlatten = smoothStep(0.08, 0.52, islandMask);
        double interiorBoost = smoothStep(0.45, 0.95, islandMask);

        double base = SEA_LEVEL + 1 + (islandMask * 17.0);
        double hills = hillsNoise.noise(worldX * 0.010, worldZ * 0.010) * 7.5;
        double detail = detailNoise.noise(worldX * 0.025, worldZ * 0.025) * 2.2;
        double rugged = Math.abs(ridgeNoise.noise(worldX * 0.018, worldZ * 0.018)) * 6.5;
        double longRidge = Math.abs(ridgeNoise.noise((worldX + 250) * 0.006, (worldZ - 100) * 0.012)) * 7.0;

        double natural = base + ((hills + detail) * coastalFlatten) + ((rugged + longRidge) * interiorBoost);

        if (islandMask < 0.55) {
            double beachBlend = smoothStep(0.08, 0.55, islandMask);
            double maxCoastHeight = SEA_LEVEL + 1 + (beachBlend * 7.0);
            natural = Math.min(natural, maxCoastHeight);
        }
        if (islandMask < 0.64) {
            double transition = smoothStep(0.18, 0.64, islandMask);
            double softCap = SEA_LEVEL + 1 + (transition * 6.0);
            natural = Math.min(natural, softCap);
        }

        if (islandMask > 0.72) {
            double interiorPeak = smoothStep(0.72, 1.0, islandMask);
            natural += interiorPeak * 8.5;
        }

        return Math.max(SEA_LEVEL + 1, (int) Math.round(natural));
    }

    private Material pickLayerMaterial(int y, int topY, double islandMask) {
        if (y == topY) {
            return islandMask < 0.52 ? Material.SAND : Material.GRASS_BLOCK;
        }
        if (topY >= SEA_LEVEL + 14 && topY - y <= 2) {
            return Material.STONE;
        }
        if (topY - y <= 4) {
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
        // Populador natural extraído para classe dedicada para reduzir complexidade desta unidade.
        return List.of(new NaturalPopulator(islandGenerator));
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

    private void tryPlacePalm(ChunkData chunkData, int worldX, int worldZ, int localX, int localZ, int topY, double islandMask) {
        if (localX < 3 || localX > 12 || localZ < 3 || localZ > 12) {
            return;
        }
        if (islandMask < 0.18 || islandMask > 0.56) {
            return;
        }
        if (chunkData.getType(localX, topY, localZ) != Material.SAND) {
            return;
        }
        if (!isPalmSpot(worldX, worldZ)) {
            return;
        }

        int height = 6 + Math.floorMod(worldX * 13 + worldZ * 17, 3);
        BlockFace bendFace;
        switch (Math.floorMod(worldX * 7 + worldZ * 5, 4)) {
            case 0:
                bendFace = BlockFace.NORTH;
                break;
            case 1:
                bendFace = BlockFace.SOUTH;
                break;
            case 2:
                bendFace = BlockFace.EAST;
                break;
            default:
                bendFace = BlockFace.WEST;
                break;
        }

        int tx = localX;
        int tz = localZ;
        for (int i = 1; i <= height; i++) {
            if (i > 2 && Math.floorMod(worldX * 31 + worldZ * 29 + i * 11, 100) < 42) {
                tx += bendFace.getModX();
                tz += bendFace.getModZ();
            }
            if (tx < 1 || tx > 14 || tz < 1 || tz > 14) {
                return;
            }
            chunkData.setBlock(tx, topY + i, tz, Material.JUNGLE_LOG);
        }

        int crownY = topY + height;
        chunkData.setBlock(tx, crownY + 1, tz, Material.JUNGLE_LEAVES);
        buildFrond(chunkData, tx, crownY, tz, 1, 0);
        buildFrond(chunkData, tx, crownY, tz, -1, 0);
        buildFrond(chunkData, tx, crownY, tz, 0, 1);
        buildFrond(chunkData, tx, crownY, tz, 0, -1);
        buildFrond(chunkData, tx, crownY, tz, 1, 1);
        buildFrond(chunkData, tx, crownY, tz, -1, -1);
    }

    private void buildFrond(ChunkData chunkData, int x, int y, int z, int dx, int dz) {
        for (int i = 1; i <= 3; i++) {
            int lx = x + dx * i;
            int lz = z + dz * i;
            int ly = y - (i > 1 ? 1 : 0);
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15) {
                continue;
            }
            if (chunkData.getType(lx, ly, lz).isAir()) {
                chunkData.setBlock(lx, ly, lz, Material.JUNGLE_LEAVES);
            }
        }
    }

    private boolean isPalmSpot(int worldX, int worldZ) {
        int hash = Math.floorMod(worldX * 7349 + worldZ * 9151 + worldX * worldZ, 100);
        return hash < 18;
    }

    private double smoothStep(double start, double end, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - start) / (end - start)));
        return t * t * (3.0 - 2.0 * t);
    }

}
