package me.cjcrafter.biomemanager.compatibility;

import com.comphenix.protocol.events.PacketEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import me.cjcrafter.biomemanager.BiomeRegistry;
import me.cjcrafter.biomemanager.events.BiomePacketEvent;
import me.deecaad.core.utils.LogLevel;
import me.deecaad.core.utils.ReflectionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;

import java.lang.reflect.Field;

public class v1_21_R1 implements BiomeCompatibility {

    private static final Field chunkBiomesField;

    static {
        chunkBiomesField = ReflectionUtil.getField(ClientboundLevelChunkPacketData.class, byte[].class);
    }

    private Registry<Biome> biomes;

    public v1_21_R1() {
        biomes = MinecraftServer.getServer().registryAccess().registry(Registries.BIOME).orElseThrow();
        for (Biome biome : biomes) {

            ResourceLocation nmsKey = biomes.getKey(biome);
            if (nmsKey == null) {
                BiomeManager.inst().debug.error("Could not find key for: " + biome);
                continue;
            }

            try {
                NamespacedKey key = new NamespacedKey(nmsKey.getNamespace(), nmsKey.getPath());
                BiomeRegistry.getInstance().add(key, new BiomeWrapper_1_21_R1(biome));
            } catch (Throwable ex) {
                BiomeManager.inst().debug.error("Failed to load biome: " + nmsKey);
                BiomeManager.inst().debug.log(LogLevel.ERROR, ex.getMessage(), ex);
            }
        }
    }

    private Biome getBiome(NamespacedKey key) {
        return biomes.get(ResourceLocation.fromNamespaceAndPath(key.getNamespace(), key.getKey()));
    }

    @Override
    public BiomeWrapper createBiome(NamespacedKey key, BiomeWrapper base) {
        return new BiomeWrapper_1_21_R1(key, (BiomeWrapper_1_21_R1) base);
    }

    @Override
    public BiomeWrapper getBiomeAt(Block block) {
        ServerLevel world = ((CraftWorld) block.getWorld()).getHandle();

        // Don't attempt to load a chunk! This will lag the server.
        BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
        LevelChunk chunk = world.getChunkIfLoaded(pos);
        if (chunk == null)
            return null;

        // Get the namespaced key from the biome
        Biome biome = world.getBiome(pos).value();
        ResourceKey<Biome> location = biomes.getResourceKey(biome).orElseThrow();
        NamespacedKey key = new NamespacedKey(location.location().getNamespace(), location.location().getPath());

        // If there is no wrapper setup for the given key, create a new one.
        BiomeWrapper wrapper = BiomeRegistry.getInstance().get(key);
        if (wrapper == null)
            wrapper = new BiomeWrapper_1_21_R1(getBiome(key));

        return wrapper;
    }

    @Override
    public void handleChunkBiomesPacket(PacketEvent event) {
        ClientboundLevelChunkWithLightPacket packet = (ClientboundLevelChunkWithLightPacket) event.getPacket().getHandle();
        ClientboundLevelChunkPacketData chunkData = packet.getChunkData();

        // 4 comes from 16 / 4 (<- and that 4 is the width of each biome section)
        int ySections = ((CraftWorld) event.getPlayer().getWorld()).getHandle().getSectionsCount();
        BiomeWrapper[] biomes = new BiomeWrapper[4 * 4 * 4 * ySections];
        LevelChunkSection[] sections = new LevelChunkSection[ySections];

        int counter = 0;
        FriendlyByteBuf sectionBuffer = chunkData.getReadBuffer();
        for (int i = 0; i < ySections; i++) {
            sections[i] = new LevelChunkSection(this.biomes);
            sections[i].read(sectionBuffer);

            for (int x = 0; x < 4; x++) {
                for (int y = 0; y < 4; y++) {
                    for (int z = 0; z < 4; z++) {
                        Biome nmsBiome = sections[i].getNoiseBiome(x, y, z).value();
                        int id = this.biomes.getId(nmsBiome);
                        biomes[counter++] = BiomeRegistry.getInstance().getById(id);
                    }
                }
            }
        }

        BiomePacketEvent bukkitEvent = new BiomePacketEvent(event, biomes);
        Bukkit.getPluginManager().callEvent(bukkitEvent);

        int bufferSize = 0;
        counter = 0;
        for (LevelChunkSection section : sections) {
            for (int x = 0; x < 4; x++) {
                for (int y = 0; y < 4; y++) {
                    for (int z = 0; z < 4; z++) {
                        BiomeWrapper wrapper = biomes[counter++];
                        // Seems to occur during generation?
                        if (wrapper == null)
                            continue;

                        int id = wrapper.getId();
                        section.setBiome(x, y, z, Holder.direct(this.biomes.byId(id)));
                    }
                }
            }

            bufferSize += section.getSerializedSize();
        }

        byte[] bytes = new byte[bufferSize];
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        buffer.writerIndex(0);
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(buffer);
        for (LevelChunkSection section : sections) {
            section.write(friendlyByteBuf);
        }

        ReflectionUtil.setField(chunkBiomesField, chunkData, bytes);
    }
}
