package com.rivalzin.bettersearch.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Consulta ja normalizada e dividida em tokens.
 *
 * <p>Tokens iniciados por {@code @} viram filtros de mod ({@code @create sword}).
 * O prefixo {@code #} (tags) continua sendo tratado pelo jogo original.
 */
public final class SearchQuery {

    public final String raw;
    /** Tokens normalizados que precisam casar com o nome/id do item. */
    public final String[] tokens;
    /** Mascara de caracteres de cada token. */
    public final long[] tokenMasks;
    /** Filtros de mod (sem o {@code @}). Vazio se nao houver. */
    public final String[] modFilters;
    /** Distancia maxima de edicao permitida por token. */
    public final int[] maxDistances;

    private SearchQuery(String raw, String[] tokens, long[] masks, int[] maxDistances, String[] modFilters) {
        this.raw = raw;
        this.tokens = tokens;
        this.tokenMasks = masks;
        this.maxDistances = maxDistances;
        this.modFilters = modFilters;
    }

    public boolean isEmpty() {
        return tokens.length == 0 && modFilters.length == 0;
    }

    public static SearchQuery parse(String rawQuery, SearchSettings settings) {
        String normalized = TextNormalizer.normalize(rawQuery);

        List<String> tokens = new ArrayList<>(4);
        List<String> mods = new ArrayList<>(1);

        // O '@' e removido pela normalizacao, entao detectamos os filtros de mod no texto cru.
        boolean hasModFilter = settings.searchModIds && rawQuery.indexOf('@') >= 0;
        if (hasModFilter) {
            for (String piece : rawQuery.split("\\s+")) {
                if (piece.isEmpty()) {
                    continue;
                }
                if (piece.charAt(0) == '@') {
                    String mod = TextNormalizer.normalize(piece.substring(1));
                    if (!mod.isEmpty()) {
                        mods.add(mod);
                    }
                } else {
                    addTokens(TextNormalizer.normalize(piece), tokens);
                }
            }
        } else {
            addTokens(normalized, tokens);
        }

        String[] tokenArray = tokens.toArray(new String[0]);
        long[] masks = new long[tokenArray.length];
        int[] distances = new int[tokenArray.length];
        for (int i = 0; i < tokenArray.length; i++) {
            masks[i] = TextNormalizer.charMask(tokenArray[i]);
            // Um unico portao de tamanho (minTypoLength) e um unico portao de quantidade
            // (typoTolerance). Antes os dois se sobrepunham e "Baixa" e "Normal" davam o
            // mesmo resultado na maioria das palavras.
            distances[i] = tokenArray[i].length() < settings.minTypoLength
                    ? 0
                    : maxDistance(tokenArray[i].length(), settings.typoTolerance);
        }

        return new SearchQuery(rawQuery, tokenArray, masks, distances, mods.toArray(new String[0]));
    }

    private static void addTokens(String normalized, List<String> out) {
        if (normalized.isEmpty()) {
            return;
        }
        int start = 0;
        for (int i = 0; i <= normalized.length(); i++) {
            if (i == normalized.length() || normalized.charAt(i) == ' ') {
                if (i > start) {
                    out.add(normalized.substring(start, i));
                }
                start = i + 1;
            }
        }
    }

    /**
     * Quantos erros de digitacao sao aceitos para um token deste tamanho.
     * Palavras curtas nao ganham tolerancia (senao "ar" casaria com meio jogo).
     */
    public static int maxDistance(int length, int tolerance) {
        switch (tolerance) {
            case 1:  return 1;                       // Baixa:  1 erro sempre
            case 2:  return length >= 8 ? 2 : 1;     // Normal: 1 erro, 2 em palavras de 8+ letras
            case 3:  return length >= 12 ? 3 : 2;    // Alta:   2 erros, 3 em palavras de 12+
            default: return 0;                       // Desligada
        }
    }
}
