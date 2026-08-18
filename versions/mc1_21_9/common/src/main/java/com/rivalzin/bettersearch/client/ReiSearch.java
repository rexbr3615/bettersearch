package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.search.SearchFilter;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A busca do Better Search dentro da lista de entradas do REI.
 *
 * <p>O REI tem uma forma diferente do JEI e do EMI. La a busca devolve uma lista pronta e da para
 * substituir; aqui ela e um {@link SearchFilter}, que e um {@code Predicate}: o REI pergunta
 * entrada por entrada "esta serve?". Nao existe lista para trocar no meio do caminho.
 *
 * <p>Entao o encaixe e outro, em duas partes:
 *
 * <ol>
 *   <li><b>Quem passa</b>: embrulhamos o filtro do REI. Na primeira pergunta o mod roda a busca
 *       inteira de uma vez sobre a lista de entradas dele e guarda o conjunto que casou. Cada
 *       {@code test} vira uma consulta de hash. O filtro original continua valendo - quem ele
 *       aprovaria continua passando, entao nunca se perde resultado.</li>
 *   <li><b>Em que ordem</b>: o REI ordena a lista filtrada em
 *       {@code EntryListSearchManager.copyAndOrder}. Como ali ja temos a pontuacao de cada
 *       entrada, da para reordenar por relevancia - quando a opcao estiver ligada no menu. Com
 *       ela desligada, a ordem do REI fica intacta.</li>
 * </ol>
 */
public final class ReiSearch {

    /**
     * Sintaxe que e do REI e nao minha: {@code #} tooltip, {@code $} tag, {@code *} id,
     * {@code -} exclusao e {@code |} para OU. Aspas e barra (expressao regular) tambem sao dele.
     * Vendo qualquer uma delas eu devolvo o filtro original sem embrulho.
     *
     * <p>O {@code @} fica de fora: filtro de mod os dois temos, e o nosso perdoa erro no nome.
     */
    private static final String REI_SYNTAX = "#$*-";

    private static final AsyncIndex<EntryStack<?>> INDEX = new AsyncIndex<>("entradas do REI");

    private ReiSearch() {
    }

    public static void invalidate() {
        INDEX.invalidate();
    }

    /**
     * A pontuacao desta consulta, ou {@code null} se este filtro nao e nosso (ou a busca ainda
     * nao rodou). Vem do proprio filtro, e nao de um campo global: assim o gancho de ordenacao
     * nunca reordena a lista de uma consulta com a pontuacao de outra.
     */
    public static Map<EntryStack<?>, Integer> rankingOf(SearchFilter filter) {
        return filter instanceof BetterSearchFilter ours ? ours.positionsIfReady() : null;
    }

    /**
     * Embrulha o filtro do REI, ou devolve ele mesmo quando o mod nao tem o que acrescentar.
     *
     * <p>Chamado uma vez por texto digitado, no {@code createFilter} do REI.
     */
    public static SearchFilter wrap(SearchFilter original) {
        try {
            if (original == null) {
                return null;
            }
            SearchSettings settings = BetterSearchClient.settings();
            if (!BetterSearchClient.isEnabled() || !settings.searchRei) {
                return original;
            }
            String text = original.getFilter();
            if (text == null || text.isBlank() || usesReiSyntax(text)) {
                return original;
            }
            return new BetterSearchFilter(original, text);
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] busca do REI inalterada: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return original;
        }
    }

    private static boolean usesReiSyntax(String text) {
        if (text.indexOf('|') >= 0 || text.indexOf('"') >= 0 || text.indexOf('/') >= 0) {
            return true;
        }
        for (String piece : text.split("\\s+")) {
            if (!piece.isEmpty() && REI_SYNTAX.indexOf(piece.charAt(0)) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * O filtro embrulhado.
     *
     * <p>{@code equals} e {@code hashCode} vao para o filtro original de proposito: o REI compara
     * filtros para saber se a busca mudou, e se o embrulho respondesse por si a lista seria
     * remontada a cada quadro.
     */
    private static final class BetterSearchFilter implements SearchFilter {
        private final SearchFilter original;
        private final String text;
        private volatile Map<EntryStack<?>, Integer> matched;

        BetterSearchFilter(SearchFilter original, String text) {
            this.original = original;
            this.text = text;
        }

        @Override
        public String getFilter() {
            return original.getFilter();
        }

        @Override
        public void prepareFilter(Collection<EntryStack<?>> stacks) {
            original.prepareFilter(stacks);
        }

        @Override
        public boolean test(EntryStack<?> stack, long hash) {
            return original.test(stack, hash) || ours().containsKey(stack);
        }

        @Override
        public boolean test(EntryStack<?> stack) {
            return original.test(stack) || ours().containsKey(stack);
        }

        /**
         * A busca de verdade, rodada uma unica vez por filtro.
         *
         * <p>O REI chama {@code test} dezenas de milhares de vezes por consulta, entao o trabalho
         * pesado nao pode morar la dentro: aqui ele acontece na primeira pergunta e as outras
         * viram consulta de hash.
         */
        Map<EntryStack<?>, Integer> positionsIfReady() {
            return matched;
        }

        private Map<EntryStack<?>, Integer> ours() {
            Map<EntryStack<?>, Integer> ready = matched;
            if (ready != null) {
                return ready;
            }
            synchronized (this) {
                if (matched != null) {
                    return matched;
                }
                matched = run(text);
                return matched;
            }
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other instanceof BetterSearchFilter wrapped) {
                return original.equals(wrapped.original);
            }
            return original.equals(other);
        }

        @Override
        public int hashCode() {
            return original.hashCode();
        }

        @Override
        public String toString() {
            return "BetterSearch(" + original + ")";
        }
    }

    private static Map<EntryStack<?>, Integer> run(String text) {
        try {
            SearchSettings settings = BetterSearchClient.settings();
            SearchIndex<EntryStack<?>> index = ensureIndex(settings);
            if (index == null) {
                return Map.of();
            }
            SearchQuery query = SearchQuery.parse(text, settings);
            if (query.isEmpty()) {
                return Map.of();
            }

            List<EntryStack<?>> found = index.search(query, settings);
            if (found.isEmpty()) {
                return Map.of();
            }
            // A posicao na lista E a relevancia: o SearchIndex ja devolveu ordenado.
            Map<EntryStack<?>, Integer> positions = new HashMap<>(found.size() * 2);
            for (int i = 0; i < found.size(); i++) {
                positions.putIfAbsent(found.get(i), i);
            }
            return positions;
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] busca do REI inalterada: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return Map.of();
        }
    }

    /** Devolve o indice pronto, ou {@code null} enquanto ele nao existir. Nunca bloqueia. */
    private static SearchIndex<EntryStack<?>> ensureIndex(SearchSettings settings) {
        EntryRegistry registry = EntryRegistry.getInstance();
        if (registry == null || registry.isReloading()) {
            return null;
        }
        List<EntryStack<?>> source = registry.getPreFilteredList();
        if (source == null || source.isEmpty()) {
            return null;
        }

        final long stamp = BetterSearchClient.languageStamp();
        SearchIndex<EntryStack<?>> ready = INDEX.ready(registry, source.size(), stamp);
        if (ready != null) {
            return ready;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        // A copia so acontece no caminho em que o indice falta mesmo.
        final List<EntryStack<?>> copy = List.copyOf(source);
        final LanguageTable languages = BetterSearchClient.languages();
        final SearchSettings captured = settings.copy();
        final Item.TooltipContext tooltipContext = Item.TooltipContext.of(minecraft.level);
        final net.minecraft.world.entity.player.Player player = minecraft.player;

        return INDEX.get(registry, copy.size(), stamp,
                () -> ReiIndexBuilder.build(copy, languages, captured, tooltipContext, player));
    }

    /**
     * Reordena a lista ja filtrada do REI pela relevancia desta consulta.
     *
     * <p>Quem o mod achou vem primeiro, na ordem da busca; o que so o REI achou vem depois, na
     * ordem que ele mesmo escolheu. Com a ordenacao por relevancia desligada no menu, ou com um
     * filtro que nao e nosso, devolve {@code null} e a lista do REI segue intacta.
     */
    public static <T> List<T> reorder(SearchFilter filter, List<T> ordered,
                                      java.util.function.Function<T, EntryStack<?>> unwrap) {
        try {
            SearchSettings settings = BetterSearchClient.settings();
            if (!BetterSearchClient.isEnabled() || !settings.searchRei || !settings.sortByRelevance) {
                return null;
            }
            if (ordered == null || ordered.size() < 2) {
                return null;
            }
            final Map<EntryStack<?>, Integer> positions = rankingOf(filter);
            if (positions == null || positions.isEmpty()) {
                return null;
            }

            List<T> ours = new ArrayList<>(Math.min(ordered.size(), positions.size()));
            List<T> rest = new ArrayList<>();
            for (T item : ordered) {
                EntryStack<?> stack = unwrap.apply(item);
                if (stack != null && positions.containsKey(stack)) {
                    ours.add(item);
                } else {
                    rest.add(item);
                }
            }
            if (ours.isEmpty()) {
                return null;
            }
            ours.sort(java.util.Comparator.comparingInt(
                    item -> positions.getOrDefault(unwrap.apply(item), Integer.MAX_VALUE)));
            ours.addAll(rest);
            return ours;
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] ordem do REI inalterada: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return null;
        }
    }
}
