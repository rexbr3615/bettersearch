package com.rivalzin.bettersearch.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import com.rivalzin.bettersearch.client.gui.ComponentCompat;
import net.minecraft.network.chat.Component;

/**
 * Um {@link KeyMapping} que se anuncia como "Alt + <tecla>" na tela de Controles.
 *
 * <p><b>Por que isto existe.</b> O NeoForge tem uma API de tecla modificadora
 * ({@code KeyModifier.ALT}); o Fabric nao tem. Registrando o atalho como uma tecla simples e
 * conferindo o Alt na mao, o comportamento fica igual nos dois - mas a tela de Controles
 * mostraria so "O", e quem apertasse O sozinho acharia que o mod esta quebrado.
 *
 * <p>O conserto e pequeno: {@code getTranslatedKeyMessage()} e publico e nao e final, e e
 * exatamente o que a tela de Controles usa para escrever o rotulo do botao. Sobrescrevendo
 * ele, o Fabric passa a mostrar "Alt + O" - e continua certo se a pessoa remapear, porque o
 * prefixo e colado no nome da tecla nova, seja ela qual for.
 *
 * <p><b>O que este truque nao resolve, honestamente:</b> a deteccao de conflito do vanilla
 * ({@code KeyMapping#same}) compara so a tecla base. Se outro mod usar O sozinho, o Fabric
 * vai pintar os dois de vermelho mesmo sem conflito real, ja que o nosso exige Alt.
 */
final class AltKeyMapping extends KeyMapping {

    AltKeyMapping(String name, int key, String category) {
        super(name, InputConstants.Type.KEYSYM, key, category);
    }

    @Override
    public Component getTranslatedKeyMessage() {
        Component key = super.getTranslatedKeyMessage();
        // "Alt + Nao vinculada" nao faria sentido nenhum.
        return isUnbound() ? key : ComponentCompat.translatable("bettersearch.key.alt", key);
    }
}
