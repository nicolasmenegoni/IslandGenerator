package com.islanddayz.generator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public class ShipwreckGenerator {
    private static final int SEA_LEVEL = 63;

    public void generateCornerShipwrecks(World world) {
        placeAtCorner(world, 1, 1);
        placeAtCorner(world, 1, -1);
        placeAtCorner(world, -1, 1);
        placeAtCorner(world, -1, -1);
    }

    private void placeAtCorner(World world, int sx, int sz) {
        Location coast = findCoast(world, sx, sz);
        if (coast == null) {
            return;
        }

        boolean alongX = Math.abs(coast.getBlockX()) > Math.abs(coast.getBlockZ());
        buildShipwreck(world, coast, alongX);
    }

    private Location findCoast(World world, int sx, int sz) {
        for (int radius = 520; radius <= 740; radius += 4) {
            int x = sx * radius;
            int z = sz * radius;
            world.getChunkAt(x >> 4, z >> 4);

            int y = world.getHighestBlockYAt(x, z);
            Material ground = world.getBlockAt(x, y - 1, z).getType();
            if (ground == Material.SAND && hasAdjacentWater(world, x, y - 1, z)) {
                return new Location(world, x, Math.max(SEA_LEVEL - 1, y - 1), z);
            }
        }
        return null;
    }

    private boolean hasAdjacentWater(World world, int x, int y, int z) {
        return world.getBlockAt(x + 1, y, z).getType() == Material.WATER
                || world.getBlockAt(x - 1, y, z).getType() == Material.WATER
                || world.getBlockAt(x, y, z + 1).getType() == Material.WATER
                || world.getBlockAt(x, y, z - 1).getType() == Material.WATER;
    }

    private void buildShipwreck(World world, Location base, boolean alongX) {
        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();

        int lx = alongX ? 1 : 0;
        int lz = alongX ? 0 : 1;
        int wx = alongX ? 0 : 1;
        int wz = alongX ? 1 : 0;

        for (int l = -6; l <= 7; l++) {
            for (int w = -2; w <= 2; w++) {
                int x = bx + (l * lx) + (w * wx);
                int z = bz + (l * lz) + (w * wz);
                int taper = Math.max(0, Math.abs(l) - 4);
                if (Math.abs(w) > 2 - Math.min(1, taper)) {
                    continue;
                }
                int hullY = by + Math.max(0, 2 - Math.abs(w)) - (taper > 0 ? 1 : 0);

                world.getBlockAt(x, hullY, z).setType(Material.SPRUCE_PLANKS, false);
                world.getBlockAt(x, hullY - 1, z).setType(Material.OAK_LOG, false);

                if (Math.abs(w) <= 1 && Math.floorMod(l + 14, 3) == 0) {
                    world.getBlockAt(x, hullY + 1, z).setType(Material.SPRUCE_SLAB, false);
                }
            }
        }

        for (int l = -4; l <= 5; l++) {
            int x = bx + (l * lx);
            int z = bz + (l * lz);
            world.getBlockAt(x + wx * 2, by + 1, z + wz * 2).setType(Material.SPRUCE_PLANKS, false);
            world.getBlockAt(x - wx * 2, by + 1, z - wz * 2).setType(Material.SPRUCE_PLANKS, false);
            if (Math.abs(l) >= 4) {
                world.getBlockAt(x + wx, by + 2, z + wz).setType(Material.SPRUCE_STAIRS, false);
                world.getBlockAt(x - wx, by + 2, z - wz).setType(Material.SPRUCE_STAIRS, false);
            }
        }

        int mastX = bx + (1 * lx);
        int mastZ = bz + (1 * lz);
        for (int y = by + 1; y <= by + 7; y++) {
            world.getBlockAt(mastX, y, mastZ).setType(Material.SPRUCE_LOG, false);
        }
        world.getBlockAt(mastX + wx, by + 5, mastZ + wz).setType(Material.WHITE_WOOL, false);
        world.getBlockAt(mastX + wx * 2, by + 5, mastZ + wz * 2).setType(Material.WHITE_WOOL, false);
        world.getBlockAt(mastX - wx, by + 5, mastZ - wz).setType(Material.WHITE_WOOL, false);
        world.getBlockAt(mastX - wx * 2, by + 5, mastZ - wz * 2).setType(Material.WHITE_WOOL, false);
        world.getBlockAt(mastX + wx, by + 4, mastZ + wz).setType(Material.WHITE_WOOL, false);
        world.getBlockAt(mastX - wx, by + 4, mastZ - wz).setType(Material.WHITE_WOOL, false);
        world.getBlockAt(mastX + lx * 3, by + 2, mastZ + lz * 3).setType(Material.CHEST, false);
    }
}
