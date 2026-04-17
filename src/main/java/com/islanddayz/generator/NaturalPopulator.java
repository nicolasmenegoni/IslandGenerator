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

                int highestY = world.getHighestBlockYAt(x, z);
                int y = highestY + 1;
                Material ground = world.getBlockAt(x, y - 1, z).getType();
                if (ground == Material.SAND || ground == Material.RED_SAND) {
                    if (world.getBlockAt(x, y, z).getType().isAir() && random.nextDouble() < 0.008) {
                        world.getBlockAt(x, y, z).setType(Material.DEAD_BUSH, false);
                    }
                    continue;
                }
                if (ground == Material.WATER) {
                    continue;
                }
                if (ground != Material.GRASS_BLOCK && ground != Material.DIRT && ground != Material.COARSE_DIRT) {
                    continue;
                }
                if (!world.getBlockAt(x, y, z).getType().isAir()) {
                    continue;
                }

                if (mask > 0.40 && shouldPlaceTree(x, z, mask) && generateLargeTree(world, random, x, y, z, mask)) {
                    continue;
                }

                placeGroundVegetation(world, random, x, y, z, mask);
            }
        }
    }

    private boolean shouldPlaceTree(int x, int z, double mask) {
        int hash = Math.floorMod(x * 1847 + z * 2633 + x * z, 100);
        int threshold = mask > 0.76 ? 35 : (mask > 0.62 ? 28 : 20);
        return hash < threshold;
    }

    private boolean generateLargeTree(World world, Random random, int x, int y, int z, double mask) {
        int trunkHeight = 9 + random.nextInt(6);
        int trunkRadius = 1;

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
                        world.getBlockAt(tx + dx, y + yy, tz + dz).setType(Material.OAK_LOG, false);
                    }
                }
            }
        }

        int topX = x + Math.max(-2, Math.min(2, bendX * 2));
        int topZ = z + Math.max(-2, Math.min(2, bendZ * 2));
        int canopyBase = y + trunkHeight - 3;
        buildIrregularCanopy(world, random, topX, canopyBase + 1, topZ, 3);
        buildLayeredCanopy(world, random, topX, canopyBase, topZ);

        placeBranch(world, random, topX, y + trunkHeight - 4, topZ, BlockFace.NORTH);
        placeBranch(world, random, topX, y + trunkHeight - 5, topZ, BlockFace.SOUTH);
        placeBranch(world, random, topX, y + trunkHeight - 6, topZ, BlockFace.EAST);
        placeBranch(world, random, topX, y + trunkHeight - 5, topZ, BlockFace.WEST);
        if (random.nextDouble() < 0.55) {
            placeBranch(world, random, topX, y + trunkHeight - 6, topZ, BlockFace.NORTH_EAST);
        }
        if (random.nextDouble() < 0.55) {
            placeBranch(world, random, topX, y + trunkHeight - 6, topZ, BlockFace.SOUTH_WEST);
        }
        addVines(world, random, topX, canopyBase, topZ, 10);
        placeForestUndergrowth(world, random, x, y, z, trunkRadius);
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

    private void buildLayeredCanopy(World world, Random random, int x, int y, int z) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            int lx = x + (face.getModX() * (2 + random.nextInt(2)));
            int lz = z + (face.getModZ() * (2 + random.nextInt(2)));
            int ly = y + random.nextInt(2);
            buildIrregularCanopy(world, random, lx, ly, lz, 2 + random.nextInt(2));
        }
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

    private void placeForestUndergrowth(World world, Random random, int x, int y, int z, int trunkRadius) {
        int radius = 4 + trunkRadius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > (radius * radius)) {
                    continue;
                }

                int px = x + dx;
                int pz = z + dz;
                int py = world.getHighestBlockYAt(px, pz) + 1;
                if (!world.getBlockAt(px, py, pz).getType().isAir()) {
                    continue;
                }
                Material base = world.getBlockAt(px, py - 1, pz).getType();
                if (base == Material.WATER || base == Material.SAND) {
                    continue;
                }
                if (random.nextDouble() < 0.18) {
                    placeGroundVegetation(world, random, px, py, pz, 0.85);
                }
                if (random.nextDouble() < 0.04) {
                    placeShrub(world, random, px, py, pz);
                }
            }
        }
    }

    private void placeShrub(World world, Random random, int x, int y, int z) {
        if (!world.getBlockAt(x, y, z).getType().isAir()) {
            return;
        }
        world.getBlockAt(x, y, z).setType(Material.OAK_LEAVES, false);
        if (random.nextBoolean() && world.getBlockAt(x, y + 1, z).getType().isAir()) {
            world.getBlockAt(x, y + 1, z).setType(Material.OAK_LEAVES, false);
        }
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (random.nextDouble() < 0.75 && world.getBlockAt(x + face.getModX(), y, z + face.getModZ()).getType().isAir()) {
                world.getBlockAt(x + face.getModX(), y, z + face.getModZ()).setType(Material.OAK_LEAVES, false);
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
        if (roll < 0.08) {
            Material[] flowers = {
                    Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM,
                    Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY
            };
            world.getBlockAt(x, y, z).setType(flowers[random.nextInt(flowers.length)], false);
            return;
        }
        if (roll < 0.18 && mask > 0.50) {
            world.getBlockAt(x, y, z).setType(Material.MOSS_BLOCK, false);
            if (world.getBlockAt(x, y + 1, z).getType().isAir() && random.nextDouble() < 0.65) {
                world.getBlockAt(x, y + 1, z).setType(Material.SHORT_GRASS, false);
            }
            return;
        }
        if (roll < 0.45) {
            world.getBlockAt(x, y, z).setType(Material.FERN, false);
            return;
        }
        if (roll < 0.73 && world.getBlockAt(x, y + 1, z).getType().isAir()) {
            world.getBlockAt(x, y, z).setType(Material.TALL_GRASS, false);
            return;
        }
        if (roll < 0.93) {
            world.getBlockAt(x, y, z).setType(Material.SHORT_GRASS, false);
            return;
        }
        if (mask > 0.50 && world.getBlockAt(x, y + 1, z).getType().isAir()) {
            world.getBlockAt(x, y, z).setType(Material.LARGE_FERN, false);
        }
    }
}
