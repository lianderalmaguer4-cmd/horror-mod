package com.rostroshuecos.client;

import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Libro sencillo: pagina con papel, texto ajustado y navegacion con flechas del teclado o clic en los bordes. */
public class DiarioScreen extends Screen {
    private final List<String> pages;
    private int page = 0;

    public DiarioScreen(List<String> pages) {
        super(Text.literal("Diario de San Mudo"));
        this.pages = pages;
    }

    private int boxW() { return 250; }
    private int boxH() { return 200; }
    private int boxX() { return (this.width - boxW()) / 2; }
    private int boxY() { return (this.height - boxH()) / 2; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);   // fondo
        int x = boxX();
        int y = boxY();
        int w = boxW();
        int h = boxH();
        ctx.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF3B2412);   // borde
        ctx.fill(x, y, x + w, y + h, 0xFFE6D6B4);                    // papel

        String text = pages.isEmpty() ? "" : pages.get(page);
        int ty = y + 14;
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) {
                ty += 9;
                continue;
            }
            for (OrderedText line : this.textRenderer.wrapLines(Text.literal(para), w - 28)) {
                ctx.drawText(this.textRenderer, line, x + 14, ty, 0xFF2B1B10, false);
                ty += 9;
            }
        }

        String num = (page + 1) + " / " + pages.size();
        ctx.drawText(this.textRenderer, Text.literal(num),
                x + w / 2 - this.textRenderer.getWidth(num) / 2, y + h - 14, 0xFF5A4630, false);
        if (page > 0) ctx.drawText(this.textRenderer, Text.literal("<"), x + 8, y + h - 14, 0xFF5A4630, false);
        if (page < pages.size() - 1) ctx.drawText(this.textRenderer, Text.literal(">"), x + w - 14, y + h - 14, 0xFF5A4630, false);
    }

    private void go(int delta) {
        page = Math.max(0, Math.min(pages.size() - 1, page + delta));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < boxX() + boxW() / 2.0) go(-1); else go(1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) { go(1); return true; }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) { go(-1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
