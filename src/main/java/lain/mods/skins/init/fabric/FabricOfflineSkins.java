package lain.mods.skins.init.fabric;

import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import lain.mods.skins.api.SkinProviderAPI;
import lain.mods.skins.api.interfaces.ISkin;
import lain.mods.skins.impl.ConfigOptions;
import lain.mods.skins.impl.LegacyConversion;
import lain.mods.skins.impl.PlayerProfile;
import lain.mods.skins.impl.fabric.CustomSkinTexture;
import lain.mods.skins.providers.CrafatarCachedCapeProvider;
import lain.mods.skins.providers.CrafatarCachedSkinProvider;
import lain.mods.skins.providers.CustomServerCachedCapeProvider;
import lain.mods.skins.providers.CustomServerCachedSkinProvider;
import lain.mods.skins.providers.MojangCachedCapeProvider;
import lain.mods.skins.providers.MojangCachedSkinProvider;
import lain.mods.skins.providers.UserManagedCapeProvider;
import lain.mods.skins.providers.UserManagedSkinProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.events.client.ClientTickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

public class FabricOfflineSkins implements ClientModInitializer
{

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> DefaultSkins = ImmutableSet.of("textures/entity/steve.png", "textures/entity/alex.png");

    private static final Map<ByteBuffer, CustomSkinTexture> textures = new WeakHashMap<>();

    private static boolean skinPass = false;
    private static boolean capePass = false;
    private static boolean overwrite = true;

    private static Identifier generateRandomLocation()
    {
        return new Identifier("offlineskins", String.format("textures/generated/%s", UUID.randomUUID().toString()));
    }

    public static Identifier getLocationCape(GameProfile profile, Identifier result)
    {
        if (capePass)
            return null;

        if (overwrite || isDefaultSkin(result))
        {
            ISkin skin = SkinProviderAPI.CAPE.getSkin(PlayerProfile.wrapGameProfile(profile));
            if (skin != null && skin.isDataReady())
                return getOrCreateTexture(skin.getData(), skin).getLocation();
        }
        return null;
    }

    public static Identifier getLocationSkin(GameProfile profile, Identifier result)
    {
        if (skinPass)
            return null;

        if (overwrite || isDefaultSkin(result))
        {
            ISkin skin = SkinProviderAPI.SKIN.getSkin(PlayerProfile.wrapGameProfile(profile));
            if (skin != null && skin.isDataReady())
                return getOrCreateTexture(skin.getData(), skin).getLocation();
        }
        return null;
    }

    private static CustomSkinTexture getOrCreateTexture(ByteBuffer data, ISkin skin)
    {
        if (!textures.containsKey(data))
        {
            CustomSkinTexture texture = new CustomSkinTexture(generateRandomLocation(), data);
            MinecraftClient.getInstance().getTextureManager().registerTexture(texture.getLocation(), texture);
            textures.put(data, texture);

            if (skin != null)
            {
                skin.setRemovalListener(s -> {
                    if (data == s.getData())
                    {
                        MinecraftClient.getInstance().execute(() -> {
                            MinecraftClient.getInstance().getTextureManager().destroyTexture(texture.getLocation());
                            textures.remove(data);
                        });
                    }
                });
            }
        }
        return textures.get(data);
    }

    public static String getSkinType(GameProfile profile, String result)
    {
        Identifier location = getLocationSkin(profile, null);
        if (location != null)
        {
            ISkin skin = SkinProviderAPI.SKIN.getSkin(PlayerProfile.wrapGameProfile(profile));
            if (skin != null && skin.isDataReady())
                return skin.getSkinType();
        }
        return null;
    }

    private static boolean isDefaultSkin(Identifier id)
    {
        return "minecraft".equals(id.getNamespace()) && DefaultSkins.contains(id.getPath());
    }

    @Override
    public void onInitializeClient()
    {
        ClientTickEvent.CLIENT.register(mc -> {
            if (mc.world != null)
            {
                for (PlayerEntity player : mc.world.players)
                {
                    SkinProviderAPI.SKIN.getSkin(PlayerProfile.wrapGameProfile(player.getGameProfile()));
                    SkinProviderAPI.CAPE.getSkin(PlayerProfile.wrapGameProfile(player.getGameProfile()));
                }
            }
        });

        reloadConfig();
    }

    public void reloadConfig()
    {
        Path pathToConfig = Paths.get(".", "config", "offlineskins.json");
        pathToConfig.toFile().getParentFile().mkdirs();
        if (!pathToConfig.toFile().exists())
        {
            try (Writer w = Files.newBufferedWriter(pathToConfig, StandardCharsets.UTF_8))
            {
                gson.toJson(new ConfigOptions().defaultOptions(), w);
            }
            catch (Throwable t)
            {
                t.printStackTrace();
                System.err.println("[OfflineSkins] Failed to write default config file.");
            }
        }
        ConfigOptions config = null;
        try
        {
            config = gson.fromJson(Files.lines(pathToConfig, StandardCharsets.UTF_8).collect(Collectors.joining(System.getProperty("line.separator"))), ConfigOptions.class);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            System.err.println("[OfflineSkins] Failed to read config file.");
            config = new ConfigOptions();
        }
        config.validate();

        SkinProviderAPI.SKIN.clearProviders();
        SkinProviderAPI.SKIN.registerProvider(new UserManagedSkinProvider(Paths.get(".", "cachedImages")).withFilter(LegacyConversion.createFilter()));
        if (config.useCustomServer)
            SkinProviderAPI.SKIN.registerProvider(new CustomServerCachedSkinProvider(Paths.get(".", "cachedImages", "custom"), config.hostCustomServer).withFilter(LegacyConversion.createFilter()));
        if (config.useMojang)
            SkinProviderAPI.SKIN.registerProvider(new MojangCachedSkinProvider(Paths.get(".", "cachedImages", "mojang")).withFilter(LegacyConversion.createFilter()));
        if (config.useCrafatar)
            SkinProviderAPI.SKIN.registerProvider(new CrafatarCachedSkinProvider(Paths.get(".", "cachedImages", "crafatar")).withFilter(LegacyConversion.createFilter()));

        SkinProviderAPI.CAPE.clearProviders();
        SkinProviderAPI.CAPE.registerProvider(new UserManagedCapeProvider(Paths.get(".", "cachedImages")));
        if (config.useCustomServer)
            SkinProviderAPI.CAPE.registerProvider(new CustomServerCachedCapeProvider(Paths.get(".", "cachedImages", "custom"), config.hostCustomServer));
        if (config.useMojang)
            SkinProviderAPI.CAPE.registerProvider(new MojangCachedCapeProvider(Paths.get(".", "cachedImages", "mojang")));
        if (config.useCrafatar)
            SkinProviderAPI.CAPE.registerProvider(new CrafatarCachedCapeProvider(Paths.get(".", "cachedImages", "crafatar")));
    }

}
