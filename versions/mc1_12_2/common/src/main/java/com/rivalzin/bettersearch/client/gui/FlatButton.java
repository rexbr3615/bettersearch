package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

/**
 * Botao discreto: preto chapado semitransparente, sem borda e sem textura.
 *
 * <p>Usado nos links do rodape, onde um botao vanilla cinza roubaria atencao das opcoes.
 * Ao passar o mouse ele escurece um pouco e o texto assume a cor de destaque do mod.
 */
public final class FlatButton extends GuiButton implements Acionavel {

    private static final int BACKGROUND = 0x66000000;
    private static final int BACKGROUND_HOVER = 0xAA000000;

    private final Runnable onPress;

    public FlatButton(int x, int y, int width, int height, String label, Runnable onPress) {
        super(0, x, y, width, height, label);
        this.onPress = onPress;
    }

    /** Largura sugerida para caber o texto com uma folga confortavel. */
    public static int widthFor(String label) {
        return Minecraft.getMinecraft().fontRenderer.getStringWidth(label) + 16;
    }

    @Override
    public void aoApertar() {
        onPress.run();
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        /*
         * O drawButton vanilla comeca conferindo visible e recalculando hovered; ao trocar o
         * desenho inteiro, essas duas linhas vem junto - sem elas o botao apareceria mesmo
         * escondido pela rolagem e nunca acenderia no hover.
         */
        if (!this.visible) {
            return;
        }
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;

        boolean highlight = this.hovered && this.enabled;
        Gui.drawRect(this.x, this.y, this.x + this.width, this.y + this.height,
                highlight ? BACKGROUND_HOVER : BACKGROUND);
        this.drawCenteredString(mc.fontRenderer, this.displayString,
                this.x + this.width / 2, this.y + (this.height - 8) / 2,
                highlight ? Theme.ACCENT : Theme.TEXT);
    }
}
