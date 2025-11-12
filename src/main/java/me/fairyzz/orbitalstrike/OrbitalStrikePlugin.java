package me.fairyzz.orbitalstrike;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class OrbitalStrikePlugin extends JavaPlugin implements CommandExecutor, Listener, TabCompleter {

    private static final int CUSTOM_MODEL_DATA = 12345;
    private static final String[] STRIKE_TYPES = {"nuke", "stab", "dogs"};

    private final Map<UUID, Set<UUID>> strikeTNT = new HashMap<>();
    private final Map<UUID, String> pendingStrikes = new HashMap<>();
    private FileConfiguration config;

    private static final String GITHUB_REPO = "xFairyzz/orbitalstrike";
    private static final String CURRENT_VERSION = "v1.3.0";
    private boolean hasUpdate = false;
    private String latestVersion = "";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        setDefaults();
        saveConfig();

        Objects.requireNonNull(getCommand("orbital")).setExecutor(this);
        Objects.requireNonNull(getCommand("orbital")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);

        checkForUpdate();
        Bukkit.getScheduler().runTaskLater(this, this::sendConsoleUpdate, 40L);
    }

    private void checkForUpdate() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String releasesUrl = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
                URL url = new URL(releasesUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    getLogger().warning("§e[OrbitalStrike] GitHub API ERROR: HTTP " + responseCode);
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String json = response.toString();
                String tagName = extractTagName(json);

                String normalizedTag = tagName.toLowerCase().startsWith("v") ? tagName.substring(1) : tagName;
                String normalizedCurrent = CURRENT_VERSION.toLowerCase().startsWith("v") ? CURRENT_VERSION.substring(1) : CURRENT_VERSION;

                if (!normalizedTag.equalsIgnoreCase(normalizedCurrent)) {
                    hasUpdate = true;
                    latestVersion = tagName;
                }
            } catch (Exception e) {
                getLogger().warning("§e[OrbitalStrike] Update-Check Failed: " + e.getMessage());
            }
        });
    }

    private String extractTagName(String json) {
        try {
            int tagIndex = json.indexOf("\"tag_name\":\"");
            if (tagIndex == -1) return null;

            int start = tagIndex + 12;
            int end = json.indexOf("\"", start);
            if (end == -1) return null;

            return json.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    private void sendUpdateNotification(Player player) {
        if (hasUpdate && player.isOp()) {
            player.sendMessage("§c[OrbitalStrike] §fUpdate available! §cCurrent: " + CURRENT_VERSION + " → §aLatest: " + latestVersion);
            player.sendMessage("§cDownload: §Fhttps://modrinth.com/plugin/orbitalstrike-plugin");
        }
    }

    private void sendConsoleUpdate() {
        if (hasUpdate) {
            getLogger().warning("\u001B[31mUPDATE AVAILABLE! Current: " + CURRENT_VERSION + " → Latest: " + latestVersion + "\u001B[0m");
            getLogger().warning("\u001B[31mDownload: https://modrinth.com/plugin/orbitalstrike-plugin\u001B[0m");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> sendUpdateNotification(event.getPlayer()), 20L);
    }
    @Override
    public void onDisable() {
        strikeTNT.clear();
        pendingStrikes.clear();
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
        if (type == null) return;

        Player player = event.getPlayer();
        Location target = getTargetLocation(player);

        if (target == null) {
            sendMessage(player, "no-target", Map.of());
            return;
        }

        boolean throwRod = config.getBoolean("rod.throw-rod", true);

        if (throwRod) {
            pendingStrikes.put(player.getUniqueId(), type);
        } else {
            executeStrike(player, item, type, target);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (!config.getBoolean("rod.throw-rod", true)) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!pendingStrikes.containsKey(playerId)) {
            return;
        }

        if (event.getState() != PlayerFishEvent.State.REEL_IN &&
                event.getState() != PlayerFishEvent.State.FAILED_ATTEMPT) {
            return;
        }

        String type = pendingStrikes.remove(playerId);

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        boolean consumed = false;
        if (isStrikeRod(mainHand)) {
            mainHand.setAmount(mainHand.getAmount() - 1);
            consumed = true;
        } else if (isStrikeRod(offHand)) {
            offHand.setAmount(offHand.getAmount() - 1);
            consumed = true;
        }

        if (consumed) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.ITEM,
                    player.getEyeLocation().add(0, -0.5, 0),
                    20, 0.3, 0.3, 0.3, 0.1,
                    new ItemStack(Material.FISHING_ROD));
        }

        Location target = getTargetLocation(player);
        if (target == null) return;

        executeStrike(player, new ItemStack(Material.FISHING_ROD), type, target);
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

        if (!config.getBoolean("rod.throw-rod", true)) {
            consumeRodDelayed(player, item);
        }

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
        float yield = (float) config.getDouble("nuke.yield",6.0);
        int baseTnt = config.getInt("nuke.tnt-per-ring-base", 40);
        int increase = config.getInt("nuke.tnt-per-ring-increase", 2);
        boolean centerTnt = config.getBoolean("nuke.center-tnt", true);
        boolean animatedRings = config.getBoolean("nuke.Animated-rings", true);

        if (animatedRings) {
            Location centerLoc = new Location(world, center.getX() + 0.5, height, center.getZ() + 0.5);
            UUID centerId;

            if (centerTnt) {
                TNTPrimed centerTntEntity = (TNTPrimed) world.spawnEntity(centerLoc.clone(), EntityType.TNT);
                centerTntEntity.setFuseTicks(10000);
                centerTntEntity.setVelocity(new Vector(0, 0, 0));
                centerTntEntity.setGravity(false);
                centerTntEntity.setYield(yield);
                centerTntEntity.setInvulnerable(true);

                centerId = centerTntEntity.getUniqueId();
                tntList.add(centerId);

                Bukkit.getScheduler().runTaskLater(this, () -> {
                    for (Entity entity : world.getNearbyEntities(centerLoc, 100, 100, 100)) {
                        if (entity instanceof TNTPrimed tnt && tnt.getUniqueId().equals(centerId) && !tnt.isDead()) {
                            tnt.setGravity(true);
                            break;
                        }
                    }
                }, 30L);
            } else {
                centerId = null;
            }

            for (int ring = 1; ring <= rings; ring++) {
                double radius = ring * 4.0;
                int tntCount = baseTnt + ring * increase;
                double step = 360.0 / tntCount;

                for (int i = 0; i < tntCount; i++) {
                    double angle = i * step + (ring * 10);
                    double targetX = center.getX() + radius * Math.cos(Math.toRadians(angle));
                    double targetZ = center.getZ() + radius * Math.sin(Math.toRadians(angle));
                    double roundedTargetX = Math.round(targetX * 10) / 10.0;
                    double roundedTargetZ = Math.round(targetZ * 10) / 10.0;

                    TNTPrimed ringTnt = (TNTPrimed) world.spawnEntity(centerLoc.clone(), EntityType.TNT);
                    ringTnt.setFuseTicks(10000);
                    ringTnt.setVelocity(new Vector(0, 0, 0));
                    ringTnt.setGravity(false);
                    ringTnt.setYield(yield);
                    ringTnt.setInvulnerable(true);

                    UUID ringId = ringTnt.getUniqueId();
                    tntList.add(ringId);

                    final double finalX = roundedTargetX + 0.5;
                    final double finalZ = roundedTargetZ + 0.5;
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        for (Entity entity : world.getNearbyEntities(centerLoc, 200, 200, 200)) {
                            if (entity instanceof TNTPrimed tnt && tnt.getUniqueId().equals(ringId) && !tnt.isDead()) {
                                Vector velocity = getVector(finalX, centerLoc, finalZ);
                                tnt.setVelocity(velocity);
                                tnt.setGravity(true);
                                break;
                            }
                        }
                    }, 30L);
                }
            }

            int fuseFallbackTicks = config.getInt("nuke.fuse-fallback-ticks", 160);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (UUID id : new ArrayList<>(tntList)) {
                    for (Entity entity : world.getNearbyEntities(centerLoc, 200, 200, 200)) {
                        if (entity instanceof TNTPrimed tnt && tnt.getUniqueId().equals(id) && !tnt.isDead()) {
                            tnt.setFuseTicks(1);
                            tntList.remove(id);
                            break;
                        }
                    }
                }
            }, fuseFallbackTicks);

        } else {
            if (centerTnt) {
                Location loc = new Location(world, center.getX() + 0.5, height, center.getZ() + 0.5);
                spawnNukeTNT(world, loc, yield, strikeId, tntList);
            }

            for (int ring = 1; ring <= rings; ring++) {
                double radius = ring * 4.0;
                int tntCount = baseTnt + ring * increase;
                double step = 360.0 / tntCount;
                double startAngle = ring * 13.0;

                for (int i = 0; i < tntCount; i++) {
                    double angle = startAngle + i * step;
                    double x = center.getX() + radius * Math.cos(Math.toRadians(angle));
                    double z = center.getZ() + radius * Math.sin(Math.toRadians(angle));
                    double roundedX = Math.round(x * 10) / 10.0;
                    double roundedZ = Math.round(z * 10) / 10.0;

                    Location loc = new Location(world, roundedX + 0.5, height, roundedZ + 0.5);
                    spawnNukeTNT(world, loc, yield, strikeId, tntList);
                }
            }
        }
    }

    private static Vector getVector(double finalTargetX, Location centerLoc, double finalTargetZ) {
        double deltaX = finalTargetX - centerLoc.getX();
        double deltaZ = finalTargetZ - centerLoc.getZ();
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double speed = distance / 30.0;

        return new Vector(deltaX / distance * speed, 0, deltaZ / distance * speed);
    }

    private void spawnNukeTNT(World world, Location loc, float yield, UUID strikeId, Set<UUID> tntList) {
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;

        if (!world.isChunkLoaded(cx, cz)) return;

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
        if (loc.getBlock().isLiquid()) return;

        TNTPrimed tnt = (TNTPrimed) world.spawnEntity(loc, EntityType.TNT);
        tnt.setFuseTicks(0);
        tnt.setYield(yield);
    }

    private void spawnDogs(World world, Location center, Player owner) {
        int count = config.getInt("dogs.count", 50);
        double radius = config.getDouble("dogs.radius", 5.0);
        int durationTicks = config.getInt("dogs.effect-duration", 2400);

        List<String> effectStrings = config.getStringList("dogs.effects");
        List<PotionEffect> effects = new ArrayList<>();

        for (int i = 0; i < Math.min(effectStrings.size(), 2); i++) {
            String entry = effectStrings.get(i).trim().toUpperCase();
            String[] parts = entry.split(":");
            if (parts.length != 2) continue;

            PotionEffectType type = PotionEffectType.getByName(parts[0]);
            if (type == null) continue;

            int amplifier;
            try {
                amplifier = Integer.parseInt(parts[1]) - 1;
            } catch (NumberFormatException e) {
                continue;
            }

            if (amplifier < 0) amplifier = 0;
            effects.add(new PotionEffect(type, durationTicks, amplifier));
        }

        if (effects.isEmpty()) {
            effects.add(new PotionEffect(PotionEffectType.SPEED, durationTicks, 1));
        }

        Location ground = findGroundLevel(world, center);

        for (int i = 0; i < count; i++) {
            double angle = Math.random() * 360;
            double dist = Math.random() * radius;
            double x = ground.getX() + dist * Math.cos(Math.toRadians(angle));
            double z = ground.getZ() + dist * Math.sin(Math.toRadians(angle));

            Location spawnLoc = findGroundLevel(world, new Location(world, x, ground.getY(), z));
            if (spawnLoc.getBlock().isLiquid()) continue;

            Wolf wolf = (Wolf) world.spawnEntity(spawnLoc, EntityType.WOLF);
            wolf.setTamed(true);
            wolf.setOwner(owner);
            wolf.setSitting(false);
            wolf.setCollarColor(DyeColor.RED);

            for (PotionEffect effect : effects) {
                wolf.addPotionEffect(effect);
            }
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
        if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && meta.hasCustomModelData() && meta.getCustomModelData() == CUSTOM_MODEL_DATA;
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
        int distance = config.getInt("rod.distance", 256);
        RayTraceResult result = player.rayTraceBlocks(distance);
        if (result == null || result.getHitBlock() == null) return null;
        return result.getHitBlock().getLocation().add(0, 60, 0);
    }

    private Location findGroundLevel(World world, Location start) {
        Location ground = start.clone();
        while (ground.getY() > world.getMinHeight() && ground.getBlock().getType().isAir()) {
            ground.subtract(0, 1, 0);
        }
        return ground.add(0, 1, 0);
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
        if (!config.getBoolean("messages-enabled", true)) return;
        String path = "messages." + key;
        String message = config.getString(path);
        if (message == null || message.isEmpty()) return;

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }
        player.sendMessage(message);
    }

    private void setDefaults() {
        config.addDefault("messages-enabled", true);
        config.addDefault("permission", "orbital.use");

        setRodDefaults();
        setNukeDefaults();
        setStabDefaults();
        setDogsDefaults();
        setMessageDefaults();

        config.options().copyDefaults(true);
    }

    private void setRodDefaults() {
        Map<String, Object> rod = new HashMap<>();
        rod.put("distance", 100);
        rod.put("throw-rod", true);
        config.addDefault("rod", rod);
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
        nuke.put("Animated-rings", true);
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

        List<String> effects = new ArrayList<>();
        effects.add("SPEED:1");
        effects.add("STRENGTH:2");
        dogs.put("effects", effects);

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