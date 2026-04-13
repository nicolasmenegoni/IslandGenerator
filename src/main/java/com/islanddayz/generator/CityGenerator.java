package com.islanddayz.generator;

import org.bukkit.util.noise.SimplexNoiseGenerator;

public class CityGenerator {
    private static final int ROAD_WIDTH = 5;
    private static final int AXIS_OFFSET = 1200;

    private static final int[][] CITY_CENTERS = {
            {0, 0},
            {-300, 220},
            {320, -200},
            {220, 330},
            {-360, -280}
    };

    private static final int[] LOT_PATTERN_X = {18, 20, 22, 18, 29, 25, 30, 27, 35, 24, 18, 29};
    private static final int[] LOT_PATTERN_Z = {18, 18, 20, 22, 29, 35, 25, 27, 30, 19, 24, 29};

    private final SimplexNoiseGenerator warpNoise = new SimplexNoiseGenerator(42888L);
    private final SimplexNoiseGenerator shapeNoise = new SimplexNoiseGenerator(90812L);

    public double cityInfluence(int x, int z) {
        double max = 0.0;
        for (int i = 0; i < CITY_CENTERS.length; i++) {
            int cx = CITY_CENTERS[i][0];
            int cz = CITY_CENTERS[i][1];
            double localX = x - cx;
            double localZ = z - cz;
            double angle = Math.atan2(localZ, localX);
            double dist = Math.sqrt(localX * localX + localZ * localZ);

            double irregularRadius = 165
                    + Math.sin(angle * (2.0 + i * 0.25)) * 20
                    + Math.sin(angle * (4.6 + i * 0.2)) * 12
                    + shapeNoise.noise((x + i * 300) * 0.006, (z - i * 300) * 0.006) * 28;

            double edge = irregularRadius - dist;
            double influence = smooth((edge + 50.0) / 70.0);
            if (influence > max) {
                max = influence;
            }
        }
        return max;
    }

    public boolean isInsideCity(int x, int z) {
        return cityInfluence(x, z) > 0.06;
    }

    public boolean isRoad(int x, int z) {
        double influence = cityInfluence(x, z);
        if (influence <= 0.45) {
            return false;
        }

        int cityIndex = nearestCityIndex(x, z);
        int localX = x - CITY_CENTERS[cityIndex][0];
        int localZ = z - CITY_CENTERS[cityIndex][1];

        AxisSample sampleX = sampleAxis(localX + warpX(localX, localZ), LOT_PATTERN_X);
        AxisSample sampleZ = sampleAxis(localZ + warpZ(localX, localZ), LOT_PATTERN_Z);

        return sampleX.road || sampleZ.road || isRareCurvedRoad(localX, localZ, influence, cityIndex);
    }

    public boolean isRoadStripe(int x, int z) {
        double influence = cityInfluence(x, z);
        if (influence <= 0.45) {
            return false;
        }

        int cityIndex = nearestCityIndex(x, z);
        int localX = x - CITY_CENTERS[cityIndex][0];
        int localZ = z - CITY_CENTERS[cityIndex][1];

        AxisSample sampleX = sampleAxis(localX + warpX(localX, localZ), LOT_PATTERN_X);
        AxisSample sampleZ = sampleAxis(localZ + warpZ(localX, localZ), LOT_PATTERN_Z);

        boolean stripeX = sampleX.road && sampleX.roadOffset == 2 && stripePattern(localZ);
        boolean stripeZ = sampleZ.road && sampleZ.roadOffset == 2 && stripePattern(localX);
        return stripeX || stripeZ;
    }

    private int nearestCityIndex(int x, int z) {
        int best = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < CITY_CENTERS.length; i++) {
            long dx = x - CITY_CENTERS[i][0];
            long dz = z - CITY_CENTERS[i][1];
            long d = dx * dx + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    private boolean stripePattern(int axisCoord) {
        int segment = Math.floorDiv(axisCoord, 24);
        int cycle = (segment & 1) == 0 ? 3 : 4;
        int pos = Math.floorMod(axisCoord, cycle);
        return pos < 2;
    }

    private boolean isRareCurvedRoad(int x, int z, double influence, int cityIndex) {
        if (influence < 0.63) {
            return false;
        }

        double curveCenter = Math.sin((x + shapeNoise.noise((x + cityIndex * 200) * 0.008, (z - cityIndex * 200) * 0.008) * 20.0) / 38.0) * 25.0;
        double distance = Math.abs(z - curveCenter);
        boolean nearCurve = distance <= 2.0;
        double rareGate = Math.abs(shapeNoise.noise((x + 1200 + cityIndex * 100) * 0.015, (z - 300 - cityIndex * 100) * 0.015));
        return nearCurve && rareGate < 0.02;
    }

    private double warpX(int x, int z) {
        return warpNoise.noise(x * 0.008, z * 0.008) * 5.0;
    }

    private double warpZ(int x, int z) {
        return warpNoise.noise((x + 900) * 0.008, (z - 900) * 0.008) * 5.0;
    }

    private AxisSample sampleAxis(double coordinate, int[] pattern) {
        int value = (int) Math.floor(coordinate) + AXIS_OFFSET;
        int cursor = 0;
        int index = 0;

        while (cursor < AXIS_OFFSET * 2 + 2048) {
            int lotSize = pattern[index % pattern.length];
            int roadStart = cursor + lotSize;
            int roadEnd = roadStart + ROAD_WIDTH;

            if (value >= roadStart && value < roadEnd) {
                return new AxisSample(true, value - roadStart);
            }

            if (value >= cursor && value < roadStart) {
                return new AxisSample(false, -1);
            }

            cursor = roadEnd;
            index++;
        }

        return new AxisSample(false, -1);
    }

    private double smooth(double value) {
        double v = Math.max(0, Math.min(1, value));
        return v * v * (3 - 2 * v);
    }

    private record AxisSample(boolean road, int roadOffset) {
    }
}
