package com.islanddayz.generator;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.generator.BlockPopulator;

import java.util.Random;

public class NaturalPopulator extends BlockPopulator {
    private final IslandGenerator islandGenerator;

    public NaturalPopulator(IslandGenerator islandGenerator) {
        this.islandGenerator = islandGenerator;
    }

    @Override
    public void populate(World world, Random random, Chunk source) {
        int startX = source.getX() << 4;
        int startZ = source.getZ() << 4;

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = startX + localX;
                int z = startZ + localZ;
                double mask = islandGenerator.islandMask(x, z);
                if (mask < 0.36) {
                    continue;
                }

                int y = world.getHighestBlockYAt(x, z);
                Material ground = world.getBlockAt(x, y - 1, z).getType();
                if (ground == Material.WATER || ground == Material.SAND) {
                    continue;
                }
                if (!world.getBlockAt(x, y, z).getType().isAir()) {
                    continue;
                }

                if (mask > 0.58 && shouldPlaceTree(x, z) && generateLargeTree(world, random, x, y, z)) {
                    continue;
                }

                placeGroundVegetation(world, random, x, y, z, mask);
            }
        }
    }

    private boolean shouldPlaceTree(int x, int z) {
        int hash = Math.floorMod(x * 1847 + z * 2633 + x * z, 100);
        return hash < 9;
    }

    private boolean generateLargeTree(World world, Random random, int x, int y, int z) {
        int trunkHeight = 10 + random.nextInt(7);
        int radius = 1;

        for (int yy = 0; yy <= trunkHeight + 2; yy++) {
            int r = yy < trunkHeight * 0.6 ? radius : 0;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!world.getBlockAt(x + dx, y + yy, z + dz).getType().isAir()) {
                        return false;
                    }
                }
            }
        }

        for (int yy = 0; yy < trunkHeight; yy++) {
            int r = yy < trunkHeight * 0.65 ? 1 : 0;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    world.getBlockAt(x + dx, y + yy, z + dz).setType(Material.OAK_LOG, false);
                }
            }
        }

        int canopyBase = y + trunkHeight - 3;
        buildIrregularCanopy(world, random, x, canopyBase + 2, z, 4);
        buildIrregularCanopy(world, random, x + randomOffset(random), canopyBase, z + randomOffset(random), 3);
        buildIrregularCanopy(world, random, x + randomOffset(random), canopyBase + 1, z + randomOffset(random), 3);

        placeBranch(world, random, x, y + trunkHeight - 4, z, BlockFace.NORTH);
        placeBranch(world, random, x, y + trunkHeight - 5, z, BlockFace.SOUTH);
        placeBranch(world, random, x, y + trunkHeight - 6, z, BlockFace.EAST);
        placeBranch(world, random, x, y + trunkHeight - 5, z, BlockFace.WEST);
        addVines(world, random, x, canopyBase, z, 5);
        return true;
    }

    private int randomOffset(Random random) {
        return random.nextInt(5) - 2;
    }

    private void placeBranch(World world, Random random, int x, int y, int z, BlockFace face) {
        int len = 2 + random.nextInt(3);
        for (int i = 1; i <= len; i++) {
            int bx = x + (face.getModX() * i);
            int bz = z + (face.getModZ() * i);
            int by = y + (i / 2);
            world.getBlockAt(bx, by, bz).setType(Material.OAK_LOG, false);
            if (i == len) {
                buildIrregularCanopy(world, random, bx, by + 1, bz, 2);
            }
        }
    }

    private void buildIrregularCanopy(World world, Random random, int cx, int cy, int cz, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    double dist = Math.sqrt((dx * dx) + (dz * dz) + (dy * dy * 1.25));
                    if (dist <= radius + (random.nextDouble() * 0.9)) {
                        int x = cx + dx;
                        int y = cy + dy;
                        int z = cz + dz;
                        if (world.getBlockAt(x, y, z).getType().isAir()) {
                            world.getBlockAt(x, y, z).setType(Material.OAK_LEAVES, false);
                        }
                    }
                }
            }
        }
    }

    private void addVines(World world, Random random, int cx, int baseY, int cz, int count) {
        for (int i = 0; i < count; i++) {
            int x = cx + random.nextInt(9) - 4;
            int z = cz + random.nextInt(9) - 4;
            int y = baseY + random.nextInt(5);
            if (!world.getBlockAt(x, y, z).getType().name().contains("LEAVES")) {
                continue;
            }
            int length = 2 + random.nextInt(4);
            for (int l = 1; l <= length; l++) {
                if (!world.getBlockAt(x, y - l, z).getType().isAir()) {
                    break;
                }
                world.getBlockAt(x, y - l, z).setType(Material.VINE, false);
            }
        }
    }

    private void placeGroundVegetation(World world, Random random, int x, int y, int z, double mask) {
        double roll = random.nextDouble();
        if (roll < 0.14 && mask > 0.50) {
            world.getBlockAt(x, y, z).setType(Material.AZALEA, false);
            return;
        }
        if (roll < 0.30 && mask > 0.50) {
            world.getBlockAt(x, y, z).setType(Material.FLOWERING_AZALEA, false);
            return;
        }
        if (roll < 0.53) {
            world.getBlockAt(x, y, z).setType(Material.FERN, false);
            return;
        }
        if (roll < 0.80 && world.getBlockAt(x, y + 1, z).getType().isAir()) {
            world.getBlockAt(x, y, z).setType(Material.TALL_GRASS, false);
            return;
        }
        if (roll < 0.95) {
            world.getBlockAt(x, y, z).setType(Material.SHORT_GRASS, false);
        }
    }
}
