package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.MathHelper;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * Slider de numero inteiro com passo fixo.
 *
 * <p>Mostra apenas o valor - "Normal", "3 letras", "Sem limite" - porque o nome da opcao
 * ja aparece a esquerda, na propria linha. Assim o usuario ajusta tudo arrastando, sem
 * nunca precisar digitar um numero.
 *
 * <p>Nas outras versoes isto herda do {@code AbstractSliderButton}; a 1.12.2 nao tem slider
 * generico (o {@code GuiOptionSlider} vanilla e casado com o GameSettings), entao o
 * comportamento dele e reproduzido aqui em cima do {@code GuiButton}, do jeito que o proprio
 * jogo faz: {@code mousePressed} agarra, {@code mouseDragged} - que o {@code drawButton}
 * vanilla chama a cada quadro - arrasta e desenha o botao por cima, {@code mouseReleased}
 * solta. A trilha e o botao usam a MESMA textura widgets.png do slider vanilla, entao a cara
 * e identica a do AbstractSliderButton de la.
 */
public final class IntSlider extends GuiButton {

    private final int min;
    private final int max;
    private final int step;
    private final IntFunction<String> valueLabel;
    private final IntConsumer onChange;

    /** Fracao 0..1, como o {@code value} do AbstractSliderButton. */
    private double value;
    private boolean dragging;
    /** Ultimo valor entregue ao onChange - o slider so avisa quando o INTEIRO muda. */
    private int lastApplied;

    public IntSlider(int x, int y, int width, int height,
                     int min, int max, int step, int initialValue,
                     IntFunction<String> valueLabel, IntConsumer onChange) {
        super(0, x, y, width, height, "");
        this.min = min;
        this.max = max;
        this.step = Math.max(1, step);
        this.valueLabel = valueLabel;
        this.onChange = onChange;
        this.value = toFraction(initialValue, min, max);
        this.lastApplied = intValue();
        updateMessage();
    }

    public int intValue() {
        int raw = min + (int) Math.round(this.value * (max - min));
        int snapped = min + Math.round((raw - min) / (float) step) * step;
        return MathHelper.clamp(snapped, min, max);
    }

    private void updateMessage() {
        this.displayString = valueLabel.apply(intValue());
    }

    private void applyValue() {
        int now = intValue();
        if (now != lastApplied) {
            lastApplied = now;
            onChange.accept(now);
        }
    }

    private void setFromMouse(int mouseX) {
        this.value = MathHelper.clamp((mouseX - (this.x + 4)) / (double) (this.width - 8), 0.0, 1.0);
        applyValue();
        updateMessage();
    }

    /**
     * Trilha sempre com a textura "apagada" (v=46), exatamente como o slider vanilla faz -
     * e o mesmo visual que o AbstractSliderButton das outras versoes escolhe para a trilha.
     */
    @Override
    protected int getHoverState(boolean mouseOver) {
        return 0;
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            setFromMouse(mouseX);
            this.dragging = true;
            return true;
        }
        return false;
    }

    /**
     * O {@code drawButton} vanilla chama isto a cada quadro, depois de desenhar a trilha e o
     * texto - e aqui que o slider vanilla arrasta e desenha o botao. Copiamos o desenho dele
     * (widgets.png, 0/66 + 196/66) no ponto da fracao crua, como o AbstractSliderButton.
     */
    @Override
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        if (this.dragging) {
            setFromMouse(mouseX);
        }
        mc.getTextureManager().bindTexture(BUTTON_TEXTURES);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int knobX = this.x + (int) (this.value * (this.width - 8));
        this.drawTexturedModalRect(knobX, this.y, 0, 66, 4, 20);
        this.drawTexturedModalRect(knobX + 4, this.y, 196, 66, 4, 20);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        this.dragging = false;
    }

    private static double toFraction(int value, int min, int max) {
        return max == min ? 0.0 : MathHelper.clamp((value - min) / (double) (max - min), 0.0, 1.0);
    }
}
