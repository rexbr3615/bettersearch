package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.MutableComponent;
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
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        boolean active = selected.getAsBoolean();
        int x = this.x;
        int y = this.y;
        int right = x + getWidth();
        int bottom = y + getHeight();

        int background = active ? Theme.TAB_ACTIVE : (this.isHovered ? Theme.TAB_HOVER : Theme.TAB_IDLE);
        GuiComponent.fill(poseStack, x, y, right, bottom, Theme.BORDER);
        GuiComponent.fill(poseStack, x + 1, y + 1, right - 1, bottom - 1, background);
        if (active) {
            GuiComponent.fill(poseStack, x + 1, bottom - 2, right - 1, bottom - 1, Theme.ACCENT);
        }

        // Com quatro abas em uma tela estreita o nome pode nao caber; cortamos em vez de
        // deixar o texto vazar por cima da aba vizinha.
        Minecraft minecraft = Minecraft.getInstance();
        String label = getMessage().getString();
        int limit = getWidth() - 6;
        if (minecraft.font.width(label) > limit) {
            label = minecraft.font.plainSubstrByWidth(label, limit - minecraft.font.width("..")) + "..";
        }
        GuiComponent.drawCenteredString(poseStack, minecraft.font, label,
                x + getWidth() / 2, y + (getHeight() - 8) / 2,
                active ? Theme.TITLE : Theme.TEXT_DIM);
    }

    /*
     * Na 1.16.5 nao existe o pacote net.minecraft.client.gui.narration - ele chegou na 1.17,
     * junto com o updateNarration(NarrationElementOutput). Aqui o leitor de tela pergunta o
     * texto por createNarrationMessage(), que devolve o componente direto.
     *
     * Conferido com javap no jar de verdade: AbstractWidget da 1.16.5 declara
     * "protected MutableComponent createNarrationMessage()".
     */
    @Override
    protected MutableComponent createNarrationMessage() {
        return (MutableComponent) ComponentCompat.translatable("gui.narrate.tab", getMessage());
    }
}
