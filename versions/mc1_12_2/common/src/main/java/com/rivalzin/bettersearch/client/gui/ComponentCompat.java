package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.resources.I18n;

/**
 * As fabricas de texto das outras versoes, no mundo em que texto de interface e String.
 *
 * <p>Na 1.12.2 o {@code Component} nem existe: tela, botao e fonte trabalham com
 * {@code String} pura, e a traducao e a {@link I18n} quem resolve. Manter as fabricas com
 * os MESMOS nomes ({@code translatable}, {@code literal}, {@code empty}) e o que deixa as
 * tres telas escritas exatamente como nas outras versoes - a diferenca da versao mora
 * neste arquivo, junto com {@link ButtonCompat} e {@link Tips}.
 *
 * <p>A traducao e resolvida aqui, na construcao, e nao no desenho. Nas outras versoes o
 * componente traduzivel resolve a cada quadro - mas trocar de idioma exige sair para a tela
 * de idiomas do jogo, e voltar de la reconstroi a nossa; o resultado na pratica e o mesmo.
 */
public final class ComponentCompat {

    private ComponentCompat() {
    }

    public static String translatable(String chave) {
        return I18n.format(chave);
    }

    public static String translatable(String chave, Object... argumentos) {
        return I18n.format(chave, argumentos);
    }

    public static String literal(String texto) {
        return texto;
    }

    public static String empty() {
        return "";
    }
}
