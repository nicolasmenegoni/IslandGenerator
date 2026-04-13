package com.islanddayz.generator;

import org.bukkit.util.noise.SimplexNoiseGenerator;

public class CityGenerator {
    private static final int CITY_RADIUS = 150;
    private static final int ROAD_WIDTH = 5;
    private static final int AXIS_OFFSET = 320;

    private static final int[] LOT_PATTERN_X = {10, 8, 12, 15, 11, 18, 10, 15, 19, 9, 14};
    private static final int[] LOT_PATTERN_Z = {8, 10, 12, 15, 10, 18, 13, 19, 11, 10, 16};

    private final SimplexNoiseGenerator warpNoise = new SimplexNoiseGenerator(42888L);

    public boolean isInsideCity(int x, int z) {
        return (x * x) + (z * z) <= CITY_RADIUS * CITY_RADIUS;
    }

    public boolean isRoad(int x, int z) {
        if (!isInsideCity(x, z)) {
            return false;
        }

        double warpedX = x + warpX(x, z);
        double warpedZ = z + warpZ(x, z);

        AxisSample sampleX = sampleAxis(warpedX, LOT_PATTERN_X);
        AxisSample sampleZ = sampleAxis(warpedZ, LOT_PATTERN_Z);
        return sampleX.road || sampleZ.road;
    }

    public boolean isRoadStripe(int x, int z) {
        if (!isInsideCity(x, z)) {
            return false;
        }

        double warpedX = x + warpX(x, z);
        double warpedZ = z + warpZ(x, z);

        AxisSample sampleX = sampleAxis(warpedX, LOT_PATTERN_X);
        AxisSample sampleZ = sampleAxis(warpedZ, LOT_PATTERN_Z);

        boolean stripeOnXRoad = sampleX.road && sampleX.roadOffset >= 1 && sampleX.roadOffset <= 2;
        boolean stripeOnZRoad = sampleZ.road && sampleZ.roadOffset >= 1 && sampleZ.roadOffset <= 2;

        return stripeOnXRoad || stripeOnZRoad;
    }

    private double warpX(int x, int z) {
        return warpNoise.noise(x * 0.012, z * 0.012) * 3.5;
    }

    private double warpZ(int x, int z) {
        return warpNoise.noise((x + 900) * 0.012, (z - 900) * 0.012) * 3.5;
    }

    private AxisSample sampleAxis(double coordinate, int[] pattern) {
        int value = (int) Math.floor(coordinate) + AXIS_OFFSET;
        int cursor = 0;
        int index = 0;

        while (cursor < AXIS_OFFSET * 2 + 256) {
            int lotSize = pattern[index % pattern.length];
            int roadStart = cursor + lotSize;
            int roadEnd = roadStart + ROAD_WIDTH;

            if (value >= cursor && value < lotSize + cursor) {
                return new AxisSample(false, -1);
            }

            if (value >= roadStart && value < roadEnd) {
                return new AxisSample(true, value - roadStart);
            }

            cursor = roadEnd;
            index++;
        }

        return new AxisSample(false, -1);
    }

    private record AxisSample(boolean road, int roadOffset) {
    }
}
