package com.islanddayz.generator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class StreetLightGenerator {
    private final CityGenerator cityGenerator;

    public StreetLightGenerator(CityGenerator cityGenerator) {
        this.cityGenerator = cityGenerator;
    }

    public void populateChunk(LimitedRegion region, int chunkX, int chunkZ) {
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        List<Vector> placed = new ArrayList<>();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = startX + localX;
                int z = startZ + localZ;

                BlockFace roadDirection = getRoadDirectionIfSidewalk(x, z);
                if (roadDirection == null) {
                    continue;
                }

                if (!passesSpacingRule(x, z) || tooClose(placed, x, z)) {
                    continue;
                }

                int y = region.getHighestBlockYAt(x, z);
                Material ground = region.getType(x, y - 1, z);
                if (ground == Material.WATER || ground == Material.AIR || cityGenerator.isRoad(x, z)) {
                    continue;
                }

                buildLight(region, x, y, z, roadDirection);
                placed.add(new Vector(x, y, z));
            }
        }
    }

    private BlockFace getRoadDirectionIfSidewalk(int x, int z) {
        if (!cityGenerator.isInsideCity(x, z) || cityGenerator.isRoad(x, z)) {
            return null;
        }

        boolean nearJunction = cityGenerator.isRoad(x + 4, z) && cityGenerator.isRoad(x, z + 4)
                || cityGenerator.isRoad(x - 4, z) && cityGenerator.isRoad(x, z - 4)
                || cityGenerator.isRoad(x + 4, z) && cityGenerator.isRoad(x, z - 4)
                || cityGenerator.isRoad(x - 4, z) && cityGenerator.isRoad(x, z + 4);
        if (!nearJunction) {
            return null;
        }

        if (cityGenerator.isRoad(x + 1, z) && !cityGenerator.isRoad(x - 1, z)) return BlockFace.EAST;
        if (cityGenerator.isRoad(x - 1, z) && !cityGenerator.isRoad(x + 1, z)) return BlockFace.WEST;
        if (cityGenerator.isRoad(x, z + 1) && !cityGenerator.isRoad(x, z - 1)) return BlockFace.SOUTH;
        if (cityGenerator.isRoad(x, z - 1) && !cityGenerator.isRoad(x, z + 1)) return BlockFace.NORTH;

        return null;
    }

    private boolean passesSpacingRule(int x, int z) {
        int spacing = 21 + Math.floorMod(x * 5 + z * 9, 10);
        return Math.floorMod((x * 3) + (z * 5), spacing) == 0;
    }

    private boolean tooClose(List<Vector> placed, int x, int z) {
        for (Vector pos : placed) {
            if (pos.distanceSquared(new Vector(x, pos.getY(), z)) < 20 * 20) {
                return true;
            }
        }
        return false;
    }

    private void buildLight(LimitedRegion region, int x, int y, int z, BlockFace roadDirection) {
        region.setType(x, y - 1, z, Material.STONE_BRICKS);
        for (int i = 0; i < 6; i++) {
            region.setType(x, y + i, z, Material.ANDESITE_WALL);
        }

        int bar1X = x + roadDirection.getModX();
        int bar1Z = z + roadDirection.getModZ();
        int bar2X = x + roadDirection.getModX() * 2;
        int bar2Z = z + roadDirection.getModZ() * 2;

        region.setBlockData(bar1X, y + 5, bar1Z, createBarData(roadDirection, roadDirection.getOppositeFace()));
        region.setBlockData(bar2X, y + 5, bar2Z, createBarData(roadDirection.getOppositeFace()));
        region.setType(bar2X, y + 4, bar2Z, Material.CHAIN);
        region.setType(bar2X, y + 3, bar2Z, Material.LANTERN);
    }

    private MultipleFacing createBarData(BlockFace... faces) {
        MultipleFacing data = (MultipleFacing) Bukkit.createBlockData(Material.IRON_BARS);
        for (BlockFace face : faces) {
            data.setFace(face, true);
        }
        return data;
    }
}
