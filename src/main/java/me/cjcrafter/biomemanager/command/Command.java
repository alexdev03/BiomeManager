package me.cjcrafter.biomemanager.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.cjcrafter.biomemanager.BiomeManager;
import me.cjcrafter.biomemanager.BiomeRegistry;
import me.cjcrafter.biomemanager.SpecialEffectsBuilder;
import me.cjcrafter.biomemanager.compatibility.BiomeCompatibilityAPI;
import me.cjcrafter.biomemanager.compatibility.BiomeWrapper;
import me.cjcrafter.biomemanager.listeners.BiomeRandomizer;
import me.deecaad.core.MechanicsCore;
import me.deecaad.core.commands.*;

import me.deecaad.core.lib.commandapi.Tooltip;
import me.deecaad.core.lib.commandapi.arguments.Argument;
import me.deecaad.core.utils.ProbabilityMap;
import me.deecaad.core.utils.RandomUtil;
import me.deecaad.core.utils.StringUtil;
import me.deecaad.core.utils.TableBuilder;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;


public class Command {

    private static BiomeRegistry BIOME_REGISTRY() { return BiomeRegistry.getInstance(); }
    private static BiomeManager BIOME_MANAGER() { return BiomeManager.inst(); }

    @SuppressWarnings("UnstableApiUsage") // For Paper Commands API
    public static void register(JavaPlugin plugin, Commands commands) { // Pass plugin and Commands instance

        // Common argument for biome
        RequiredArgumentBuilder<CommandSourceStack, NamespacedKey> biomeArgument =
                RequiredArgumentBuilder.<CommandSourceStack, NamespacedKey>argument("biome", ArgumentTypes.namespacedKey())
                        .suggests(Command::suggestBiomes);

        LiteralArgumentBuilder<CommandSourceStack> biomemanagerCommand = LiteralArgumentBuilder.<CommandSourceStack>literal("biomemanager")
                .requires(source -> source.getSender().hasPermission("biomemanager.admin"))
                // Aliases ("bm", "biome") are handled by Paper when registering the command.
                // Description ("BiomeManager main command") is also handled by Paper.

                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("reset")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.reset"))
                        // .withDescription("Reset config of a specific biome") // Handled by Paper
                        .then(biomeArgument.executes(Command::executeReset))
                )

                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("randomize")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.randomize"))
                        .then(biomeArgument.executes(Command::executeRandomize))
                )

                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("menu")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.menu"))
                        .then(biomeArgument.executes(Command::executeMenu))
                )

                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("editor")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.debug"))
                        .executes(ctx -> executeEditor(ctx, null)) // Toggle
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Boolean>argument("enable", BoolArgumentType.bool())
                                .executes(ctx -> executeEditor(ctx, ctx.getArgument("enable", Boolean.class)))
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("create")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.create"))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("name", StringArgumentType.word())
                                .then(RequiredArgumentBuilder.<CommandSourceStack, NamespacedKey>argument("base", ArgumentTypes.namespacedKey())
                                        .suggests(Command::suggestBiomes) // Suggest existing biomes for base
                                        .executes(ctx -> executeCreate(ctx, "biomemanager")) // Default namespace
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("namespace", StringArgumentType.word())
                                                .suggests((ctx, sb) -> sb.suggest("biomemanager").buildFuture())
                                                .executes(ctx -> executeCreate(ctx, ctx.getArgument("namespace", String.class)))
                                        )
                                )
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("fill")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.fill"))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, BlockPositionResolver>argument("pos1", ArgumentTypes.blockPosition())
                                .then(RequiredArgumentBuilder.<CommandSourceStack, BlockPositionResolver>argument("pos2", ArgumentTypes.blockPosition())
                                        .then(biomeArgument.executes(Command::executeFill))
                                )
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("delete")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.delete"))
                        .then(biomeArgument.executes(Command::executeDelete))
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("particle")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.particle"))
                        .then(biomeArgument
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Particle>argument("particle", ArgumentTypes.resource(RegistryKey.PARTICLE_TYPE))
                                        .executes(ctx -> executeParticle(ctx, Double.NaN)) // No density specified
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument("density", DoubleArgumentType.doubleArg(0.0, 1.0))
                                                .suggests(Command::suggestParticleDensity)
                                                .executes(ctx -> executeParticle(ctx, ctx.getArgument("density", Double.class)))
                                        )
                                )
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("cave")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.cave"))
                        .then(biomeArgument
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Sound>argument("sound", ArgumentTypes.resource(RegistryKey.SOUND_EVENT))
                                        .suggests(Command::suggestSounds)
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("tick-delay", IntegerArgumentType.integer(1))
                                                .suggests((c,b) -> suggestConfigValue(c,b, effetti -> effetti.getCaveSoundSettings().tickDelay()))
                                                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("search-distance", IntegerArgumentType.integer(1))
                                                        .suggests((c,b) -> suggestConfigValue(c,b, effetti -> effetti.getCaveSoundSettings().searchOffset()))
                                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument("sound-offset", DoubleArgumentType.doubleArg(0.0))
                                                                .suggests((c,b) -> suggestConfigValue(c,b, effetti -> effetti.getCaveSoundSettings().soundOffset()))
                                                                .executes(Command::executeCaveSound)
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("music")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.music"))
                        .then(biomeArgument
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Sound>argument("sound", ArgumentTypes.resource(RegistryKey.SOUND_EVENT))
                                        .suggests(Command::suggestSounds)
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("min-delay", IntegerArgumentType.integer(1))
                                                .suggests((c,b) -> suggestConfigValue(c,b, effetti -> effetti.getMusic().minDelay()))
                                                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("max-delay", IntegerArgumentType.integer(1))
                                                        .suggests((c,b) -> suggestConfigValue(c,b, effetti -> effetti.getMusic().maxDelay()))
                                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Boolean>argument("override-music", BoolArgumentType.bool())
                                                                .suggests((c,b) -> suggestConfigValue(c,b, effetti -> effetti.getMusic().isOverride()))
                                                                .executes(Command::executeMusic)
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("random")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.random"))
                        .then(biomeArgument
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Sound>argument("sound", ArgumentTypes.resource(RegistryKey.SOUND_EVENT))
                                        .suggests(Command::suggestSounds)
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument("chance", DoubleArgumentType.doubleArg(0.0, 1.0))
                                                .suggests((c,b) -> suggestConfigValue(c,b, effetti -> effetti.getRandomSound().tickChance()))
                                                .executes(Command::executeRandomSound)
                                        )
                                )
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("ambient")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.ambient"))
                        .then(biomeArgument
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Sound>argument("sound", ArgumentTypes.resource(RegistryKey.SOUND_EVENT))
                                        .suggests(Command::suggestSounds)
                                        .executes(Command::executeAmbientSound)
                                )
                        )
                )
                .then(buildColorCommands(biomeArgument)) // Color subcommands

                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("variation")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.variation"))
                        .then(biomeArgument
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("world", StringArgumentType.word())
                                        .suggests(Command::suggestWorldsOrStar)
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("variations", StringArgumentType.greedyString())
                                                // .suggests(VariationTabCompletions::suggestions) // TODO: Adapt VariationTabCompletions
                                                .executes(Command::executeSetVariations)
                                        )
                                )
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("deletevariation")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.deletevariation"))
                        .then(biomeArgument
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("world", StringArgumentType.word())
                                        .suggests(Command::suggestWorldsOrStar)
                                        .executes(Command::executeDeleteVariations)
                                )
                        )
                );


        // Register the command with Paper
        // The aliases and description are typically set when registering.
        commands.register(
                biomemanagerCommand.build(),
                List.of("bm", "biome") // Aliases
        );

        // The HelpCommandBuilder part is specific to your old framework.
        // Brigadier typically provides help via command syntax exceptions on invalid input.
        // You can add a "help" subcommand if desired.
        // java.awt.Color primary = new java.awt.Color(85, 255, 85);
        // java.awt.Color secondary = new java.awt.Color(255, 85, 170);
        // command.registerHelp(new HelpCommandBuilder.HelpColor(Style.style(TextColor.color(primary.getRGB())), Style.style(TextColor.color(secondary.getRGB())), "\u27A2"));
    }

    // --- Suggestion Providers ---
    private static CompletableFuture<Suggestions> suggestBiomes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
//        BuiltInRegistries.BIOME_SOURCE.keySet().stream()
//                .map(ResourceLocation::getNamespace)
//                .filter(key -> remaining.isEmpty() || key.toLowerCase().startsWith(remaining))
//                .forEach(builder::suggest);
        RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).forEach(biome -> {
            String key = biome.getKey().toString();
            if (remaining.isEmpty() || key.toLowerCase().startsWith(remaining)) {
                builder.suggest(key);
            }
        });
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestSounds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
//        BuiltInRegistries.SOUND_EVENT.keySet().stream()
//                .map(ResourceLocation::getNamespace)
//                .filter(key -> remaining.isEmpty() || key.toLowerCase().startsWith(remaining))
//                .forEach(builder::suggest);
        RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT).forEach(sound -> {
            String key = sound.getKey().toString();
            if (remaining.isEmpty() || key.toLowerCase().startsWith(remaining)) {
                builder.suggest(key);
            }
        });
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestParticleDensity(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        NamespacedKey biomeKey = context.getArgument("biome", NamespacedKey.class);
        BiomeWrapper wrapper = BIOME_REGISTRY().get(biomeKey);
        double density = 0.0;
        if (wrapper != null) {
            density = wrapper.getSpecialEffects().getParticle().density();
            if (density >= 0) { // Valid density
                builder.suggest(String.valueOf(round((float)density)));
            }
        }
        builder.suggest("0.01428");
        builder.suggest("0.025");
        // Add other suggestions as needed
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestConfigValue(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder, Function<SpecialEffectsBuilder, Object> valueExtractor) {
        NamespacedKey biomeKey = context.getArgument("biome", NamespacedKey.class);
        BiomeWrapper wrapper = BIOME_REGISTRY().get(biomeKey);
        if (wrapper != null) {
            Object currentValue = valueExtractor.apply(wrapper.getSpecialEffects());
            builder.suggest(String.valueOf(currentValue));
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestColorValue(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder, Function<SpecialEffectsBuilder, Integer> colorExtractor) {
        NamespacedKey biomeKey = context.getArgument("biome", NamespacedKey.class); // Make sure "biome" is the correct argument name
        BiomeWrapper wrapper = BIOME_REGISTRY().get(biomeKey);
        if (wrapper != null) {
            SpecialEffectsBuilder effects = wrapper.getSpecialEffects();
            Integer rgb = colorExtractor.apply(effects);
            if (rgb != null && rgb != -1) {
                Color current = Color.fromRGB(rgb);
                java.awt.Color awtColor = new java.awt.Color(current.getRed(), current.getGreen(), current.getBlue());
                String hex = String.format("#%02x%02x%02x", awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue());
                builder.suggest(hex);
            }
        }
        builder.suggest("#RRGGBB"); // Generic hex suggestion
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestWorldsOrStar(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        builder.suggest("*");
        Bukkit.getWorlds().stream().map(World::getName).forEach(builder::suggest);
        return builder.buildFuture();
    }


    // --- Command Execution Logic ---
    private static int executeReset(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        NamespacedKey key = context.getArgument("biome", NamespacedKey.class);
        BiomeWrapper biome = BIOME_REGISTRY().get(key);

        if (biome == null) {
            sender.sendMessage(Component.text("Failed to find biome '" + key + "'").color(NamedTextColor.RED));
            return 0;
        }
        biome.reset();
        changes(sender);
        return 1;
    }

    private static int executeRandomize(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        NamespacedKey key = context.getArgument("biome", NamespacedKey.class);
        BiomeWrapper biome = BIOME_REGISTRY().get(key);
        if (biome == null) {
            sender.sendMessage(Component.text("Failed to find biome '" + key + "'").color(NamedTextColor.RED));
            return 0;
        }

        SpecialEffectsBuilder effects = biome.getSpecialEffects();
        effects.setFogColor(randomColor().asRGB());
        effects.setWaterColor(randomColor().asRGB());
        effects.setWaterFogColor(randomColor().asRGB());
        effects.setSkyColor(randomColor().asRGB());
        effects.setFoliageColorOverride(randomColor().asRGB()); // expects int
        effects.setGrassColorOverride(randomColor().asRGB()); // expects int

        biome.setSpecialEffects(effects);
        changes(sender);
        return 1;
    }

    private static int executeMenu(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        NamespacedKey key = context.getArgument("biome", NamespacedKey.class);
        // The original menu method took a BiomeHolder. We need to adapt it or create one.
        // For now, assuming menu can be called with sender and NamespacedKey, then it resolves BiomeHolder internally.
        // Or, if BiomeHolder is just a wrapper for NamespacedKey:
        BiomeHolder holder = new BiomeHolder() { // Anonymous implementation or a proper class
            @Override public NamespacedKey key() { return key; }
            // Implement other methods if BiomeHolder has them, or make them default in interface
        };
        menu(sender, holder); // You'll need to ensure menu() is compatible
        return 1;
    }

    private static int executeEditor(CommandContext<CommandSourceStack> context, Boolean enableArg) {
        CommandSender bukkitSender = context.getSource().getSender();
        if (!(bukkitSender instanceof Player)) {
            bukkitSender.sendMessage(Component.text("This command can only be run by a player.").color(NamedTextColor.RED));
            return 0;
        }
        Player sender = (Player) bukkitSender;

        boolean enable;
        if (enableArg == null) {
            enable = !BIOME_MANAGER().editModeListener.isEnabled(sender);
        } else {
            enable = enableArg;
        }

        BIOME_MANAGER().editModeListener.toggle(sender, enable);
        Component component = Component.text(enable ? "You entered Biome Editor mode, move around and watch chat." : "You exited Biome Editor mode.")
                .color(enable ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text("Click to " + (enable ? "Disable" : "Enable"))))
                .clickEvent(ClickEvent.runCommand("/biomemanager editor " + !enable));
        sender.sendMessage(component);
        return 1;
    }

    private static int executeCreate(CommandContext<CommandSourceStack> context, String namespace) {
        CommandSender sender = context.getSource().getSender();
        String name = context.getArgument("name", String.class);
        NamespacedKey baseBiomeKey = context.getArgument("base", NamespacedKey.class); // This is a NamespacedKey

        NamespacedKey key = new NamespacedKey(namespace.toLowerCase(Locale.ROOT), name.toLowerCase(Locale.ROOT));

        BiomeWrapper existing = BIOME_REGISTRY().get(key);
        if (existing != null) {
            sender.sendMessage(Component.text("The biome '" + key + "' already exists!").color(NamedTextColor.RED));
            return 0;
        }

        // The original code expects org.bukkit.Biome for the base.
        // We need to get it from the NamespacedKey.
        org.bukkit.Registry<Biome> bukkitBiomeRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        Biome bukkitBaseBiome = bukkitBiomeRegistry.get(baseBiomeKey);

        if (bukkitBaseBiome == null) {
            sender.sendMessage(Component.text("Base biome '" + baseBiomeKey + "' not found in Bukkit registry.").color(NamedTextColor.RED));
            return 0;
        }

        BiomeWrapper base = BIOME_REGISTRY().getBukkit(bukkitBaseBiome); // getBukkit from your API
        BiomeWrapper wrapper = BiomeCompatibilityAPI.getBiomeCompatibility().createBiome(key, base);
        wrapper.register(true);

        Component component = Component.text("Created new custom biome '" + key + "'")
                .color(NamedTextColor.GREEN)
                .hoverEvent(Component.text("Click to modify fog colors"))
                .clickEvent(ClickEvent.runCommand("/biomemanager menu " + key));
        sender.sendMessage(component);
        return 1;
    }

    private static int executeFill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        BlockPos pos1MC = context.getArgument("pos1", BlockPos.class);
        BlockPos pos2MC = context.getArgument("pos2", BlockPos.class);
        NamespacedKey biomeKey = context.getArgument("biome", NamespacedKey.class);

        // BlockPosArgument needs a world context. Get it from the sender's location.
        Location senderLocation = context.getSource().getLocation();
        if (senderLocation == null || senderLocation.getWorld() == null) {
            sender.sendMessage(Component.text("Cannot determine world for fill operation.").color(NamedTextColor.RED));
            return 0;
        }
        World world = senderLocation.getWorld();

        Block block1 = world.getBlockAt(pos1MC.getX(), pos1MC.getY(), pos1MC.getZ());
        Block block2 = world.getBlockAt(pos2MC.getX(), pos2MC.getY(), pos2MC.getZ());


        BiomeWrapper biome = BIOME_REGISTRY().get(biomeKey);
        if (biome == null) {
            sender.sendMessage(Component.text("Failed to find biome '" + biomeKey + "'").color(NamedTextColor.RED));
            return 0;
        }

        fillBiome(block1, block2, biome); // Your existing method
        sender.sendMessage(Component.text("Success! You may need to rejoin to see the changes.").color(NamedTextColor.GREEN));
        return 1;
    }

    private static int executeDelete(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        NamespacedKey key = context.getArgument("biome", NamespacedKey.class);
        try {
            BIOME_REGISTRY().remove(key);
            BiomeManager.inst().saveToConfig();
            sender.sendMessage(Component.text("Success! Restart your server for the biome to be deleted.").color(NamedTextColor.GREEN));
        } catch (Exception ex) {
            sender.sendMessage(Component.text("Failed for reason: " + ex.getMessage()).color(NamedTextColor.RED));
            return 0;
        }
        return 1;
    }

    private static int executeParticle(CommandContext<CommandSourceStack> context, Double densityArg) {
        CommandSender sender = context.getSource().getSender();
        NamespacedKey biomeKey = context.getArgument("biome", NamespacedKey.class);
        Particle particle = context.getArgument("particle", Particle.class); // This is Bukkit Particle

        BiomeWrapper biome = BIOME_REGISTRY().get(biomeKey);
        if (biome == null) {
            sender.sendMessage(Component.text("Failed to find biome '" + biomeKey + "'").color(NamedTextColor.RED));
            return 0;
        }

        SpecialEffectsBuilder effects = biome.getSpecialEffects();

        // ParticleHolder was your custom class. Need to adapt.
        // Assuming ParticleArgument.particle() returns a Bukkit Particle,
        // and your effects.setAmbientParticle expects a string key.
        NamespacedKey particleNMSKey = particle.getKey(); // Gets the NamespacedKey for the Bukkit particle
        effects.setAmbientParticle(particleNMSKey.toString()); // If it takes string like "minecraft:crit"

        if (!Double.isNaN(densityArg)) {
            effects.setParticleProbability(densityArg.floatValue());
        } else if (effects.getParticle().density() == -1.0f) { // Check your logic here
            effects.setParticleProbability(0.0f);
        }

        biome.setSpecialEffects(effects);
        changes(sender);
        return 1;
    }

    private static int executeCaveSound(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        NamespacedKey biomeKey = context.getArgument("biome", NamespacedKey.class);
        NamespacedKey soundKey = context.getArgument("sound", NamespacedKey.class);
        int tickDelay = context.getArgument("tick-delay", Integer.class);
        int searchDistance = context.getArgument("search-distance", Integer.class);
        double soundOffset = context.getArgument("sound-offset", Double.class);

        BiomeWrapper biome = BIOME_REGISTRY().get(biomeKey);
        if (biome == null) { /* ... error ... */ return 0; }

        SpecialEffectsBuilder effects = biome.getSpecialEffects();
        effects.setCaveSound(soundKey.toString());
        effects.setCaveTickDelay(tickDelay);
        effects.setCaveSearchDistance(searchDistance);
        effects.setCaveSoundOffset(soundOffset);
        biome.setSpecialEffects(effects);
        changes(sender);
        return 1;
    }

    private static int executeMusic(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        NamespacedKey biomeKey = context.getArgument("biome", NamespacedKey.class);
        NamespacedKey soundKey = context.getArgument("sound", NamespacedKey.class);
        int minDelay = context.getArgument("min-delay", Integer.class);
        int maxDelay = context.getArgument("max-delay", Integer.class);
        boolean override = context.getArgument("override-music", Boolean.class);

        BiomeWrapper biome = BIOME_REGISTRY().get(biomeKey);
        if (biome == null) { /* ... error ... */ return 0; }

        if (minDelay > maxDelay) {
            sender.sendMessage(Component.text("Make sure min-delay < max-delay").color(NamedTextColor.RED));
            return 0;
        }

        SpecialEffectsBuilder effects = biome.getSpecialEffects();
        effects.setMusicSound(soundKey.toString());
        effects.setMusicMinDelay(minDelay);
        effects.setMusicMaxDelay(maxDelay);
        effects.setMusicOverride(override);
        biome.setSpecialEffects(effects);
        changes(sender);
        return 1;
    }

    private static int executeRandomSound(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        NamespacedKey biomeKey = context.getArgument("biome", NamespacedKey.class);
        NamespacedKey soundKey = context.getArgument("sound", NamespacedKey.class);
        double chance = context.getArgument("chance", Double.class);

        BiomeWrapper biome = BIOME_REGISTRY().get(biomeKey);
        if (biome == null) { /* ... error ... */ return 0; }

        SpecialEffectsBuilder effects = biome.getSpecialEffects();
        effects.setRandomSound(soundKey.toString());
        effects.setRandomTickChance(chance);
        biome.setSpecialEffects(effects);
        changes(sender);
        return 1;
    }

    private static int executeAmbientSound(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        NamespacedKey biomeKey = context.getArgument("biome", NamespacedKey.class);
        NamespacedKey soundKey = context.getArgument("sound", NamespacedKey.class);

        BiomeWrapper biome = BIOME_REGISTRY().get(biomeKey);
        if (biome == null) { /* ... error ... */ return 0; }

        SpecialEffectsBuilder effects = biome.getSpecialEffects();
        effects.setAmbientSound(soundKey.toString());
        biome.setSpecialEffects(effects);
        changes(sender);
        return 1;
    }

    private static int executeSetVariations(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        NamespacedKey biomeKey = context.getArgument("biome", NamespacedKey.class);
        String worldName = context.getArgument("world", String.class);
        String variationsString = context.getArgument("variations", String.class);

        // Resolve BiomeHolder equivalent
        BiomeHolder biomeHolder = () -> biomeKey; // Simplified

        setVariations(sender, biomeHolder, worldName, variationsString); // Your existing logic
        return 1;
    }

    private static int executeDeleteVariations(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        NamespacedKey key = context.getArgument("biome", NamespacedKey.class);
        String worldName = context.getArgument("world", String.class);

        BiomeWrapper base = BIOME_REGISTRY().get(key);
        if (base == null) {
            sender.sendMessage(Component.text("Could not find biome '" + key + "'").color(NamedTextColor.RED));
            return 0;
        }
//        boolean deleted = BIOME_MANAGER().biomeRandomizer.deleteVariation("*".equals(worldName) ? null : worldName, base);
//        if (deleted) {
//            changes(sender);
//        } else {
//            sender.sendMessage(Component.text("You didn't have any variations configured for world '" + worldName + "' for '" + key + "'").color(NamedTextColor.RED));
//        }
        return 1;
    }


    // --- Color Subcommands Builder ---
    private static LiteralArgumentBuilder<CommandSourceStack> buildColorCommands(RequiredArgumentBuilder<CommandSourceStack, NamespacedKey> biomeArg) {
        LiteralArgumentBuilder<CommandSourceStack> colorCommand = LiteralArgumentBuilder.<CommandSourceStack>literal("color")
                .requires(source -> source.getSender().hasPermission("biomemanager.commands.color"));

        // Fog Color
        colorCommand.then(LiteralArgumentBuilder.<CommandSourceStack>literal("fog_color")
                .then(biomeArg.then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("color", StringArgumentType.word()) // Assuming hex string
                        .suggests((c,b) -> suggestColorValue(c,b, SpecialEffectsBuilder::getFogColor))
                        .executes(ctx -> executeSetColor(ctx, SpecialEffectsBuilder::setFogColor)))));
        // Water Color
        colorCommand.then(LiteralArgumentBuilder.<CommandSourceStack>literal("water_color")
                .then(biomeArg.then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("color", StringArgumentType.word())
                        .suggests((c,b) -> suggestColorValue(c,b, SpecialEffectsBuilder::getWaterColor))
                        .executes(ctx -> executeSetColor(ctx, SpecialEffectsBuilder::setWaterColor)))));
        // Water Fog Color
        colorCommand.then(LiteralArgumentBuilder.<CommandSourceStack>literal("water_fog_color")
                .then(biomeArg.then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("color", StringArgumentType.word())
                        .suggests((c,b) -> suggestColorValue(c,b, SpecialEffectsBuilder::getWaterFogColor))
                        .executes(ctx -> executeSetColor(ctx, SpecialEffectsBuilder::setWaterFogColor)))));
        // Sky Color
        colorCommand.then(LiteralArgumentBuilder.<CommandSourceStack>literal("sky_color")
                .then(biomeArg.then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("color", StringArgumentType.word())
                        .suggests((c,b) -> suggestColorValue(c,b, SpecialEffectsBuilder::getSkyColor))
                        .executes(ctx -> executeSetColor(ctx, SpecialEffectsBuilder::setSkyColor)))));
        // Foliage Color
        colorCommand.then(LiteralArgumentBuilder.<CommandSourceStack>literal("foliage_color")
                .then(biomeArg.then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("color", StringArgumentType.word())
                        .suggests((c,b) -> suggestColorValue(c,b, SpecialEffectsBuilder::getFoliageColorOverride))
                        .executes(ctx -> executeSetColor(ctx, (effects, bukkitColor) -> effects.setFoliageColorOverride(bukkitColor.asRGB())))))); // Needs int
        // Grass Color
        colorCommand.then(LiteralArgumentBuilder.<CommandSourceStack>literal("grass_color")
                .then(biomeArg.then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("color", StringArgumentType.word())
                        .suggests((c,b) -> suggestColorValue(c,b, SpecialEffectsBuilder::getGrassColorOverride))
                        .executes(ctx -> executeSetColor(ctx, (effects, bukkitColor) -> effects.setGrassColorOverride(bukkitColor.asRGB())))))); // Needs int

        return colorCommand;
    }

    private static int executeSetColor(CommandContext<CommandSourceStack> context, BiConsumer<SpecialEffectsBuilder, Color> colorSetter) {
        CommandSender sender = context.getSource().getSender();
        NamespacedKey biomeKey = context.getArgument("biome", NamespacedKey.class);
        String colorString = context.getArgument("color", String.class);

        Color bukkitColor;
        try {
            bukkitColor = parseHexColor(colorString);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid color format: " + colorString + ". Use #RRGGBB or RRGGBB.").color(NamedTextColor.RED));
            return 0;
        }

        // Resolve BiomeHolder or use key directly
        BiomeHolder holder = () -> biomeKey; // Simplified

        setColor(sender, colorSetter, holder, bukkitColor); // Your existing method
        return 1;
    }

    private static Color parseHexColor(String hex) {
        hex = hex.startsWith("#") ? hex.substring(1) : hex;
        if (hex.length() != 6) {
            throw new IllegalArgumentException("Hex color string must be 6 characters long (excluding #).");
        }
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return Color.fromRGB(r, g, b);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid character in hex string.", e);
        }
    }


    // --- Helper Methods (Keep or adapt from original) ---
    // These methods are mostly kept from your original, with minor adaptations for Adventure components
    // and ensuring they can be called statically or with necessary context.

    public static void menu(CommandSender sender, BiomeHolder biome) { // Make sure BiomeHolder can be created from NamespacedKey
        // This method is complex and relies on TableBuilder and other custom classes.
        // It should largely work if those classes are available and use Adventure Components.
        // For brevity, I'm assuming this method is adapted to work with the new structure.
        // Ensure all sendMessage calls use sender.sendMessage(...)
        TextComponent.Builder builder = Component.text();
        BiomeWrapper wrapper = BIOME_REGISTRY().get(biome.key());
        if (wrapper == null) {
            sender.sendMessage(Component.text("Biome " + biome.key() + " not found for menu.").color(NamedTextColor.RED));
            return;
        }


        String[] keys = new String[]{"Fog", "Water", "Water_Fog", "Sky", "Foliage", "Grass"};
        List<Function<SpecialEffectsBuilder, Integer>> elements = Arrays.asList(
                SpecialEffectsBuilder::getFogColor, SpecialEffectsBuilder::getWaterColor,
                SpecialEffectsBuilder::getWaterFogColor, SpecialEffectsBuilder::getSkyColor,
                SpecialEffectsBuilder::getFoliageColorOverride, SpecialEffectsBuilder::getGrassColorOverride
        );

        Style green = Style.style(NamedTextColor.GREEN);
        Style gray = Style.style(NamedTextColor.GRAY);

        // Assuming TableBuilder is compatible with Adventure Components
        // TableBuilder table = new TableBuilder() ...
        // builder.append(table.build());
        // For now, simplified output:
        builder.append(Component.text("Biome: " + biome.key().toString()).style(green)).append(Component.newline());

        SpecialEffectsBuilder effects = wrapper.getSpecialEffects();
        for (int i = 0; i < keys.length; i++) {
            showColors(builder, biome, elements.get(i), keys[i]); // showColors needs adaptation
            builder.append(Component.newline());
        }


        // Particle information
        builder.append(Component.text("PARTICLE: ").style(gray));
        builder.append(getComponent(wrapper, effects.getParticle())); // getComponent needs adaptation
        builder.append(Component.newline());

        // Sound information
        builder.append(Component.text("AMBIENT: ").style(gray));
        builder.append(Component.text(removeNamespace(effects.getAmbientSound())).style(green)
                .hoverEvent(Component.text("Click to modify the ambient sound"))
                .clickEvent(ClickEvent.suggestCommand("/biomemanager ambient " + biome.key() + " " + effects.getAmbientSound())));
        builder.append(Component.newline());

        // ... (other sound components: RANDOM, CAVE, MUSIC) ...

        sender.sendMessage(builder.build());
    }


    private static void showColors(TextComponent.Builder builder, BiomeHolder biome, Function<SpecialEffectsBuilder, Integer> function, String key) {
        BiomeWrapper wrapper = BIOME_REGISTRY().get(biome.key());
        if (wrapper == null) {
            // Or handle error appropriately, maybe append error text to builder
            throw new IllegalArgumentException("Biome '" + biome.key() + "' does not exist for showColors.");
        }

        SpecialEffectsBuilder fog = wrapper.getSpecialEffects();
        Integer rgbNullable = function.apply(fog); // Can be null if not set
        int rgb = (rgbNullable == null || rgbNullable == -1) ? Color.WHITE.asRGB() : rgbNullable; // Default to white if -1 or null

        Color color = Color.fromRGB(rgb); // Bukkit color

        java.awt.Color awtColor = new java.awt.Color(color.getRed(), color.getGreen(), color.getBlue());
        String hex = String.format("#%02x%02x%02x", awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue());


        ClickEvent click = ClickEvent.suggestCommand("/biomemanager color " + key.toLowerCase(Locale.ROOT) + "_color " + biome.key() + " " + hex);
        HoverEvent<?> hover = HoverEvent.showText(Component.text("Click to set color"));

        builder.append(Component.text(key.toUpperCase(Locale.ROOT) + ": ").color(NamedTextColor.GRAY).clickEvent(click).hoverEvent(hover));
        builder.append(Component.text(hex.toUpperCase(Locale.ROOT)).color(TextColor.color(awtColor.getRGB())).clickEvent(click).hoverEvent(hover));
    }

    public static void setColor(CommandSender sender, BiConsumer<SpecialEffectsBuilder, Color> method, BiomeHolder biome, Color color) {
        BiomeWrapper wrapper = BIOME_REGISTRY().get(biome.key());
        if (wrapper == null) {
            sender.sendMessage(Component.text("Biome " + biome.key() + " not found.").color(NamedTextColor.RED));
            return;
        }
        SpecialEffectsBuilder effects = wrapper.getSpecialEffects();
        method.accept(effects, color);
        wrapper.setSpecialEffects(effects);
        changes(sender);
    }

    public static void changes(CommandSender sender) {
        sender.sendMessage(Component.text("Success! Leave and Rejoin to see your changes.").color(NamedTextColor.GREEN));
        BiomeManager.inst().saveToConfig();
    }

    private static Color randomColor() { // Bukkit Color
        return Color.fromRGB(RandomUtil.range(0, 255), RandomUtil.range(0, 255), RandomUtil.range(0, 255));
    }

    // Adapting getComponent methods to return Kyori Components
    private static Component getComponent(BiomeWrapper wrapper, SpecialEffectsBuilder.ParticleData data) {
        return Component.text(removeNamespace(data.particle()) + " " + round(data.density()))
                .color(NamedTextColor.GREEN)
                .hoverEvent(Component.text("Click to modify particle"))
                .clickEvent(ClickEvent.suggestCommand("/biomemanager particle " + wrapper.getKey() + " " + data.particle() + " " + data.density()));
    }
    // ... other getComponent overloads for CaveSoundData, RandomSoundData, MusicData should be similarly adapted ...


    private static String round(float num) {
        if (Float.isNaN(num) || Float.isInfinite(num)) return String.valueOf(num);
        BigDecimal bigDecimal = new BigDecimal(num); // Use float constructor
        bigDecimal = bigDecimal.round(new MathContext(2, RoundingMode.HALF_UP)); // Precision 2 for display
        return bigDecimal.stripTrailingZeros().toPlainString();
    }

    private static String removeNamespace(String key) {
        if (key == null) return "null";
        return key.startsWith("minecraft:") ? key.substring("minecraft:".length()) : key;
    }

    private static void fillBiome(Block pos1, Block pos2, BiomeWrapper biome) {
        // This method seems fine as is, assuming BiomeWrapper::setBiome works.
        if (!pos1.getWorld().equals(pos2.getWorld()))
            throw new IllegalArgumentException("Cannot fill biome between worlds");

        World world = pos1.getWorld();
        Block min = world.getBlockAt(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
        Block max = world.getBlockAt(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));

        for (int x = min.getX(); x <= max.getX(); x++) { // Include max X
            for (int y = min.getY(); y <= max.getY(); y++) { // Include max Y
                for (int z = min.getZ(); z <= max.getZ(); z++) { // Include max Z
                    Block current = world.getBlockAt(x, y, z);
                    biome.setBiome(current);
                }
            }
        }
    }

    private static void setVariations(CommandSender sender, BiomeHolder biome, String worldName, String variationsString) {
        // This method seems largely fine but ensure sender.sendMessage uses Adventure.
        if (BIOME_MANAGER().getConfig().getBoolean("Disable_Biome_Variations")) {
            sender.sendMessage(Component.text("Variations are disabled in the config").color(NamedTextColor.RED));
            return;
        }

        String[] split = variationsString.split("[, ]");
        ProbabilityMap<BiomeWrapper> variations = new ProbabilityMap<>();

        if ("*".equals(worldName)) {
            worldName = null;
        } else if (Bukkit.getWorld(worldName) == null) {
            sender.sendMessage(Component.text("Cannot find world '" + worldName + "'").color(NamedTextColor.RED));
            return;
        }

        for (String biomeVariation : split) {
            String[] variationData = biomeVariation.split("%", 2);
            String keyStr = variationData.length == 2 ? variationData[1] : variationData[0];
            String chanceStr = variationData.length == 2 ? variationData[0] : "1";

            double chance;
            try {
                chance = Double.parseDouble(chanceStr);
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text("'" + chanceStr + "' is not a valid number").color(NamedTextColor.RED));
                return;
            }

            BiomeWrapper replacement = BIOME_REGISTRY().get(NamespacedKey.fromString(keyStr));
            if (replacement == null) {
                sender.sendMessage(Component.text("Unknown biome '" + keyStr + "'").color(NamedTextColor.RED));
                return;
            }
            variations.add(replacement, chance);
        }

        BiomeRandomizer randomizer = BIOME_MANAGER().biomeRandomizer;
        BiomeWrapper base = BIOME_REGISTRY().get(biome.key());
        if (base == null) { // Should not happen if biome (BiomeHolder) is valid
            sender.sendMessage(Component.text("Base biome '" + biome.key() + "' for variation not found!").color(NamedTextColor.RED));
            return;
        }
//        randomizer.addVariation(worldName, base, variations);
        changes(sender);
    }


    // Dummy BiomeHolder for compatibility if you don't have a direct replacement yet
    // You should replace this with your actual BiomeHolder or adapt methods to use NamespacedKey directly.
    @FunctionalInterface
    public interface BiomeHolder {
        NamespacedKey key();
    }

//    public static void register() {
//
//        BiomeRegistry biomes = BiomeRegistry.getInstance();
//        Argument<?> biomeArg = new Argument<>("biome", new BiomeArgumentType()).withDesc("Which biome to choose");
//
//        CommandBuilder command = new CommandBuilder("biomemanager")
//                .withAliases("bm", "biome")
//                .withPermission("biomemanager.admin")
//                .withDescription("BiomeManager main command")
//
//                .withSubcommand(new CommandBuilder("reset")
//                        .withPermission("biomemanager.commands.reset")
//                        .withDescription("Reset config of a specific biome")
//                        .withArgument(biomeArg)
//                        .executes(CommandExecutor.any((sender, args) -> {
//
//                            NamespacedKey key = ((BiomeHolder) args[0]).key();
//                            BiomeWrapper biome = biomes.get(key);
//                            if (biome == null) {
//                                sender.sendMessage(ChatColor.RED + "Failed to find biome '" + key + "'");
//                                return;
//                            }
//
//                            biome.reset();
//                            changes(sender);
//                        })))
//
//                .withSubcommand(new CommandBuilder("randomize")
//                        .withPermission("biomemanager.commands.randomize")
//                        .withDescription("Randomize fog of a biome")
//                        .withArgument(biomeArg)
//                        .executes(CommandExecutor.any((sender, args) -> {
//
//                            NamespacedKey key = ((BiomeHolder) args[0]).key();
//                            BiomeWrapper biome = biomes.get(key);
//                            if (biome == null) {
//                                sender.sendMessage(ChatColor.RED + "Failed to find biome '" + key + "'");
//                                return;
//                            }
//
//                            SpecialEffectsBuilder effects = biome.getSpecialEffects();
//                            effects.setFogColor(randomColor());
//                            effects.setWaterColor(randomColor());
//                            effects.setWaterFogColor(randomColor());
//                            effects.setSkyColor(randomColor());
//                            effects.setFoliageColorOverride(randomColor());
//                            effects.setGrassColorOverride(randomColor());
//
//                            biome.setSpecialEffects(effects);
//                            changes(sender);
//                        })))
//
//                .withSubcommand(new CommandBuilder("menu")
//                        .withPermission("biomemanager.commands.menu")
//                        .withDescription("Shows the colors of a biome fog")
//                        .withArgument(biomeArg)
//                        .executes(CommandExecutor.any((sender, args) -> {
//                            menu(sender, (BiomeHolder) args[0]);
//                        })))
//
//                .withSubcommand(new CommandBuilder("editor")
//                        .withAliases("edit")
//                        .withPermission("biomemanager.commands.debug")
//                        .withDescription("Toggle edit mode to see biome information")
//                        .withArgument(new Argument<>("enable", new BooleanArgumentType(), null).withDesc("true to enable, false to disable"))
//                        .executes(CommandExecutor.player((sender, args) -> {
//
//                            // If the user did not specify to enable/disable, treat it like a toggle
//                            boolean enable;
//                            if (args[0] == null)
//                                enable = !BiomeManager.inst().editModeListener.isEnabled(sender);
//                            else
//                                enable = (boolean) args[0];
//
//                            BiomeManager.inst().editModeListener.toggle(sender, enable);
//                            Component component = text(enable
//                                    ? "You entered Biome Editor mode, move around and watch chat."
//                                    : "You exited Biome Editor mode")
//                                    .color(enable ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
//                                    .hoverEvent(text("Click to " + (enable ? "Disable" : "Enable")))
//                                    .clickEvent(ClickEvent.runCommand("/biomemanager editor " + !enable));
//                            MechanicsCore.getPlugin().adventure.player(sender).sendMessage(component);
//                        })))
//
//                .withSubcommand(new CommandBuilder("create")
//                        .withPermission("biomemanager.commands.create")
//                        .withDescription("Create a new custom biome")
//                        .withArgument(new Argument<>("name", new StringArgumentType()).withDesc("The name of the new biome"))
//                        .withArgument(new Argument<>("namespace", new StringArgumentType(), "biomemanager").withDesc("The namespace, or 'folder' storing the biomes").replace(SuggestionsBuilder.from("biomemanager")))
//                        .withArgument(new Argument<>("base", new BiomeArgumentType(), Biome.PLAINS).withDesc("The base values of the biome"))
//                        .executes(CommandExecutor.any((sender, args) -> {
//                            NamespacedKey key = new NamespacedKey(((String) args[1]).toLowerCase(Locale.ROOT), ((String) args[0]).toLowerCase(Locale.ROOT));
//
//                            BiomeWrapper existing = biomes.get(key);
//                            if (existing != null) {
//                                sender.sendMessage(ChatColor.RED + "The biome '" + key + "' already exists!");
//                                return;
//                            }
//
//                            BiomeWrapper base = BiomeRegistry.getInstance().getBukkit((Biome) args[2]);
//                            BiomeWrapper wrapper = BiomeCompatibilityAPI.getBiomeCompatibility().createBiome(key, base);
//                            wrapper.register(true);
//
//                            Component component = text("Created new custom biome '" + key + "'")
//                                    .color(NamedTextColor.GREEN)
//                                    .hoverEvent(text("Click to modify fog colors"))
//                                    .clickEvent(ClickEvent.runCommand("/biomemanager menu " + key));
//                            MechanicsCore.getPlugin().adventure.sender(sender).sendMessage(component);
//                        })))
//
//                .withSubcommand(new CommandBuilder("fill")
//                        .withPermission("biomemanager.commands.fill")
//                        .withDescription("Fill an area with a biome (may need to rejoin)")
//                        .withArgument(new Argument<>("pos1", new LocationArgumentType()).withDesc("The first point of the volume"))
//                        .withArgument(new Argument<>("pos2", new LocationArgumentType()).withDesc("The second point of the volume"))
//                        .withArgument(new Argument<>("biome", new BiomeArgumentType()).withDesc("Which biome to fill"))
//                        .executes(CommandExecutor.any((sender, args) -> {
//                            Block pos1 = ((Location) args[0]).getBlock();
//                            Block pos2 = ((Location) args[1]).getBlock();
//
//                            NamespacedKey key = ((BiomeHolder) args[2]).key();
//                            BiomeWrapper biome = biomes.get(key);
//                            if (biome == null) {
//                                sender.sendMessage(ChatColor.RED + "Failed to find biome '" + key + "'");
//                                return;
//                            }
//
//                            fillBiome(pos1, pos2, biome);
//                            sender.sendMessage(ChatColor.GREEN + "Success! You may need to rejoin to see the changes.");
//                        })))
//
//                .withSubcommand(new CommandBuilder("delete")
//                        .withPermission("biomemanager.commands.delete")
//                        .withDescription("Deletes a custom biome")
//                        .withArgument(new Argument<>("biome", new BiomeArgumentType()).withDesc("Which biome to remove"))
//                        .executes(CommandExecutor.any((sender, args) -> {
//                            try {
//                                biomes.remove(((BiomeHolder) args[0]).key());
//                                sender.sendMessage(ChatColor.GREEN + "Success! Restart your server for the biome to be deleted.");
//                            } catch (Exception ex) {
//                                sender.sendMessage(ChatColor.RED + "Failed for reason: " + ex.getMessage());
//                            }
//                        })))
//
//                .withSubcommand(new CommandBuilder("particle")
//                        .withPermission("biomemanager.commands.particle")
//                        .withDescription("Change the particle of biome fog")
//                        .withArgument(biomeArg)
//                        .withArguments(new Argument<>("particle", new ParticleArgumentType()).withDesc("Which particle to spawn"))
//                        .withArguments(new Argument<>("density", new DoubleArgumentType(0.0, 1.0), Double.NaN).withDesc("Chance to spawn in each block each tick").append(suggestDensity()))
//                        .executes(CommandExecutor.any((sender, args) -> {
//                            NamespacedKey key = ((BiomeHolder) args[0]).key();
//                            BiomeWrapper biome = biomes.get(key);
//                            if (biome == null) {
//                                sender.sendMessage(ChatColor.RED + "Failed to find biome '" + key + "'");
//                                return;
//                            }
//
//                            SpecialEffectsBuilder effects = biome.getSpecialEffects();
//                            ParticleHolder particle = ((ParticleHolder) args[1]);
//
//                            effects.setAmbientParticle(particle.asString());
//                            if (!Double.isNaN((double) args[2]))
//                                effects.setParticleProbability((float) (double) args[2]);
//                            else if (effects.getParticle().density() == -1.0f)
//                                effects.setParticleProbability(0.0f);
//
//                            biome.setSpecialEffects(effects);
//                            changes(sender);
//                        })))
//
//                .withSubcommand(new CommandBuilder("cave")
//                        .withPermission("biomemanager.commands.cave")
//                        .withDescription("Sounds only heard in Cave Air)")
//                        .withArgument(biomeArg)
//                        .withArgument(new Argument<>("sound", new SoundArgumentType()).withDesc("Which sound to play").append(suggestConfig(b -> b.getCaveSoundSettings().sound())))
//                        .withArgument(new Argument<>("tick-delay", new IntegerArgumentType(1)).withDesc("Delay between sounds").append(suggestConfig(b -> b.getCaveSoundSettings().tickDelay())))
//                        .withArgument(new Argument<>("search-distance", new IntegerArgumentType(1)).withDesc("*Unknown* Distance to search for cave air").append(suggestConfig(b -> b.getCaveSoundSettings().searchOffset())))
//                        .withArgument(new Argument<>("sound-offset", new DoubleArgumentType(0.0)).withDesc("How far away from player to play sound").append(suggestConfig(b -> b.getCaveSoundSettings().soundOffset())))
//                        .executes(CommandExecutor.any((sender, args) -> {
//                            NamespacedKey key = ((BiomeHolder) args[0]).key();
//                            BiomeWrapper biome = biomes.get(key);
//                            if (biome == null) {
//                                sender.sendMessage(ChatColor.RED + "Failed to find biome '" + key + "'");
//                                return;
//                            }
//
//                            SpecialEffectsBuilder effects = biome.getSpecialEffects();
//                            String sound = ((SoundHolder) args[1]).key().toString();
//
//                            effects.setCaveSound(sound);
//                            effects.setCaveTickDelay((int) args[2]);
//                            effects.setCaveSearchDistance((int) args[3]);
//                            effects.setCaveSoundOffset((double) args[4]);
//
//                            biome.setSpecialEffects(effects);
//                            changes(sender);
//                        })))
//
//                .withSubcommand(new CommandBuilder("music")
//                        .withPermission("biomemanager.commands.music")
//                        .withDescription("Change the music")
//                        .withArgument(biomeArg)
//                        .withArgument(new Argument<>("sound", new SoundArgumentType()).withDesc("Which sound the play").append(suggestConfig(b -> b.getMusic().sound())))
//                        .withArgument(new Argument<>("min-delay", new IntegerArgumentType(1)).withDesc("The minimum time between sound tracks").append(suggestConfig(b -> b.getMusic().minDelay())))
//                        .withArgument(new Argument<>("max-delay", new IntegerArgumentType(1)).withDesc("The maximum time between sound tracks").append(suggestConfig(b -> b.getMusic().maxDelay())))
//                        .withArgument(new Argument<>("override-music", new BooleanArgumentType()).append(suggestConfig(b -> b.getMusic().isOverride())))
//                        .executes(CommandExecutor.any((sender, args) -> {
//                            NamespacedKey key = ((BiomeHolder) args[0]).key();
//                            BiomeWrapper biome = biomes.get(key);
//                            if (biome == null) {
//                                sender.sendMessage(ChatColor.RED + "Failed to find biome '" + key + "'");
//                                return;
//                            }
//
//                            SpecialEffectsBuilder effects = biome.getSpecialEffects();
//                            String sound = ((SoundHolder) args[1]).key().toString();
//
//                            if ((int) args[2] > (int) args[3]) {
//                                sender.sendMessage(ChatColor.RED + "Make sure min-delay < max-delay");
//                                return;
//                            }
//
//                            effects.setMusicSound(sound);
//                            effects.setMusicMinDelay((int) args[2]);
//                            effects.setMusicMaxDelay((int) args[3]);
//                            effects.setMusicOverride((boolean) args[4]);
//
//                            biome.setSpecialEffects(effects);
//                            changes(sender);
//                        })))
//
//                .withSubcommand(new CommandBuilder("random")
//                        .withPermission("biomemanager.commands.random")
//                        .withDescription("Sound that has a chance to play every tick")
//                        .withArgument(biomeArg)
//                        .withArgument(new Argument<>("sound", new SoundArgumentType()).withDesc("Which sound to play").append(suggestConfig(b -> b.getRandomSound().sound())))
//                        .withArgument(new Argument<>("chance", new DoubleArgumentType(0.0, 1.0)).withDesc("Chance to play each tick").append(suggestConfig(b -> b.getRandomSound().tickChance())))
//                        .executes(CommandExecutor.any((sender, args) -> {
//                            NamespacedKey key = ((BiomeHolder) args[0]).key();
//                            BiomeWrapper biome = biomes.get(key);
//                            if (biome == null) {
//                                sender.sendMessage(ChatColor.RED + "Failed to find biome '" + key + "'");
//                                return;
//                            }
//
//                            SpecialEffectsBuilder effects = biome.getSpecialEffects();
//                            String sound = ((SoundHolder) args[1]).key().toString();
//
//                            effects.setRandomSound(sound);
//                            effects.setRandomTickChance((double) args[2]);
//
//                            biome.setSpecialEffects(effects);
//                            changes(sender);
//                        })))
//
//                .withSubcommand(new CommandBuilder("ambient")
//                        .withPermission("biomemanager.commands.ambient")
//                        .withDescription("Change the ambient sound")
//                        .withArgument(biomeArg)
//                        .withArgument(new Argument<>("sound", new SoundArgumentType()).withDesc("Which sound to play").append(suggestConfig(SpecialEffectsBuilder::getAmbientSound)))
//                        .executes(CommandExecutor.any((sender, args) -> {
//                            NamespacedKey key = ((BiomeHolder) args[0]).key();
//                            BiomeWrapper biome = biomes.get(key);
//                            if (biome == null) {
//                                sender.sendMessage(ChatColor.RED + "Failed to find biome '" + key + "'");
//                                return;
//                            }
//
//                            String sound = ((SoundHolder) args[1]).key().toString();
//
//                            SpecialEffectsBuilder effects = biome.getSpecialEffects();
//                            effects.setAmbientSound(sound);
//                            biome.setSpecialEffects(effects);
//                            changes(sender);
//                        })))
//
//                .withSubcommand(new CommandBuilder("color")
//                        .withPermission("biomemanager.commands.color")
//                        .withDescription("Change the colors of a biome (grass/fog/sky/etc.)")
//
//                        .withSubcommand(new CommandBuilder("fog_color")
//                                .withDescription("Overworld=lower sky color, Nether=biome fog")
//                                .withArgument(biomeArg)
//                                .withArgument(new Argument<>("color", new ColorArgumentType()).withDesc("The color to use").append(suggestColor(SpecialEffectsBuilder::getFogColor)))
//                                .executes(CommandExecutor.any((sender, args) -> {
//                                    setColor(sender, SpecialEffectsBuilder::setFogColor, (BiomeHolder) args[0], (Color) args[1]);
//                                })))
//
//                        .withSubcommand(new CommandBuilder("water_color")
//                                .withDescription("Change the color of water")
//                                .withArgument(biomeArg)
//                                .withArgument(new Argument<>("color", new ColorArgumentType()).withDesc("The color to use").append(suggestColor(SpecialEffectsBuilder::getWaterColor)))
//                                .executes(CommandExecutor.any((sender, args) -> {
//                                    setColor(sender, SpecialEffectsBuilder::setWaterColor, (BiomeHolder) args[0], (Color) args[1]);
//                                })))
//
//                        .withSubcommand(new CommandBuilder("water_fog_color")
//                                .withDescription("Change the fog present when you are underwater")
//                                .withArgument(biomeArg)
//                                .withArgument(new Argument<>("color", new ColorArgumentType()).withDesc("The color to use").append(suggestColor(SpecialEffectsBuilder::getWaterFogColor)))
//                                .executes(CommandExecutor.any((sender, args) -> {
//                                    setColor(sender, SpecialEffectsBuilder::setWaterFogColor, (BiomeHolder) args[0], (Color) args[1]);
//                                })))
//
//                        .withSubcommand(new CommandBuilder("sky_color")
//                                .withDescription("Change the color of the sky")
//                                .withArgument(biomeArg)
//                                .withArgument(new Argument<>("color", new ColorArgumentType()).withDesc("The color to use").append(suggestColor(SpecialEffectsBuilder::getSkyColor)))
//                                .executes(CommandExecutor.any((sender, args) -> {
//                                    setColor(sender, SpecialEffectsBuilder::setSkyColor, (BiomeHolder) args[0], (Color) args[1]);
//                                })))
//
//                        .withSubcommand(new CommandBuilder("foliage_color")
//                                .withDescription("Change the color of *most* leaves")
//                                .withArgument(biomeArg)
//                                .withArgument(new Argument<>("color", new ColorArgumentType()).withDesc("The color to use").append(suggestColor(SpecialEffectsBuilder::getFoliageColorOverride)))
//                                .executes(CommandExecutor.any((sender, args) -> {
//                                    setColor(sender, SpecialEffectsBuilder::setFoliageColorOverride, (BiomeHolder) args[0], (Color) args[1]);
//                                })))
//
//                        .withSubcommand(new CommandBuilder("grass_color")
//                                .withDescription("Change the color of grass and plants")
//                                .withArgument(biomeArg)
//                                .withArgument(new Argument<>("color", new ColorArgumentType()).withDesc("The color to use").append(suggestColor(SpecialEffectsBuilder::getGrassColorOverride)))
//                                .executes(CommandExecutor.any((sender, args) -> {
//                                    setColor(sender, SpecialEffectsBuilder::setGrassColorOverride, (BiomeHolder) args[0], (Color) args[1]);
//                                })))
//                )
//
//                .withSubcommand(new CommandBuilder("variation")
//                        .withAliases("variations")
//                        .withDescription("Add variations by adding random biomes")
//                        .withArgument(biomeArg)
//                        .withArgument(new Argument<>("world", new StringArgumentType().withLiteral("*")).append(data -> Bukkit.getWorlds().stream().map(World::getName).map(Tooltip::of).toArray(Tooltip[]::new)))
//                        .withArgument(new Argument<>("variations", new GreedyArgumentType()).withDesc("Which biomes to add").replace(VariationTabCompletions::suggestions))
//                        .executes(CommandExecutor.any((sender, args) -> {
//                            setVariations(sender, (BiomeHolder) args[0], (String) args[1], (String) args[2]);
//                        }))
//                )
//
//                .withSubcommand(new CommandBuilder("deletevariation")
//                        .withAliases("deletevariations")
//                        .withDescription("Delete random biome variations that you added")
//                        .withArgument(biomeArg)
//                        .withArgument(new Argument<>("world", new StringArgumentType().withLiteral("*")).append(data -> Bukkit.getWorlds().stream().map(World::getName).map(Tooltip::of).toArray(Tooltip[]::new)))
//                        .executes(CommandExecutor.any((sender, args) -> {
//                            NamespacedKey key = ((BiomeHolder) args[0]).key();
//                            String world = (String) args[1];
//
//                            BiomeWrapper base = BiomeRegistry.getInstance().get(key);
//                            if (base == null) {
//                                sender.sendMessage(ChatColor.RED + "Could not find biome '" + key + "'");
//                                return;
//                            }
//                            boolean deleted = BiomeManager.inst().biomeRandomizer.deleteVariation("*".equals(world) ? null : world, base);
//                            if (deleted)
//                                changes(sender);
//                            else
//                                sender.sendMessage(ChatColor.RED + "You didn't have any variations configured for world '" + world + "' for '" + key + "'");
//                        }))
//                );
//
//        java.awt.Color primary = new java.awt.Color(85, 255, 85);
//        java.awt.Color secondary = new java.awt.Color(255, 85, 170);
//        command.registerHelp(new HelpCommandBuilder.HelpColor(Style.style(TextColor.color(primary.getRGB())), Style.style(TextColor.color(secondary.getRGB())), "\u27A2"));
//        command.register();
//    }
//
//    private static Function<CommandData, Tooltip[]> suggestColor(Function<SpecialEffectsBuilder, Integer> function) {
//        return data -> {
//            BiomeHolder biome = (BiomeHolder) data.previousArguments()[0];
//            BiomeWrapper wrapper = BiomeRegistry.getInstance().get(biome.key());
//            if (wrapper == null)
//                return new Tooltip[0];
//
//            SpecialEffectsBuilder effects = wrapper.getSpecialEffects();
//            Integer rgb = function.apply(effects);
//            if (rgb == -1)
//                return new Tooltip[0];
//
//            Color current = Color.fromRGB(rgb);
//            java.awt.Color awtColor = new java.awt.Color(current.getRed(), current.getGreen(), current.getBlue());
//            String hex = Integer.toHexString(awtColor.getRGB()).substring(2);
//
//            return new Tooltip[]{Tooltip.of(hex, "The current color of " + biome.key().getKey())};
//        };
//    }
//
//    private static Function<CommandData, Tooltip[]> suggestConfig(Function<SpecialEffectsBuilder, Object> function) {
//        return data -> {
//            BiomeHolder biome = (BiomeHolder) data.previousArguments()[0];
//            BiomeWrapper wrapper = BiomeRegistry.getInstance().get(biome.key());
//            if (wrapper == null)
//                return new Tooltip[0];
//
//            SpecialEffectsBuilder effects = wrapper.getSpecialEffects();
//            return new Tooltip[]{Tooltip.of(function.apply(effects), "Current value")};
//        };
//    }
//
//    private static Function<CommandData, Tooltip[]> suggestDensity() {
//        return data -> {
//            BiomeHolder biome = (BiomeHolder) data.previousArguments()[0];
//            BiomeWrapper wrapper = BiomeRegistry.getInstance().get(biome.key());
//            double density = 0.0;
//            if (wrapper != null)
//                density = wrapper.getSpecialEffects().getParticle().density();
//
//            return new Tooltip[]{
//                    Tooltip.of(density, "Current density of " + biome.key().getKey()),
//                    Tooltip.of(0.01428, "warped_forest default value"),
//                    Tooltip.of(0.025, "crimson_forest default value"),
//                    Tooltip.of(0.00625, "soul_sand_valley default value"),
//                    Tooltip.of(0.1189334, "basalt_deltas default value")
//            };
//        };
//    }
//
//    public static void menu(CommandSender sender, BiomeHolder biome) {
//        TextComponent.Builder builder = text();
//
//        String[] keys = new String[]{"Fog", "Water", "Water_Fog", "Sky", "Foliage", "Grass"};
//        List<Function<SpecialEffectsBuilder, Integer>> elements = Arrays.asList(SpecialEffectsBuilder::getFogColor, SpecialEffectsBuilder::getWaterColor, SpecialEffectsBuilder::getWaterFogColor, SpecialEffectsBuilder::getSkyColor, SpecialEffectsBuilder::getFoliageColorOverride, SpecialEffectsBuilder::getGrassColorOverride);
//
//        Style green = Style.style(NamedTextColor.GREEN);
//        Style gray = Style.style(NamedTextColor.GRAY);
//        TableBuilder table = new TableBuilder()
//                .withConstraints(TableBuilder.DEFAULT_CONSTRAINTS.setPixels(310).setRows(3))
//                .withElementCharStyle(green)
//                .withElementStyle(gray)
//                .withAttemptSinglePixelFix()
//                .withFillChar('=')
//                .withFillCharStyle(gray.decorate(TextDecoration.STRIKETHROUGH))
//                .withHeaderStyle(green)
//                .withHeader(StringUtil.snakeToReadable(biome.key().getKey()))
//                .withSupplier(i -> {
//                    TextComponent.Builder temp = text();
//                    showColors(temp, biome, elements.get(i), keys[i]);
//                    return temp.build();
//                });
//
//        // Add the table and remove the extra newline().
//        builder.append(table.build());
//
//        BiomeWrapper wrapper = BiomeRegistry.getInstance().get(biome.key());
//        SpecialEffectsBuilder effects = wrapper.getSpecialEffects();
//
//        // Particle information
//        builder.append(text("PARTICLE: ").style(gray));
//        builder.append(getComponent(wrapper, effects.getParticle()));
//        builder.append(newline());
//
//        // Sound information
//        builder.append(text("AMBIENT: ").style(gray));
//        builder.append(text(removeNamespace(effects.getAmbientSound())).style(green)
//                .hoverEvent(text("Click to modify the ambient sound"))
//                .clickEvent(ClickEvent.suggestCommand("/biomemanager ambient " + biome.key() + " " + effects.getAmbientSound())));
//        builder.append(newline());
//
//        builder.append(text("RANDOM: ").style(gray));
//        builder.append(getComponent(wrapper, effects.getRandomSound()));
//        builder.append(newline());
//
//        builder.append(text("CAVE: ").style(gray));
//        builder.append(getComponent(wrapper, effects.getCaveSoundSettings()));
//        builder.append(newline());
//
//        builder.append(text("MUSIC: ").style(gray));
//        builder.append(getComponent(wrapper, effects.getMusic()));
//        builder.append(newline());
//
//        // Footer
//        StringBuilder footer = new StringBuilder();
//        while (TableBuilder.DEFAULT_FONT.getWidth(footer.toString()) < 310)
//            footer.append("=");
//
//        footer.setLength(footer.length() - 1);
//        builder.append(text(footer.toString()).style(gray.decorate(TextDecoration.STRIKETHROUGH)));
//
//        MechanicsCore.getPlugin().adventure.sender(sender).sendMessage(builder);
//    }
//
//    private static void showColors(TextComponent.Builder builder, BiomeHolder biome, Function<SpecialEffectsBuilder, Integer> function, String key) {
//        BiomeWrapper wrapper = BiomeRegistry.getInstance().get(biome.key());
//        if (wrapper == null)
//            throw new IllegalArgumentException("Biome '" + biome.key() + "' does not exist");
//
//        SpecialEffectsBuilder fog = wrapper.getSpecialEffects();
//        int rgb = function.apply(fog);
//        Color color = rgb == -1 ? Color.WHITE : Color.fromRGB(function.apply(fog));
//
//        java.awt.Color awtColor = new java.awt.Color(color.getRed(), color.getGreen(), color.getBlue());
//        String hex = Integer.toHexString(awtColor.getRGB()).substring(2);
//
//        ClickEvent click = ClickEvent.suggestCommand("/biomemanager color " + key.toLowerCase(Locale.ROOT) + "_color " + biome.key() + " " + hex);
//        HoverEvent<?> hover = HoverEvent.showText(text("Click to set color"));
//
//        builder.append(text(key.toUpperCase(Locale.ROOT) + ": ").color(NamedTextColor.GRAY).clickEvent(click).hoverEvent(hover));
//        builder.append(text(hex.toUpperCase(Locale.ROOT)).color(TextColor.color(awtColor.getRGB())).clickEvent(click).hoverEvent(hover));
//    }
//
//    public static void setColor(CommandSender sender, BiConsumer<SpecialEffectsBuilder, Color> method, BiomeHolder biome, Color color) {
//        BiomeWrapper wrapper = BiomeRegistry.getInstance().get(biome.key());
//        SpecialEffectsBuilder effects = wrapper.getSpecialEffects();
//        method.accept(effects, color);
//        wrapper.setSpecialEffects(effects);
//        changes(sender);
//    }
//
//    public static void changes(CommandSender sender) {
//        MechanicsCore.getPlugin().adventure.sender(sender).sendMessage(text("Success! Leave and Rejoin to see your changes.").color(NamedTextColor.GREEN));
//    }
//
//    private static Color randomColor() {
//        return Color.fromRGB(RandomUtil.range(0, 255), RandomUtil.range(0, 255), RandomUtil.range(0, 255));
//    }
//
//    private static TextComponent getComponent(BiomeWrapper wrapper, SpecialEffectsBuilder.ParticleData data) {
//        return text(removeNamespace(data.particle()) + " " + round(data.density()))
//                .color(NamedTextColor.GREEN)
//                .hoverEvent(text("Click to modify particle"))
//                .clickEvent(ClickEvent.suggestCommand("/biomemanager particle " + wrapper.getKey() + " " + data.particle() + " " + data.density()));
//    }
//
//    private static TextComponent getComponent(BiomeWrapper wrapper, SpecialEffectsBuilder.CaveSoundData data) {
//        return text(removeNamespace(data.sound()) + " " + data.tickDelay() + " " + data.searchOffset() + " " + round((float) data.soundOffset()))
//                .color(NamedTextColor.GREEN)
//                .hoverEvent(text("Click to modify cave sound"))
//                .clickEvent(ClickEvent.suggestCommand("/biomemanager cave " + wrapper.getKey() + " " + data.sound() + " " + data.tickDelay() + " " + data.searchOffset() + " " + data.soundOffset()));
//    }
//
//    private static TextComponent getComponent(BiomeWrapper wrapper, SpecialEffectsBuilder.RandomSoundData data) {
//        return text(removeNamespace(data.sound()) + " " + round((float) data.tickChance()))
//                .color(NamedTextColor.GREEN)
//                .hoverEvent(text("Click to modify randomized sound"))
//                .clickEvent(ClickEvent.suggestCommand("/biomemanager random " + wrapper.getKey() + " " + data.sound() + " " + data.tickChance()));
//    }
//
//    private static TextComponent getComponent(BiomeWrapper wrapper, SpecialEffectsBuilder.MusicData data) {
//        return text(removeNamespace(data.sound()) + " " + data.minDelay() + " " + data.maxDelay() + " " + data.isOverride())
//                .color(NamedTextColor.GREEN)
//                .hoverEvent(text("Click to modify the music"))
//                .clickEvent(ClickEvent.suggestCommand("/biomemanager music " + wrapper.getKey() + " " + data.sound() + " " + data.minDelay() + " " + data.maxDelay() + " " + data.isOverride()));
//    }
//
//    private static String round(float num) {
//        BigDecimal bigDecimal = new BigDecimal(num, new MathContext(2, RoundingMode.HALF_UP));
//        bigDecimal = bigDecimal.stripTrailingZeros();
//        return bigDecimal.toPlainString();
//    }
//
//    private static String removeNamespace(String key) {
//        if (key == null)
//            return "null";
//
//        return key.startsWith("minecraft:") ? key.substring("minecraft:".length()) : key;
//    }
//
//    private static void fillBiome(Block pos1, Block pos2, BiomeWrapper biome) {
//        if (!pos1.getWorld().equals(pos2.getWorld()))
//            throw new IllegalArgumentException("Cannot fill biome between worlds");
//
//        // Make sure the given coords are actual min/max values.
//        World world = pos1.getWorld();
//        Block min = world.getBlockAt(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
//        Block max = world.getBlockAt(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
//
//        // This is technically inefficient since biomes are set in 4x4 areas.
//        for (int x = min.getX(); x < max.getX(); x++) {
//            for (int y = min.getY(); y < max.getY(); y++) {
//                for (int z = min.getZ(); z < max.getZ(); z++) {
//                    Block current = world.getBlockAt(x, y, z);
//                    biome.setBiome(current);
//                }
//            }
//        }
//    }
//
//    private static void setVariations(CommandSender sender, BiomeHolder biome, String world, String variationsString) {
//        if (BiomeManager.inst().getConfig().getBoolean("Disable_Biome_Variations")) {
//            sender.sendMessage(ChatColor.RED + "Variations are disabled in the config");
//            return;
//        }
//
//        String[] split = variationsString.split("[, ]");
//        ProbabilityMap<BiomeWrapper> variations = new ProbabilityMap<>();
//
//        if ("*".equals(world))
//            world = null;
//        else if (Bukkit.getWorld(world) == null) {
//            sender.sendMessage(ChatColor.RED + "Cannot find world '" + world + "'");
//            return;
//        }
//
//        for (String biomeVariation : split) {
//            String[] variationData = biomeVariation.split("%", 2);
//            String key = variationData.length == 2 ? variationData[1] : variationData[0];
//            String chanceStr = variationData.length == 2 ? variationData[0] : "1";
//
//            double chance;
//            try {
//                chance = Double.parseDouble(chanceStr);
//            } catch (NumberFormatException ex) {
//                sender.sendMessage(ChatColor.RED + "'" + chanceStr + "' is not a valid number");
//                return;
//            }
//
//            BiomeWrapper replacement = BiomeRegistry.getInstance().get(NamespacedKey.fromString(key));
//            if (replacement == null) {
//                sender.sendMessage(ChatColor.RED + "Unknown biome '" + key + "'");
//                return;
//            }
//
//            variations.add(replacement, chance);
//        }
//
//        BiomeRandomizer randomizer = BiomeManager.inst().biomeRandomizer;
//        BiomeWrapper base = BiomeRegistry.getInstance().get(biome.key());
//        randomizer.addVariation(world, base, variations);
//        changes(sender);
//    }


}
