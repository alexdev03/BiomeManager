package me.cjcrafter.biomemanager.command;

public class WorldEditCommand {

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
