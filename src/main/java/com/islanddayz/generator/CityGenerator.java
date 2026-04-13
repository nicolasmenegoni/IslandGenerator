package com.islanddayz.generator;

import org.bukkit.util.noise.SimplexNoiseGenerator;

public class CityGenerator {
    private static final int ROAD_WIDTH = 5;
    private static final int AXIS_OFFSET = 420;

    private static final int[] LOT_PATTERN_X = {10, 8, 12, 15, 11, 18, 10, 15, 19, 9, 14};
    private static final int[] LOT_PATTERN_Z = {8, 10, 12, 15, 10, 18, 13, 19, 11, 10, 16};

    private final SimplexNoiseGenerator warpNoise = new SimplexNoiseGenerator(42888L);
    private final SimplexNoiseGenerator shapeNoise = new SimplexNoiseGenerator(90812L);

    public double cityInfluence(int x, int z) {
        double angle = Math.atan2(z, x);
        double dist = Math.sqrt((double) x * x + (double) z * z);

        double irregularRadius = 138
                + Math.sin(angle * 2.6) * 16
                + Math.sin(angle * 5.3) * 9
                + shapeNoise.noise(x * 0.01, z * 0.01) * 18;

        double edge = irregularRadius - dist;
        return smooth((edge + 18.0) / 30.0);
    }

    public boolean isInsideCity(int x, int z) {
        return cityInfluence(x, z) > 0.08;
    }

    public boolean isRoad(int x, int z) {
        double influence = cityInfluence(x, z);
        if (influence <= 0.12) {
            return false;
        }

        AxisSample sampleX = sampleAxis(x + warpX(x, z), LOT_PATTERN_X);
        AxisSample sampleZ = sampleAxis(z + warpZ(x, z), LOT_PATTERN_Z);

        return sampleX.road || sampleZ.road || isRareCurvedRoad(x, z, influence);
    }

    public boolean isRoadStripe(int x, int z) {
        double influence = cityInfluence(x, z);
        if (influence <= 0.12) {
            return false;
        }

        AxisSample sampleX = sampleAxis(x + warpX(x, z), LOT_PATTERN_X);
        AxisSample sampleZ = sampleAxis(z + warpZ(x, z), LOT_PATTERN_Z);

        boolean stripeX = sampleX.road && sampleX.roadOffset == 2 && stripePattern(z);
        boolean stripeZ = sampleZ.road && sampleZ.roadOffset == 2 && stripePattern(x);
        return stripeX || stripeZ;
    }

    private boolean stripePattern(int axisCoord) {
        int segment = Math.floorDiv(axisCoord, 24);
        int cycle = (segment & 1) == 0 ? 3 : 4;
        int pos = Math.floorMod(axisCoord, cycle);
        return pos < 2;
    }

    private boolean isRareCurvedRoad(int x, int z, double influence) {
        if (influence < 0.35) {
            return false;
        }

        double curveCenter = Math.sin((x + shapeNoise.noise(x * 0.01, z * 0.01) * 20.0) / 26.0) * 18.0;
        double distance = Math.abs(z - curveCenter);
        boolean nearCurve = distance <= 2.0;
        double rareGate = Math.abs(shapeNoise.noise((x + 1200) * 0.02, (z - 300) * 0.02));
        return nearCurve && rareGate < 0.045;
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

        while (cursor < AXIS_OFFSET * 2 + 512) {
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
