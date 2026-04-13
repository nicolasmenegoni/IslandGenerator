package com.islanddayz;

import com.islanddayz.generator.WorldManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class IslandDayzPlugin extends JavaPlugin {
    private WorldManager worldManager;

    @Override
    public void onEnable() {
        this.worldManager = new WorldManager(this);
        this.worldManager.createOrLoadWorld();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("ilha")) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cApenas jogadores podem usar este comando.");
            return true;
        }

        player.teleport(worldManager.findBeachSpawn());
        player.sendMessage("§aTeleportado para a ilha DayZ.");
        return true;
    }
}
