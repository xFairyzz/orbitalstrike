package me.fairyzz.orbitalstrike;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

public class OrbitalStrikePlugin extends JavaPlugin implements CommandExecutor, Listener, TabCompleter {

    private final Set<UUID> nukeTNT = new HashSet<>();
    private FileConfiguration config;

    @Override
    public void onEnable() {

        saveDefaultConfig();
        config = getConfig();

        setDefaults();
        saveConfig();

        getCommand("orbital").setExecutor(this);
        getCommand("orbital").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        nukeTNT.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (!player.hasPermission(config.getString("permission", "orbital.use"))) {
            sendMessage(player, "permission", Map.of());
            return true;
        }

        if (args.length != 1) {
            sendMessage(player, "usage", Map.of("{CMD}", "/orbital <nuke|stab>"));
            return true;
        }

        String type = args[0].toLowerCase();
        if (!type.equals("nuke") && !type.equals("stab")) {
            sendMessage(player, "invalid-type", Map.of());
            return true;
        }

        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();
        meta.setDisplayName("§cOrbital Strike Rod - " + type.toUpperCase());
        meta.setCustomModelData(12345);
        rod.setItemMeta(meta);

        player.getInventory().addItem(rod);
        sendMessage(player, "received", Map.of("{TYPE}", type.toUpperCase()));

        return true;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().toString().contains("RIGHT") || !event.hasItem()) return;

        ItemStack item = event.getItem();
        if (item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) return;

        String name = item.getItemMeta().getDisplayName();
        if (!name.startsWith("§cOrbital Strike Rod - ")) return;

        Player player = event.getPlayer();
        RayTraceResult result = player.rayTraceBlocks(100);
        if (result == null || result.getHitBlock() == null) {
            sendMessage(player, "no-target", Map.of());
            return;
        }

        Location target = result.getHitBlock().getLocation().add(0, 60, 0);
        World world = target.getWorld();

        String type = name.substring(name.lastIndexOf(" - ") + 3).toLowerCase();

        sendMessage(player, "incoming", Map.of("{TYPE}", type.toUpperCase()));

        Bukkit.getScheduler().runTaskLater(this, () -> {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.FISHING_ROD && hand.hasItemMeta() &&
                    hand.getItemMeta().getDisplayName().equals(name)) {
                hand.setAmount(hand.getAmount() - 1);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            }
        }, 1);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            nukeTNT.clear();
            if (type.equals("nuke")) {
                spawnNuke(world, target);
            } else {
                spawnStab(world, target);
            }
        }, 0);

        event.setCancelled(true);
    }

    @EventHandler
    public void onTNTCollide(VehicleBlockCollisionEvent event) {
        if (!(event.getVehicle() instanceof TNTPrimed tnt)) return;
        if (!nukeTNT.contains(tnt.getUniqueId())) return;

        nukeTNT.remove(tnt.getUniqueId());
        startExplosionDelay(tnt);
    }

    @EventHandler
    public void onTNTLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) return;
        if (!nukeTNT.contains(tnt.getUniqueId())) return;

        if (event.getTo().isSolid() || event.getBlock().getType().isSolid()) {
            nukeTNT.remove(tnt.getUniqueId());
            startExplosionDelay(tnt);
            event.setCancelled(true);
        }
    }

    private void startExplosionDelay(TNTPrimed tnt) {
        int delay = config.getInt("nuke.delay-after-impact", 40);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!tnt.isDead()) {
                tnt.setFuseTicks(1);
            }
        }, delay);
    }

    private void spawnNuke(World world, Location center) {
        nukeTNT.clear();
        int rings = config.getInt("nuke.rings", 10);
        double height = center.getY() + config.getInt("nuke.height", 80);
        float yield = (float) config.getDouble("nuke.yield", 6.0);
        int baseTnt = config.getInt("nuke.tnt-per-ring-base", 40);
        int increase = config.getInt("nuke.tnt-per-ring-increase", 2);
        boolean centerTnt = config.getBoolean("nuke.center-tnt", true);

        if (centerTnt) {
            Location loc = new Location(world, center.getX() + 0.5, height, center.getZ() + 0.5);
            spawnNukeTNT(world, loc, yield);
        }

        for (int ring = 1; ring <= rings; ring++) {
            double radius = ring * 4.0;
            int tntCount = baseTnt + ring * increase;
            double step = 360.0 / tntCount;

            for (int i = 0; i < tntCount; i++) {
                double angle = i * step + (ring * 10);
                double x = center.getX() + radius * Math.cos(Math.toRadians(angle));
                double z = center.getZ() + radius * Math.sin(Math.toRadians(angle));
                Location loc = new Location(world, x + 0.5, height, z + 0.5);

                spawnNukeTNT(world, loc, yield);
            }
        }
    }

    private void spawnNukeTNT(World world, Location loc, float yield) {
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        if (!world.isChunkLoaded(cx, cz)) return;

        TNTPrimed tnt = (TNTPrimed) world.spawnEntity(loc, EntityType.TNT);
        tnt.setFuseTicks(10000);
        tnt.setVelocity(new Vector(0, -0.8, 0));
        tnt.setYield(yield);
        nukeTNT.add(tnt.getUniqueId());

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (nukeTNT.contains(tnt.getUniqueId()) && !tnt.isDead()) {
                tnt.setFuseTicks(1);
                nukeTNT.remove(tnt.getUniqueId());
            }
        }, 120);
    }

    private void spawnStab(World world, Location center) {
        Location ground = center.clone();
        while (ground.getY() > world.getMinHeight() && ground.getBlock().getType().isAir()) {
            ground.subtract(0, 1, 0);
        }
        ground.add(0, 1, 0);

        float yield = (float) config.getDouble("stab.yield", 8.0);
        double offset = config.getDouble("stab.tnt-offset", 0.3);

        int y = (int) ground.getY();
        int minY = world.getMinHeight();

        while (y >= minY) {
            Location loc = new Location(world, ground.getX(), y, ground.getZ());
            spawnTNTAt(world, loc.clone().add(offset, 0, offset), yield);
            spawnTNTAt(world, loc.clone().subtract(offset, 0, offset), yield);
            y -= 2;
        }
    }

    private void spawnTNTAt(World world, Location loc, float yield) {
        if (loc.getBlock().isLiquid()) return;
        TNTPrimed tnt = (TNTPrimed) world.spawnEntity(loc, EntityType.TNT);
        tnt.setFuseTicks(0);
        tnt.setYield(yield);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("orbital") || args.length != 1) {
            return null;
        }

        String input = args[0].toLowerCase();
        return Arrays.asList("nuke", "stab").stream()
                .filter(opt -> opt.startsWith(input))
                .sorted()
                .collect(Collectors.toList());
    }

    private void setDefaults() {
        config.addDefault("messages-enabled", true);
        config.addDefault("permission", "orbital.use");

        Map<String, Object> nuke = new HashMap<>();
        nuke.put("rings", 10);
        nuke.put("height", 80);
        nuke.put("yield", 6.0);
        nuke.put("delay-after-impact", 40);
        nuke.put("tnt-per-ring-base", 40);
        nuke.put("tnt-per-ring-increase", 2);
        nuke.put("center-tnt", true);
        config.addDefault("nuke", nuke);

        Map<String, Object> stab = new HashMap<>();
        stab.put("yield", 8.0);
        stab.put("tnt-offset", 0.3);
        config.addDefault("stab", stab);

        Map<String, Object> messages = new HashMap<>();
        messages.put("received", "§aReceived Orbital Strike Rod - §l{TYPE}§a!");
        messages.put("incoming", "§6Orbital Strike incoming... §l{TYPE}§6!");
        messages.put("no-target", "§cNo target found!");
        config.addDefault("messages", messages);

        config.options().copyDefaults(true);
    }

    private void sendMessage(Player player, String key, Map<String, String> placeholders) {
        if (!config.getBoolean("messages-enabled", true)) return;

        String path = key.contains(".") ? key : "messages." + key;
        String message = config.getString(path);
        if (message == null || message.isEmpty()) return;

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }

        player.sendMessage(message);
    }
}