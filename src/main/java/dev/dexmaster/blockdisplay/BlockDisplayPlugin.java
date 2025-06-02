package dev.dexmaster.blockdisplay;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class BlockDisplayPlugin extends JavaPlugin implements Listener, CommandExecutor {
    
    private static BlockDisplayPlugin instance;
    // Changed from WeakHashMap<Location, UUID> to Map<UUID, BlockLocation> for stronger tracking
    private final Map<UUID, BlockLocation> trackedDisplays = new HashMap<>();
    private String defaultColor;
    private double offsetX, offsetY, offsetZ;
    private BukkitTask failsafeTask;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Team glowTeam;
    
    @Override
    public void onEnable() {
        instance = this;
        
        saveDefaultConfig();
        
        // Load configuration
        loadConfiguration();
        
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        
        // Register command and tab completer
        getCommand("bd").setExecutor(this);
        getCommand("bd").setTabCompleter(new BlockDisplayTabCompleter());
        
        // Load displays from JSON file
        loadDisplaysFromFile();
        
        // Start failsafe cleanup task (every 2 seconds)
        startFailsafeTask();
        
        getLogger().info("BlockDisplay plugin enabled!");
    }
    
    @Override
    public void onDisable() {
        if (failsafeTask != null) {
            failsafeTask.cancel();
        }
        // Save displays to JSON file
        saveDisplaysToFile();
        cleanupAllDisplays();
        getLogger().info("BlockDisplay plugin disabled!");
    }
    
    private void loadConfiguration() {
        // Load offsets
        offsetX = getConfig().getDouble("offset.x", 0.99999);
        offsetY = getConfig().getDouble("offset.y", -1.0);
        offsetZ = getConfig().getDouble("offset.z", 0.99999);
        
        // Load default color
        defaultColor = getConfig().getString("default-color", "GREEN");
        try {
            // Validate color by trying to parse it
            parseColor(defaultColor);
        } catch (IllegalArgumentException e) {
            defaultColor = "GREEN";
            getLogger().warning("Invalid color in config: " + defaultColor + ", using GREEN");
        }
    }
    
    private void loadDisplaysFromFile() {
        try {
            File displaysFile = new File(getDataFolder(), "displays.json");
            if (!displaysFile.exists()) {
                return;
            }
            
            String json = new String(Files.readAllBytes(displaysFile.toPath()));
            Type listType = new TypeToken<List<DisplayData>>(){}.getType();
            List<DisplayData> displayList = gson.fromJson(json, listType);
            
            if (displayList == null) {
                return;
            }
            
            // Initialize glow team
            this.glowTeam = setupGlowTeam();
            
            int loaded = 0;
            for (DisplayData data : displayList) {
                World world = getServer().getWorld(data.world);
                if (world != null) {
                    Location blockLoc = new Location(world, data.x, data.y, data.z);
                    Location spawnLoc = blockLoc.clone().add(data.offsetX, data.offsetY, data.offsetZ);
                    
                    NamedTextColor glowColor = parseColor(data.glowColor);
                    
                    BlockDisplay display = (BlockDisplay) world.spawn(spawnLoc, BlockDisplay.class, entity -> {
                        entity.setBlock(Material.SHULKER_BOX.createBlockData());
                        entity.setGlowing(true);
                        entity.setGlowColorOverride(Color.fromRGB(glowColor.value()));
                        entity.setInterpolationDuration(0);
                        entity.setBrightness(new Display.Brightness(15, 15));
                    });
                    
                    // Add to glow team
                    glowTeam.addEntry(display.getUniqueId().toString());
                    
                    // Track the display
                    trackedDisplays.put(display.getUniqueId(), new BlockLocation(blockLoc, blockLoc.getBlock().getType()));
                    loaded++;
                }
            }
            
            getLogger().info("Loaded " + loaded + " displays from file");
        } catch (Exception e) {
            getLogger().severe("Failed to load displays: " + e.getMessage());
        }
    }
    
    private void saveDisplaysToFile() {
        try {
            File dataFolder = getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            File displaysFile = new File(dataFolder, "displays.json");
            List<DisplayData> displayList = new ArrayList<>();
            
            for (Map.Entry<UUID, BlockLocation> entry : trackedDisplays.entrySet()) {
                UUID displayId = entry.getKey();
                BlockLocation blockLoc = entry.getValue();
                Entity entity = getServer().getEntity(displayId);
                
                if (entity instanceof BlockDisplay) {
                    BlockDisplay display = (BlockDisplay) entity;
                    Color glowColor = display.getGlowColorOverride();
                    String colorName = glowColor != null ? colorFromRGB(glowColor.asRGB()) : defaultColor;
                    
                    DisplayData data = new DisplayData();
                    data.world = blockLoc.location.getWorld().getName();
                    data.x = blockLoc.location.getBlockX();
                    data.y = blockLoc.location.getBlockY();
                    data.z = blockLoc.location.getBlockZ();
                    data.glowColor = colorName;
                    data.offsetX = offsetX;
                    data.offsetY = offsetY;
                    data.offsetZ = offsetZ;
                    
                    displayList.add(data);
                }
            }
            
            try (FileWriter writer = new FileWriter(displaysFile)) {
                gson.toJson(displayList, writer);
            }
            
            getLogger().info("Saved " + displayList.size() + " displays to file");
        } catch (IOException e) {
            getLogger().severe("Failed to save displays: " + e.getMessage());
        }
    }
    
    private Team setupGlowTeam() {
        Scoreboard sb = getServer().getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam("blockdisplay_glow");
        
        if (team == null) {
            team = sb.registerNewTeam("blockdisplay_glow");
        }
        
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        team.setAllowFriendlyFire(true);
        
        return team;
    }
    
    private String colorFromRGB(int rgb) {
        // Simple mapping from RGB back to named colors
        for (NamedTextColor color : getValidColors()) {
            if (color.value() == rgb) {
                return color.toString().toLowerCase();
            }
        }
        return defaultColor.toLowerCase();
    }
    
    private NamedTextColor[] getValidColors() {
        return new NamedTextColor[] {
            NamedTextColor.BLACK, NamedTextColor.DARK_BLUE, NamedTextColor.DARK_GREEN, 
            NamedTextColor.DARK_AQUA, NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE,
            NamedTextColor.GOLD, NamedTextColor.GRAY, NamedTextColor.DARK_GRAY,
            NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.AQUA,
            NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE, NamedTextColor.YELLOW, NamedTextColor.WHITE
        };
    }
    
    public static NamedTextColor parseColor(String input) throws IllegalArgumentException {
        // List of valid NamedTextColor values
        NamedTextColor[] validColors = new NamedTextColor[] {
            NamedTextColor.BLACK, NamedTextColor.DARK_BLUE, NamedTextColor.DARK_GREEN, 
            NamedTextColor.DARK_AQUA, NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE,
            NamedTextColor.GOLD, NamedTextColor.GRAY, NamedTextColor.DARK_GRAY,
            NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.AQUA,
            NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE, NamedTextColor.YELLOW, NamedTextColor.WHITE
        };
        
        return Arrays.stream(validColors)
                .filter(c -> c.toString().equalsIgnoreCase(input))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid color: " + input));
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /bd <spawn|reload>", NamedTextColor.RED));
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "spawn" -> handleSpawnCommand(sender, args);
            case "reload" -> handleReloadCommand(sender);
            default -> sender.sendMessage(Component.text("Unknown subcommand: " + args[0], NamedTextColor.RED));
        }
        
        return true;
    }
    
    private void handleSpawnCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can spawn block displays!", NamedTextColor.RED));
            return;
        }
        
        if (!player.hasPermission("bd.use")) {
            sender.sendMessage(Component.text("You don't have permission to use this command!", NamedTextColor.RED));
            return;
        }
        
        Block targetBlock = player.getTargetBlockExact(6);
        if (targetBlock == null || targetBlock.getType() == Material.AIR) {
            player.sendMessage(Component.text("No solid block found within 6 blocks!", NamedTextColor.RED));
            return;
        }
        
        // Spawn guard - check for disallowed materials
        Material blockType = targetBlock.getType();
        
        // Check for leaves, chests, and trapped chests (original restrictions)
        if (blockType.name().contains("LEAVES") || blockType == Material.CHEST || blockType == Material.TRAPPED_CHEST) {
            sender.sendMessage(Component.text("Cannot place on that block.", NamedTextColor.RED));
            return;
        }
        
        // Check for non-full blocks (stairs, fences, slabs)
        if (blockType.name().contains("STAIRS") || blockType.name().contains("FENCE") || 
            blockType.name().contains("SLAB")) {
            sender.sendMessage(Component.text("§cYou can't place a BlockDisplay on non-full blocks.", NamedTextColor.RED));
            return;
        }
        
        // Full-block only restrictions
        boolean isGlass = blockType.name().endsWith("GLASS") || blockType.name().endsWith("GLASS_PANE");
        boolean isNonFull = !targetBlock.getBlockData().isOccluding();   // Paper API 1.19+
        boolean disallowed = (isNonFull || isGlass) && blockType != Material.SOUL_SAND;
        
        if (disallowed) {
            sender.sendMessage(Component.text("That block can't host a display.", NamedTextColor.RED));
            return;
        }
        
        // Check if there's already a display at this location
        boolean alreadyExists = trackedDisplays.values().stream()
                .anyMatch(blockLoc -> blockLoc.location.equals(targetBlock.getLocation()));
        
        if (alreadyExists) {
            player.sendMessage(Component.text("A block display already exists at this location!", NamedTextColor.YELLOW));
            return;
        }
        
        // Determine color (from args or default)
        String colorName = defaultColor;
        if (args.length >= 2) {
            colorName = args[1];
        }
        
        NamedTextColor glowColor;
        try {
            glowColor = parseColor(colorName);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Invalid color: " + colorName, NamedTextColor.RED));
            player.sendMessage(Component.text("Available colors: black, dark_blue, dark_green, dark_aqua, dark_red, dark_purple, gold, gray, dark_gray, blue, green, aqua, red, light_purple, yellow, white", NamedTextColor.GRAY));
            return;
        }
        
        // Calculate spawn location using configured offsets
        Location spawnLoc = targetBlock.getLocation().clone().add(offsetX, offsetY, offsetZ);
        
        // Spawn the block display with per-display glow color
        BlockDisplay display = (BlockDisplay) player.getWorld().spawn(spawnLoc, BlockDisplay.class, entity -> {
            entity.setBlock(Material.SHULKER_BOX.createBlockData());
            entity.setGlowing(true);
            entity.setGlowColorOverride(Color.fromRGB(glowColor.value())); // Convert NamedTextColor to Color
            entity.setInterpolationDuration(0);
            entity.setBrightness(new Display.Brightness(15, 15));
        });
        
        // Add to glow team
        if (glowTeam != null) {
            glowTeam.addEntry(display.getUniqueId().toString());
        }
        
        // Track the display with its support block
        trackedDisplays.put(display.getUniqueId(), new BlockLocation(targetBlock.getLocation(), blockType));
        
        player.sendMessage(Component.text("Spawned glowing shulker box display with " + glowColor.toString().toLowerCase() + " glow at offset (" + 
                offsetX + ", " + offsetY + ", " + offsetZ + ")!", NamedTextColor.GREEN));
    }
    
    private void handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("bd.admin")) {
            sender.sendMessage(Component.text("You don't have permission to reload!", NamedTextColor.RED));
            return;
        }
        
        reloadConfig();
        loadConfiguration();
        sender.sendMessage(Component.text("Configuration reloaded! New offsets: (" + 
                offsetX + ", " + offsetY + ", " + offsetZ + "), Default color: " + defaultColor, NamedTextColor.GREEN));
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location blockLoc = event.getBlock().getLocation();
        removeDisplayAtLocation(blockLoc);
    }
    
    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getTo() == Material.AIR) {
            Location blockLoc = event.getBlock().getLocation();
            removeDisplayAtLocation(blockLoc);
        }
    }
    
    @EventHandler
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            removeDisplayAtLocation(block.getLocation());
        }
    }
    
    @EventHandler
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            removeDisplayAtLocation(block.getLocation());
        }
    }
    
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            removeDisplayAtLocation(block.getLocation());
        }
    }
    
    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            removeDisplayAtLocation(block.getLocation());
        }
    }
    
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == this) {
            cleanupAllDisplays();
        }
    }
    
    private void removeDisplayAtLocation(Location blockLoc) {
        // Find display by support block location
        UUID displayToRemove = null;
        for (Map.Entry<UUID, BlockLocation> entry : trackedDisplays.entrySet()) {
            if (entry.getValue().location.equals(blockLoc)) {
                displayToRemove = entry.getKey();
                break;
            }
        }
        
        if (displayToRemove != null) {
            removeDisplay(displayToRemove);
        }
    }
    
    private void removeDisplay(UUID displayId) {
        // Find and remove the entity
        for (World world : getServer().getWorlds()) {
            Entity entity = world.getEntity(displayId);
            if (entity instanceof BlockDisplay) {
                entity.remove();
                break;
            }
        }
        
        // Remove from team
        if (glowTeam != null) {
            glowTeam.removeEntry(displayId.toString());
        }
        
        // Remove from tracking
        trackedDisplays.remove(displayId);
    }
    
    private void cleanupAllDisplays() {
        for (UUID displayId : trackedDisplays.keySet()) {
            // Remove entity
            for (World world : getServer().getWorlds()) {
                Entity entity = world.getEntity(displayId);
                if (entity instanceof BlockDisplay) {
                    entity.remove();
                    break;
                }
            }
        }
        trackedDisplays.clear();
    }
    
    public static BlockDisplayPlugin getInstance() {
        return instance;
    }
    
    // Helper class to store block location and material type
    private static class BlockLocation {
        final Location location;
        final Material originalMaterial;
        
        BlockLocation(Location location, Material originalMaterial) {
            this.location = location.clone();
            this.originalMaterial = originalMaterial;
        }
    }
    
    // Data class for JSON serialization
    private static class DisplayData {
        public String world;
        public int x;
        public int y;
        public int z;
        public String glowColor;
        public double offsetX;
        public double offsetY;
        public double offsetZ;
    }
    
    private void startFailsafeTask() {
        failsafeTask = getServer().getScheduler().runTaskTimer(this, () -> {
            // Iterate through tracked displays and check if their support blocks still exist
            trackedDisplays.entrySet().removeIf(entry -> {
                UUID displayId = entry.getKey();
                BlockLocation blockLoc = entry.getValue();
                
                Block block = blockLoc.location.getBlock();
                Entity entity = getServer().getEntity(displayId);
                
                // Check if block is no longer solid or changed type, or entity doesn't exist
                boolean shouldRemove = !block.getType().isSolid() || 
                                     block.getType() != blockLoc.originalMaterial ||
                                     !(entity instanceof BlockDisplay);
                
                if (shouldRemove && entity != null) {
                    entity.remove();
                    if (glowTeam != null) {
                        glowTeam.removeEntry(entity.getUniqueId().toString());
                    }
                    return true; // Remove from map
                }
                return false; // Keep in map
            });
        }, 40L, 40L); // Every 2 seconds (40 ticks)
    }
} 