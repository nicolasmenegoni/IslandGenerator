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

                if (mask > 0.52 && shouldPlaceTree(x, z, mask) && generateLargeTree(world, random, x, y, z, mask)) {
                    continue;
                }

                placeGroundVegetation(world, random, x, y, z, mask);
            }
        }
    }

    private boolean shouldPlaceTree(int x, int z, double mask) {
        int hash = Math.floorMod(x * 1847 + z * 2633 + x * z, 100);
        int threshold = mask > 0.72 ? 16 : 11;
        return hash < threshold;
    }

    private boolean generateLargeTree(World world, Random random, int x, int y, int z, double mask) {
        int trunkHeight = 11 + random.nextInt(8);
        int trunkRadius = mask > 0.75 ? 2 : 1;

        for (int yy = 0; yy <= trunkHeight + 2; yy++) {
            int r = yy < trunkHeight * 0.7 ? trunkRadius : 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!world.getBlockAt(x + dx, y + yy, z + dz).getType().isAir()) {
                        return false;
                    }
                }
            }
        }

        createButtressRoots(world, random, x, y, z, trunkRadius);

        int bendX = random.nextInt(3) - 1;
        int bendZ = random.nextInt(3) - 1;
        for (int yy = 0; yy < trunkHeight; yy++) {
            int shiftX = yy > trunkHeight / 2 ? (yy - trunkHeight / 2) / 4 * bendX : 0;
            int shiftZ = yy > trunkHeight / 2 ? (yy - trunkHeight / 2) / 4 * bendZ : 0;
            int tx = x + shiftX;
            int tz = z + shiftZ;
            int r = yy < trunkHeight * 0.68 ? trunkRadius : 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if ((dx * dx) + (dz * dz) <= (r * r) + 1) {
                        Material trunkType = random.nextDouble() < 0.20 ? Material.DARK_OAK_LOG : Material.OAK_LOG;
                        world.getBlockAt(tx + dx, y + yy, tz + dz).setType(trunkType, false);
                    }
                }
            }
        }

        int topX = x + Math.max(-2, Math.min(2, bendX * 2));
        int topZ = z + Math.max(-2, Math.min(2, bendZ * 2));
        int canopyBase = y + trunkHeight - 4;
        buildIrregularCanopy(world, random, topX, canopyBase + 2, topZ, 5);
        buildIrregularCanopy(world, random, topX + randomOffset(random), canopyBase, topZ + randomOffset(random), 4);
        buildIrregularCanopy(world, random, topX + randomOffset(random), canopyBase + 1, topZ + randomOffset(random), 3);
        buildIrregularCanopy(world, random, topX + randomOffset(random), canopyBase + 4, topZ + randomOffset(random), 3);

        placeBranch(world, random, topX, y + trunkHeight - 4, topZ, BlockFace.NORTH);
        placeBranch(world, random, topX, y + trunkHeight - 5, topZ, BlockFace.SOUTH);
        placeBranch(world, random, topX, y + trunkHeight - 6, topZ, BlockFace.EAST);
        placeBranch(world, random, topX, y + trunkHeight - 5, topZ, BlockFace.WEST);
        addVines(world, random, topX, canopyBase, topZ, 9);
        return true;
    }

    private void createButtressRoots(World world, Random random, int x, int y, int z, int trunkRadius) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            int length = 1 + random.nextInt(2 + trunkRadius);
            for (int i = 1; i <= length; i++) {
                int rx = x + face.getModX() * i;
                int rz = z + face.getModZ() * i;
                if (!world.getBlockAt(rx, y - 1, rz).getType().isSolid()) {
                    break;
                }
                world.getBlockAt(rx, y, rz).setType(Material.OAK_LOG, false);
                if (random.nextDouble() < 0.55 && world.getBlockAt(rx, y + 1, rz).getType().isAir()) {
                    world.getBlockAt(rx, y + 1, rz).setType(Material.OAK_LOG, false);
                }
            }
        }
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
            Material branchType = random.nextDouble() < 0.25 ? Material.DARK_OAK_LOG : Material.OAK_LOG;
            world.getBlockAt(bx, by, bz).setType(branchType, false);
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
                            Material leaves = random.nextDouble() < 0.28 ? Material.JUNGLE_LEAVES : Material.OAK_LEAVES;
                            world.getBlockAt(x, y, z).setType(leaves, false);
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
        if (roll < 0.10 && mask > 0.55) {
            world.getBlockAt(x, y, z).setType(Material.AZALEA, false);
            return;
        }
        if (roll < 0.18 && mask > 0.55) {
            world.getBlockAt(x, y, z).setType(Material.FLOWERING_AZALEA, false);
            return;
        }
        if (roll < 0.32 && mask > 0.55) {
            world.getBlockAt(x, y, z).setType(Material.MOSS_BLOCK, false);
            if (world.getBlockAt(x, y + 1, z).getType().isAir() && random.nextDouble() < 0.65) {
                world.getBlockAt(x, y + 1, z).setType(Material.SHORT_GRASS, false);
            }
            return;
        }
        if (roll < 0.56) {
            world.getBlockAt(x, y, z).setType(Material.FERN, false);
            return;
        }
        if (roll < 0.78 && world.getBlockAt(x, y + 1, z).getType().isAir()) {
            world.getBlockAt(x, y, z).setType(Material.TALL_GRASS, false);
            return;
        }
        if (roll < 0.94) {
            world.getBlockAt(x, y, z).setType(Material.SHORT_GRASS, false);
            return;
        }
        if (mask > 0.60 && world.getBlockAt(x, y + 1, z).getType().isAir()) {
            world.getBlockAt(x, y, z).setType(Material.LARGE_FERN, false);
        }
    }
}
