package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

import java.util.function.Consumer;

/**
 * Interruptor de ligar/desligar, com o botao deslizando de um lado para o outro.
 *
 * <p>A animacao e calculada a partir do relogio ({@link Minecraft#getSystemTime()} - o
 * {@code Util.getMillis()} desta era, conferido com javap), nao do numero de quadros, entao
 * ela dura os mesmos 140 ms tanto a 30 quanto a 240 FPS.
 *
 * <p>Desenhado apenas com retangulos, sem textura nenhuma: funciona com qualquer resource
 * pack e em qualquer versao/loader.
 */
public final class ToggleSwitch extends GuiButton implements Acionavel {

    public static final int WIDTH = 28;
    public static final int HEIGHT = 14;
    private static final int KNOB_WIDTH = 10;
    private static final long ANIMATION_MS = 140L;

    private final Consumer<Boolean> onChange;
    private boolean value;
    private float animationFrom;
    private long animationStart;

    /**
     * O quarto parametro era a narracao do leitor de tela. O Narrator desta era so le chat,
     * nunca botoes, entao o texto vira o displayString - que este widget nem desenha. Fica
     * so para as chamadas das telas continuarem identicas as das outras versoes.
     */
    public ToggleSwitch(int x, int y, boolean value, String narration, Consumer<Boolean> onChange) {
        super(0, x, y, WIDTH, HEIGHT, narration);
        this.value = value;
        this.onChange = onChange;
        this.animationFrom = value ? 1.0F : 0.0F;
        this.animationStart = 0L; // ja terminada
    }

    public boolean value() {
        return value;
    }

    @Override
    public void aoApertar() {
        set(!value);
    }

    private void set(boolean newValue) {
        if (newValue != value) {
            animationFrom = animation();
            animationStart = Minecraft.getSystemTime();
            value = newValue;
            onChange.accept(newValue);
        }
    }

    /** 0 = desligado (botao a esquerda), 1 = ligado (botao a direita). */
    private float animation() {
        float target = value ? 1.0F : 0.0F;
        long elapsed = Minecraft.getSystemTime() - animationStart;
        if (elapsed < 0L || elapsed >= ANIMATION_MS) {
            return target;
        }
        float progress = elapsed / (float) ANIMATION_MS;
        progress = progress * progress * (3.0F - 2.0F * progress); // suaviza o comeco e o fim
        return animationFrom + (target - animationFrom) * progress;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;

        float position = animation();
        int x = this.x;
        int y = this.y;
        int right = x + this.width;
        int bottom = y + this.height;

        int track = this.enabled
                ? Theme.blend(Theme.SWITCH_OFF, Theme.SWITCH_ON, position)
                : Theme.SWITCH_DISABLED;
        Gui.drawRect(x, y, right, bottom, Theme.BORDER);
        Gui.drawRect(x + 1, y + 1, right - 1, bottom - 1, track);

        int knobX = x + 1 + Math.round((this.width - 2 - KNOB_WIDTH) * position);
        int knob = !this.enabled
                ? Theme.KNOB_DISABLED
                : (this.hovered ? Theme.KNOB_HOVER : Theme.KNOB);
        Gui.drawRect(knobX, y + 2, knobX + KNOB_WIDTH, bottom - 2, knob);
    }
}
