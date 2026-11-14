package fr.citizen.realisme.client;

import com.mojang.blaze3d.platform.NativeImage;
import fr.citizen.realisme.CitizenRealisme;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pack de ressources généré à la volée : les écrans inventaire, établi et table d'enchantement
 * de Minecraft sont recolorés en parchemin (gris -> tons de vieux papier et d'encre brune).
 * On part des textures d'origine du jeu : la disposition des cases reste exactement la même.
 */
public final class ParchmentPack implements PackResources {
    static final List<ResourceLocation> TARGETS = List.of(
            mc("textures/gui/container/inventory.png"),
            mc("textures/gui/container/crafting_table.png"),
            mc("textures/gui/container/enchanting_table.png"),
            mc("textures/gui/sprites/container/enchanting_table/enchantment_slot.png"),
            mc("textures/gui/sprites/container/enchanting_table/enchantment_slot_disabled.png"),
            mc("textures/gui/sprites/container/enchanting_table/enchantment_slot_highlighted.png"));

    private static final Map<ResourceLocation, byte[]> CACHE = new ConcurrentHashMap<>();
    private static final byte[] NONE = new byte[0];

    private final PackLocationInfo info;

    public ParchmentPack(PackLocationInfo info) {
        this.info = info;
    }

    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    @Nullable
    private static byte[] recolored(ResourceLocation loc) {
        byte[] data = CACHE.computeIfAbsent(loc, ParchmentPack::build);
        return data.length == 0 ? null : data;
    }

    private static byte[] build(ResourceLocation loc) {
        try {
            IoSupplier<InputStream> original = Minecraft.getInstance().getVanillaPackResources().getResource(PackType.CLIENT_RESOURCES, loc);
            if (original == null) return NONE;
            try (InputStream in = original.get(); NativeImage image = NativeImage.read(in)) {
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        image.setPixelRGBA(x, y, Parchment.recolor(image.getPixelRGBA(x, y), x, y));
                    }
                }
                return image.asByteArray();
            }
        } catch (Exception e) {
            CitizenRealisme.LOGGER.warn("[Citizen Réalisme] Texture parchemin impossible pour {}", loc, e);
            return NONE;
        }
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.CLIENT_RESOURCES || !TARGETS.contains(location)) return null;
        byte[] data = recolored(location);
        return data == null ? null : () -> new ByteArrayInputStream(data);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !namespace.equals(ResourceLocation.DEFAULT_NAMESPACE)) return;
        String prefix = path.endsWith("/") ? path : path + "/";
        for (ResourceLocation loc : TARGETS) {
            if (!loc.getPath().startsWith(prefix)) continue;
            byte[] data = recolored(loc);
            if (data != null) output.accept(loc, () -> new ByteArrayInputStream(data));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(ResourceLocation.DEFAULT_NAMESPACE) : Set.of();
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        if (deserializer.getMetadataSectionName().equals(PackMetadataSection.TYPE.getMetadataSectionName())) {
            return (T) new PackMetadataSection(Component.literal("Interface parchemin de Citizen"),
                    SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES), Optional.empty());
        }
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return info;
    }

    @Override
    public void close() {
    }

    static void clearCache() {
        CACHE.clear();
    }
}
