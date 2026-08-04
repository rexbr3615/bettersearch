package com.rivalzin.bettersearch.core;

/**
 * Um texto pesquisavel ja normalizado e pre-processado.
 *
 * <p>Cada item do inventario criativo vira varios {@code SearchField}: o nome no idioma do jogo,
 * o nome em ingles, o nome em cada idioma extra, o id, as linhas de tooltip...
 * Todo o trabalho caro (normalizar, achar limites de palavra, calcular mascara) e feito uma
 * unica vez, na construcao do indice; a digitacao so faz comparacoes baratas.
 */
public final class SearchField {

    /** Nome no idioma atual do jogo. */
    public static final byte SOURCE_NATIVE = 0;
    /** Nome em ingles (en_us). */
    public static final byte SOURCE_ENGLISH = 1;
    /** Nome em qualquer outro idioma. */
    public static final byte SOURCE_FOREIGN = 2;
    /** Id do item / do mod. */
    public static final byte SOURCE_ID = 3;
    /** Linha de tooltip (encantamento, efeito de pocao...). */
    public static final byte SOURCE_TOOLTIP = 4;

    /** Texto normalizado (minusculo, sem acentos, separadores colapsados em espaco). */
    public final String text;
    /** Assinatura de caracteres, usada como pre-filtro da busca aproximada. */
    public final long mask;
    /** Indice inicial de cada palavra dentro de {@link #text}. */
    public final int[] wordStarts;
    /** Iniciais das palavras ("diamond sword" -> "ds"); {@code null} se houver menos de 2 palavras. */
    public final String initials;
    /** {@link #text} sem espacos ("netheritesword"); {@code null} se so houver uma palavra. */
    public final String compact;
    /** Assinatura de {@link #compact}. */
    public final long compactMask;
    /** De onde este texto veio (uma das constantes {@code SOURCE_*}). */
    public final byte source;

    public SearchField(String normalizedText, byte source) {
        this.text = normalizedText;
        this.source = source;
        this.mask = TextNormalizer.charMask(normalizedText);

        int words = normalizedText.isEmpty() ? 0 : 1;
        for (int i = 0; i < normalizedText.length(); i++) {
            if (normalizedText.charAt(i) == ' ') {
                words++;
            }
        }

        int[] starts = new int[words];
        if (words > 0) {
            starts[0] = 0;
            int w = 1;
            for (int i = 0; i < normalizedText.length(); i++) {
                if (normalizedText.charAt(i) == ' ') {
                    starts[w++] = i + 1;
                }
            }
        }
        this.wordStarts = starts;

        if (words >= 2) {
            StringBuilder ini = new StringBuilder(words);
            StringBuilder flat = new StringBuilder(normalizedText.length());
            for (int i = 0; i < words; i++) {
                int s = starts[i];
                int e = wordEnd(normalizedText, starts, i);
                if (s < e) {
                    ini.append(normalizedText.charAt(s));
                    flat.append(normalizedText, s, e);
                }
            }
            this.initials = ini.toString();
            this.compact = flat.toString();
            this.compactMask = TextNormalizer.charMask(this.compact);
        } else {
            this.initials = null;
            this.compact = null;
            this.compactMask = 0L;
        }
    }

    /** Fim (exclusivo) da palavra {@code index}. */
    public int wordEnd(int index) {
        return wordEnd(text, wordStarts, index);
    }

    private static int wordEnd(String text, int[] starts, int index) {
        return index + 1 < starts.length ? starts[index + 1] - 1 : text.length();
    }

    public int wordCount() {
        return wordStarts.length;
    }

    @Override
    public String toString() {
        return text + " (src=" + source + ')';
    }
}
