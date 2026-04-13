package com.islanddayz.generator;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.util.Vector;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TreeGenerator {
    private final IslandGenerator islandGenerator;
    private final CityGenerator cityGenerator;
    private final SimplexNoiseGenerator treeNoise = new SimplexNoiseGenerator(224477L);

    public TreeGenerator(IslandGenerator islandGenerator, CityGenerator cityGenerator) {
        this.islandGenerator = islandGenerator;
        this.cityGenerator = cityGenerator;
    }

    public void populateChunk(LimitedRegion region, int chunkX, int chunkZ, Random random) {
        List<Vector> placed = new ArrayList<>();
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;

        for (int localX = 1; localX < 15; localX += 2) {
            for (int localZ = 1; localZ < 15; localZ += 2) {
                int x = startX + localX;
                int z = startZ + localZ;
                double mask = islandGenerator.islandMask(x, z);
                if (mask < 0.2 || cityGenerator.isInsideCity(x, z)) {
                    continue;
                }

                double density = treeNoise.noise(x * 0.03, z * 0.03);
                if (density < 0.36) {
                    continue;
                }

                int y = region.getHighestBlockYAt(x, z);
                Material ground = region.getType(x, y - 1, z);
                if (ground == Material.WATER || ground == Material.AIR) {
                    continue;
                }

                if (isTooClose(placed, x, y, z, 5.0)) {
                    continue;
                }

                if (ground == Material.SAND && random.nextDouble() < 0.75) {
                    generatePalm(region, random, x, y, z);
                } else if ((ground == Material.GRASS_BLOCK || ground == Material.COARSE_DIRT || ground == Material.DIRT)
                        && random.nextDouble() < 0.6) {
                    generateInteriorTree(region, random, x, y, z);
                }

                placed.add(new Vector(x, y, z));
            }
        }
    }

    private boolean isTooClose(List<Vector> existing, int x, int y, int z, double minDistance) {
        for (Vector pos : existing) {
            if (pos.distanceSquared(new Vector(x, y, z)) < minDistance * minDistance) {
                return true;
            }
        }
        return false;
    }

    private void generateInteriorTree(LimitedRegion region, Random random, int x, int y, int z) {
        int trunkHeight = 5 + random.nextInt(4);
        for (int i = 0; i < trunkHeight; i++) {
            region.setType(x, y + i, z, Material.OAK_LOG);
        }

        int canopyCenterY = y + trunkHeight;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    double dist = Math.sqrt(dx * dx + dz * dz + (dy * dy * 0.7));
                    if (dist <= 2.4 + (random.nextDouble() * 0.5)) {
                        int lx = x + dx;
                        int ly = canopyCenterY + dy;
                        int lz = z + dz;
                        if (region.getType(lx, ly, lz).isAir()) {
                            region.setType(lx, ly, lz, Material.OAK_LEAVES);
                        }
                    }
                }
            }
        }
    }

    private void generatePalm(LimitedRegion region, Random random, int x, int y, int z) {
        int height = 6 + random.nextInt(4);
        int cx = x;
        int cz = z;

        BlockFace bendFace = switch (random.nextInt(4)) {
            case 0 -> BlockFace.NORTH;
            case 1 -> BlockFace.SOUTH;
            case 2 -> BlockFace.EAST;
            default -> BlockFace.WEST;
        };

        for (int i = 0; i < height; i++) {
            if (i > height / 2 && random.nextDouble() < 0.42) {
                cx += bendFace.getModX();
                cz += bendFace.getModZ();
            }
            region.setType(cx, y + i, cz, Material.JUNGLE_LOG);
        }

        int topY = y + height;
        region.setType(cx, topY, cz, Material.JUNGLE_LEAVES);

        int[][] arms = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (int[] arm : arms) {
            for (int i = 1; i <= 3; i++) {
                int lx = cx + (arm[0] * i) / 2;
                int lz = cz + (arm[1] * i) / 2;
                int ly = topY - (i > 2 ? 1 : 0);
                if (region.getType(lx, ly, lz).isAir()) {
                    region.setType(lx, ly, lz, Material.JUNGLE_LEAVES);
                }
            }
        }
    }
}
