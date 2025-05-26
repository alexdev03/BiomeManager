package me.cjcrafter.biomemanager.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.cjcrafter.biomemanager.BiomeRegistry;
import me.cjcrafter.biomemanager.compatibility.BiomeWrapper;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class WorldEditCommand {

    public static void register(Commands commands) {
        LiteralArgumentBuilder<CommandSourceStack> biomemanagerCommand = LiteralArgumentBuilder.<CommandSourceStack>literal("/setcustombiome").requires(source -> source.getSender().hasPermission("biomemanager.commands.worldedit.setcustombiome"))
                .then(Command.getBiomeArgument())
                .executes(context -> {
                            // Get the player who executed the command

                            if (!(context.getSource().getSender() instanceof Player player)) {
                                throw new IllegalArgumentException("This command can only be executed by a player");
                            }

                            // Get the selected region from WorldEdit
                            LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
                            Region region;
                            try {
                                region = session.getSelection();
                                if (region.getWorld() == null)
                                    throw new IncompleteRegionException();
                            } catch (IncompleteRegionException e) {
                                player.sendRichMessage("<red>Please make a region selection first");
                                return 0;
                            }

                            // Set each block in the region to the new biome
                            World world = BukkitAdapter.adapt(region.getWorld());
                            Command.BiomeHolder holder = context.getArgument("biome", Command.BiomeHolder.class);
                            BiomeWrapper wrapper = BiomeRegistry.getInstance().get(holder.key());
                            if (wrapper == null) {
                                player.sendRichMessage("<red>Please make a region selection first");
                                return 0;
                            }

                            int count = 0;
                            for (BlockVector3 pos : region) {
                                Block block = world.getBlockAt(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
                                wrapper.setBiome(block);
                                count++;
                            }

                            player.sendRichMessage("<green>" + count + " blocks were affected");
                            return count;
                        }
                );

        commands.register(biomemanagerCommand.build());
    }

//    public static void register() {
//        CommandBuilder builder = new CommandBuilder("/setcustombiome")
//                .withPermission("biomemanager.commands.worldedit.setcustombiome")
//                .withDescription("Uses WorldEdit to fill custom biomes")
//                .withArguments(new Argument<>("biome", new BiomeArgumentType()))
//                .executes(CommandExecutor.player((sender, args) -> {
//                    LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(sender));
//                    Region region;
//                    try {
//                        region = session.getSelection();
//                        if (region.getWorld() == null)
//                            throw new IncompleteRegionException();
//                    } catch (IncompleteRegionException e) {
//                        sender.sendMessage(ChatColor.RED + "Please make a region selection first");
//                        return;
//                    }
//
//                    // Set each block in the region to the new biome
//                    World world = BukkitAdapter.adapt(region.getWorld());
//                    BiomeHolder holder = (BiomeHolder) args[0];
//                    BiomeWrapper wrapper = BiomeRegistry.getInstance().get(holder.key());
//                    if (wrapper == null) {
//                        sender.sendMessage(ChatColor.RED + "Unknown biome '" + holder.key() + "'");
//                        return;
//                    }
//
//                    int count = 0;
//                    for (BlockVector3 pos : region) {
//                        Block block = world.getBlockAt(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
//                        wrapper.setBiome(block);
//                        count++;
//                    }
//
//                    sender.sendMessage(ChatColor.GREEN + "" + count + " blocks were effected");
//                }));
//
//        builder.register();
//    }
}
