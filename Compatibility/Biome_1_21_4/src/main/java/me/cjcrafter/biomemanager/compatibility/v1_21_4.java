package me.cjcrafter.biomemanager.compatibility;

import com.comphenix.protocol.events.PacketEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.cjcrafter.biomemanager.BiomeManager;
import me.cjcrafter.biomemanager.BiomeRegistry;
import me.cjcrafter.biomemanager.events.BiomePacketEvent;
import me.deecaad.core.lib.scheduler.util.FieldAccessor;
import me.deecaad.core.lib.scheduler.util.ReflectionUtil;
import me.deecaad.core.utils.LogLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBiome;

public class v1_21_4 implements BiomeCompatibility {

    private static final FieldAccessor chunkBiomesAccessor;

    static {
        chunkBiomesAccessor = ReflectionUtil.getField(ClientboundLevelChunkPacketData.class, byte[].class);
    }

    private final Registry<org.bukkit.block.Biome> biomes;

    public static final MappedRegistry<net.minecraft.world.level.biome.Biome> biomeRegistry = (MappedRegistry<net.minecraft.world.level.biome.Biome>) MinecraftServer.getServer().registryAccess().lookup(Registries.BIOME).orElseThrow();

    public v1_21_4() {

        biomes = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        for (Biome biome : biomes) {
            NamespacedKey key = biome.getKey();
            try {
                BiomeRegistry.getInstance().add(key, new BiomeWrapper_1_21_4(CraftRegistry.bukkitToMinecraft(biome)));
            } catch (Throwable ex) {
                BiomeManager.inst().debug.error("Failed to load biome: " + key);
                BiomeManager.inst().debug.log(LogLevel.ERROR, ex.getMessage(), ex);
            }
        }
    }

    private Biome getBiome(NamespacedKey key) {
        return biomes.get(key);
    }

    private net.minecraft.world.level.biome.Biome getMinecraftBiome(NamespacedKey key) {
        return CraftRegistry.bukkitToMinecraft(biomes.get(key));
    }

    private net.minecraft.world.level.biome.Biome fromBukkitBiome(Biome biome) {
        return CraftBiome.bukkitToMinecraft(biome);
    }

    private Biome fromMinecraftBiome(net.minecraft.world.level.biome.Biome biome) {
        return CraftBiome.minecraftToBukkit(biome);
    }

    private int getBiomeId(net.minecraft.world.level.biome.Biome biome) {
        return 1;
    }

    @Override
    public BiomeWrapper createBiome(NamespacedKey key, BiomeWrapper base) {
        return new BiomeWrapper_1_21_4(key, (BiomeWrapper_1_21_4) base);
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
//        net.minecraft.world.level.biome.Biome biome =
//        ResourceKey<net.minecraft.world.level.biome.Biome> location = biomes.getResourceKey(biome).orElseThrow();
//        NamespacedKey key = new NamespacedKey(location.location().getNamespace(), location.location().getPath());
        NamespacedKey key = block.getBiome().getKey();

        // If there is no wrapper setup for the given key, create a new one.
        BiomeWrapper wrapper = BiomeRegistry.getInstance().get(key);
        if (wrapper == null)
            wrapper = new BiomeWrapper_1_21_4(getMinecraftBiome(key));

        return wrapper;
    }

    @Override
    public void handleChunkBiomesPacket(PacketEvent event) {
        ClientboundLevelChunkWithLightPacket packet = (ClientboundLevelChunkWithLightPacket) event.getPacket().getHandle();
        ClientboundLevelChunkPacketData chunkData = packet.getChunkData();

        // 4 comes from 16 / 4 (<- and that 4 is the width of each biome section)
        CraftWorld world = (CraftWorld) event.getPlayer().getWorld();
        ServerLevel level = world.getHandle();
        int ySections = level.getSectionsCount();
        ChunkPos chunkPos = new ChunkPos(packet.getX(), packet.getZ());
        BiomeWrapper[] biomes = new BiomeWrapper[4 * 4 * 4 * ySections];
        LevelChunkSection[] sections = new LevelChunkSection[ySections];


        int counter = 0;
        FriendlyByteBuf sectionBuffer = chunkData.getReadBuffer();
        for (int i = 0; i < ySections; i++) {
            sections[i] = new LevelChunkSection(biomeRegistry, level, chunkPos, i);
            sections[i].read(sectionBuffer);

            for (int x = 0; x < 4; x++) {
                for (int y = 0; y < 4; y++) {
                    for (int z = 0; z < 4; z++) {
                        net.minecraft.world.level.biome.Biome biome = sections[i].getNoiseBiome(x, y, z).value();
                        int id = biomeRegistry.getId(biome);
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
                        section.setBiome(x, y, z, Holder.direct(biomeRegistry.byId(id)));
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

        chunkBiomesAccessor.set(chunkData, bytes);
    }
}
