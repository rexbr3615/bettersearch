package com.rivalzin.bettersearch.core;

/**
 * Compara UM texto solto com a consulta, sem montar indice nenhum.
 *
 * <p>O {@link SearchIndex} vale a pena para milhares de itens, onde o pre-processamento se
 * paga. Para listas curtas - nomes de mods, nomes de jogadores online - montar um indice
 * custaria mais do que a busca. Esta classe usa exatamente o mesmo algoritmo (mesmas
 * camadas, mesma normalizacao, mesma tolerancia a erro) em cima de um texto por vez.
 *
 * <p>Java puro, testavel sem Minecraft.
 */
public final class QuickMatcher {

    /** Devolvido quando o texto nao casa com a consulta. */
    public static final int NO_MATCH = Integer.MIN_VALUE;

    private QuickMatcher() {
    }

    /** Atalho para um unico teste. Para varios textos, reaproveite o {@link Session}. */
    public static boolean matches(String rawText, String rawQuery, SearchSettings settings) {
        return new Session(rawQuery, settings).score(rawText) != NO_MATCH;
    }

    /**
     * Consulta preparada uma vez e reutilizada para varios textos.
     *
     * <p>Nao e thread-safe (guarda um buffer de trabalho); use uma por laco de busca.
     */
    public static final class Session {

        private final SearchQuery query;
        private final SearchSettings settings;
        private final MatchPolicy policy;
        private final FuzzyMatcher.Scratch scratch = new FuzzyMatcher.Scratch();

        public Session(String rawQuery, SearchSettings settings) {
            this.settings = settings;
            this.query = SearchQuery.parse(rawQuery, settings);
            this.policy = MatchPolicy.of(settings, settings.typoTolerance > 0);
        }

        public boolean isEmpty() {
            return query.tokens.length == 0;
        }

        /** Pontuacao do texto (quanto maior, melhor), ou {@link #NO_MATCH}. */
        public int score(String rawText) {
            if (rawText == null || rawText.isEmpty()) {
                return NO_MATCH;
            }
            if (query.tokens.length == 0) {
                return 0;
            }
            String normalized = TextNormalizer.normalize(rawText);
            if (normalized.isEmpty()) {
                return NO_MATCH;
            }
            SearchField field = new SearchField(normalized, SearchField.SOURCE_NATIVE);

            int minTier = Integer.MAX_VALUE;
            int totalDistance = 0;
            int matchedChars = 0;
            int lastPosition = -1;
            boolean inOrder = true;
            boolean startsAtBeginning = false;

            for (int i = 0; i < query.tokens.length; i++) {
                String token = query.tokens[i];
                int tier = FuzzyMatcher.matchToken(field, token, query.tokenMasks[i],
                        query.maxDistances[i], policy, scratch);
                if (tier == FuzzyMatcher.NO_MATCH) {
                    return NO_MATCH;
                }
                minTier = Math.min(minTier, tier);
                totalDistance += scratch.distance;
                matchedChars += token.length();
                if (scratch.position < lastPosition) {
                    inOrder = false;
                }
                lastPosition = scratch.position;
                if (i == 0 && scratch.position == 0) {
                    startsAtBeginning = true;
                }
            }

            int score = minTier * 100;
            if (inOrder) {
                score += 250;
            }
            if (startsAtBeginning) {
                score += 150;
            }
            score += (int) (400L * matchedChars / Math.max(1, normalized.length()));
            score -= 150 * totalDistance;
            return score;
        }

        public SearchSettings settings() {
            return settings;
        }
    }
}
