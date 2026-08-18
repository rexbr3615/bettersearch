package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Botao discreto: preto chapado semitransparente, sem borda e sem textura.
 *
 * <p>Usado nos links do rodape, onde um botao vanilla cinza roubaria atencao das opcoes.
 * Ao passar o mouse ele escurece um pouco e o texto assume a cor de destaque do mod.
 */
public final class FlatButton extends AbstractWidget {

    private static final int BACKGROUND = 0x66000000;
    private static final int BACKGROUND_HOVER = 0xAA000000;

    private final Runnable onPress;

    public FlatButton(int x, int y, int width, int height, Component label, Runnable onPress) {
        super(x, y, width, height, label);
        this.onPress = onPress;
    }

    /** Largura sugerida para caber o texto com uma folga confortavel. */
    public static int widthFor(Component label) {
        return Minecraft.getInstance().font.width(label) + 16;
    }

    @Override
    @SuppressWarnings("deprecation") // o NeoForge prefere onClick(x, y, botao), que por padrao chama este
    public void onClick(double mouseX, double mouseY) {
        onPress.run();
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        boolean highlight = this.isHovered && this.active;
        GuiComponent.fill(poseStack, this.x, this.y, this.x + getWidth(), this.y + getHeight(),
                highlight ? BACKGROUND_HOVER : BACKGROUND);
        GuiComponent.drawCenteredString(poseStack, Minecraft.getInstance().font, getMessage(),
                this.x + getWidth() / 2, this.y + (getHeight() - 8) / 2,
                highlight ? Theme.ACCENT : Theme.TEXT);
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, ComponentCompat.translatable("gui.narrate.button", getMessage()));
    }
}
