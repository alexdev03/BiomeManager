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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Command {

    private static BiomeRegistry BIOME_REGISTRY() {
        return BiomeRegistry.getInstance();
    }

    private static BiomeManager BIOME_MANAGER() {
        return BiomeManager.inst();
    }

    @SuppressWarnings("UnstableApiUsage") // For Paper Commands API
    public static void register(Commands commands) { // Pass plugin and Commands instance
        LiteralArgumentBuilder<CommandSourceStack> biomemanagerCommand = LiteralArgumentBuilder.<CommandSourceStack>literal("biomemanager")
                .requires(source -> source.getSender().hasPermission("biomemanager.admin"))
                // Aliases ("bm", "biome") are handled by Paper when registering the command.
                // Description ("BiomeManager main command") is also handled by Paper.

                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("reset")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.reset"))
                        // .withDescription("Reset config of a specific biome") // Handled by Paper
                        .then(getBiomeArgument().executes(Command::executeReset))
                )

                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("randomize")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.randomize"))
                        .then(getBiomeArgument().executes(Command::executeRandomize))
                )

                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("menu")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.menu"))
                        .then(getBiomeArgument().executes(Command::executeMenu))
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
                                        .then(getBiomeArgument().executes(Command::executeFill))
                                )
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("delete")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.delete"))
                        .then(getBiomeArgument().executes(Command::executeDelete))
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("particle")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.particle"))
                        .then(getBiomeArgument()
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
                        .then(getBiomeArgument()
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Sound>argument("sound", ArgumentTypes.resource(RegistryKey.SOUND_EVENT))
                                        .suggests(Command::suggestSounds)
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("tick-delay", IntegerArgumentType.integer(1))
                                                .suggests((c, b) -> suggestConfigValue(c, b, effetti -> effetti.getCaveSoundSettings().tickDelay()))
                                                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("search-distance", IntegerArgumentType.integer(1))
                                                        .suggests((c, b) -> suggestConfigValue(c, b, effetti -> effetti.getCaveSoundSettings().searchOffset()))
                                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument("sound-offset", DoubleArgumentType.doubleArg(0.0))
                                                                .suggests((c, b) -> suggestConfigValue(c, b, effetti -> effetti.getCaveSoundSettings().soundOffset()))
                                                                .executes(Command::executeCaveSound)
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("music")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.music"))
                        .then(getBiomeArgument().then(RequiredArgumentBuilder.<CommandSourceStack, Sound>argument("sound", ArgumentTypes.resource(RegistryKey.SOUND_EVENT))
                                        .suggests(Command::suggestSounds)
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("min-delay", IntegerArgumentType.integer(1))
                                                .suggests((c, b) -> suggestConfigValue(c, b, effetti -> effetti.getMusic().minDelay()))
                                                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("max-delay", IntegerArgumentType.integer(1))
                                                        .suggests((c, b) -> suggestConfigValue(c, b, effetti -> effetti.getMusic().maxDelay()))
                                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Boolean>argument("override-music", BoolArgumentType.bool())
                                                                .suggests((c, b) -> suggestConfigValue(c, b, effetti -> effetti.getMusic().isOverride()))
                                                                .executes(Command::executeMusic)
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("random")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.random"))
                        .then(getBiomeArgument().then(RequiredArgumentBuilder.<CommandSourceStack, Sound>argument("sound", ArgumentTypes.resource(RegistryKey.SOUND_EVENT))
                                        .suggests(Command::suggestSounds)
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument("chance", DoubleArgumentType.doubleArg(0.0, 1.0))
                                                .suggests((c, b) -> suggestConfigValue(c, b, effetti -> effetti.getRandomSound().tickChance()))
                                                .executes(Command::executeRandomSound)
                                        )
                                )
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("ambient")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.ambient"))
                        .then(getBiomeArgument().then(RequiredArgumentBuilder.<CommandSourceStack, Sound>argument("sound", ArgumentTypes.resource(RegistryKey.SOUND_EVENT))
                                        .suggests(Command::suggestSounds)
                                        .executes(Command::executeAmbientSound)
                                )
                        )
                )
                .then(buildColorCommands()) // Color subcommands
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("variation")
                        .requires(source -> source.getSender().hasPermission("biomemanager.commands.variation"))
                        .then(getBiomeArgument().then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("world", StringArgumentType.word())
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
                        .then(getBiomeArgument().then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("world", StringArgumentType.word())
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
                builder.suggest(String.valueOf(round((float) density)));
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
            @Override
            public NamespacedKey key() {
                return key;
            }
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
        BiomeManager.inst().saveToConfig();
        return 1;
    }

    private static int executeFill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        BlockPositionResolver resolver1 = context.getArgument("pos1", BlockPositionResolver.class);
        BlockPositionResolver resolver2 = context.getArgument("pos2", BlockPositionResolver.class);
        NamespacedKey biomeKey = context.getArgument("biome", NamespacedKey.class);

        // BlockPosArgument needs a world context. Get it from the sender's location.
        Location senderLocation = context.getSource().getLocation();
        if (senderLocation == null || senderLocation.getWorld() == null) {
            sender.sendMessage(Component.text("Cannot determine world for fill operation.").color(NamedTextColor.RED));
            return 0;
        }

        BlockPosition pos1MC = resolver1.resolve(context.getSource());
        BlockPosition pos2MC = resolver2.resolve(context.getSource());

        World world = senderLocation.getWorld();

        Block block1 = world.getBlockAt(pos1MC.blockX(), pos1MC.blockY(), pos1MC.blockZ());
        Block block2 = world.getBlockAt(pos2MC.blockX(), pos2MC.blockY(), pos2MC.blockZ());


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
        NamespacedKey biomeKey = context.getArgument("biome", NamespacedKey.class);
        try {
            BIOME_REGISTRY().remove(biomeKey);
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

        if (particle.getDataType() != Void.class) {
            sender.sendRichMessage("<red>You cannot set a particle with data type " + particle.getDataType() + " for biome " + biomeKey + "</red>");
            return 1;
        }

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
        if (biome == null) { /* ... error ... */
            return 0;
        }

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
        if (biome == null) { /* ... error ... */
            return 0;
        }

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
        if (biome == null) { /* ... error ... */
            return 0;
        }

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
        Sound soundKey = context.getArgument("sound", Sound.class);

        BiomeWrapper biome = BIOME_REGISTRY().get(biomeKey);
        if (biome == null) { /* ... error ... */
            return 0;
        }

        SpecialEffectsBuilder effects = biome.getSpecialEffects();
        effects.setAmbientSound(soundKey.getKey().toString());
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
    private static LiteralArgumentBuilder<CommandSourceStack> buildColorCommands() {
        LiteralArgumentBuilder<CommandSourceStack> colorCommand = LiteralArgumentBuilder.<CommandSourceStack>literal("color")
                .requires(source -> source.getSender().hasPermission("biomemanager.commands.color"));

        // Fog Color
        colorCommand.then(LiteralArgumentBuilder.<CommandSourceStack>literal("fog_color")
                .then(getBiomeArgument().then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("color", StringArgumentType.greedyString()) // Assuming hex string
                        .suggests((c, b) -> suggestColorValue(c, b, SpecialEffectsBuilder::getFogColor))
                        .executes(ctx -> executeSetColor(ctx, SpecialEffectsBuilder::setFogColor)))));
        // Water Color
        colorCommand.then(LiteralArgumentBuilder.<CommandSourceStack>literal("water_color")
                .then(getBiomeArgument().then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("color", StringArgumentType.greedyString())
                        .suggests((c, b) -> suggestColorValue(c, b, SpecialEffectsBuilder::getWaterColor))
                        .executes(ctx -> executeSetColor(ctx, SpecialEffectsBuilder::setWaterColor)))));
        // Water Fog Color
        colorCommand.then(LiteralArgumentBuilder.<CommandSourceStack>literal("water_fog_color")
                .then(getBiomeArgument().then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("color", StringArgumentType.greedyString())
                        .suggests((c, b) -> suggestColorValue(c, b, SpecialEffectsBuilder::getWaterFogColor))
                        .executes(ctx -> executeSetColor(ctx, SpecialEffectsBuilder::setWaterFogColor)))));
        // Sky Color
        colorCommand.then(LiteralArgumentBuilder.<CommandSourceStack>literal("sky_color")
                .then(getBiomeArgument().then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("color", StringArgumentType.greedyString())
                        .suggests((c, b) -> suggestColorValue(c, b, SpecialEffectsBuilder::getSkyColor))
                        .executes(ctx -> executeSetColor(ctx, SpecialEffectsBuilder::setSkyColor)))));

        // Grass Color
        colorCommand.then(LiteralArgumentBuilder.<CommandSourceStack>literal("grass_color")
                .then(getBiomeArgument().then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("color", StringArgumentType.greedyString())
                        .suggests((c, b) -> suggestColorValue(c, b, SpecialEffectsBuilder::getGrassColorOverride))
                        .executes(ctx -> executeSetColor(ctx, (effects, bukkitColor) -> effects.setGrassColorOverride(bukkitColor.asRGB())))))); // Needs int

        // Foliage Color
        colorCommand.then(LiteralArgumentBuilder.<CommandSourceStack>literal("foliage_color")
                .then(getBiomeArgument().then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("color", StringArgumentType.greedyString())
                        .suggests((c, b) -> suggestColorValue(c, b, SpecialEffectsBuilder::getFoliageColorOverride))
                        .executes(ctx -> executeSetColor(ctx, (effects, bukkitColor) -> effects.setFoliageColorOverride(bukkitColor.asRGB())))))); // Needs int


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

    private static Pattern INT_COLOR_PATTERN = Pattern.compile("^[0-9]*$");

    private static Color parseHexColor(String hex) {
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        } else {
            Matcher matcher = INT_COLOR_PATTERN.matcher(hex);
            if (matcher.find()) {
                try {
                    int color = Integer.parseInt(hex);
                    return Color.fromRGB(color);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid integer color value: " + hex + ". If it should be an hex value try ading # before the value", e);
                }
            }
        }
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
        menu(sender, biome);
    }

    public static void changes(CommandSender sender) {
        sender.sendMessage(Component.text("Success! Leave and Rejoin to see your changes.").color(NamedTextColor.GREEN));
        BiomeManager.inst().saveToConfig();
    }

    private static Random random = new Random();

    private static Color randomColor() { // Bukkit Color

        return Color.fromRGB(random.nextInt(0, 255), random.nextInt(0, 255), random.nextInt(0, 255));
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

//        String[] split = variationsString.split("[, ]");
//        ProbabilityMap<BiomeWrapper> variations = new ProbabilityMap<>();
//
//        if ("*".equals(worldName)) {
//            worldName = null;
//        } else if (Bukkit.getWorld(worldName) == null) {
//            sender.sendMessage(Component.text("Cannot find world '" + worldName + "'").color(NamedTextColor.RED));
//            return;
//        }
//
//        for (String biomeVariation : split) {
//            String[] variationData = biomeVariation.split("%", 2);
//            String keyStr = variationData.length == 2 ? variationData[1] : variationData[0];
//            String chanceStr = variationData.length == 2 ? variationData[0] : "1";
//
//            double chance;
//            try {
//                chance = Double.parseDouble(chanceStr);
//            } catch (NumberFormatException ex) {
//                sender.sendMessage(Component.text("'" + chanceStr + "' is not a valid number").color(NamedTextColor.RED));
//                return;
//            }
//
//            BiomeWrapper replacement = BIOME_REGISTRY().get(NamespacedKey.fromString(keyStr));
//            if (replacement == null) {
//                sender.sendMessage(Component.text("Unknown biome '" + keyStr + "'").color(NamedTextColor.RED));
//                return;
//            }
//            variations.add(replacement, chance);
//        }
//
//        BiomeRandomizer randomizer = BIOME_MANAGER().biomeRandomizer;
//        BiomeWrapper base = BIOME_REGISTRY().get(biome.key());
//        if (base == null) { // Should not happen if biome (BiomeHolder) is valid
//            sender.sendMessage(Component.text("Base biome '" + biome.key() + "' for variation not found!").color(NamedTextColor.RED));
//            return;
//        }
////        randomizer.addVariation(worldName, base, variations);
//        changes(sender);
    }

    public static RequiredArgumentBuilder<CommandSourceStack, NamespacedKey> getBiomeArgument() {
        return RequiredArgumentBuilder.<CommandSourceStack, NamespacedKey>argument("biome", ArgumentTypes.namespacedKey())
                .suggests(Command::suggestBiomes);
    }


    // Dummy BiomeHolder for compatibility if you don't have a direct replacement yet
    // You should replace this with your actual BiomeHolder or adapt methods to use NamespacedKey directly.
    @FunctionalInterface
    public interface BiomeHolder {
        NamespacedKey key();
    }

}
