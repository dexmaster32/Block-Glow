package dev.dexmaster.blockdisplay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
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
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Arrays;
import java.util.UUID;
import java.util.WeakHashMap;

public class BlockDisplayPlugin extends JavaPlugin implements Listener, CommandExecutor {
    
    private static BlockDisplayPlugin instance;
    private final WeakHashMap<Location, UUID> trackedDisplays = new WeakHashMap<>();
    private Team glowTeam;
    private ChatColor currentColor;
    private double offsetX, offsetY, offsetZ;
    
    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        
        // Load configuration
        loadConfiguration();
        
        // Initialize scoreboard team
        this.glowTeam = setupGlowTeam(currentColor);
        
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        
        // Register command and tab completer
        getCommand("bd").setExecutor(this);
        getCommand("bd").setTabCompleter(new BlockDisplayTabCompleter());
        
        getLogger().info("BlockDisplay plugin enabled!");
    }
    
    @Override
    public void onDisable() {
        cleanupAllDisplays();
        getLogger().info("BlockDisplay plugin disabled!");
    }
    
    private void loadConfiguration() {
        // Load offsets
        offsetX = getConfig().getDouble("offset.x", 0.99999);
        offsetY = getConfig().getDouble("offset.y", -1.0);
        offsetZ = getConfig().getDouble("offset.z", 0.99999);
        
        // Load color
        String colorName = getConfig().getString("default-color", "GREEN");
        try {
            currentColor = ChatColor.valueOf(colorName.toUpperCase());
        } catch (IllegalArgumentException e) {
            currentColor = ChatColor.GREEN;
            getLogger().warning("Invalid color in config: " + colorName + ", using GREEN");
        }
    }
    
    private Team setupGlowTeam(ChatColor color) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam("blockdisplay_glow");
        
        if (team == null) {
            team = sb.registerNewTeam("blockdisplay_glow");
        }
        
        // Apply options every time (color, visibility, collision)
        team.setColor(color);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        team.setAllowFriendlyFire(true);
        
        return team;
    }
    
    public static ChatColor parseColor(String input) throws IllegalArgumentException {
        return Arrays.stream(ChatColor.values())
                .filter(c -> c.isColor() && c.name().equalsIgnoreCase(input))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid color: " + input));
    }
    
    private void saveConfigColor(ChatColor color) {
        getConfig().set("default-color", color.name());
        saveConfig();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /bd <spawn|color|reload>", NamedTextColor.RED));
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "spawn" -> handleSpawnCommand(sender);
            case "color" -> handleColorCommand(sender, args);
            case "reload" -> handleReloadCommand(sender);
            default -> sender.sendMessage(Component.text("Unknown subcommand: " + args[0], NamedTextColor.RED));
        }
        
        return true;
    }
    
    private void handleSpawnCommand(CommandSender sender) {
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
        
        // Calculate spawn location using configured offsets
        Location spawnLoc = targetBlock.getLocation().clone().add(offsetX, offsetY, offsetZ);
        
        // Check if there's already a display at this location
        if (trackedDisplays.containsKey(targetBlock.getLocation())) {
            player.sendMessage(Component.text("A block display already exists at this location!", NamedTextColor.YELLOW));
            return;
        }
        
        // Spawn the block display
        BlockDisplay display = (BlockDisplay) player.getWorld().spawn(spawnLoc, BlockDisplay.class, entity -> {
            entity.setBlock(Material.SHULKER_BOX.createBlockData());
            entity.setGlowing(true);
            entity.setInterpolationDuration(0);
            entity.setBrightness(new Display.Brightness(15, 15));
        });
        
        // Add to glow team
        glowTeam.addEntry(display.getUniqueId().toString());
        
        // Track the display
        trackedDisplays.put(targetBlock.getLocation(), display.getUniqueId());
        
        player.sendMessage(Component.text("Spawned glowing shulker box display at offset (" + 
                offsetX + ", " + offsetY + ", " + offsetZ + ")!", NamedTextColor.GREEN));
    }
    
    private void handleColorCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bd.admin")) {
            sender.sendMessage(Component.text("You don't have permission to change colors!", NamedTextColor.RED));
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /bd color <color>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Available colors: " + 
                Arrays.stream(ChatColor.values())
                    .filter(ChatColor::isColor)
                    .map(c -> c.name().toLowerCase())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("none"), NamedTextColor.GRAY));
            return;
        }
        
        String colorName = args[1];
        try {
            ChatColor newColor = parseColor(colorName);
            currentColor = newColor;
            glowTeam.setColor(currentColor);  // live update
            saveConfigColor(currentColor);    // persist in config
            
            sender.sendMessage(Component.text("Glow color set to " + newColor.name() + "!", NamedTextColor.GREEN));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid color: " + colorName, NamedTextColor.RED));
            sender.sendMessage(Component.text("Available colors: " + 
                Arrays.stream(ChatColor.values())
                    .filter(ChatColor::isColor)
                    .map(c -> c.name().toLowerCase())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("none"), NamedTextColor.GRAY));
        }
    }
    
    private void handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("bd.admin")) {
            sender.sendMessage(Component.text("You don't have permission to reload!", NamedTextColor.RED));
            return;
        }
        
        reloadConfig();
        loadConfiguration();
        this.glowTeam = setupGlowTeam(currentColor);
        sender.sendMessage(Component.text("Configuration reloaded! New offsets: (" + 
                offsetX + ", " + offsetY + ", " + offsetZ + "), Color: " + currentColor.name(), NamedTextColor.GREEN));
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location blockLoc = event.getBlock().getLocation();
        UUID displayId = trackedDisplays.get(blockLoc);
        
        if (displayId != null) {
            removeDisplay(blockLoc, displayId);
        }
    }
    
    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getTo() == Material.AIR) {
            Location blockLoc = event.getBlock().getLocation();
            UUID displayId = trackedDisplays.get(blockLoc);
            
            if (displayId != null) {
                removeDisplay(blockLoc, displayId);
            }
        }
    }
    
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == this) {
            cleanupAllDisplays();
        }
    }
    
    private void removeDisplay(Location blockLoc, UUID displayId) {
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
        trackedDisplays.remove(blockLoc);
    }
    
    private void cleanupAllDisplays() {
        for (UUID displayId : trackedDisplays.values()) {
            // Remove entity
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
        }
        trackedDisplays.clear();
    }
    
    public static BlockDisplayPlugin getInstance() {
        return instance;
    }
} 