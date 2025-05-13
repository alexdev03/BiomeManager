package me.cjcrafter.biomemanager.compatibility;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.registry.RegistryKey;
import me.cjcrafter.biomemanager.BiomeManager;
import me.cjcrafter.biomemanager.BiomeRegistry;
import me.cjcrafter.biomemanager.SpecialEffectsBuilder;
import me.deecaad.core.lib.scheduler.util.FieldAccessor;
import me.deecaad.core.lib.scheduler.util.MethodInvoker;
import me.deecaad.core.lib.scheduler.util.ReflectionUtil;
import me.deecaad.core.utils.LogLevel;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftSound;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBiome;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static me.cjcrafter.biomemanager.compatibility.v1_21_4.biomeRegistry;


public class BiomeWrapper_1_21_4 implements BiomeWrapper {

    private static final FieldAccessor climateSettingsField;
    private static final FieldAccessor temperatureAdjustmentField;
    private static final FieldAccessor generationSettingsField;
    private static final FieldAccessor mobSettingsField;
    private static final FieldAccessor particleDensityField;
    private static final FieldAccessor specialEffectsField;
    private static final MethodInvoker bindTagsMethod;

    static {
        // https://nms.screamingsandals.org/1.19.4/net/minecraft/world/level/biome/Biome$ClimateSettings.html
        climateSettingsField = ReflectionUtil.getField(Biome.class, "climateSettings");
        temperatureAdjustmentField = ReflectionUtil.getField(Biome.ClimateSettings.class, "temperatureModifier");
        generationSettingsField = ReflectionUtil.getField(Biome.class, "generationSettings");
        mobSettingsField = ReflectionUtil.getField(Biome.class, "mobSettings");
        particleDensityField = ReflectionUtil.getField(AmbientParticleSettings.class, "probability");
        specialEffectsField = ReflectionUtil.getField(Biome.class, "specialEffects");
        try {
            Method method = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
            method.setAccessible(true);
            bindTagsMethod = new MethodInvoker(method);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private final NamespacedKey key;
    private Biome base;
    private Biome biome;
    private boolean isVanilla;
    private boolean isExternalPlugin;
    private boolean isDirty; // true for custom and modified vanilla biomes


    public BiomeWrapper_1_21_4(Biome biome) {
        this.key = NamespacedKey.fromString(biomeRegistry.getKey(biome).toString());
        this.base = biome;

        // Swap so reset() will work in the future (keep a copy of original biome).
        reset();
        Biome temp = this.base;
        this.base = this.biome;
        this.biome = temp;

        isDirty = false;
        isVanilla = true;
        isExternalPlugin = !key.getKey().equals(NamespacedKey.MINECRAFT);
    }

    public BiomeWrapper_1_21_4(NamespacedKey key, BiomeWrapper_1_21_4 base) {
        this.key = key;
        this.base = biomeRegistry.get(ResourceLocation.fromNamespaceAndPath(base.getKey().getNamespace(), base.getKey().getKey())).get().value();
        reset();

        // This is true by default, since custom biomes are always "dirty".
        isDirty = true;

        // When these keys are equal, we are overriding a vanilla biome. In
        // this case, we want to modify the raw biome object instead of
        // modifying the copied biome object. We still need to create that
        // copy for the reset() method to work for future calls, though.
        if (key.equals(base.getKey())) {
            Biome temp = this.biome;
            this.biome = this.base;
            this.base = temp;

            isVanilla = true;
            isDirty = false;
        }
    }

    @Override
    public void reset() {
        if (isVanilla)
            isDirty = false;

        Biome temp = new Biome.BiomeBuilder()
                .hasPrecipitation(base.hasPrecipitation())
                .temperature(base.getBaseTemperature())
                .downfall(base.climateSettings.downfall())
                .specialEffects(base.getSpecialEffects())
                .mobSpawnSettings(base.getMobSettings())
                .generationSettings(base.getGenerationSettings())
//                .temperatureAdjustment((Biome.TemperatureModifier) invokeField(temperatureAdjustmentField, invokeField(climateSettingsField, base)))
                .temperatureAdjustment((Biome.TemperatureModifier) temperatureAdjustmentField.get(climateSettingsField.get(base)))
                .build();

        // If we don't have a biome already, just set
        if (biome == null) {
            biome = temp;
            return;
        }

        // When the biome already exists, we have to use reflection since we
        // want to modify the biome that is in the NMS registry.
        climateSettingsField.set(biome, climateSettingsField.get(temp));
        temperatureAdjustmentField.set(biome, temperatureAdjustmentField.get(temp));
        generationSettingsField.set(biome, generationSettingsField.get(temp));
        mobSettingsField.set(biome, mobSettingsField.get(temp));
        specialEffectsField.set(biome, specialEffectsField.get(temp));
    }

    @Override
    public org.bukkit.block.Biome getBase() {
        if (isVanilla)
            return getBukkitBiome();

//        Registry<Biome> biomes = MinecraftServer.getServer().registryAccess().get(Registries.BIOME).orElseThrow().value();
//        ResourceKey<Biome> key = biomeRegistry.getResourceKey(base).orElseThrow();
//
//        if (!key.location().getNamespace().equals(NamespacedKey.MINECRAFT))
//            return org.bukkit.block.Biome.CUSTOM;
//
//        return EnumUtil.getIfPresent(org.bukkit.block.Biome.class, key.location().getPath()).orElse(org.bukkit.block.Biome.CUSTOM);
        return CraftBiome.minecraftToBukkit(base);
    }

    @Override
    public void setBase(org.bukkit.block.Biome biome) {
        base = biomeRegistry.get(ResourceLocation.fromNamespaceAndPath(biome.getKey().getNamespace(), biome.getKey().getKey())).get().value();
    }

    @Override
    public NamespacedKey getKey() {
        return key;
    }

    @Override
    public int getId() {
        return biomeRegistry.getId(biome);
    }

    public String writeParticle(ParticleOptions particle) {
        if (particle instanceof DustParticleOptions dust) {

        }
        return "";
    }

    @Override
    public SpecialEffectsBuilder getSpecialEffects() {
        BiomeSpecialEffects effects = biome.getSpecialEffects();
        SpecialEffectsBuilder builder = new SpecialEffectsBuilder();
        builder.setFogColor(effects.getFogColor())
                .setWaterColor(effects.getWaterColor())
                .setWaterFogColor(effects.getWaterFogColor())
                .setSkyColor(effects.getSkyColor())
                .setGrassColorModifier(effects.getGrassColorModifier().name());

        effects.getGrassColorOverride().ifPresent(builder::setGrassColorOverride);
        effects.getFoliageColorOverride().ifPresent(builder::setFoliageColorOverride);
        effects.getAmbientLoopSoundEvent().ifPresent(holder -> builder.setAmbientSound(holder.value().location().toString()));

        if (effects.getAmbientParticleSettings().isPresent()) {
            AmbientParticleSettings particle = effects.getAmbientParticleSettings().get();

            builder.setAmbientParticle(writeParticle(particle.getOptions()))
                    .setParticleProbability((float) particleDensityField.get(particle));
        }

        if (effects.getAmbientMoodSettings().isPresent()) {
            AmbientMoodSettings settings = effects.getAmbientMoodSettings().get();

            builder.setCaveSound(settings.getSoundEvent().value().location().toString())
                    .setCaveTickDelay(settings.getTickDelay())
                    .setCaveSearchDistance(settings.getBlockSearchExtent())
                    .setCaveSoundOffset(settings.getSoundPositionOffset());
        }

        if (effects.getAmbientAdditionsSettings().isPresent()) {
            AmbientAdditionsSettings settings = effects.getAmbientAdditionsSettings().get();

            builder.setRandomSound(settings.getSoundEvent().value().location().toString())
                    .setRandomTickChance(settings.getTickChance());
        }

//        if (effects.getBackgroundMusic().isPresent()) {
//            Music music = effects.getBackgroundMusic().get();
//
//            builder.setMusicSound(music.getEvent().value().location().toString())
//                    .setMusicMinDelay(music.getMinDelay())
//                    .setMusicMaxDelay(music.getMaxDelay())
//                    .setMusicOverride(music.replaceCurrentMusic());
//        }

        return builder;
    }

    @Override
    public void setSpecialEffects(SpecialEffectsBuilder builder) {
        isDirty = true;

        SpecialEffectsBuilder.ParticleData particle = builder.getParticle();
        SpecialEffectsBuilder.MusicData music = builder.getMusic();
        SpecialEffectsBuilder.CaveSoundData caveSettings = builder.getCaveSoundSettings();
        SpecialEffectsBuilder.RandomSoundData cave = builder.getRandomSound();

        BiomeSpecialEffects.Builder a = new BiomeSpecialEffects.Builder()
                .fogColor(builder.getFogColor())
                .waterColor(builder.getWaterColor())
                .waterFogColor(builder.getWaterFogColor())
                .skyColor(builder.getSkyColor())
                .grassColorModifier(BiomeSpecialEffects.GrassColorModifier.valueOf(builder.getGrassColorModifier().trim().toUpperCase()));

        if (builder.getGrassColorOverride() != -1) {
            a.grassColorOverride(builder.getGrassColorOverride());
        }
        if (builder.getFoliageColorOverride() != -1) {
            a.foliageColorOverride(builder.getFoliageColorOverride());
        }
        if (builder.getAmbientSound() != null) {
            a.ambientLoopSound(getSound(builder.getAmbientSound()));
        }
        if (particle.particle() != null) {
            try {
                RegistryAccess access = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();
                ParticleOptions nmsParticle = ParticleArgument.readParticle(new StringReader(particle.particle()), access);
                a.ambientParticle(new AmbientParticleSettings(nmsParticle, particle.density()));
            } catch (CommandSyntaxException ex) {
                BiomeManager.inst().debug.log(LogLevel.ERROR, "Could not set particle: " + particle, ex);
            }
        }
        if (caveSettings.sound() != null) {
            a.ambientMoodSound(new AmbientMoodSettings(getSound(caveSettings.sound()), caveSettings.tickDelay(), caveSettings.searchOffset(), caveSettings.soundOffset()));
        }
        if (cave.sound() != null) {
            a.ambientAdditionsSound(new AmbientAdditionsSettings(getSound(cave.sound()), cave.tickChance()));
        }
        if (music.sound() != null) {
            a.backgroundMusic(new Music(getSound(music.sound()), music.minDelay(), music.maxDelay(), music.isOverride()));
        }

        specialEffectsField.set(biome, a.build());
    }

    @Override
    public boolean setBiome(Block block) {
        ServerLevel world = ((CraftWorld) block.getWorld()).getHandle();

        // Don't attempt to load a chunk! This will lag the server.
        LevelChunk chunk = world.getChunkIfLoaded(new BlockPos(block.getX(), block.getY(), block.getZ()));
        if (chunk == null)
            return false;

        // Biomes are stored in 4x4 chunks.
        int x = QuartPos.toSection(block.getX());
        int y = QuartPos.toSection(block.getY());
        int z = QuartPos.toSection(block.getZ());

        chunk.setBiome(x, y, z, Holder.direct(biome));
        return true;
    }

    @Override
    public void register(boolean isCustom) {
        ResourceKey<Biome> resource = ResourceKey.create(biomeRegistry.key(), ResourceLocation.fromNamespaceAndPath(key.getNamespace(), key.getKey()));

        // Register the biome to BiomeManager's registry, and to the vanilla registry
        if (isCustom) {
            FieldAccessor freezeFieldAccessor = ReflectionUtil.getField(MappedRegistry.class, boolean.class);
            freezeFieldAccessor.set(biomeRegistry, false);

            FieldAccessor intrusiveHoldersFieldAccessor = ReflectionUtil.getField(MappedRegistry.class, "unregisteredIntrusiveHolders");
            intrusiveHoldersFieldAccessor.set(biomeRegistry, new HashMap<>());

            biomeRegistry.createIntrusiveHolder(biome);
            biomeRegistry.register(resource, biome, RegistrationInfo.BUILT_IN);

            intrusiveHoldersFieldAccessor.set(biomeRegistry, null);
            freezeFieldAccessor.set(biomeRegistry, true);

            Set<TagKey<Biome>> tags = new HashSet<>();
            Holder<Biome> minecraftHolder = Holder.direct(base);
            minecraftHolder.tags().forEach(tags::add);
            System.out.println(minecraftHolder.getClass().getSimpleName());
            Holder<Biome> holder = biomeRegistry.wrapAsHolder(biome);
            bindTagsMethod.invoke(holder, tags);
        }
        BiomeRegistry.getInstance().add(key, this);
    }

    @Override
    public boolean isExternalPlugin() {
        return isExternalPlugin;
    }

    @Override
    public boolean isDirty() {
        return isDirty;
    }

    @Override
    public String toString() {
        return key.toString();
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BiomeWrapper biome)) return false;
        return key.equals(biome.getKey());
    }

    private static Holder<SoundEvent> getSound(String key) {
//        ResourceLocation key = ResourceLocation.tryParse(sound);
//        SoundEvent existing = BuiltInRegistries.SOUND_EVENT.get(key).get().value(); // Use vanilla settings for sound event
//        if (existing == null) {
//            existing = SoundEvent.createVariableRangeEvent(key);
//        }
        Sound sound = io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT).get(NamespacedKey.fromString(key));
        return Holder.direct(CraftSound.bukkitToMinecraft(sound));
    }

    private static ParticleType<?> getParticle(String particle) {
        ResourceLocation key = ResourceLocation.tryParse(particle);
        return BuiltInRegistries.PARTICLE_TYPE.get(key).get().value();
    }
}
