package com.islanddayz;

import com.islanddayz.generator.IslandGenerator;
import com.islanddayz.generator.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.Color;
import java.util.ArrayList;

public final class IslandDayzPlugin extends JavaPlugin {
    private static final int BORDER_SIZE = 512;
    private WorldManager worldManager;

    @Override
    public void onEnable() {
        this.worldManager = new WorldManager(this);
        this.worldManager.createOrLoadWorld();
        startGroundItemCleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("ilha")) {
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cApenas jogadores podem usar este comando.");
            return true;
        }
        Player player = (Player) sender;

        player.teleport(worldManager.findBeachSpawn());
        giveIslandMap(player);
        player.sendMessage("§aTeleportado para a ilha DayZ.");
        return true;
    }

    private void giveIslandMap(Player player) {
        ItemStack mapItem = createOverviewMap(worldManager.getWorld());
        player.getInventory().addItem(mapItem);
        player.sendMessage("§eVocê recebeu um mapa da ilha (nível 2).");
    }

    private ItemStack createOverviewMap(World world) {
        MapView mapView = Bukkit.createMap(world);
        mapView.setCenterX(0);
        mapView.setCenterZ(0);
        mapView.setScale(MapView.Scale.NORMAL); // nível 2
        mapView.setTrackingPosition(true);
        mapView.setUnlimitedTracking(true);
        mapView.setLocked(false);

        for (MapRenderer renderer : new ArrayList<>(mapView.getRenderers())) {
            mapView.removeRenderer(renderer);
        }
        mapView.addRenderer(new IslandOverviewRenderer());

        ItemStack item = new ItemStack(Material.FILLED_MAP, 1);
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta != null) {
            meta.setMapView(mapView);
            meta.setDisplayName("§aMapa da Ilha DayZ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private void startGroundItemCleanup() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            World world = worldManager.getWorld();
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (!item.isDead()) {
                    item.remove();
                }
            }
        }, 20L * 20, 20L * 20);
    }

    private static final class IslandOverviewRenderer extends MapRenderer {
        private final IslandGenerator islandGenerator = new IslandGenerator();
        private boolean rendered;

        private IslandOverviewRenderer() {
            super(true);
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (!rendered) {
                int half = BORDER_SIZE / 2;
                for (int px = 0; px < 128; px++) {
                    for (int pz = 0; pz < 128; pz++) {
                        int worldX = ((px - 64) * BORDER_SIZE) / 128;
                        int worldZ = ((pz - 64) * BORDER_SIZE) / 128;

                        byte color;
                        if (Math.abs(worldX) > half || Math.abs(worldZ) > half) {
                            color = MapPalette.matchColor(new Color(45, 45, 45));
                        } else {
                            double mask = islandGenerator.islandMask(worldX, worldZ);
                            if (mask <= 0.02) {
                                color = MapPalette.matchColor(new Color(40, 95, 190));
                            } else if (mask < 0.54) {
                                color = MapPalette.matchColor(new Color(232, 220, 170));
                            } else if (mask < 0.72) {
                                color = MapPalette.matchColor(new Color(92, 168, 86));
                            } else {
                                color = MapPalette.matchColor(new Color(58, 124, 54));
                            }
                        }
                        canvas.setPixel(px, pz, color);
                    }
                }
                rendered = true;
            }

            drawPlayerCursor(map, canvas, player);
        }

        private void drawPlayerCursor(MapView map, MapCanvas canvas, Player player) {
            while (canvas.getCursors().size() > 0) {
                canvas.getCursors().removeCursor(canvas.getCursors().getCursor(0));
            }

            Location location = player.getLocation();
            int scaleFactor = 1 << map.getScale().ordinal();
            double worldSpan = 128.0 * scaleFactor;
            double relX = (location.getX() - map.getCenterX()) / (worldSpan / 2.0);
            double relZ = (location.getZ() - map.getCenterZ()) / (worldSpan / 2.0);

            int cursorX = (int) Math.round(relX * 127.0);
            int cursorY = (int) Math.round(relZ * 127.0);

            if (cursorX < -128) {
                cursorX = -128;
            } else if (cursorX > 127) {
                cursorX = 127;
            }
            if (cursorY < -128) {
                cursorY = -128;
            } else if (cursorY > 127) {
                cursorY = 127;
            }

            float yaw = player.getLocation().getYaw();
            byte direction = (byte) Math.floorMod((int) Math.round((yaw / 360.0) * 16.0), 16);
            canvas.getCursors().addCursor((byte) cursorX, (byte) cursorY, direction, (byte) 0, true);
        }
    }
}
