package com.islanddayz.generator;

import com.islanddayz.IslandDayzPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

import java.util.Random;

public class WorldManager {
    public static final String WORLD_NAME = "ilha_dayz";

    private final IslandDayzPlugin plugin;
    private final Random random = new Random();
    private final IslandGenerator islandGenerator = new IslandGenerator();
    private World world;

    public WorldManager(IslandDayzPlugin plugin) {
        this.plugin = plugin;
    }

    public void createOrLoadWorld() {
        CityGenerator cityGenerator = new CityGenerator();
        CustomChunkGenerator generator = new CustomChunkGenerator(islandGenerator, new TerrainGenerator(), cityGenerator, new TreeGenerator(islandGenerator, cityGenerator));

        WorldCreator creator = new WorldCreator(WORLD_NAME)
                .type(WorldType.NORMAL)
                .generator(generator);

        this.world = Bukkit.createWorld(creator);
        if (this.world == null) {
            throw new IllegalStateException("Falha ao criar/carregar o mundo " + WORLD_NAME);
        }

        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(1536);

        Location spawn = findBeachSpawn();
        world.setSpawnLocation(spawn);

        new ShipwreckGenerator().generateCornerShipwrecks(world);

        plugin.getLogger().info("Mundo '" + WORLD_NAME + "' pronto. Border 1536x1536 aplicada.");
    }

    public Location findBeachSpawn() {
        ensureWorld();
        for (int i = 0; i < 200; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = 520 + random.nextDouble() * 180;
            int x = (int) Math.round(Math.cos(angle) * radius);
            int z = (int) Math.round(Math.sin(angle) * radius);

            world.getChunkAt(x >> 4, z >> 4);
            int y = world.getHighestBlockYAt(x, z);
            Material ground = world.getBlockAt(x, y - 1, z).getType();
            Material feet = world.getBlockAt(x, y, z).getType();

            if ((ground == Material.SAND || ground == Material.RED_SAND)
                    && feet.isAir()
                    && world.getBlockAt(x, y + 1, z).getType().isAir()
                    && isShoreline(x, y - 1, z)) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }

        int fallbackY = world.getHighestBlockYAt(0, 0);
        return new Location(world, 0.5, fallbackY + 1, 0.5);
    }


    private boolean isShoreline(int x, int y, int z) {
        return world.getBlockAt(x + 1, y, z).getType() == Material.WATER
                || world.getBlockAt(x - 1, y, z).getType() == Material.WATER
                || world.getBlockAt(x, y, z + 1).getType() == Material.WATER
                || world.getBlockAt(x, y, z - 1).getType() == Material.WATER;
    }

    private void ensureWorld() {
        if (world == null) {
            throw new IllegalStateException("Mundo ainda não foi criado.");
        }
    }
}
