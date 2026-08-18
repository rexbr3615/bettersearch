package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.gui.GuiButton;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A dica de um widget, guardada aqui porque na 1.12.2 ela nao cabe no widget.
 *
 * <p>Da 1.19.4 em diante existe {@code AbstractWidget#setTooltip(Tooltip)} e o proprio jogo
 * desenha a caixinha quando o cursor para em cima. Nesta versao o {@code GuiButton} nem
 * sonha com isso: quem quiser dica guarda o texto e desenha na mao.
 *
 * <p>Deixar isso num mapa a parte - e nao criar uma subclasse de cada widget - e o que mantem
 * {@code ToggleSwitch}, {@code IntSlider} e companhia identicos aos das outras versoes. A
 * diferenca da versao fica presa aqui e no {@link ButtonCompat}.
 *
 * <p>Mapa fraco de proposito: cada troca de tela joga fora todos os widgets, e nada aqui deve
 * segura-los vivos.
 */
public final class Tips {

    private static final Map<GuiButton, String> DICAS = new WeakHashMap<>();

    private Tips() {
    }

    public static void set(GuiButton widget, String dica) {
        if (widget != null && dica != null) {
            DICAS.put(widget, dica);
        }
    }

    /** @return a dica, ou {@code null} se este widget nao tem uma */
    public static String of(GuiButton widget) {
        return widget == null ? null : DICAS.get(widget);
    }
}
