package me.cjcrafter.biomemanager;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.google.gson.JsonObject;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.cjcrafter.biomemanager.command.Command;
import me.cjcrafter.biomemanager.command.WorldEditCommand;
import me.cjcrafter.biomemanager.compatibility.BiomeCompatibilityAPI;
import me.cjcrafter.biomemanager.compatibility.BiomeWrapper;
import me.cjcrafter.biomemanager.compatibility.JsonSerializable;
import me.cjcrafter.biomemanager.listeners.BiomeRandomizer;
import me.cjcrafter.biomemanager.listeners.EditModeListener;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SingleLineChart;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.logging.Level;

import static com.comphenix.protocol.PacketType.Play.Server.MAP_CHUNK;

public class BiomeManager extends JavaPlugin {

    private static BiomeManager INSTANCE;
    public EditModeListener editModeListener;
    public BiomeRandomizer biomeRandomizer;

    public void onLoad() {
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        loadConfig();

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            Commands registrar = commands.registrar();
            Command.register(registrar);
            WorldEditCommand.register(registrar);
        });

//        if (getServer().getPluginManager().getPlugin("WorldEdit") != null)
//            WorldEditCommand.register();

        // Register packet listeners
        if (!getConfig().getBoolean("Disable_Biome_Variations", false)) {
            ProtocolManager manager = ProtocolLibrary.getProtocolManager();
            manager.addPacketListener(new PacketAdapter(this, MAP_CHUNK) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    BiomeCompatibilityAPI.getBiomeCompatibility().handleChunkBiomesPacket(event);
                }
            });
        }

        registerBStats();

        // Register events
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(editModeListener = new EditModeListener(), this);
//        pm.registerEvents(biomeRandomizer = new BiomeRandomizer(), this);
    }

    public void onDisable() {
    }

    public void loadConfig() {
        // Make sure we load NMS onEnable
        BiomeCompatibilityAPI.loadBiomeCompatibility();

        if (!getDataFolder().exists() || getDataFolder().listFiles() == null || getDataFolder().listFiles().length == 0) {
            getLogger().info("Copying files from jar (This process may take up to 30 seconds during the first load!)");
//            FileUtil.copyResourcesTo(getClassLoader().getResource("BiomeManager"), getDataFolder().toPath());
            saveDefaultConfig();
        }

        try {
            File biomesFolder = new File(getDataFolder(), "biomes");
            biomesFolder.mkdirs();

            Files.walkFileTree(biomesFolder.toPath(), new SimpleFileVisitor<>() {
                @NotNull
                public FileVisitResult visitFile(@NotNull Path file, BasicFileAttributes attrs) {
                    try (BufferedReader br = new BufferedReader(new FileReader(file.toFile()))) {

                        BiomeWrapper biomeWrapper = BiomeWrapper.deserialize(JsonSerializable.gson.fromJson(br, JsonObject.class));
                        if (biomeWrapper == null) {
                            getLogger().warning("Biome wrapper could not be deserialized!");
                            return FileVisitResult.CONTINUE;
                        }
                    } catch (Throwable ex) {
                        getLogger().log(Level.SEVERE, "Failed to load biome: " + file, ex);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Failed to load biomes", ex);
        }
    }

    public void deleteRecursively(File directory) {
        if (!directory.isDirectory())
            throw new InternalError(directory + " is not a directory");

        for (File file : directory.listFiles()) {
            if (file.isDirectory())
                deleteRecursively(file);

            file.delete();
        }
    }

    public void saveToConfig() {
        // Save biome variations
//        biomeRandomizer.save();

        // All custom biomes are stored in this folder, make sure it exists.
        File overridesFolder = new File(getDataFolder(), "biomes");
        if (overridesFolder.exists())
            deleteRecursively(overridesFolder);
        overridesFolder.mkdirs();

        Set<NamespacedKey> keys = BiomeRegistry.getInstance().getKeys(true);
        for (NamespacedKey key : keys) {
            BiomeWrapper wrapper = BiomeRegistry.getInstance().get(key);

            // Each namespace (usually a lowercase plugin name, like
            // "biomemanager") gets their own folder to hold their list of
            // custom biomes.
            File namespaceDirectory = new File(overridesFolder, key.getNamespace());
            namespaceDirectory.mkdirs();

            File configFile = new File(namespaceDirectory, key.getKey() + ".json");
            try (FileWriter writer = new FileWriter(configFile)) {
                String object = JsonSerializable.gson.toJson(wrapper.serialize());
                writer.write(object);
            } catch (IOException ex) {
                getLogger().log(Level.SEVERE, "Error creating/saving file '" + configFile + "'", ex);
            }
        }
    }

    public void registerBStats() {
        Metrics metrics = new Metrics(this, 17119);
        metrics.addCustomChart(new SingleLineChart("custom_biomes",
                () -> BiomeRegistry.getInstance().getKeys(true).size()));
    }

    public static BiomeManager inst() {
        return INSTANCE;
    }
}
