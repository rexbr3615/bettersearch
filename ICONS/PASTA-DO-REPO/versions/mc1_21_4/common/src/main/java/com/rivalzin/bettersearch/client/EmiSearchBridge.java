package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.search.EmiSearch;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A busca do Better Search dentro da lista do EMI.
 *
 * <p>Mesma correcao do {@link JeiSearch}: na 1.5 isto era um socorro com um comparador reduzido,
 * agora e o motor de verdade, com o indice montado pelo mesmo {@link CreativeIndexBuilder#fill}
 * do criativo. Toda opcao do menu vale aqui porque quem decide e o mesmo {@link SearchIndex}.
 *
 * <p>A diferenca para o JEI e so onde o trabalho acontece. <b>O EMI ja busca fora da thread do
 * jogo</b> - o gancho roda na {@code searchThread} dele - entao o indice e montado ali mesmo, na
 * hora, sem {@code CompletableFuture} e sem o problema de cache velho. Enquanto a primeira busca
 * monta o indice, a lista anterior continua na tela, que e como o EMI ja se comporta sozinho.
 */
public final class EmiSearchBridge {

    /**
     * Sintaxe que e do EMI e nao minha: {@code #} tooltip, {@code $} tag, {@code /} regex e
     * {@code |} para OU. Vendo qualquer uma delas eu devolvo a lista dele intacta.
     *
     * <p>O {@code @} fica de fora: filtro de mod os dois temos, e o nosso perdoa erro no nome.
     */
    private static final String EMI_SYNTAX = "#$/|";

    private static volatile SearchIndex<EmiIngredient> index;
    private static volatile int indexedSize = -1;
    private static volatile long indexedStamp = Long.MIN_VALUE;

    static {
        /*
         * O EMI guarda o texto digitado e a ULTIMA lista computada (EmiSearch.stacks);
         * nada o faz recomputar quando a NOSSA configuracao muda - desligar o interruptor
         * parecia nao fazer nada ate o texto mudar (bug de campo, Cobblemon 1.21.1).
         * update() refaz a consulta atual; com o gate desligado, a lista volta a ser 100%%
         * do EMI na hora. javap no jar 1.21.1: public static void update().
         */
        BetterSearchClient.onSettingsApplied(() -> {
            try {
                EmiSearch.update();
            } catch (Throwable ignored) {
                // sem tela do EMI aberta nao ha o que refazer
            }
        });
    }

    private EmiSearchBridge() {
    }

    public static void invalidate() {
        index = null;
        indexedSize = -1;
        indexedStamp = Long.MIN_VALUE;
    }

    /**
     * @param query  o texto que o jogador digitou
     * @param result o que o EMI achou sozinho, que nunca e jogado fora
     * @param source a lista completa, que o proprio worker ja tinha em maos
     * @return a lista que substitui a do EMI, ou {@code null} para deixar a dele em paz
     */
    public static List<? extends EmiIngredient> search(String query,
                                                       List<? extends EmiIngredient> result,
                                                       List<? extends EmiIngredient> source) {
        try {
            SearchSettings settings = BetterSearchClient.settings();
            if (!BetterSearchClient.isEnabled() || !settings.searchEmi) {
                return null;
            }
            if (query == null || query.isBlank() || source == null || source.isEmpty()) {
                return null;
            }
            if (usesEmiSyntax(query)) {
                return null;
            }

            SearchIndex<EmiIngredient> ready = ensureIndex(source, settings);
            if (ready == null) {
                return null;
            }
            SearchQuery parsed = SearchQuery.parse(query, settings);
            if (parsed.isEmpty()) {
                return null;
            }

            List<EmiIngredient> ours = ready.search(parsed, settings);
            if (result == null || result.isEmpty()) {
                return ours.isEmpty() ? null : List.copyOf(ours);
            }

            // O que o EMI achou sozinho nunca se perde: ele procura em campos que o mod nao
            // guarda, entao a lista final e sempre um superconjunto da dele.
            List<EmiIngredient> merged = new ArrayList<>(ours.size() + result.size());
            if (settings.sortByRelevance) {
                Set<EmiIngredient> seen = new HashSet<>(ours);
                merged.addAll(ours);
                for (EmiIngredient ingredient : result) {
                    if (seen.add(ingredient)) {
                        merged.add(ingredient);
                    }
                }
            } else {
                Set<EmiIngredient> fromEmi = new HashSet<>(result);
                merged.addAll(result);
                for (EmiIngredient ingredient : ours) {
                    if (!fromEmi.contains(ingredient)) {
                        merged.add(ingredient);
                    }
                }
            }
            return List.copyOf(merged);
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] busca do EMI inalterada: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return null;
        }
    }

    /**
     * Monta o indice na hora, na propria thread de busca do EMI.
     *
     * <p>Sem assincronia de proposito: ja estamos fora da thread do jogo, entao a unica coisa que
     * esta montagem atrasa e esta busca.
     */
    private static SearchIndex<EmiIngredient> ensureIndex(List<? extends EmiIngredient> source,
                                                          SearchSettings settings) {
        long stamp = BetterSearchClient.languageStamp();
        SearchIndex<EmiIngredient> current = index;
        if (current != null && indexedSize == source.size() && indexedStamp == stamp) {
            return current;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }

        long start = System.nanoTime();
        LanguageTable languages = BetterSearchClient.languages();
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        boolean englishSearched = CreativeIndexBuilder.englishSearched(codes);
        Item.TooltipContext tooltipContext = Item.TooltipContext.of(minecraft.level);

        List<SearchIndex.Entry<EmiIngredient>> entries = new ArrayList<>(source.size());
        for (EmiIngredient ingredient : source) {
            try {
                EntryBuilder<EmiIngredient> builder = new EntryBuilder<>(ingredient);
                ItemStack stack = stackOf(ingredient);
                if (stack != null && !stack.isEmpty()) {
                    CreativeIndexBuilder.fill(builder, stack, languages, codes, settings,
                            tooltipContext, minecraft.player, englishSearched);
                } else {
                    fillOther(builder, ingredient, settings);
                }
                if (!builder.isEmpty()) {
                    entries.add(builder.build());
                }
            } catch (Throwable t) {
                BetterSearch.LOGGER.debug("[{}] ingrediente do EMI ignorado no indice: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }

        SearchIndex<EmiIngredient> built = new SearchIndex<>(entries);
        BetterSearch.LOGGER.info("[{}] indice do EMI pronto: {} de {} ingredientes em {} ms",
                BetterSearch.MOD_NAME, entries.size(), source.size(),
                (System.nanoTime() - start) / 1_000_000);
        index = built;
        indexedSize = source.size();
        indexedStamp = stamp;
        return built;
    }

    /**
     * Fluido, ou o tipo proprio de algum mod: sobra o nome.
     *
     * <p>Nas duas versoes onde o EMI existe oficialmente esta passada tambem indexa o id do
     * ingrediente. Aqui nao, e de proposito: {@code EmiStack.getId()} devolve um tipo do
     * Minecraft que mudou de nome ao longo das versoes, e este arquivo e compilado contra o jar
     * do EMI da 1.21.1 - o unico que existe. Ficando so no nome, o codigo vale para qualquer
     * port sem depender de como aquele tipo se chama nesta versao. Item continua sendo indexado
     * por id normalmente, porque isso vem do caminho do criativo.
     */
    private static void fillOther(EntryBuilder<EmiIngredient> builder, EmiIngredient ingredient,
                                  SearchSettings settings) {
        List<EmiStack> stacks = ingredient.getEmiStacks();
        if (stacks.isEmpty()) {
            return;
        }
        builder.add(stacks.get(0).getName().getString(), SearchField.SOURCE_NATIVE);
    }

    private static ItemStack stackOf(EmiIngredient ingredient) {
        try {
            List<EmiStack> stacks = ingredient.getEmiStacks();
            return stacks.isEmpty() ? null : stacks.get(0).getItemStack();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean usesEmiSyntax(String query) {
        if (query.indexOf('|') >= 0) {
            return true;
        }
        for (String piece : query.split("\\s+")) {
            if (!piece.isEmpty() && EMI_SYNTAX.indexOf(piece.charAt(0)) >= 0) {
                return true;
            }
        }
        return false;
    }
}
