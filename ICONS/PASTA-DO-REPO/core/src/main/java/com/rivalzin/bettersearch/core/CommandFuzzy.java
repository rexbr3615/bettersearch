package com.rivalzin.bettersearch.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Acha a opcao correta a partir da palavra errada que o jogador digitou num comando.
 *
 * <p>Comandos nao sao traduzidos: quem joga em portugues escreve {@code /gamemode criativo}
 * e leva um erro vermelho, porque a unica resposta certa e {@code creative}. Aqui a palavra
 * errada e comparada com as opcoes que o proprio jogo aceita naquele ponto do comando, e as
 * mais parecidas voltam como sugestao.
 *
 * <p><b>Duas camadas, com propositos opostos.</b>
 * <ol>
 *   <li><b>Parecidos</b> - todo candidato que passa da nota minima entra, sem limite de
 *       quantidade. Se dez opcoes se parecem com o que voce escreveu, aparecem as dez e voce
 *       rola a lista. E o mesmo criterio das outras buscas do mod: parecido aparece, diferente
 *       some.</li>
 *   <li><b>O mais proximo, custe o que custar</b> - se <i>nenhum</i> candidato passou da nota
 *       minima, ainda assim volta <b>um</b>: o de maior subsequencia comum. E o que faz
 *       {@code /gamemode sobrevivencia} oferecer {@code survival}, que nao chega perto de ser
 *       um erro de digitacao, mas e claramente mais parecido que {@code creative} ou
 *       {@code spectator}. Nunca deixar o jogador sem saida vale mais do que ficar calado.</li>
 * </ol>
 *
 * <p>Java puro, sem Minecraft: da para testar fora do jogo e vale igual em qualquer loader.
 */
public final class CommandFuzzy {

    /**
     * Teto de seguranca, nao um limite de verdade.
     *
     * <p>Nao existe caso real em que cem opcoes se parecam com a mesma palavra; este numero
     * so evita que uma lista absurda escape de um pacote de mods estranho.
     */
    public static final int SAFETY_CAP = 100;

    /** Nota minima para uma opcao ser "parecida"; abaixo disto ela so entra como ultimo recurso. */
    private static final int SCORE_FLOOR = 380;

    private static final int NO_MATCH = Integer.MIN_VALUE;

    /** Palavras menores que isto sao ambiguas demais para corrigir. */
    private static final int MIN_WORD = 2;

    private CommandFuzzy() {
    }

    /** Era um record; classe comum pelo mesmo motivo do MatchPolicy (o core/ compila em Java 8). */
    private static final class Scored {

        private final String text;
        private final int score;

        Scored(String text, int score) {
            this.text = text;
            this.score = score;
        }

        String text() {
            return text;
        }

        int score() {
            return score;
        }
    }

    /**
     * As opcoes mais parecidas com {@code word}, da melhor para a pior.
     *
     * <p>Nunca devolve lista vazia quando ha candidatos com letras: se nada for parecido,
     * volta o mais proximo, sozinho.
     */
    public static List<String> best(String word, Collection<String> candidates, int limit) {
        List<String> out = new ArrayList<>();
        if (word == null || candidates == null || candidates.isEmpty() || limit <= 0) {
            return out;
        }
        String query = letters(fold(word));
        if (query.length() < MIN_WORD || !hasLetter(query)) {
            return out; // uma letra, ou so numeros: nao da para adivinhar nada
        }

        List<Scored> hits = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            int score = score(query, candidate);
            if (score != NO_MATCH && score >= SCORE_FLOOR) {
                hits.add(new Scored(candidate, score));
            }
        }

        if (hits.isEmpty()) {
            // Camada 2: ninguem e parecido, entao vale o menos diferente.
            String closest = closest(query, candidates);
            if (closest != null) {
                out.add(closest);
            }
            return out;
        }

        // Nota primeiro; empatando, a opcao mais curta (quase sempre a que a pessoa queria);
        // empatando de novo, ordem alfabetica - so para a lista nao mudar sozinha entre
        // duas teclas.
        hits.sort(Comparator.comparingInt(Scored::score).reversed()
                .thenComparingInt(hit -> hit.text().length())
                .thenComparing(Scored::text));
        for (int i = 0; i < hits.size() && out.size() < limit; i++) {
            out.add(hits.get(i).text());
        }
        return out;
    }

    public static List<String> best(String word, Collection<String> candidates) {
        return best(word, candidates, SAFETY_CAP);
    }

    // ------------------------------------------------------------------ o menos diferente

    /**
     * O candidato de maior subsequencia comum com a palavra digitada.
     *
     * <p>Subsequencia, e nao distancia de edicao: para textos de tamanhos bem diferentes a
     * distancia vira quase so a diferenca de comprimento e perde a noticia. Em
     * "sobrevivencia" x "survival" a subsequencia comum e {@code s r v i v a} - seis letras
     * na ordem certa, mais do que qualquer outro modo de jogo consegue.
     */
    private static String closest(String query, Collection<String> candidates) {
        String best = null;
        double bestScore = -1.0;
        long queryMask = mask(query);
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            String target = letters(fold(candidate));
            if (target.isEmpty() || (mask(target) & queryMask) == 0L) {
                continue; // sem uma unica letra em comum nao ha o que comparar
            }
            double score = similarity(query, target);
            int cut = Math.max(candidate.lastIndexOf(':'), candidate.lastIndexOf('/'));
            if (cut >= 0 && cut + 1 < candidate.length()) {
                String tail = letters(fold(candidate.substring(cut + 1)));
                if (!tail.isEmpty()) {
                    score = Math.max(score, similarity(query, tail));
                }
            }
            if (score > bestScore + 1e-9
                    || (best != null && Math.abs(score - bestScore) <= 1e-9 && shorterOrEarlier(candidate, best))) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean shorterOrEarlier(String candidate, String current) {
        if (candidate.length() != current.length()) {
            return candidate.length() < current.length();
        }
        return candidate.compareTo(current) < 0;
    }

    /** Razao da maior subsequencia comum: 0 = nada em comum, 1 = identicos. */
    static double similarity(String a, String b) {
        int longest = Math.max(a.length(), b.length());
        return longest == 0 ? 0.0 : commonSubsequence(a, b) / (double) longest;
    }

    static int commonSubsequence(String a, String b) {
        int la = a.length();
        int lb = b.length();
        int[] previous = new int[lb + 1];
        int[] current = new int[lb + 1];
        for (int i = 1; i <= la; i++) {
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= lb; j++) {
                current[j] = ca == b.charAt(j - 1)
                        ? previous[j - 1] + 1
                        : Math.max(previous[j], current[j - 1]);
            }
            int[] recycled = previous;
            previous = current;
            current = recycled;
            current[0] = 0;
        }
        return previous[lb];
    }

    /** Assinatura de 64 bits das letras presentes, usada so como pre-filtro barato. */
    private static long mask(String text) {
        long mask = 0L;
        for (int i = 0; i < text.length(); i++) {
            mask |= 1L << (text.charAt(i) % 64);
        }
        return mask;
    }

    // ------------------------------------------------------------------ pontuacao

    /**
     * Nota de uma opcao. O mesmo candidato e olhado de tres jeitos e vale o melhor deles:
     * inteiro ({@code minecraft:zombie}), so o fim ({@code zombie}) e so as iniciais
     * ({@code doDaylightCycle} -> {@code ddc}).
     */
    static int score(String query, String candidate) {
        int best = compare(query, letters(fold(candidate)));

        // O namespace nao pode atrapalhar: "zumbi" tem de achar "minecraft:zombie".
        int cut = Math.max(candidate.lastIndexOf(':'), candidate.lastIndexOf('/'));
        String tail = cut >= 0 && cut + 1 < candidate.length() ? candidate.substring(cut + 1) : null;
        if (tail != null) {
            best = Math.max(best, demote(compare(query, letters(fold(tail))), 10));
        }

        if (query.length() >= 2) {
            best = Math.max(best, initialsScore(query, candidate));
            if (tail != null) {
                best = Math.max(best, demote(initialsScore(query, tail), 10));
            }
        }
        return best;
    }

    private static int initialsScore(String query, String raw) {
        String initials = initials(raw);
        if (initials.length() < 2) {
            return NO_MATCH;
        }
        if (initials.equals(query)) {
            return 730;
        }
        return initials.startsWith(query) ? 640 : NO_MATCH;
    }

    /** Desconto que nunca "da a volta" no inteiro quando a nota e {@link #NO_MATCH}. */
    private static int demote(int score, int penalty) {
        return score == NO_MATCH ? NO_MATCH : score - penalty;
    }

    private static int compare(String query, String target) {
        if (target.isEmpty()) {
            return NO_MATCH;
        }
        if (target.equals(query)) {
            return 1000;
        }
        if (target.startsWith(query)) {
            return 900 - Math.min(80, target.length() - query.length());
        }
        // "conter" so vale com palavra de verdade dos dois lados. Sem isto, "w" caberia
        // dentro de "wheater" e o comando /w ganharia de /weather - foi o que aconteceu.
        if (query.length() >= 3 && target.length() >= 3 && target.contains(query)) {
            return 760 - Math.min(60, target.length() - query.length());
        }
        if (target.length() >= 4 && target.length() * 2 >= query.length() && query.contains(target)) {
            // Digitou uma opcao valida e sobrou texto. Cada sobra pesa como um erro, senao
            // "tellrow" ficaria mais perto de "tell" do que de "tellraw".
            return 640 - Math.min(160, (query.length() - target.length()) * 40);
        }
        int max = maxEdits(query.length(), target.length());
        int distance = distance(query, target, max);
        if (distance >= 0) {
            return 680 - distance * 70;
        }
        if (query.length() >= 4 && isSubsequence(query, target)) {
            return 430 - Math.min(100, target.length() - query.length());
        }
        return NO_MATCH;
    }

    /**
     * Quantos erros de digitacao sao aceitos entre duas palavras.
     *
     * <p>Bem mais generoso que a busca de itens - metade da palavra - porque aqui o
     * candidato vem de uma lista fechada (as opcoes daquele comando) e nao do jogo inteiro,
     * e porque o corte por distancia ate o primeiro colocado limpa o resto depois.
     */
    static int maxEdits(int queryLength, int targetLength) {
        int n = Math.max(queryLength, targetLength);
        int allowed = n <= 3 ? 1 : n <= 4 ? 2 : n <= 7 ? 3 : n <= 10 ? 4 : 5;
        return Math.max(1, Math.min(allowed, n / 2));
    }

    /**
     * Distancia de Damerau-Levenshtein, ou -1 se passar de {@code max}.
     *
     * <p>Damerau, e nao Levenshtein simples, porque trocar duas letras de lugar
     * ({@code craetive}) e o erro de digitacao mais comum que existe e deve custar 1, nao 2.
     */
    static int distance(String a, String b, int max) {
        int la = a.length();
        int lb = b.length();
        if (Math.abs(la - lb) > max) {
            return -1;
        }
        if (la == 0) {
            return lb <= max ? lb : -1;
        }
        if (lb == 0) {
            return la <= max ? la : -1;
        }
        int[] beforePrevious = new int[lb + 1];
        int[] previous = new int[lb + 1];
        int[] current = new int[lb + 1];
        for (int j = 0; j <= lb; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= la; i++) {
            current[0] = i;
            int rowBest = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= lb; j++) {
                char cb = b.charAt(j - 1);
                int cost = ca == cb ? 0 : 1;
                int value = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1),
                        previous[j - 1] + cost);
                if (i > 1 && j > 1 && ca == b.charAt(j - 2) && a.charAt(i - 2) == cb) {
                    value = Math.min(value, beforePrevious[j - 2] + 1);
                }
                current[j] = value;
                rowBest = Math.min(rowBest, value);
            }
            if (rowBest > max) {
                return -1; // nenhuma continuacao desta linha cabe no limite
            }
            int[] recycled = beforePrevious;
            beforePrevious = previous;
            previous = current;
            current = recycled;
        }
        int distance = previous[lb];
        return distance > max ? -1 : distance;
    }

    static boolean isSubsequence(String query, String target) {
        int at = 0;
        for (int i = 0; i < target.length() && at < query.length(); i++) {
            if (target.charAt(i) == query.charAt(at)) {
                at++;
            }
        }
        return at == query.length();
    }

    /** Primeira letra de cada pedaco, quebrando em separadores e em maiuscula no meio. */
    static String initials(String raw) {
        StringBuilder out = new StringBuilder();
        boolean starting = true;
        char previous = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                starting = true;
                previous = c;
                continue;
            }
            boolean camel = Character.isUpperCase(c) && Character.isLowerCase(previous);
            if (starting || camel) {
                out.append(Character.toLowerCase(c));
            }
            starting = false;
            previous = c;
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ texto

    /** Minusculas e sem acento, mas <b>mantendo</b> os separadores ({@code : _ . -}). */
    static String fold(String input) {
        String decomposed = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD);
        StringBuilder out = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** So letras e digitos: {@code minecraft:zombie} vira {@code minecraftzombie}. */
    static String letters(String folded) {
        StringBuilder out = new StringBuilder(folded.length());
        for (int i = 0; i < folded.length(); i++) {
            char c = folded.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean hasLetter(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ recorte da palavra

    /** Um caractere faz parte da palavra que esta sendo corrigida? */
    public static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == ':';
    }

    /** Inicio da palavra que contem (ou termina em) {@code at}. */
    public static int wordStart(String text, int at) {
        int index = Math.max(0, Math.min(at, text.length()));
        while (index > 0 && isWordChar(text.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    /** Fim da palavra que comeca em {@code at}. */
    public static int wordEnd(String text, int at) {
        int index = Math.max(0, Math.min(at, text.length()));
        while (index < text.length() && isWordChar(text.charAt(index))) {
            index++;
        }
        return index;
    }
}
