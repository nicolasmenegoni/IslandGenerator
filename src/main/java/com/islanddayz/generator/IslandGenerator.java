package com.islanddayz.generator;

import org.bukkit.util.noise.SimplexNoiseGenerator;

public class IslandGenerator {
    private static final double ISLAND_RADIUS = 100.0;
    private final SimplexNoiseGenerator coastNoise = new SimplexNoiseGenerator(289341L);

    public double islandMask(int x, int z) {
        double distance = Math.sqrt((double) x * x + (double) z * z);
        double edge = coastRadius(x, z) - distance;
        return smooth(edge / 52.0);
    }

    public double distanceFromIslandEdge(int x, int z) {
        double distance = Math.sqrt((double) x * x + (double) z * z);
        return distance - coastRadius(x, z);
    }

    private double coastRadius(int x, int z) {
        double angle = Math.atan2(z, x);
        return ISLAND_RADIUS
                + Math.sin(angle * 2.3) * 34.0
                + Math.sin(angle * 5.8) * 26.0
                + Math.sin(angle * 10.6) * 12.0
                + coastNoise.noise(x * 0.0055, z * 0.0055) * 58.0
                + coastNoise.noise(x * 0.017, z * 0.017) * 24.0;
    }

    private double smooth(double value) {
        double v = Math.max(0, Math.min(1, value));
        return v * v * (3 - 2 * v);
    }
}
