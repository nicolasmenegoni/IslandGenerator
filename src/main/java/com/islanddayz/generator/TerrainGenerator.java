package com.islanddayz.generator;

import org.bukkit.util.noise.SimplexNoiseGenerator;

public class TerrainGenerator {
    private final SimplexNoiseGenerator hillsNoise = new SimplexNoiseGenerator(99173L);
    private final SimplexNoiseGenerator ridgeNoise = new SimplexNoiseGenerator(551239L);

    public int computeHeight(int x, int z, double islandMask, int seaLevel, double cityInfluence) {
        double hills = hillsNoise.noise(x * 0.006, z * 0.006) * 15.0;
        double detail = hillsNoise.noise(x * 0.02, z * 0.02) * 3.5;
        double ridges = Math.abs(ridgeNoise.noise(x * 0.01, z * 0.01)) * 8.0;

        double interiorBoost = Math.max(0.0, (islandMask - 0.35) * 26.0);
        double natural = seaLevel - 1 + (hills + detail + ridges + interiorBoost) * islandMask;

        // Suaviza subida areia -> terra para evitar montanhas imediatas na costa.
        if (islandMask < 0.62) {
            double coastalProgress = Math.max(0.0, Math.min(1.0, (islandMask - 0.10) / 0.52));
            double eased = coastalProgress * coastalProgress * coastalProgress;
            double coastalCap = (seaLevel + 1) + (eased * 8.0);
            natural = Math.min(natural, coastalCap);
        }

        double urbanVariation = hillsNoise.noise((x + 300) * 0.012, (z - 300) * 0.012) * 1.2;
        double urban = seaLevel + 4 + urbanVariation;

        double blend = Math.max(0.0, Math.min(1.0, cityInfluence));
        double result = natural * (1.0 - blend) + urban * blend;
        return Math.max(seaLevel + 1, (int) Math.round(result));
    }
}
