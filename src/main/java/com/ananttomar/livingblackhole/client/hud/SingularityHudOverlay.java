package com.ananttomar.livingblackhole.client.hud;

import com.ananttomar.livingblackhole.LivingBlackHole;
import com.ananttomar.livingblackhole.client.ClientEffects;
import com.ananttomar.livingblackhole.client.ClientSingularityState;
import com.ananttomar.livingblackhole.entity.BlackHoleEntity;
import com.ananttomar.livingblackhole.network.CinematicPacket;
import com.ananttomar.livingblackhole.singularity.SingularityEvent;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Locale;
import java.util.Random;

/**
 * The persistent singularity HUD. While any escaped black hole exists it shows, in flashing red,
 * "WARNING: BLACK HOLE ESCAPED!" and the exact Core Fragment coordinates, plus a live tracker
 * (distance + heading arrow per fragment), status telemetry, a pulsing red vignette and the
 * cinematic flashes/banners of the breach and the collapse. It only disappears once the black
 * hole is destroyed.
 */
public final class SingularityHudOverlay {
    public static final IGuiOverlay OVERLAY = SingularityHudOverlay::render;

    private static final ResourceLocation VIGNETTE = LivingBlackHole.id("textures/gui/vignette.png");
    private static final ResourceLocation STATIC = LivingBlackHole.id("textures/gui/static.png");
    private static final String[] NUMERALS = {"I", "II", "III"};
    private static final Random GLITCH = new Random();

    private SingularityHudOverlay() {
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }
        float time = ClientEffects.ticks() + partialTick;
        Font font = mc.font;
        SingularityEvent ev = ClientSingularityState.focus(player);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (ev != null) {
            drawVignette(g, width, height, time, ev);
        }
        drawFlashes(g, width, height, partialTick);
        if (ClientEffects.glitchTicks() > 0) {
            drawStatic(g, width, height, ClientEffects.glitchTicks());
        }
        if (ev != null) {
            drawWarningPanel(g, font, player, ev, width, time, partialTick);
        }
        drawBanner(g, font, width, height, time, partialTick);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ full-screen layers

    private static void drawVignette(GuiGraphics g, int w, int h, float time, SingularityEvent ev) {
        boolean collapsing = ev.phase == SingularityEvent.PHASE_COLLAPSING;
        float pulse = 0.5F + 0.5F * Mth.sin(time * (collapsing ? 0.9F : 0.22F));
        float alpha = Mth.clamp(0.32F + 0.22F * pulse + ClientEffects.proximity(0.0F) * 0.25F, 0.0F, 0.85F);
        if (collapsing) {
            RenderSystem.setShaderColor(0.35F, 0.85F, 1.0F, alpha);
        } else {
            RenderSystem.setShaderColor(1.0F, 0.06F, 0.04F, alpha);
        }
        g.blit(VIGNETTE, 0, 0, 0.0F, 0.0F, w, h, w, h);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawFlashes(GuiGraphics g, int w, int h, float partialTick) {
        float red = ClientEffects.redFlashAlpha(partialTick);
        if (red > 0.01F) {
            g.fill(0, 0, w, h, argb(Math.min(0.75F, red * 0.75F), 0.85F, 0.0F, 0.0F));
        }
        float white = ClientEffects.flashAlpha(partialTick);
        if (white > 0.01F) {
            g.fill(0, 0, w, h, argb(Math.min(1.0F, white * 1.1F), 1.0F, 0.98F, 0.95F));
        }
    }

    private static void drawStatic(GuiGraphics g, int w, int h, int ticks) {
        float strength = Mth.clamp(ticks / 30.0F, 0.0F, 1.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.22F * strength);
        int u = GLITCH.nextInt(256);
        int v = GLITCH.nextInt(256);
        g.blit(STATIC, 0, 0, u, v, w, h, 256, 256);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int tears = 2 + (int) (strength * 6);
        for (int i = 0; i < tears; i++) {
            int y = GLITCH.nextInt(Math.max(1, h));
            int th = 1 + GLITCH.nextInt(4);
            int shift = GLITCH.nextInt(41) - 20;
            int color = GLITCH.nextBoolean() ? argb(0.35F * strength, 1.0F, 0.1F, 0.15F) : argb(0.3F * strength, 0.1F, 0.95F, 1.0F);
            g.fill(Math.max(0, shift), y, Math.min(w, w + shift), y + th, color);
        }
    }

    // ------------------------------------------------------------------ the warning panel

    private static void drawWarningPanel(GuiGraphics g, Font font, Player player, SingularityEvent ev, int w, float time, float partialTick) {
        boolean collapsing = ev.phase == SingularityEvent.PHASE_COLLAPSING;
        int intro = ClientEffects.introTicks();
        float slide = intro > 0 ? (float) Math.pow(Mth.clamp((intro - 90 + partialTick) / 30.0F, 0.0F, 1.0F), 2.0) : 0.0F;
        int top = 6 - Math.round(slide * 120.0F);

        // Title: flashing red, chromatic glitch ghosts, occasional horizontal tear.
        float titleScale = Mth.clamp(w / 330.0F, 1.15F, 2.2F);
        Component title = Component.translatable(collapsing ? "hud.livingblackhole.destabilizing" : "hud.livingblackhole.warning");
        float flash = 0.5F + 0.5F * Mth.sin(time * 0.62F);
        boolean strobe = ((int) (time / 6.0F)) % 2 == 0;
        int color = collapsing
                ? lerpColor(0x1FA9C9, 0xC8FFFF, flash)
                : (strobe ? lerpColor(0xB00000, 0xFF3B30, flash) : lerpColor(0x6A0000, 0xD01010, flash));
        boolean glitching = ClientEffects.glitchTicks() > 0 || ((int) time % 97) < 3;
        int jitter = glitching ? GLITCH.nextInt(7) - 3 : 0;

        g.pose().pushPose();
        g.pose().translate(w / 2.0F, top, 0.0F);
        g.pose().scale(titleScale, titleScale, 1.0F);
        int tw = font.width(title);
        if (glitching) {
            g.drawString(font, title, -tw / 2 + jitter - 1, 0, 0x8000E5FF, false);
            g.drawString(font, title, -tw / 2 + jitter + 1, 0, 0x80FF0040, false);
        }
        g.drawString(font, title, -tw / 2 + jitter, 0, 0xFF000000 | color, true);
        g.pose().popPose();

        int y = top + Math.round(font.lineHeight * titleScale) + 5;

        // Main instruction line - the exact wording, with the live coordinates.
        Component instruction = Component.translatable("hud.livingblackhole.collect",
                coord(ev.fragments[0], ev.collected[0]), coord(ev.fragments[1], ev.collected[1]), coord(ev.fragments[2], ev.collected[2]));
        int maxWidth = Math.max(160, w - 24);
        var lines = font.split(instruction, maxWidth);

        // Measure the panel.
        int panelWidth = 0;
        for (FormattedCharSequence line : lines) {
            panelWidth = Math.max(panelWidth, font.width(line));
        }
        panelWidth = Math.max(panelWidth, 250);
        panelWidth = Math.min(panelWidth + 16, w - 8);
        int rows = lines.size() + SingularityEvent.FRAGMENTS + 2;
        int panelHeight = rows * (font.lineHeight + 2) + 10;
        int x0 = (w - panelWidth) / 2;
        int y0 = y - 4;
        int border = collapsing ? argb(0.9F, 0.3F, 0.9F, 1.0F) : argb(0.55F + 0.4F * flash, 1.0F, 0.12F, 0.1F);
        g.fill(x0, y0, x0 + panelWidth, y0 + panelHeight, 0xB0080006);
        // Scanlines.
        for (int sy = y0 + 1; sy < y0 + panelHeight; sy += 3) {
            g.fill(x0, sy, x0 + panelWidth, sy + 1, 0x14FF2020);
        }
        g.fill(x0, y0, x0 + panelWidth, y0 + 1, border);
        g.fill(x0, y0 + panelHeight - 1, x0 + panelWidth, y0 + panelHeight, border);
        g.fill(x0, y0, x0 + 1, y0 + panelHeight, border);
        g.fill(x0 + panelWidth - 1, y0, x0 + panelWidth, y0 + panelHeight, border);
        // Corner brackets.
        int bl = 8;
        g.fill(x0 - 2, y0 - 2, x0 + bl, y0, border);
        g.fill(x0 - 2, y0 - 2, x0, y0 + bl, border);
        g.fill(x0 + panelWidth - bl, y0 - 2, x0 + panelWidth + 2, y0, border);
        g.fill(x0 + panelWidth, y0 - 2, x0 + panelWidth + 2, y0 + bl, border);

        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, (w - font.width(line)) / 2, y, 0xFFFF6A5E, true);
            y += font.lineHeight + 2;
        }
        y += 2;

        // Live tracker.
        boolean sameDimension = ev.dimension.equals(player.level().dimension());
        Vec3 eye = player.getEyePosition();
        for (int i = 0; i < SingularityEvent.FRAGMENTS; i++) {
            BlockPos f = ev.fragments[i];
            int rowX = x0 + 10;
            if (ev.collected[i]) {
                g.drawString(font, Component.translatable("hud.livingblackhole.fragment_secured", NUMERALS[i]), rowX, y, 0xFF5CFF8A, true);
            } else {
                Component label = Component.translatable("hud.livingblackhole.fragment", NUMERALS[i], f.getX(), f.getY(), f.getZ());
                g.drawString(font, label, rowX, y, 0xFF8FF7FF, true);
                if (sameDimension) {
                    double dx = f.getX() + 0.5 - eye.x;
                    double dz = f.getZ() + 0.5 - eye.z;
                    double dist = Math.sqrt(dx * dx + dz * dz + Math.pow(f.getY() + 0.5 - eye.y, 2));
                    float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float rel = Mth.wrapDegrees(targetYaw - player.getYRot());
                    String distText = String.format(Locale.ROOT, "%,dm", Math.round(dist));
                    int dw = font.width(distText);
                    int rx = x0 + panelWidth - 10 - dw;
                    g.drawString(font, distText, rx, y, 0xFFE8E8E8, true);
                    drawArrow(g, font, rx - 9, y + 4, rel, time, i);
                }
            }
            y += font.lineHeight + 2;
        }

        // Status / objective line.
        y += 2;
        Component status;
        int statusColor;
        if (collapsing) {
            status = Component.translatable("hud.livingblackhole.status_collapse");
            statusColor = lerpColor(0x40D8FF, 0xFFFFFF, flash) | 0xFF000000;
        } else if (ev.collectedCount() >= SingularityEvent.FRAGMENTS) {
            status = Component.translatable("hud.livingblackhole.status_craft");
            statusColor = lerpColor(0xFFB000, 0xFFF0A0, flash) | 0xFF000000;
        } else {
            BlackHoleEntity bh = ClientEffects.nearest();
            int mass = bh != null ? bh.getMass() : ev.mass;
            float reach = bh != null ? bh.getReach() : ev.reach;
            String massText = String.format(Locale.ROOT, "%,d", mass);
            if (sameDimension) {
                int dist = (int) Math.round(ev.center.distanceTo(player.position()));
                status = Component.translatable("hud.livingblackhole.status", massText, Math.round(reach), String.format(Locale.ROOT, "%,d", dist));
            } else {
                status = Component.translatable("hud.livingblackhole.status_elsewhere", massText, ev.dimension.location().toString());
            }
            statusColor = 0xFFC7A4FF;
        }
        int sw = font.width(status);
        g.drawString(font, status, (w - sw) / 2, y, statusColor, true);
    }

    private static Component coord(BlockPos pos, boolean collected) {
        Component c = Component.literal("[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]");
        return collected
                ? c.copy().withStyle(s -> s.withColor(0x5CFF8A).withStrikethrough(true))
                : c.copy().withStyle(s -> s.withColor(0x8FF7FF).withBold(true));
    }

    private static void drawArrow(GuiGraphics g, Font font, int cx, int cy, float degrees, float time, int index) {
        float bob = Mth.sin(time * 0.3F + index) * 0.6F;
        g.pose().pushPose();
        g.pose().translate(cx, cy + bob, 0.0F);
        g.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
        String arrow = "▲";
        g.drawString(font, arrow, -font.width(arrow) / 2, -4, 0xFF35E6FF, false);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ centre banners

    private static void drawBanner(GuiGraphics g, Font font, int w, int h, float time, float partialTick) {
        int type = ClientEffects.bannerType();
        if (type == ClientEffects.BANNER_NONE) {
            return;
        }
        float p = ClientEffects.bannerProgress(partialTick);
        float alpha = Mth.clamp(Math.min(p / 0.08F, (1.0F - p) / 0.25F), 0.0F, 1.0F);
        if (alpha <= 0.02F) {
            return;
        }
        Component title;
        Component subtitle;
        int color;
        float scale;
        switch (type) {
            case CinematicPacket.ESCAPE -> {
                title = Component.translatable("banner.livingblackhole.breach");
                subtitle = Component.translatable("banner.livingblackhole.breach.sub");
                color = ((int) (time / 3.0F)) % 2 == 0 ? 0xFF2A1F : 0xFFFFFF;
                scale = 3.2F;
            }
            case CinematicPacket.COLLAPSE_START -> {
                title = Component.translatable("banner.livingblackhole.collapse");
                float remaining = (1.0F - p) * (BlackHoleEntity.DESTABILIZE_TICKS + BlackHoleEntity.COLLAPSE_TICKS) / 20.0F;
                subtitle = Component.translatable("banner.livingblackhole.collapse.sub", String.format(Locale.ROOT, "%.1f", remaining));
                color = lerpColor(0x40D8FF, 0xFFFFFF, 0.5F + 0.5F * Mth.sin(time * 1.2F));
                scale = 2.6F;
            }
            case CinematicPacket.NEUTRALIZED -> {
                title = Component.translatable("banner.livingblackhole.neutralized");
                subtitle = Component.translatable("banner.livingblackhole.neutralized.sub");
                color = 0x6BFFE4;
                scale = 2.8F;
            }
            case CinematicPacket.BOMB_CAPTURED -> {
                title = Component.translatable("banner.livingblackhole.captured");
                subtitle = Component.translatable("banner.livingblackhole.captured.sub");
                color = 0x4DF0FF;
                scale = 2.0F;
            }
            case CinematicPacket.FRAGMENT_SECURED -> {
                title = Component.translatable("banner.livingblackhole.fragment", Math.round(ClientEffects.bannerValue()), SingularityEvent.FRAGMENTS);
                subtitle = Component.translatable(Math.round(ClientEffects.bannerValue()) >= SingularityEvent.FRAGMENTS
                        ? "banner.livingblackhole.fragment.all" : "banner.livingblackhole.fragment.sub");
                color = 0x8FF7FF;
                scale = 2.0F;
            }
            default -> {
                return;
            }
        }
        scale = Math.min(scale, Math.max(1.0F, (w - 20) / (float) Math.max(1, font.width(title))));
        // A slight zoom-in as it appears.
        float zoom = scale * (1.0F + (1.0F - Mth.clamp(p / 0.08F, 0.0F, 1.0F)) * 0.35F);
        int a = Math.round(alpha * 255.0F) << 24;
        float cy = h * 0.40F;

        g.pose().pushPose();
        g.pose().translate(w / 2.0F, cy, 0.0F);
        g.pose().scale(zoom, zoom, 1.0F);
        int tw = font.width(title);
        g.drawString(font, title, -tw / 2, -font.lineHeight / 2, a | (color & 0xFFFFFF), true);
        g.pose().popPose();

        int sw = font.width(subtitle);
        g.drawString(font, subtitle, (w - sw) / 2, Math.round(cy + font.lineHeight * zoom * 0.5F + 6), a | 0xE6E6E6, true);
    }

    // ------------------------------------------------------------------ colour helpers

    private static int argb(float a, float r, float g, float b) {
        return (Mth.clamp(Math.round(a * 255.0F), 0, 255) << 24)
                | (Mth.clamp(Math.round(r * 255.0F), 0, 255) << 16)
                | (Mth.clamp(Math.round(g * 255.0F), 0, 255) << 8)
                | Mth.clamp(Math.round(b * 255.0F), 0, 255);
    }

    private static int lerpColor(int from, int to, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int r = Math.round(Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return (r << 16) | (g << 8) | b;
    }
}
