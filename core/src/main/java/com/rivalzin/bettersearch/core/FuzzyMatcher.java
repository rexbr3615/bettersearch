package com.rivalzin.bettersearch.core;

/**
 * O coracao do mod: compara um token da consulta com um {@link SearchField} e devolve
 * o quao bem eles combinam.
 *
 * <p>A comparacao e feita em camadas, da mais forte para a mais fraca. A primeira que casar
 * vence, entao o caso comum ("bau" == "bau") custa um {@code String.equals} e nada mais.
 * A camada aproximada (distancia de edicao) so e alcancada quando todas as outras falham.
 *
 * <p>Java puro: nenhuma dependencia de Minecraft, loader ou biblioteca externa.
 */
public final class FuzzyMatcher {

    // ---- Camadas de qualidade (quanto maior, melhor) ------------------------------------
    /** O texto inteiro e exatamente a consulta. */
    public static final int TIER_EXACT = 100;
    /** O texto comeca com a consulta ("dia" -> "diamante"). */
    public static final int TIER_PREFIX = 90;
    /** Alguma palavra e exatamente a consulta ("sword" -> "diamond sword"). */
    public static final int TIER_WORD_EXACT = 80;
    /** Alguma palavra comeca com a consulta ("nether" -> "netherite sword"). */
    public static final int TIER_WORD_PREFIX = 70;
    /** Casa ignorando os espacos ("netheritesword" -> "netherite sword"). */
    public static final int TIER_COMPACT = 60;
    /** Aparece em algum lugar do texto ("mond" -> "diamond"). */
    public static final int TIER_SUBSTRING = 50;
    /** Casa as iniciais das palavras ("ds" -> "diamond sword"). */
    public static final int TIER_INITIALS = 40;
    /** Casa com erros de digitacao ("netherrite", "swrod"). */
    public static final int TIER_TYPO = 30;
    /** Nao casou. */
    public static final int NO_MATCH = -1;

    private FuzzyMatcher() {
    }

    /**
     * Estado reutilizavel de uma busca. Evita alocar arrays a cada comparacao;
     * use um por thread de busca.
     */
    public static final class Scratch {
        /** Posicao (em caracteres) onde o token casou; usada para bonus de ordem. */
        public int position;
        /** Erros de digitacao gastos no ultimo casamento. */
        public int distance;

        int[] rowA = new int[64];
        int[] rowB = new int[64];
        int[] rowC = new int[64];

        void ensure(int size) {
            if (rowA.length < size) {
                rowA = new int[size];
                rowB = new int[size];
                rowC = new int[size];
            }
        }
    }

    /**
     * Compara um token com um campo.
     *
     * @param maxDist erros permitidos (0 desliga a camada aproximada)
     * @param policy  quais camadas o usuario deixou ligadas
     * @return a camada alcancada, ou {@link #NO_MATCH}
     */
    public static int matchToken(SearchField field, String token, long tokenMask,
                                 int maxDist, MatchPolicy policy, Scratch scratch) {
        scratch.position = 0;
        scratch.distance = 0;

        final boolean allowTypos = policy.allowTypos();
        final String text = field.text;
        final int tokenLength = token.length();
        if (tokenLength == 0 || text.isEmpty()) {
            return NO_MATCH;
        }

        // Pre-filtro: um token so pode casar de forma estrita se todos os seus caracteres
        // existirem no alvo. Uma instrucao de AND + popcount elimina a maioria dos itens.
        int missing = Long.bitCount(tokenMask & ~field.mask);
        int allowedMissing = allowTypos ? maxDist : 0;
        if (missing > allowedMissing) {
            return NO_MATCH;
        }

        if (missing == 0) {
            // --- camadas estritas ---
            if (text.length() == tokenLength) {
                if (text.equals(token)) {
                    return TIER_EXACT;
                }
            } else if (text.startsWith(token)) {
                return TIER_PREFIX;
            }

            int[] starts = field.wordStarts;
            int bestWordTier = NO_MATCH;
            int bestWordPos = 0;
            for (int w = 0; w < starts.length; w++) {
                int s = starts[w];
                int e = field.wordEnd(w);
                if (e - s < tokenLength) {
                    continue;
                }
                if (text.regionMatches(s, token, 0, tokenLength)) {
                    if (e - s == tokenLength) {
                        scratch.position = s;
                        return TIER_WORD_EXACT;
                    }
                    if (bestWordTier < TIER_WORD_PREFIX) {
                        bestWordTier = TIER_WORD_PREFIX;
                        bestWordPos = s;
                    }
                }
            }
            if (bestWordTier != NO_MATCH) {
                scratch.position = bestWordPos;
                return bestWordTier;
            }

            String compact = field.compact;
            if (policy.allowCompact() && compact != null && compact.length() >= tokenLength) {
                int idx = compact.indexOf(token);
                if (idx >= 0) {
                    scratch.position = idx;
                    return TIER_COMPACT;
                }
            }

            int idx = text.indexOf(token);
            if (idx >= 0) {
                scratch.position = idx;
                return TIER_SUBSTRING;
            }

            String initials = field.initials;
            if (policy.allowInitials() && initials != null && tokenLength >= 2
                    && matchesInitials(initials, token)) {
                scratch.position = 0;
                return TIER_INITIALS;
            }
        }

        // --- camada aproximada ---
        // Nao ha limite de tamanho aqui de proposito: quem decide isso e o "Tamanho minimo
        // para erros" da configuracao, que ja zerou o maxDist para palavras curtas demais.
        if (!allowTypos || maxDist <= 0) {
            return NO_MATCH;
        }

        int bestDistance = maxDist + 1;
        int bestPos = 0;
        int[] starts = field.wordStarts;
        for (int w = 0; w < starts.length; w++) {
            int s = starts[w];
            int e = field.wordEnd(w);
            if (e - s <= 0 || tokenLength - (e - s) > maxDist) {
                continue; // palavra curta demais para caber o token
            }
            int d = prefixDistance(token, text, s, e, bestDistance - 1, scratch);
            if (d < bestDistance) {
                bestDistance = d;
                bestPos = s;
                if (d == 0) {
                    break;
                }
            }
        }

        // Consulta escrita sem espacos e com erro ("netheritsword").
        String compact = policy.allowCompact() ? field.compact : null;
        if (bestDistance > 0 && compact != null && tokenLength - compact.length() <= maxDist) {
            int d = prefixDistance(token, compact, 0, compact.length(), bestDistance - 1, scratch);
            if (d < bestDistance) {
                bestDistance = d;
                bestPos = 0;
            }
        }

        if (bestDistance <= maxDist) {
            scratch.position = bestPos;
            scratch.distance = bestDistance;
            return TIER_TYPO;
        }
        return NO_MATCH;
    }

    /**
     * As iniciais do alvo cobrem o token?
     *
     * <p>Exigir prefixo exato fazia esta camada funcionar so as vezes: "Bancada de Trabalho"
     * tem iniciais "bdt", entao o "bt" que qualquer um digitaria nao casava por causa do
     * "de". Agora as letras precisam aparecer <b>na ordem</b>, mas nao coladas - e a
     * primeira precisa ser a inicial da primeira palavra, para nao virar bagunca.
     */
    static boolean matchesInitials(String initials, String token) {
        if (initials.isEmpty() || initials.charAt(0) != token.charAt(0)) {
            return false;
        }
        int matched = 0;
        for (int i = 0; i < initials.length() && matched < token.length(); i++) {
            if (initials.charAt(i) == token.charAt(matched)) {
                matched++;
            }
        }
        return matched == token.length();
    }

    /**
     * Distancia de Damerau-Levenshtein entre {@code token} e o MELHOR PREFIXO de
     * {@code target[from, to)}, com corte antecipado.
     *
     * <p>Usar prefixo (e nao a palavra inteira) e o que faz "nether" achar "netherite" e
     * "netherit" achar "netherite" com custo zero de erros: o final da palavra alvo nao e
     * penalizado. A transposicao (Damerau) resolve "swrod" -> "sword".
     *
     * @param max maior distancia ainda interessante; acima disso retorna {@code max + 1}
     */
    public static int prefixDistance(String token, String target, int from, int to, int max, Scratch scratch) {
        final int n = token.length();
        if (max < 0) {
            return 1;
        }
        // Nunca vale a pena olhar mais que n + max caracteres do alvo.
        int limit = Math.min(to, from + n + max);
        scratch.ensure(n + 1);

        int[] prev2 = scratch.rowA;
        int[] prev = scratch.rowB;
        int[] cur = scratch.rowC;

        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }
        int best = n; // prefixo vazio do alvo

        for (int i = from + 1; i <= limit; i++) {
            char tc = target.charAt(i - 1);
            int row = i - from;
            cur[0] = row;
            int rowMin = row;
            for (int j = 1; j <= n; j++) {
                char qc = token.charAt(j - 1);
                int cost = qc == tc ? 0 : 1;
                int v = prev[j - 1] + cost;
                int del = prev[j] + 1;
                if (del < v) {
                    v = del;
                }
                int ins = cur[j - 1] + 1;
                if (ins < v) {
                    v = ins;
                }
                if (row > 1 && j > 1 && qc == target.charAt(i - 2) && token.charAt(j - 2) == tc) {
                    int trans = prev2[j - 2] + 1;
                    if (trans < v) {
                        v = trans;
                    }
                }
                cur[j] = v;
                if (v < rowMin) {
                    rowMin = v;
                }
            }
            if (cur[n] < best) {
                best = cur[n];
            }
            if (rowMin > max) {
                return max + 1; // toda a linha ja estourou o limite: nao melhora mais
            }
            int[] tmp = prev2;
            prev2 = prev;
            prev = cur;
            cur = tmp;
        }

        scratch.rowA = prev2;
        scratch.rowB = prev;
        scratch.rowC = cur;
        return best;
    }
}
