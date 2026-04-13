package com.islanddayz.generator;

import org.bukkit.util.noise.SimplexNoiseGenerator;

public class CityGenerator {
    private static final int CITY_RADIUS = 120;

    private final SimplexNoiseGenerator warpNoise = new SimplexNoiseGenerator(42888L);

    public boolean isInsideCity(int x, int z) {
        return (x * x) + (z * z) <= CITY_RADIUS * CITY_RADIUS;
    }

    public boolean isRoad(int x, int z) {
        if (!isInsideCity(x, z)) {
            return false;
        }

        double warpX = warpNoise.noise(x * 0.025, z * 0.025) * 11.0;
        double warpZ = warpNoise.noise((x + 500) * 0.025, (z - 500) * 0.025) * 11.0;

        double roadXPattern = Math.abs(Math.sin((x + warpX) / 17.0));
        double roadZPattern = Math.abs(Math.sin((z + warpZ) / 19.0));

        boolean crossRoad = roadXPattern < 0.17 || roadZPattern < 0.16;
        double ring = Math.abs(Math.sqrt((double) x * x + (double) z * z) - 64.0);
        boolean ringRoad = ring < 3.0;

        return crossRoad || ringRoad;
    }

    public boolean isRoadStripe(int x, int z) {
        if (!isRoad(x, z)) {
            return false;
        }

        double lane = Math.abs(Math.sin((x + z) / 4.0));
        return lane < 0.14;
    }
}
