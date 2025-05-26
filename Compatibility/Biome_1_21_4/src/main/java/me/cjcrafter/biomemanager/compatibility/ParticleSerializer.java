package me.cjcrafter.biomemanager.compatibility;

import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.*;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftRegistry;
import org.joml.Vector3f;

import java.util.Map;
import java.util.function.Function;

public record ParticleSerializer<T extends ParticleOptions>(Function<T, JsonObject> serializer,
                                                            Function<JsonObject, T> deserializer) {

    public JsonObject serialize(final T particle) {
        return serializer.apply(particle);
    }

    public T deserialize(final JsonObject json) {
        return deserializer.apply(json);
    }

    private static Map<ParticleType<?>, ParticleSerializer<?>> serializers = Maps.newHashMap();

    static {
        /*
        public static final ParticleType<BlockParticleOption> BLOCK = register("block", false, BlockParticleOption::codec, BlockParticleOption::streamCodec);
public static final ParticleType<BlockParticleOption> BLOCK_MARKER = register("block_marker", true, BlockParticleOption::codec, BlockParticleOption::streamCodec);
public static final ParticleType<DustParticleOptions> DUST = register("dust", false, (particleType) -> DustParticleOptions.CODEC, (particleType) -> DustParticleOptions.STREAM_CODEC);
public static final ParticleType<DustColorTransitionOptions> DUST_COLOR_TRANSITION = register("dust_color_transition", false, (particleType) -> DustColorTransitionOptions.CODEC, (particleType) -> DustColorTransitionOptions.STREAM_CODEC);
public static final ParticleType<ColorParticleOption> ENTITY_EFFECT = register("entity_effect", false, ColorParticleOption::codec, ColorParticleOption::streamCodec);
public static final ParticleType<BlockParticleOption> FALLING_DUST = register("falling_dust", false, BlockParticleOption::codec, BlockParticleOption::streamCodec);
public static final ParticleType<SculkChargeParticleOptions> SCULK_CHARGE = register("sculk_charge", true, (particleType) -> SculkChargeParticleOptions.CODEC, (particleType) -> SculkChargeParticleOptions.STREAM_CODEC);
public static final ParticleType<ItemParticleOption> ITEM = register("item", false, ItemParticleOption::codec, ItemParticleOption::streamCodec);
public static final ParticleType<VibrationParticleOption> VIBRATION = register("vibration", true, (particleType) -> VibrationParticleOption.CODEC, (particleType) -> VibrationParticleOption.STREAM_CODEC);
public static final ParticleType<TrailParticleOption> TRAIL = register("trail", false, (particleType) -> TrailParticleOption.CODEC, (particleType) -> TrailParticleOption.STREAM_CODEC);
public static final ParticleType<ShriekParticleOption> SHRIEK = register("shriek", false, (particleType) -> ShriekParticleOption.CODEC, (particleType) -> ShriekParticleOption.STREAM_CODEC);
public static final ParticleType<BlockParticleOption> DUST_PILLAR = register("dust_pillar", false, BlockParticleOption::codec, BlockParticleOption::streamCodec);
public static final ParticleType<BlockParticleOption> BLOCK_CRUMBLE = register("block_crumble", false, BlockParticleOption::codec, BlockParticleOption::streamCodec);
         */
        serializers.put(ParticleTypes.BLOCK, new ParticleSerializer<>(
                particle -> {
                    JsonObject json = new JsonObject();
                    json.addProperty("type", "block");
                    json.addProperty("block", particle.getState().getBukkitMaterial().name());
                    return json;
                },
                json -> {
                    if (!json.has("block")) {
                        throw new IllegalArgumentException("Missing block property: " + json);
                    }

                    String blockName = json.get("block").getAsString();
                    Material material = Material.matchMaterial(blockName);
                    if (material == null) {
                        throw new IllegalArgumentException("Invalid block material: " + blockName);
                    }
                    return new BlockParticleOption(ParticleTypes.BLOCK, CraftRegistry.bukkitToMinecraft(material.asBlockType()));
                }
        ));
        serializers.put(ParticleTypes.BLOCK_MARKER, serializers.get(ParticleTypes.BLOCK));
        serializers.put(ParticleTypes.DUST, new ParticleSerializer<>(
                particle -> {
                    JsonObject json = new JsonObject();
                    json.addProperty("type", "dust");
                    JsonObject vector = new JsonObject();
                    Vector3f color = particle.getColor();
                    vector.addProperty("x", color.x);
                    vector.addProperty("y", color.y);
                    vector.addProperty("z", color.z);
                    json.add("color", vector);
                    json.addProperty("scale", particle.getScale());
                    return json;
                },
                json -> {
                    if (!json.has("type")) {
                        throw new IllegalArgumentException("Missing type property: " + json);
                    }

                    JsonObject vector = json.get("color").getAsJsonObject();
//                    Vector3f vecColor = new Vector3f(vector.get("x").getAsFloat(), vector.get("y").getAsFloat(), vector.get("z").getAsFloat());
                    return new DustParticleOptions(1, 1f);
                }
        ));
        serializers.put(ParticleTypes.ITEM, new ParticleSerializer<>(
                particle -> {
                    JsonObject json = new JsonObject();
                    json.addProperty("type", "item");
                    json.add("item", Bukkit.getUnsafe().serializeItemAsJson(particle.getItem().asBukkitCopy()));
                    return json;
                },
                json -> {
                    if (!json.has("item")) {
                        throw new IllegalArgumentException("Missing item property: " + json);
                    }

                    JsonObject jsonItem = json.get("item").getAsJsonObject();
                    ItemStack item = ItemStack.fromBukkitCopy(Bukkit.getUnsafe().deserializeItemFromJson(jsonItem));

                    return new ItemParticleOption(ParticleTypes.ITEM, item);
                }
        ));
    }

}
