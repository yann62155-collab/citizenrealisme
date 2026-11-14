package fr.citizen.realisme.client;

import fr.citizen.realisme.CitizenRealisme;
import fr.citizen.realisme.Weights;
import fr.citizen.realisme.net.CraftPayload;
import fr.citizen.realisme.net.EnchantPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Optional;

/** Partie PC : HUD du poids, barre de fabrication, taux du rituel et interface parchemin. */
@Mod(value = CitizenRealisme.MODID, dist = Dist.CLIENT)
public final class ClientSetup {
    private static final int GOLD = 0xFFD8B25A;
    private static final int INK = 0xFF2B1A0E;
    private static final int PAPER = 0xF0E6D2A2;

    public ClientSetup(IEventBus modBus) {
        modBus.addListener(this::onPacks);
        modBus.addListener(this::onGuiLayers);
        NeoForge.EVENT_BUS.addListener(this::onScreenRender);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onTooltip);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientState.reset());
    }

    // ------------------------------------------------------------------ interface parchemin

    private void onPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        PackLocationInfo info = new PackLocationInfo(CitizenRealisme.MODID + "_parchemin",
                Component.literal("Citizen : interface parchemin"), PackSource.BUILT_IN, Optional.empty());
        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new ParchmentPack(location);
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return new ParchmentPack(location);
            }
        };
        Pack pack = Pack.readMetaAndCreate(info, supplier, PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) {
            ParchmentPack.clearCache();
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }

    // ------------------------------------------------------------------ HUD poids

    private void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CitizenRealisme.MODID, "poids"), this::renderHud);
    }

    private void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientState.hasHud || mc.player == null || mc.options.hideGui || mc.player.isSpectator() || mc.player.isCreative()) return;
        float cap = ClientState.capacity;
        if (cap <= 0) return;
        float ratio = ClientState.weight / cap;
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        int x = w / 2 + 96;
        int y = h - 20;
        int barW = 44;
        int color = ClientState.level >= 2 ? 0xFFC0392B : ClientState.level == 1 ? 0xFFE08A1E : ratio > 0.85f ? 0xFFE3C23A : 0xFF7DAA3C;
        g.fill(x - 1, y - 1, x + barW + 1, y + 5, INK);
        g.fill(x, y, x + barW, y + 4, 0xFF6E4A27);
        g.fill(x, y, x + Math.round(barW * Math.min(1f, ratio)), y + 4, color);
        if (ratio > 1f) {
            int over = Math.round(barW * Math.min(1f, ratio - 1f));
            g.fill(x, y + 3, x + over, y + 4, 0xFF5A0F0F);
        }
        String text = Weights.format(ClientState.weight) + "/" + Weights.format(cap) + " kg";
        g.drawString(font, text, x, y + 7, ClientState.level > 0 ? color : 0xFFE6D2A2, true);
        if (ClientState.level >= 2) {
            g.drawString(font, ClientState.level >= 3 ? "Ni sprint ni saut" : "Pas de sprint", x, y - 11, color, true);
        }
    }

    private void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && ClientState.hasHud && ClientState.level >= 2 && player.isSprinting() && !player.isCreative()) {
            player.setSprinting(false);
        }
    }

    // ------------------------------------------------------------------ écrans

    private void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();

        if (screen instanceof CraftingScreen || screen instanceof InventoryScreen) {
            CraftPayload c = ClientState.craft;
            if (!c.visible()) return;
            int rx = screen instanceof CraftingScreen ? 124 : 154;
            int ry = screen instanceof CraftingScreen ? 35 : 28;
            if (c.running()) {
                int bx = left + rx - 2;
                int by = top + ry + 18;
                g.fill(bx, by, bx + 20, by + 3, INK);
                g.fill(bx, by, bx + Math.round(20 * c.progress()), by + 3, GOLD);
            }
            label(g, font, c.text(), left, top - 12, screen.getXSize());
        } else if (screen instanceof EnchantmentScreen) {
            EnchantPayload e = ClientState.enchant;
            if (e == null) return;
            int x = left + screen.getXSize() + 4;
            for (int i = 0; i < 3; i++) {
                if (e.rates()[i] < 0) continue;
                int y = top + 14 + 19 * i + 2;
                String rate = e.rates()[i] + " %";
                int color = e.ok()[i] ? 0xFF7DD35C : 0xFFE05A4A;
                g.fill(x - 2, y - 2, x + 120, y + 18, PAPER);
                g.drawString(font, rate + (e.ok()[i] ? " ✔" : " ✘"), x, y, color, false);
                g.drawString(font, font.plainSubstrByWidth(e.texts()[i], 118), x, y + 9, INK, false);
            }
            g.drawString(font, Component.literal("Rituel : réussite / catalyseurs").withStyle(ChatFormatting.ITALIC), x, top + 2, 0xFFE6D2A2, true);
        }
    }

    private static void label(GuiGraphics g, Font font, String text, int left, int y, int width) {
        int tw = font.width(text);
        int x = left + (width - tw) / 2;
        g.fill(x - 3, y - 2, x + tw + 3, y + 10, PAPER);
        g.drawString(font, text, x, y, INK, false);
    }

    private void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        double unit = Weights.unit(stack);
        if (unit <= 0) return;
        String text = "Poids : " + Weights.format(unit) + " kg" + (stack.getCount() > 1 ? " (pile : " + Weights.format(unit * stack.getCount()) + " kg)" : "");
        event.getToolTip().add(Component.literal(text).withStyle(ChatFormatting.DARK_GRAY));
    }
}
