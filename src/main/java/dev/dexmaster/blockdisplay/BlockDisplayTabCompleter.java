package dev.dexmaster.blockdisplay;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BlockDisplayTabCompleter implements TabCompleter {
    
    private static final List<String> VALID_COLORS = Arrays.stream(ChatColor.values())
            .filter(ChatColor::isColor)
            .map(color -> color.name().toLowerCase())
            .collect(Collectors.toList());
    
    private static final List<String> SUBCOMMANDS = Arrays.asList("spawn", "color", "reload");
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // First argument - suggest subcommands
            for (String subcommand : SUBCOMMANDS) {
                if (subcommand.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(subcommand);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("color")) {
            // Second argument for color command - suggest colors
            for (String color : VALID_COLORS) {
                if (color.startsWith(args[1].toLowerCase())) {
                    completions.add(color);
                }
            }
        }
        
        return completions;
    }
} 