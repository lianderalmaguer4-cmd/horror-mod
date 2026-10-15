package com.rostroshuecos.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Diario por secciones: indice a la izquierda (clic o flechas arriba/abajo) y texto con scroll a la derecha (rueda del raton). */
public class DiarioScreen extends Screen {
    private final List<String> titles = new ArrayList<>();
    private final List<String> texts = new ArrayList<>();
    private final List<OrderedText> lines = new ArrayList<>();
    private int sel = 0;
    private int scroll = 0;

    public DiarioScreen(List<String> flat) {
        super(Text.literal("Diario de San Mudo"));
        for (int i = 0; i + 1 < flat.size(); i += 2) {
            titles.add(flat.get(i));
            texts.add(flat.get(i + 1));
        }
    }

    private int boxW() { return Math.min(380, this.width - 16); }
    private int boxH() { return Math.min(230, this.height - 16); }
    private int boxX() { return (this.width - boxW()) / 2; }
    private int boxY() { return (this.height - boxH()) / 2; }
    private int idxW() { return 108; }
    private int visibleLines() { return (boxH() - 24) / 9; }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        lines.clear();
        scroll = 0;
        if (texts.isEmpty()) return;
        int w = boxW() - idxW() - 28;
        for (String para : texts.get(sel).split("\n", -1)) {
            if (para.isEmpty()) {
                lines.add(OrderedText.EMPTY);
            } else {
                lines.addAll(this.textRenderer.wrapLines(Text.literal(para), w));
            }
        }
    }

    private void select(int i) {
        if (titles.isEmpty()) return;
        sel = Math.max(0, Math.min(titles.size() - 1, i));
        rebuild();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);   // fondo
        int x = boxX();
        int y = boxY();
        int w = boxW();
        int h = boxH();
        ctx.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF3B2412);   // borde
        ctx.fill(x, y, x + w, y + h, 0xFFE6D6B4);                    // papel
        ctx.fill(x + idxW(), y + 4, x + idxW() + 1, y + h - 4, 0xFF8A7552);   // separador

        // indice
        for (int i = 0; i < titles.size(); i++) {
            int ty = y + 6 + i * 10;
            if (ty + 9 > y + h - 2) break;
            if (i == sel) ctx.fill(x + 3, ty - 1, x + idxW() - 3, ty + 9, 0xFF3B2412);
            ctx.drawText(this.textRenderer, Text.literal(titles.get(i)), x + 6, ty,
                    i == sel ? 0xFFE6D6B4 : 0xFF2B1B10, false);
        }

        // texto
        int tx = x + idxW() + 10;
        int ty = y + 8;
        int visible = visibleLines();
        for (int i = 0; i < visible && scroll + i < lines.size(); i++) {
            ctx.drawText(this.textRenderer, lines.get(scroll + i), tx, ty + i * 9, 0xFF2B1B10, false);
        }
        // barra de scroll
        if (lines.size() > visible) {
            int trackH = h - 16;
            int barH = Math.max(12, trackH * visible / lines.size());
            int barY = y + 8 + (trackH - barH) * scroll / Math.max(1, lines.size() - visible);
            ctx.fill(x + w - 6, y + 8, x + w - 4, y + 8 + trackH, 0xFFB8A57C);
            ctx.fill(x + w - 6, barY, x + w - 4, barY + barH, 0xFF3B2412);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = boxX();
        int y = boxY();
        if (mouseX >= x && mouseX < x + idxW() && mouseY >= y + 5) {
            int i = (int) ((mouseY - (y + 6)) / 10);
            if (i >= 0 && i < titles.size()) select(i);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, lines.size() - visibleLines());
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(verticalAmount) * 3));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int max = Math.max(0, lines.size() - visibleLines());
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) { select(sel + 1); return true; }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) { select(sel - 1); return true; }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) { scroll = Math.min(max, scroll + visibleLines() - 1); return true; }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) { scroll = Math.max(0, scroll - visibleLines() + 1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
