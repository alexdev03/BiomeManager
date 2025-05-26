package me.cjcrafter.biomemanager.compatibility;

import com.google.gson.JsonObject;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.cjcrafter.biomemanager.BiomeManager;
import me.cjcrafter.biomemanager.BiomeRegistry;
import me.cjcrafter.biomemanager.SpecialEffectsBuilder;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.jetbrains.annotations.Nullable;

import static me.cjcrafter.biomemanager.compatibility.JsonSerializable.gson;

public interface BiomeWrapper  {

    /**
     * Resets this biome's settings to its default values. For custom biomes,
     * this resets the biome to the base biome's values.
     */
    void reset();

    /**
     * Gets the "base" biome used for default values.
     */
    Biome getBase();

    /**
     * Sets the "base" biome used for default values. Make sure to call
     * {@link #reset()} for the changes to take effect.
     *
     * @param biome The non-null biome to use for default values.
     */
    void setBase(Biome biome);

    /**
     * Returns the key for this biome. For vanilla biomes, the namespace will
     * be <code>"minecraft:"</code>. For custom biomes, the namespace will be
     * the lowercase name of the plugin, like <code>"biomemanager:"</code>.
     *
     * @return The non-null key storing the name of the biome.
     */
    NamespacedKey getKey();

    /**
     * Returns the unique numerical ID for this biome, as set by the server.
     * This value is not guaranteed to be the same across server restarts.
     *
     * @return The unique numerical ID for this biome.
     */
    int getId();

    /**
     * Returns the name of this biome. Biome names <i>may</i> not be unique.
     * This method simply used {@link NamespacedKey#getKey()}.
     *
     * @return The non-null name of this biome.
     */
    default String getName() {
        return getKey().getKey();
    }

    /**
     * Gets the special effects of this biome. The returned effects are cloned,
     * so make sure to use {@link #setSpecialEffects(SpecialEffectsBuilder)} after any
     * changes.
     *
     * @return The non-null fog.
     */
    SpecialEffectsBuilder getSpecialEffects();

    /**
     * Sets the special effects of this biome. The changes do not take effect
     * until the players rejoins the server.
     *
     * @param builder The non-null special effects builder.
     */
    void setSpecialEffects(SpecialEffectsBuilder builder);

    /**
     * Sets this biome wrapper to the given block, similar to
     * {@link Block#setBiome(Biome)}.
     *
     * @param block The non-null block to set.
     * @return true if the biome was changed.
     */
    boolean setBiome(Block block);

    /**
     * Registers this biome wrapper to the BiomeManager's biome registry. If
     * <code>isCustom = true</code>, then the biome will also be registered to
     * the Minecraft Server's biome registry. It is important to register
     * custom biomes BEFORE worlds load. Add <code>load: STARTUP</code> to your
     * plugin.yml file.
     *
     * @param isCustom true if this biome wraps a custom biome.
     */
    void register(boolean isCustom);

    /**
     * Returns the {@link Biome} associated with this wrapper
     * from the Bukkit registry.
     *
     * @return The {@link Biome} associated with this wrapper
     */
    default Biome getBukkitBiome() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).getOrThrow(getKey());
    }

    /**
     * Returns <code>true</code> if this biome wraps a custom biome.
     *
     * @return true if this biome is custom.
     */
    default boolean isCustom() {
        return !getKey().getNamespace().equals(NamespacedKey.MINECRAFT);
    }

    /**
     * Returns <code>true</code> if this biome wraps a custom biome registered
     * by another plugin.
     *
     * @return true if the biome is from an external plugin.
     */
    boolean isExternalPlugin();

    /**
     * Returns <code>true</code> if this biome has been modified from its
     * default state. For custom biomes, this method will always return
     * <code>true</code>.
     *
     * @return true if the biome has been modified.
     */
    boolean isDirty();

    @Nullable
    static BiomeWrapper deserialize(JsonObject jsonObject) {
        BiomeCompatibility _compatibility = BiomeCompatibilityAPI.getBiomeCompatibility();
        String keyStr = jsonObject.get("Key").getAsString();
        boolean isCustom = jsonObject.get("Custom").getAsBoolean();
        boolean isExternalPlugin = jsonObject.get("External_Plugin").getAsBoolean();
        SpecialEffectsBuilder specialEffects = gson.fromJson(jsonObject.get("SpecialEffects"), SpecialEffectsBuilder.class);
        NamespacedKey key = NamespacedKey.fromString(keyStr);
        assert key != null;
        String baseKeyStr = jsonObject.get("BaseKey").getAsString();
        NamespacedKey baseKey = NamespacedKey.fromString(baseKeyStr);
        Biome base = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).getOrThrow(baseKey);

        if (isExternalPlugin) {
            BiomeWrapper wrapper = BiomeRegistry.getInstance().get(key);
            if (wrapper == null) {
                BiomeManager.inst().getLogger().severe("An externally added biome '" + key + "' doesn't exist anymore... Removing data.");
                return null;
            }

            wrapper.setSpecialEffects(specialEffects);
            return null;
        }

        BiomeWrapper baseWrapper = BiomeRegistry.getInstance().getBukkit(base);
        BiomeWrapper wrapper = _compatibility.createBiome(key, baseWrapper);
        wrapper.setSpecialEffects(specialEffects);

        if (isCustom) {
            wrapper.register(true);
        }

        return wrapper;
    }

    default JsonObject serialize() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("Key", getKey().toString());
        jsonObject.addProperty("Custom", isCustom());
        jsonObject.addProperty("External_Plugin", isExternalPlugin());
        jsonObject.add("SpecialEffects", gson.toJsonTree(getSpecialEffects()));
        jsonObject.addProperty("BaseKey", getBase().getKey().toString());
        return jsonObject;
    }
}