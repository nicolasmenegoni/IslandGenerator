package com.islanddayz.generator;

import org.bukkit.util.noise.SimplexNoiseGenerator;

public class IslandGenerator {
    private static final double[][] SPINE_POINTS = {
            {-170, 72},
            {-130, 60},
            {-88, 42},
            {-52, 12},
            {-28, -22},
            {6, -56},
            {22, -96},
            {38, -138},
            {52, -186}
    };

    private static final double[][] SIDE_BRANCH = {
            {-108, 30},
            {-128, 4},
            {-148, -14}
    };

    private final SimplexNoiseGenerator coastNoise = new SimplexNoiseGenerator(289341L);
    private final SimplexNoiseGenerator coastDetailNoise = new SimplexNoiseGenerator(982331L);

    public double islandMask(int x, int z) {
        double signed = signedCoastDistance(x, z);
        return smooth(signed / 22.0);
    }

    public double distanceFromIslandEdge(int x, int z) {
        return -signedCoastDistance(x, z);
    }

    private double signedCoastDistance(int x, int z) {
        double rx = (x * 0.94) - (z * 0.12);
        double rz = (x * 0.12) + (z * 0.94);

        SegmentDistance main = nearestDistanceToPath(rx, rz, SPINE_POINTS);
        SegmentDistance branch = nearestDistanceToPath(rx, rz, SIDE_BRANCH);

        double mergedProgress = Math.max(main.progress, branch.progress * 0.72);
        double baseWidth = Math.max(widthForProgress(main.progress), widthForBranch(branch.progress));
        double nearest = Math.min(main.distance, branch.distance + 6.0);

        double coastJitter = (coastNoise.noise(rx * 0.012, rz * 0.012) * 11.0)
                + (coastDetailNoise.noise(rx * 0.032, rz * 0.032) * 5.0);
        double edgeVariation = Math.sin((mergedProgress * 9.0) + (rz * 0.019)) * 4.0;

        return (baseWidth + coastJitter + edgeVariation) - nearest;
    }

    private double widthForProgress(double t) {
        if (t < 0.2) {
            return lerp(28.0, 40.0, t / 0.2);
        }
        if (t < 0.55) {
            return lerp(40.0, 56.0, (t - 0.2) / 0.35);
        }
        if (t < 0.85) {
            return lerp(56.0, 34.0, (t - 0.55) / 0.30);
        }
        return lerp(34.0, 22.0, (t - 0.85) / 0.15);
    }

    private double widthForBranch(double t) {
        if (t <= 0.0) {
            return 0.0;
        }
        if (t < 0.4) {
            return lerp(0.0, 30.0, t / 0.4);
        }
        return lerp(30.0, 16.0, (t - 0.4) / 0.6);
    }

    private SegmentDistance nearestDistanceToPath(double x, double z, double[][] points) {
        double bestDist = Double.MAX_VALUE;
        double bestProgress = 0.0;
        int segments = points.length - 1;
        for (int i = 0; i < segments; i++) {
            double x1 = points[i][0];
            double z1 = points[i][1];
            double x2 = points[i + 1][0];
            double z2 = points[i + 1][1];

            double dx = x2 - x1;
            double dz = z2 - z1;
            double len2 = (dx * dx) + (dz * dz);
            double proj = len2 <= 0.0 ? 0.0 : (((x - x1) * dx) + ((z - z1) * dz)) / len2;
            double clamped = Math.max(0.0, Math.min(1.0, proj));

            double cx = x1 + (dx * clamped);
            double cz = z1 + (dz * clamped);
            double dist = Math.hypot(x - cx, z - cz);
            if (dist < bestDist) {
                bestDist = dist;
                bestProgress = (i + clamped) / segments;
            }
        }
        return new SegmentDistance(bestDist, bestProgress);
    }

    private double lerp(double a, double b, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return a + ((b - a) * clamped);
    }

    private double smooth(double value) {
        double v = Math.max(0, Math.min(1, value));
        return v * v * (3 - 2 * v);
    }

    private static final class SegmentDistance {
        private final double distance;
        private final double progress;

        private SegmentDistance(double distance, double progress) {
            this.distance = distance;
            this.progress = progress;
        }
    }
}
