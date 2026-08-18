package com.rivalzin.bettersearch.client.gui;

/**
 * O clique dos widgets desta versao.
 *
 * <p>No {@code GuiScreen} da 1.12.2 o clique nao mora no botao: a tela recebe todos em
 * {@code actionPerformed(GuiButton)} e decide pelo {@code id}. O {@code onPress} guardado
 * no proprio widget so chegou na 1.14. Para manter os widgets donos do proprio
 * comportamento - como nas outras versoes - cada um implementa isto, e o
 * {@link OptionRowsScreen} despacha sem olhar id nenhum.
 */
public interface Acionavel {

    /** Chamado pela tela quando o widget recebe o clique (o som ja tocou). */
    void aoApertar();
}
