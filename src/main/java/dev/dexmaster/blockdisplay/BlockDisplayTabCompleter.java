package dev.dexmaster.blockdisplay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class BlockDisplayTabCompleter implements TabCompleter {
    
    private static final List<String> VALID_COLORS = Arrays.asList(
        "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
        "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
    );
    
    private static final List<String> SUBCOMMANDS = Arrays.asList("spawn", "reload");
    
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
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            // Second argument for spawn command - suggest colors
            for (String color : VALID_COLORS) {
                if (color.startsWith(args[1].toLowerCase())) {
                    completions.add(color);
                }
            }
        }
        
        return completions;
    }
} 