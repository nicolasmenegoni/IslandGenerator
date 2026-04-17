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

                double density = treeNoise.noise(x * 0.02, z * 0.02);
                if (density < 0.28) {
                    continue;
                }

                int y = region.getHighestBlockYAt(x, z);
                Material ground = region.getType(x, y - 1, z);
                if (ground == Material.WATER || ground == Material.AIR) {
                    continue;
                }

                if (ground == Material.GRASS_BLOCK && random.nextDouble() < 0.35) {
                    placeGroundVegetation(region, random, x, y, z);
                }

                if (isTooClose(placed, x, y, z, 6.0)) {
                    continue;
                }

                if (ground == Material.SAND && mask < 0.42 && random.nextDouble() < 0.82) {
                    generateRealisticPalm(region, random, x, y, z);
                } else if ((ground == Material.GRASS_BLOCK || ground == Material.COARSE_DIRT || ground == Material.DIRT)
                        && random.nextDouble() < 0.68) {
                    double biomeBand = treeNoise.noise((x + 900) * 0.006, (z - 900) * 0.006);
                    if (biomeBand < -0.3) {
                        generatePine(region, random, x, y, z); // área de abeto
                    } else if (biomeBand < 0.05) {
                        generateAcacia(region, random, x, y, z); // área de acácia
                    } else if (biomeBand < 0.35) {
                        generateBirch(region, random, x, y, z); // área de bétula
                    } else {
                        generateBroadleaf(region, random, x, y, z); // área de carvalho
                    }
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

    private void generateBroadleaf(LimitedRegion region, Random random, int x, int y, int z) {
        int trunkHeight = 6 + random.nextInt(5);
        for (int i = 0; i < trunkHeight; i++) {
            region.setType(x, y + i, z, Material.OAK_LOG);
            if (i > 2 && random.nextDouble() < 0.18) {
                int bx = x + (random.nextBoolean() ? 1 : -1);
                int bz = z + (random.nextBoolean() ? 1 : -1);
                region.setType(bx, y + i, bz, Material.OAK_LOG);
            }
        }

        int top = y + trunkHeight;
        buildBlobCanopy(region, random, x, top, z, 3, Material.OAK_LEAVES);
    }

    private void generateBranchyTree(LimitedRegion region, Random random, int x, int y, int z) {
        int trunkHeight = 7 + random.nextInt(4);
        for (int i = 0; i < trunkHeight; i++) {
            region.setType(x, y + i, z, Material.DARK_OAK_LOG);
        }

        int top = y + trunkHeight;
        BlockFace[] branches = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace branch : branches) {
            if (random.nextDouble() < 0.75) {
                int bx = x + branch.getModX() * 2;
                int bz = z + branch.getModZ() * 2;
                region.setType(x + branch.getModX(), top - 1, z + branch.getModZ(), Material.DARK_OAK_LOG);
                region.setType(bx, top, bz, Material.DARK_OAK_LOG);
                buildBlobCanopy(region, random, bx, top, bz, 2, Material.DARK_OAK_LEAVES);
            }
        }

        buildBlobCanopy(region, random, x, top + 1, z, 3, Material.DARK_OAK_LEAVES);
    }

    private void generatePine(LimitedRegion region, Random random, int x, int y, int z) {
        int trunkHeight = 8 + random.nextInt(6);
        for (int i = 0; i < trunkHeight; i++) {
            region.setType(x, y + i, z, Material.SPRUCE_LOG);
        }

        int top = y + trunkHeight;
        int radius = 3;
        for (int layer = 0; layer < 6; layer++) {
            int ly = top - layer;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= radius + (random.nextBoolean() ? 0 : 1)) {
                        if (region.getType(x + dx, ly, z + dz).isAir()) {
                            region.setType(x + dx, ly, z + dz, Material.SPRUCE_LEAVES);
                        }
                    }
                }
            }
            if (layer % 2 == 1 && radius > 1) {
                radius--;
            }
        }
    }

    private void generateBirch(LimitedRegion region, Random random, int x, int y, int z) {
        int trunkHeight = 6 + random.nextInt(4);
        for (int i = 0; i < trunkHeight; i++) {
            region.setType(x, y + i, z, Material.BIRCH_LOG);
        }
        buildBlobCanopy(region, random, x, y + trunkHeight, z, 3, Material.BIRCH_LEAVES);
    }

    private void generateAcacia(LimitedRegion region, Random random, int x, int y, int z) {
        int trunkHeight = 5 + random.nextInt(3);
        int tx = x;
        int tz = z;
        for (int i = 0; i < trunkHeight; i++) {
            if (i > 2 && random.nextDouble() < 0.55) {
                tx += random.nextBoolean() ? 1 : -1;
            }
            region.setType(tx, y + i, tz, Material.ACACIA_LOG);
        }
        buildBlobCanopy(region, random, tx, y + trunkHeight, tz, 3, Material.ACACIA_LEAVES);
    }

    private void generateRealisticPalm(LimitedRegion region, Random random, int x, int y, int z) {
        int height = 7 + random.nextInt(5);
        int cx = x;
        int cz = z;

        BlockFace bendFace = switch (random.nextInt(4)) {
            case 0 -> BlockFace.NORTH;
            case 1 -> BlockFace.SOUTH;
            case 2 -> BlockFace.EAST;
            default -> BlockFace.WEST;
        };

        for (int i = 0; i < height; i++) {
            if (i > 1 && random.nextDouble() < 0.55) {
                cx += bendFace.getModX();
                cz += bendFace.getModZ();
            }
            region.setType(cx, y + i, cz, Material.JUNGLE_LOG);
        }

        int crownY = y + height;
        region.setType(cx, crownY, cz, Material.JUNGLE_LEAVES);
        buildPalmFrond(region, cx, crownY, cz, 1, 0);
        buildPalmFrond(region, cx, crownY, cz, -1, 0);
        buildPalmFrond(region, cx, crownY, cz, 0, 1);
        buildPalmFrond(region, cx, crownY, cz, 0, -1);
        buildPalmFrond(region, cx, crownY, cz, 1, 1);
        buildPalmFrond(region, cx, crownY, cz, -1, -1);

        if (random.nextDouble() < 0.6) {
            region.setType(cx + 1, crownY - 1, cz, Material.COCOA);
        }
    }

    private void buildPalmFrond(LimitedRegion region, int x, int y, int z, int dx, int dz) {
        for (int i = 1; i <= 4; i++) {
            int lx = x + dx * i;
            int lz = z + dz * i;
            int ly = y - (i > 2 ? 1 : 0);
            if (region.getType(lx, ly, lz).isAir()) {
                region.setType(lx, ly, lz, Material.JUNGLE_LEAVES);
            }
        }
    }


    private void placeGroundVegetation(LimitedRegion region, Random random, int x, int y, int z) {
        if (!region.getType(x, y, z).isAir()) {
            return;
        }

        double roll = random.nextDouble();
        if (roll < 0.30) {
            region.setType(x, y, z, Material.FERN);
        } else if (roll < 0.55) {
            if (region.getType(x, y + 1, z).isAir()) {
                region.setType(x, y, z, Material.TALL_GRASS);
            }
        } else if (roll < 0.70) {
            region.setType(x, y, z, Material.SHORT_GRASS);
        } else if (roll < 0.78) {
            region.setType(x, y, z, Material.LARGE_FERN);
        } else {
            Material[] flowers = {
                    Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM,
                    Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY
            };
            region.setType(x, y, z, flowers[random.nextInt(flowers.length)]);
        }
    }

    private void buildBlobCanopy(LimitedRegion region, Random random, int x, int y, int z, int radius, Material leaves) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    double dist = Math.sqrt(dx * dx + dz * dz + (dy * dy * 0.8));
                    if (dist <= radius + (random.nextDouble() * 0.6)) {
                        int lx = x + dx;
                        int ly = y + dy;
                        int lz = z + dz;
                        if (region.getType(lx, ly, lz).isAir()) {
                            region.setType(lx, ly, lz, leaves);
                        }
                    }
                }
            }
        }
    }
}
