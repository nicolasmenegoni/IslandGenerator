package com.islanddayz.generator;

import org.bukkit.util.noise.SimplexNoiseGenerator;

public class IslandGenerator {
    private static final double ISLAND_RADIUS = 320.0;
    private final SimplexNoiseGenerator coastNoise = new SimplexNoiseGenerator(289341L);

    public double islandMask(int x, int z) {
        double angle = Math.atan2(z, x);
        double distance = Math.sqrt((double) x * x + (double) z * z);

        double noisyRadius = ISLAND_RADIUS
                + Math.sin(angle * 3.0) * 24.0
                + Math.sin(angle * 7.0) * 10.0
                + coastNoise.noise(x * 0.008, z * 0.008) * 28.0
                + coastNoise.noise(x * 0.02, z * 0.02) * 9.0;

        double edge = noisyRadius - distance;
        return smooth(edge / 42.0);
    }

    private double smooth(double value) {
        double v = Math.max(0, Math.min(1, value));
        return v * v * (3 - 2 * v);
    }
}
