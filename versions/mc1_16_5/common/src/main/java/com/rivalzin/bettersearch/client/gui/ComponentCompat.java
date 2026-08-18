package com.rivalzin.bettersearch.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/**
 * As fabricas estaticas de {@link Component}, que so chegaram na 1.19.
 *
 * <p>Na 1.18.2 texto se cria com {@code new TranslatableComponent(chave)} e
 * {@code new TextComponent(texto)}. Como isso aparece em mais de trinta lugares nas telas,
 * reproduzir aqui a forma nova deixa as telas praticamente identicas as das outras versoes -
 * a diferenca da versao mora neste arquivo, junto com {@link ButtonCompat} e {@link Tips}.
 */
public final class ComponentCompat {

    private ComponentCompat() {
    }

    public static MutableComponent translatable(String chave) {
        return new TranslatableComponent(chave);
    }

    public static MutableComponent translatable(String chave, Object... argumentos) {
        return new TranslatableComponent(chave, argumentos);
    }

    public static MutableComponent literal(String texto) {
        return new TextComponent(texto);
    }

    public static MutableComponent empty() {
        return new TextComponent("");
    }
}
