package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * Aba do topo da tela de configuracao.
 *
 * <p>A aba selecionada fica mais clara e ganha um risco na cor de destaque do mod embaixo -
 * o mesmo ciano da lupa do icone.
 */
public final class TabButton extends AbstractWidget {

    public static final int HEIGHT = 20;

    private final BooleanSupplier selected;
    private final Runnable onSelect;

    public TabButton(int x, int y, int width, Component label, BooleanSupplier selected, Runnable onSelect) {
        super(x, y, width, HEIGHT, label);
        this.selected = selected;
        this.onSelect = onSelect;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onClick(double mouseX, double mouseY) {
        onSelect.run();
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean active = selected.getAsBoolean();
        int x = getX();
        int y = getY();
        int right = x + getWidth();
        int bottom = y + getHeight();

        int background = active ? Theme.TAB_ACTIVE : (isHovered() ? Theme.TAB_HOVER : Theme.TAB_IDLE);
        guiGraphics.fill(x, y, right, bottom, Theme.BORDER);
        guiGraphics.fill(x + 1, y + 1, right - 1, bottom - 1, background);
        if (active) {
            guiGraphics.fill(x + 1, bottom - 2, right - 1, bottom - 1, Theme.ACCENT);
        }

        // Com quatro abas em uma tela estreita o nome pode nao caber; cortamos em vez de
        // deixar o texto vazar por cima da aba vizinha.
        Minecraft minecraft = Minecraft.getInstance();
        String label = getMessage().getString();
        int limit = getWidth() - 6;
        if (minecraft.font.width(label) > limit) {
            label = minecraft.font.plainSubstrByWidth(label, limit - minecraft.font.width("..")) + "..";
        }
        guiGraphics.drawCenteredString(minecraft.font, label,
                x + getWidth() / 2, y + (getHeight() - 8) / 2,
                active ? Theme.TITLE : Theme.TEXT_DIM);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.tab", getMessage()));
    }
}
