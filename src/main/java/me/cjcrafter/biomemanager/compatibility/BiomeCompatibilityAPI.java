package me.cjcrafter.biomemanager.compatibility;

import io.papermc.paper.ServerBuildInfo;

import java.lang.reflect.InvocationTargetException;

public class BiomeCompatibilityAPI {

    private static final BiomeCompatibility BIOME_COMPATIBILITY;

    static {
        try {
            String serverVersion = ServerBuildInfo.buildInfo().minecraftVersionId().replace(".", "_");
            Class<? extends BiomeCompatibility> currentVersion = (Class<? extends BiomeCompatibility>) Class.forName("me.cjcrafter.biomemanager.compatibility.v"+ serverVersion);
            BIOME_COMPATIBILITY = currentVersion.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | InvocationTargetException | InstantiationException | IllegalAccessException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static void loadBiomeCompatibility() {
        assert BIOME_COMPATIBILITY != null;
    }

    public static BiomeCompatibility getBiomeCompatibility() {
        return BIOME_COMPATIBILITY;
    }
}
