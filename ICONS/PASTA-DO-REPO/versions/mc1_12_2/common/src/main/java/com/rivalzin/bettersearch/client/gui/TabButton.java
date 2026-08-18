package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

import java.util.function.BooleanSupplier;

/**
 * Aba do topo da tela de configuracao.
 *
 * <p>A aba selecionada fica mais clara e ganha um risco na cor de destaque do mod embaixo -
 * o mesmo ciano da lupa do icone.
 */
public final class TabButton extends GuiButton implements Acionavel {

    public static final int HEIGHT = 20;

    private final BooleanSupplier selected;
    private final Runnable onSelect;

    public TabButton(int x, int y, int width, String label, BooleanSupplier selected, Runnable onSelect) {
        super(0, x, y, width, HEIGHT, label);
        this.selected = selected;
        this.onSelect = onSelect;
    }

    @Override
    public void aoApertar() {
        onSelect.run();
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;

        boolean active = selected.getAsBoolean();
        int x = this.x;
        int y = this.y;
        int right = x + this.width;
        int bottom = y + this.height;

        int background = active ? Theme.TAB_ACTIVE : (this.hovered ? Theme.TAB_HOVER : Theme.TAB_IDLE);
        Gui.drawRect(x, y, right, bottom, Theme.BORDER);
        Gui.drawRect(x + 1, y + 1, right - 1, bottom - 1, background);
        if (active) {
            Gui.drawRect(x + 1, bottom - 2, right - 1, bottom - 1, Theme.ACCENT);
        }

        // Com quatro abas em uma tela estreita o nome pode nao caber; cortamos em vez de
        // deixar o texto vazar por cima da aba vizinha.
        String label = this.displayString;
        int limit = this.width - 6;
        if (mc.fontRenderer.getStringWidth(label) > limit) {
            label = mc.fontRenderer.trimStringToWidth(label, limit - mc.fontRenderer.getStringWidth("..")) + "..";
        }
        this.drawCenteredString(mc.fontRenderer, label,
                x + this.width / 2, y + (this.height - 8) / 2,
                active ? Theme.TITLE : Theme.TEXT_DIM);
    }
}
