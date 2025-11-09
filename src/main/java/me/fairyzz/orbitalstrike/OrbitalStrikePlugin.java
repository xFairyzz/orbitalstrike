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
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

public class OrbitalStrikePlugin extends JavaPlugin implements CommandExecutor, Listener, TabCompleter {

    private static final int CUSTOM_MODEL_DATA = 12345;
    private static final String[] STRIKE_TYPES = {"nuke", "stab", "dogs"};

    private final Map<UUID, Set<UUID>> strikeTNT = new HashMap<>();
    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        setDefaults();
        saveConfig();

        Objects.requireNonNull(getCommand("orbital")).setExecutor(this);
        Objects.requireNonNull(getCommand("orbital")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        strikeTNT.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (!hasPermission(player)) {
            sendMessage(player, "no-permission", Map.of());
            return true;
        }

        if (args.length != 1) {
            sendMessage(player, "usage", Map.of("{CMD}", "/orbital <nuke|stab|dogs>"));
            return true;
        }

        String type = args[0].toLowerCase();
        if (!isValidStrikeType(type)) {
            sendMessage(player, "invalid-type", Map.of());
            return true;
        }

        giveStrikeRod(player, type);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("orbital") || args.length != 1) {
            return null;
        }

        String input = args[0].toLowerCase();
        return Arrays.stream(STRIKE_TYPES)
                .filter(type -> type.startsWith(input))
                .sorted()
                .collect(Collectors.toList());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().toString().contains("RIGHT") || !event.hasItem()) {
            return;
        }

        ItemStack item = event.getItem();
        if (!isStrikeRod(item)) {
            return;
        }

        String type = getStrikeType(item);
        if (type == null) {
            return;
        }

        Player player = event.getPlayer();
        Location target = getTargetLocation(player);

        if (target == null) {
            sendMessage(player, "no-target", Map.of());
            return;
        }

        executeStrike(player, item, type, target);
        event.setCancelled(true);
    }

    @EventHandler
    public void onTNTLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }

        UUID tntId = tnt.getUniqueId();
        if (!isTrackedTNT(tntId)) {
            return;
        }

        event.setCancelled(true);

        if (event.getBlock().getType().isSolid()) {
            removeFromTracking(tntId);
            scheduleExplosion(tnt);
        }
    }

    private void executeStrike(Player player, ItemStack item, String type, Location target) {
        sendMessage(player, "incoming", Map.of("{TYPE}", type.toUpperCase()));

        consumeRodDelayed(player, item);

        UUID strikeId = UUID.randomUUID();
        Set<UUID> tntList = new HashSet<>();
        strikeTNT.put(strikeId, tntList);

        Bukkit.getScheduler().runTask(this, () -> {
            switch (type) {
                case "nuke" -> spawnNuke(target.getWorld(), target, strikeId, tntList);
                case "stab" -> spawnStab(target.getWorld(), target);
                case "dogs" -> spawnDogs(target.getWorld(), target, player);
            }

            Bukkit.getScheduler().runTaskLater(this, () -> strikeTNT.remove(strikeId), 200L);
        });
    }

    private void consumeRodDelayed(Player player, ItemStack originalItem) {
        String displayName = originalItem.getItemMeta().getDisplayName();

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (consumeFromHand(player.getInventory().getItemInMainHand(), displayName, player)) {
                return;
            }
            consumeFromHand(player.getInventory().getItemInOffHand(), displayName, player);
        }, 1L);
    }

    private boolean consumeFromHand(ItemStack hand, String displayName, Player player) {
        if (hand.getType() == Material.FISHING_ROD &&
                hand.hasItemMeta() &&
                hand.getItemMeta().getDisplayName().equals(displayName)) {

            hand.setAmount(hand.getAmount() - 1);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            return true;
        }
        return false;
    }

    private void spawnNuke(World world, Location center, UUID strikeId, Set<UUID> tntList) {
        int rings = config.getInt("nuke.rings", 10);
        double height = center.getY() + config.getInt("nuke.height", 80);
        float yield = (float) config.getDouble("nuke.yield", 6.0);
        int baseTnt = config.getInt("nuke.tnt-per-ring-base", 40);
        int increase = config.getInt("nuke.tnt-per-ring-increase", 2);
        boolean centerTnt = config.getBoolean("nuke.center-tnt", true);

        if (centerTnt) {
            Location loc = new Location(world, center.getX() + 0.5, height, center.getZ() + 0.5);
            spawnNukeTNT(world, loc, yield, strikeId, tntList);
        }

        for (int ring = 1; ring <= rings; ring++) {
            double radius = ring * 4.0;
            int tntCount = baseTnt + ring * increase;
            double step = 360.0 / tntCount;

            for (int i = 0; i < tntCount; i++) {
                double angle = i * step + (ring * 10);
                double x = center.getX() + radius * Math.cos(Math.toRadians(angle));
                double z = center.getZ() + radius * Math.sin(Math.toRadians(angle));
                double roundedX = Math.round(x * 10) / 10.0;
                double roundedZ = Math.round(z * 10) / 10.0;

                Location loc = new Location(world, roundedX + 0.5, height, roundedZ + 0.5);
                spawnNukeTNT(world, loc, yield, strikeId, tntList);
            }
        }
    }

    private void spawnNukeTNT(World world, Location loc, float yield, UUID strikeId, Set<UUID> tntList) {
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;

        if (!world.isChunkLoaded(cx, cz)) {
            return;
        }

        TNTPrimed tnt = (TNTPrimed) world.spawnEntity(loc, EntityType.TNT);
        tnt.setFuseTicks(10000);
        tnt.setVelocity(new Vector(0, -0.8, 0));
        tnt.setGravity(true);
        tnt.setYield(yield);
        tnt.setInvulnerable(true);

        UUID tntId = tnt.getUniqueId();
        tntList.add(tntId);

        int fuseFallbackTicks = config.getInt("nuke.fuse-fallback-ticks", 160);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (tntList.contains(tntId) && !tnt.isDead()) {
                tnt.setFuseTicks(1);
                tntList.remove(tntId);
            }
        }, fuseFallbackTicks);
    }

    private void spawnStab(World world, Location center) {
        Location ground = findGroundLevel(world, center);
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
        if (loc.getBlock().isLiquid()) {
            return;
        }

        TNTPrimed tnt = (TNTPrimed) world.spawnEntity(loc, EntityType.TNT);
        tnt.setFuseTicks(0);
        tnt.setYield(yield);
    }

    private void spawnDogs(World world, Location center, Player owner) {
        int count = config.getInt("dogs.count", 50);
        double radius = config.getDouble("dogs.radius", 5.0);
        int durationTicks = config.getInt("dogs.effect-duration", 2400);

        Location ground = findGroundLevel(world, center);

        for (int i = 0; i < count; i++) {
            double angle = Math.random() * 360;
            double dist = Math.random() * radius;
            double x = ground.getX() + dist * Math.cos(Math.toRadians(angle));
            double z = ground.getZ() + dist * Math.sin(Math.toRadians(angle));

            Location spawnLoc = findGroundLevel(world, new Location(world, x, ground.getY(), z));

            if (spawnLoc.getBlock().isLiquid()) {
                continue;
            }

            Wolf wolf = (Wolf) world.spawnEntity(spawnLoc, EntityType.WOLF);
            wolf.setTamed(true);
            wolf.setOwner(owner);
            wolf.setSitting(false);
            wolf.setCollarColor(DyeColor.RED);
            wolf.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, 1));
            wolf.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks, 1));
        }
    }

    private boolean hasPermission(Player player) {
        return player.hasPermission(config.getString("permission", "orbital.use"));
    }

    private boolean isValidStrikeType(String type) {
        return Arrays.asList(STRIKE_TYPES).contains(type);
    }

    private void giveStrikeRod(Player player, String type) {
        ItemStack rod = createStrikeRod(type);
        player.getInventory().addItem(rod);
        sendMessage(player, "received", Map.of("{TYPE}", type.toUpperCase()));
    }

    private ItemStack createStrikeRod(String type) {
        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();

        String displayName = switch (type) {
            case "nuke" -> "§fNUKE SHOT";
            case "stab" -> "§fSTAB SHOT";
            case "dogs" -> "§fDOG SHOT";
            default -> "§fOrbital Strike Rod";
        };

        meta.setDisplayName(displayName);
        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        rod.setItemMeta(meta);
        rod.setDurability((short) 63);

        return rod;
    }

    private boolean isStrikeRod(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() &&
                meta.hasCustomModelData() &&
                meta.getCustomModelData() == CUSTOM_MODEL_DATA;
    }

    private String getStrikeType(ItemStack item) {
        String displayName = item.getItemMeta().getDisplayName();

        return switch (displayName) {
            case "§fNUKE SHOT" -> "nuke";
            case "§fSTAB SHOT" -> "stab";
            case "§fDOG SHOT" -> "dogs";
            default -> null;
        };
    }

    private Location getTargetLocation(Player player) {
        int distance = config.getInt("rod-distance", 256);
        RayTraceResult result = player.rayTraceBlocks(distance);

        if (result == null || result.getHitBlock() == null) {
            return null;
        }

        return result.getHitBlock().getLocation().add(0, 60, 0);
    }

    private Location findGroundLevel(World world, Location start) {
        Location ground = start.clone();

        while (ground.getY() > world.getMinHeight() && ground.getBlock().getType().isAir()) {
            ground.subtract(0, 1, 0);
        }

        ground.add(0, 1, 0);
        return ground;
    }

    private void scheduleExplosion(TNTPrimed tnt) {
        if (!tnt.isDead()) {
            tnt.setFuseTicks(1);
        }
    }

    private boolean isTrackedTNT(UUID tntId) {
        return strikeTNT.values().stream().anyMatch(set -> set.contains(tntId));
    }

    private void removeFromTracking(UUID tntId) {
        strikeTNT.values().forEach(set -> set.remove(tntId));
    }

    private void sendMessage(Player player, String key, Map<String, String> placeholders) {
        if (!config.getBoolean("messages-enabled", true)) {
            return;
        }

        String path = "messages." + key;
        String message = config.getString(path);

        if (message == null || message.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }

        player.sendMessage(message);
    }

    private void setDefaults() {
        config.addDefault("messages-enabled", true);
        config.addDefault("permission", "orbital.use");
        config.addDefault("rod-distance", 256);

        setNukeDefaults();
        setStabDefaults();
        setDogsDefaults();
        setMessageDefaults();

        config.options().copyDefaults(true);
    }

    private void setNukeDefaults() {
        Map<String, Object> nuke = new HashMap<>();
        nuke.put("rings", 10);
        nuke.put("height", 80);
        nuke.put("yield", 6.0);
        nuke.put("tnt-per-ring-base", 40);
        nuke.put("tnt-per-ring-increase", 2);
        nuke.put("center-tnt", true);
        nuke.put("fuse-fallback-ticks", 160);
        config.addDefault("nuke", nuke);
    }

    private void setStabDefaults() {
        Map<String, Object> stab = new HashMap<>();
        stab.put("yield", 8.0);
        stab.put("tnt-offset", 0.3);
        config.addDefault("stab", stab);
    }

    private void setDogsDefaults() {
        Map<String, Object> dogs = new HashMap<>();
        dogs.put("count", 50);
        dogs.put("radius", 5.0);
        dogs.put("effect-duration", 2400);
        config.addDefault("dogs", dogs);
    }

    private void setMessageDefaults() {
        Map<String, Object> messages = new HashMap<>();
        messages.put("received", "§aYou received an Orbital Strike Rod - §l{TYPE}§a!");
        messages.put("incoming", "§6Orbital Strike incoming... §l{TYPE}§6!");
        messages.put("no-target", "§cNo valid target found!");
        config.addDefault("messages", messages);
    }
}