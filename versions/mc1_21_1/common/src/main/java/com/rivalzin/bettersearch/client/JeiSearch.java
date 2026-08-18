package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IngredientFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A busca do Better Search dentro da lista de ingredientes do JEI.
 *
 * <p>Na 1.5 isto era um socorro: o JEI buscava, e o mod so entrava se ele tivesse voltado quase
 * vazio. Duas consequencias, as duas erradas. A primeira e que numa modpack quase toda palavra
 * devolve resultado por substring, entao o mod ficava desligado na maioria das buscas. A segunda
 * e que o pouco que ele fazia usava um comparador reduzido, de um campo so.
 *
 * <p>Agora o mod <b>e</b> a busca. O indice e o mesmo do criativo ({@link JeiIndexBuilder}), a
 * consulta e a mesma {@link SearchQuery}, e quem decide tudo - tolerancia a erro, iniciais,
 * idiomas, tooltip, id, ordenacao, limite - e a configuracao do menu. Nao existe mais numero
 * secreto so do JEI.
 *
 * <p>Duas garantias de seguranca:
 * <ul>
 *   <li>enquanto o indice nao estiver montado, devolvemos {@code null} e o JEI funciona como
 *       sempre funcionou. Nunca se espera o indice ficar pronto;</li>
 *   <li>o que o JEI achou sozinho nunca e jogado fora. Ele entra no fim da lista, entao o
 *       resultado do mod e sempre um superconjunto do resultado dele.</li>
 * </ul>
 */
public final class JeiSearch {

    /**
     * Prefixos que sao do JEI e nao meus: tag, cor, resource location e aba do criativo. O
     * {@code |} tambem e sintaxe dele (OU entre buscas). Vendo qualquer um destes eu devolvo a
     * busca inteira para ele.
     *
     * <p>O {@code @} fica de fora de proposito: filtro de mod os dois temos, e o nosso perdoa
     * erro de digitacao no nome do mod.
     */
    private static final String JEI_PREFIXES = "#$^%";

    private static final AsyncIndex<IListElement<?>> INDEX = new AsyncIndex<>("ingredientes do JEI");

    /**
     * O filtro do JEI, para poder mandar ele refazer a busca.
     *
     * <p>O JEI guarda o resultado em {@code ingredientListCached} e so refaz quando o texto muda.
     * A primeira busca do jogador dispara a montagem do indice e devolve null (ainda nao esta
     * pronto); o indice fica pronto um instante depois e o JEI nunca mais pergunta. Sem este
     * empurrao, parecia que o gancho nao funcionava.
     *
     * <p>Referencia fraca porque trocar de mundo joga o filtro fora e nao ha por que segurar.
     */
    private static WeakReference<IngredientFilter> filterRef = new WeakReference<>(null);

    static {
        /*
         * O JEI guarda o resultado da consulta ATUAL (filterCached) - a mesma licao do
         * 4.16 na 1.12.2, agora do lado moderno. invalidateCache derruba o cache e avisa
         * a grade, que refaz a busca com os gates novos (javap: public invalidateCache()).
         */
        BetterSearchClient.onSettingsApplied(() -> {
            try {
                IngredientFilter filter = filterRef.get();
                if (filter != null) {
                    filter.invalidateCache();
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private JeiSearch() {
    }

    public static void invalidate() {
        INDEX.invalidate();
    }

    /**
     * @param filterText o texto ja em minusculo, como o JEI entrega
     * @param jeiResult  o que o JEI achou sozinho, para nao se perder nada
     * @param source     a lista completa de ingredientes dele
     * @return a lista que substitui a do JEI, ou {@code null} para deixar a dele intacta
     */
    public static List<ITypedIngredient<?>> search(String filterText,
                                                   List<ITypedIngredient<?>> jeiResult,
                                                   Collection<IListElement<?>> source,
                                                   IIngredientManager manager,
                                                   IngredientFilter filter) {
        try {
            SearchSettings settings = BetterSearchClient.settings();
            if (!BetterSearchClient.isEnabled() || !settings.searchJei) {
                return null;
            }
            if (filterText == null || filterText.isBlank() || manager == null) {
                return null;
            }
            if (source == null || source.isEmpty() || usesJeiSyntax(filterText)) {
                return null;
            }
            remember(filter);

            SearchIndex<IListElement<?>> index = ensureIndex(source, manager, settings);
            if (index == null) {
                return null;
            }
            SearchQuery query = SearchQuery.parse(filterText, settings);
            if (query.isEmpty()) {
                return null;
            }

            List<IListElement<?>> found = index.search(query, settings);
            List<ITypedIngredient<?>> ours = new ArrayList<>(found.size());
            Set<ITypedIngredient<?>> seen = new HashSet<>(Math.max(16, found.size() * 2));
            for (IListElement<?> element : found) {
                // A visibilidade muda sem o indice mudar (modo de edicao do JEI, item escondido
                // por outro mod), entao ela e conferida agora e nao na montagem.
                if (!element.isVisible()) {
                    continue;
                }
                ITypedIngredient<?> typed = element.getTypedIngredient();
                if (seen.add(typed)) {
                    ours.add(typed);
                }
            }

            if (jeiResult == null || jeiResult.isEmpty()) {
                return ours.isEmpty() ? null : ours;
            }
            // O JEI procura em campos que o mod nao guarda (tag, cor, aba do criativo, e a
            // tooltip inteira de qualquer item). Devolver so a nossa lista poderia esconder
            // algo que ele teria achado, entao o dele sempre entra tambem.
            List<ITypedIngredient<?>> merged =
                    new ArrayList<>(ours.size() + jeiResult.size());
            if (settings.sortByRelevance) {
                merged.addAll(ours);
                for (ITypedIngredient<?> typed : jeiResult) {
                    if (seen.add(typed)) {
                        merged.add(typed);
                    }
                }
            } else {
                // Ordenacao por relevancia desligada: a lista do JEI fica exatamente como era e
                // o que o mod achou a mais entra depois dela.
                Set<ITypedIngredient<?>> fromJei = new HashSet<>(jeiResult);
                merged.addAll(jeiResult);
                for (ITypedIngredient<?> typed : ours) {
                    if (!fromJei.contains(typed)) {
                        merged.add(typed);
                    }
                }
            }
            return merged;
        } catch (Throwable t) {
            com.rivalzin.bettersearch.BetterSearch.LOGGER.debug(
                    "[{}] busca do JEI inalterada: {}",
                    com.rivalzin.bettersearch.BetterSearch.MOD_NAME, t.toString());
            return null;
        }
    }

    private static void remember(IngredientFilter filter) {
        if (filter != null && filterRef.get() != filter) {
            filterRef = new WeakReference<>(filter);
        }
    }

    /** Devolve o indice pronto, ou {@code null} enquanto ele nao existir. Nunca bloqueia. */
    private static SearchIndex<IListElement<?>> ensureIndex(Collection<IListElement<?>> source,
                                                            IIngredientManager manager,
                                                            SearchSettings settings) {
        final long stamp = BetterSearchClient.languageStamp();
        SearchIndex<IListElement<?>> ready = INDEX.ready(manager, source.size(), stamp);
        if (ready != null) {
            return ready;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        // A copia so acontece aqui, no caminho em que o indice falta mesmo. Perguntar antes ao
        // ready() evita copiar dezenas de milhares de elementos a cada tecla digitada.
        final List<IListElement<?>> copy = List.copyOf(source);
        final LanguageTable languages = BetterSearchClient.languages();
        final SearchSettings captured = settings.copy();
        final Item.TooltipContext tooltipContext = Item.TooltipContext.of(minecraft.level);
        final net.minecraft.world.entity.player.Player player = minecraft.player;

        return INDEX.get(manager, copy.size(), stamp,
                () -> JeiIndexBuilder.build(copy, manager, languages, captured, tooltipContext, player),
                JeiSearch::askJeiToSearchAgain);
    }

    private static void askJeiToSearchAgain() {
        IngredientFilter filter = filterRef.get();
        if (filter != null) {
            filter.invalidateCache();
        }
    }

    private static boolean usesJeiSyntax(String filterText) {
        if (filterText.indexOf('|') >= 0) {
            return true;
        }
        for (String piece : filterText.split("\\s+")) {
            if (!piece.isEmpty() && JEI_PREFIXES.indexOf(piece.charAt(0)) >= 0) {
                return true;
            }
        }
        return false;
    }
}
