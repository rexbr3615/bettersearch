package com.rivalzin.bettersearch.core;

/**
 * Quais camadas de comparacao estao liberadas nesta passada da busca.
 *
 * <p>E um objeto imutavel criado uma vez por busca (nao por item), justamente para que as
 * opcoes do usuario nao custem nada dentro do laco quente.
 *
 * @param allowTypos    liberar a camada de distancia de edicao
 * @param allowInitials liberar o casamento por iniciais ("ds" -> "diamond sword")
 * @param allowCompact  liberar o casamento ignorando espacos ("netheritesword")
 */
public record MatchPolicy(boolean allowTypos, boolean allowInitials, boolean allowCompact) {

    public static MatchPolicy of(SearchSettings settings, boolean allowTypos) {
        return new MatchPolicy(allowTypos, settings.matchInitials, settings.ignoreSpaces);
    }
}
