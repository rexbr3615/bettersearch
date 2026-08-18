package com.rivalzin.bettersearch.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;

/**
 * Descobre a <b>chave de traducao</b> de um item - o {@code item.minecraft.apple} que serve de
 * indice nos arquivos de idioma.
 *
 * <p>E ela que faz a busca entre idiomas funcionar: com a chave em maos, o mod procura o mesmo
 * item em cada idioma configurado e indexa "apple", "pomme", "manzana" e "Apfel" apontando para
 * a mesma maca.
 *
 * <p><b>Por que isto virou uma classe.</b> Ate a 1.21.1 bastava {@code stack.getDescriptionId()}.
 * Esse metodo nao existe mais na 1.21.9: o nome do item passou a ser um componente de dados, e
 * a chave mora dentro dele. O caminho novo tem uma vantagem de quebra - itens de mod que definem
 * o proprio componente de nome tambem entregam a chave certa, em vez do id generico do item.
 */
final class ItemNames {

    private ItemNames() {
    }

    /** @return a chave de traducao do item, ou {@code null} se ele nao tiver uma */
    static String translationKey(ItemStack stack) {
        Component name = stack.getItemName();
        if (name.getContents() instanceof TranslatableContents translatable) {
            return translatable.getKey();
        }
        // Nome fixo, sem traducao (alguns itens de mod). Nao ha o que procurar nos idiomas.
        return null;
    }
}
