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
public final class MatchPolicy {

    /*
     * Isto era um record. Virou classe comum porque o core/ e compilado tambem para a 1.16.5,
     * que roda em Java 8 - e record so existe do Java 16 em diante. Sintaxe de Java 8 vale
     * como sintaxe de Java 25, entao UMA copia deste arquivo serve as nove versoes; duas
     * copias do algoritmo seriam duas vezes o mesmo bug para consertar.
     *
     * Nada aqui depende do equals/hashCode que o record dava de graca: este objeto so e lido.
     */
    private final boolean allowTypos;
    private final boolean allowInitials;
    private final boolean allowCompact;

    public MatchPolicy(boolean allowTypos, boolean allowInitials, boolean allowCompact) {
        this.allowTypos = allowTypos;
        this.allowInitials = allowInitials;
        this.allowCompact = allowCompact;
    }

    public boolean allowTypos() {
        return allowTypos;
    }

    public boolean allowInitials() {
        return allowInitials;
    }

    public boolean allowCompact() {
        return allowCompact;
    }

    public static MatchPolicy of(SearchSettings settings, boolean allowTypos) {
        return new MatchPolicy(allowTypos, settings.matchInitials, settings.ignoreSpaces);
    }
}
