package com.islanddayz.generator;

import org.bukkit.util.noise.SimplexNoiseGenerator;

public class CityGenerator {
    public enum RoadType { NONE, MAIN, DIRT }

    private static final int ROAD_WIDTH = 5;
    private static final int AXIS_OFFSET = 1200;

    private static final int[][] CITY_CENTERS = {
            {0, 0},
            {-300, 220},
            {320, -200},
            {220, 330},
            {-360, -280}
    };
    private static final int[] LOT_PATTERN_X = {8, 10, 12, 14, 9, 11, 13, 15};
    private static final int[] LOT_PATTERN_Z = {8, 11, 9, 12, 10, 13, 9, 14};

    private final SimplexNoiseGenerator warpNoise = new SimplexNoiseGenerator(42888L);
    private final SimplexNoiseGenerator shapeNoise = new SimplexNoiseGenerator(90812L);

    public double cityInfluence(int x, int z) {
        double max = 0.0;
        for (int i = 0; i < CITY_CENTERS.length; i++) {
            double edge = cityEdgeDistance(x, z, i);
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
        return getRoadType(x, z) != RoadType.NONE;
    }

    public RoadType getRoadType(int x, int z) {
        double influence = cityInfluence(x, z);
        if (influence <= 0.52) {
            return RoadType.NONE;
        }

        int cityIndex = nearestCityIndex(x, z);
        int localX = x - CITY_CENTERS[cityIndex][0];
        int localZ = z - CITY_CENTERS[cityIndex][1];

        if (cityEdgeDistance(x, z, cityIndex) < 8.0) {
            return RoadType.NONE;
        }
        double wx = localX + warpX(localX, localZ);
        double wz = localZ + warpZ(localX, localZ);

        if ((cityIndex & 1) == 0) {
            boolean primary = Math.abs(wx) <= 1.3;
            boolean parallels = Math.abs(wx - 6) <= 1.3 || Math.abs(wx + 6) <= 1.3
                    || Math.abs(wx - 12) <= 1.3 || Math.abs(wx + 12) <= 1.3;
            boolean withinVillage = Math.abs(wz) <= 34;
            return (withinVillage && (primary || parallels)) ? RoadType.DIRT : RoadType.NONE;
        }

        boolean vertical = Math.abs(wx) <= 1.3 && wz >= -34 && wz <= 34;
        boolean horizontal = Math.abs(wz) <= 1.3 && wx >= -34 && wx <= 34;
        boolean extraLeg = Math.abs(wz - 8) <= 1.3 && wx >= -22 && wx <= 18;
        return (vertical || horizontal || extraLeg) ? RoadType.DIRT : RoadType.NONE;
    }

    public boolean isRoadStripe(int x, int z) {
        return false;
    }


    private double cityEdgeDistance(int x, int z, int cityIndex) {
        int cx = CITY_CENTERS[cityIndex][0];
        int cz = CITY_CENTERS[cityIndex][1];
        double localX = x - cx;
        double localZ = z - cz;
        double angle = Math.atan2(localZ, localX);
        double dist = Math.sqrt(localX * localX + localZ * localZ);
        return irregularRadiusForCity(x, z, angle, cityIndex) - dist;
    }

    private double irregularRadiusForCity(int x, int z, double angle, int cityIndex) {
        return 64
                + Math.sin(angle * (2.0 + cityIndex * 0.25)) * 5
                + Math.sin(angle * (4.6 + cityIndex * 0.2)) * 3
                + shapeNoise.noise((x + cityIndex * 300) * 0.006, (z - cityIndex * 300) * 0.006) * 6;
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


    private boolean shouldTerminateNearEdge(int x, int z, double influence, int cityIndex) {
        if (influence >= 0.68) {
            return false;
        }

        double edgeNoise = shapeNoise.noise((x + cityIndex * 250) * 0.03, (z - cityIndex * 250) * 0.03);
        return influence < 0.58 && edgeNoise > 0.08;
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
