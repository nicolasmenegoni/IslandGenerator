package com.islanddayz.generator;

import org.bukkit.util.noise.SimplexNoiseGenerator;

public class CityGenerator {
    public enum RoadType { NONE, DIRT, SAND }

    private static final int ROAD_WIDTH = 5;
    private static final int AXIS_OFFSET = 1200;

    private static final int[][] CITY_CENTERS = {
            {0, 0},
            {-420, 260},
            {420, -260},
            {260, 420},
            {-440, -360},
            {120, -480},
            {-160, 470},
            {500, 130},
            {-560, 60},
            {560, -40},
            {360, -520},
            {-320, 560}
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

    public int villageCount() {
        return CITY_CENTERS.length;
    }

    public int villageCenterX(int index) {
        return CITY_CENTERS[index][0];
    }

    public int villageCenterZ(int index) {
        return CITY_CENTERS[index][1];
    }

    public int villagePattern(int index) {
        return Math.floorMod(index, 4);
    }

    public RoadType getRoadType(int x, int z) {
        if ((x * x) + (z * z) < (170 * 170)) {
            return RoadType.NONE;
        }
        if (isMountainRegion(x, z)) {
            return RoadType.NONE;
        }
        int cityIndex = nearestCityIndex(x, z);
        int localX = x - CITY_CENTERS[cityIndex][0];
        int localZ = z - CITY_CENTERS[cityIndex][1];
        double radial = Math.sqrt(localX * (double) localX + localZ * (double) localZ);
        if (radial > 74.0) {
            return RoadType.NONE;
        }

        boolean onRoad = isOnVillageRoad(cityIndex, localX, localZ);
        if (!onRoad) {
            return RoadType.NONE;
        }
        return usesSandRoads(cityIndex) ? RoadType.SAND : RoadType.DIRT;
    }

    public boolean isRoadStripe(int x, int z) {
        return false;
    }

    private boolean isOnVillageRoad(int villageIndex, double x, double z) {
        double half = usesWideRoads(villageIndex) ? 1.9 : 1.4; // 4x4 ou 3x3
        boolean crossHorizontal = Math.abs(z) <= half && x >= -42 && x <= 42;
        boolean crossVertical = Math.abs(x) <= half && z >= -42 && z <= 42;
        boolean leftUpper = Math.abs(z + 14) <= half && x >= -30 && x <= 0;
        boolean rightUpper = Math.abs(z + 8) <= half && x >= 0 && x <= 28;
        boolean rightMiddle = Math.abs(z - 8) <= half && x >= 0 && x <= 28;
        boolean rightLower = Math.abs(z - 22) <= half && x >= 0 && x <= 24;
        boolean leftLower = Math.abs(z - 34) <= half && x >= -24 && x <= 0;
        return crossHorizontal || crossVertical || leftUpper || rightUpper || rightMiddle || rightLower || leftLower;
    }

    private boolean usesWideRoads(int villageIndex) {
        return (villageIndex & 1) == 0;
    }

    private boolean usesSandRoads(int villageIndex) {
        return (villageIndex % 3) == 1;
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
        return 56
                + Math.sin(angle * (2.0 + cityIndex * 0.25)) * 4
                + Math.sin(angle * (4.6 + cityIndex * 0.2)) * 2.5
                + shapeNoise.noise((x + cityIndex * 300) * 0.006, (z - cityIndex * 300) * 0.006) * 5;
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

    private boolean isMountainRegion(int x, int z) {
        int dx = x - 300;
        int dz = z - 250;
        return (dx * dx) + (dz * dz) <= 220 * 220;
    }
}
