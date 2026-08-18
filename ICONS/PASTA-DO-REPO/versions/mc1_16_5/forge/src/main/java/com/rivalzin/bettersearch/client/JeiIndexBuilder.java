package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import mezz.jei.ingredients.IIngredientListElementInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforma a lista de ingredientes do JEI em um {@link SearchIndex}.
 *
 * <p>Este arquivo existe por causa de um erro meu na 1.5. Naquela versao a busca dentro do JEI
 * era uma copia reduzida do motor: comparava um texto so, o nome do item no idioma do jogo.
 * Resultado pratico - dentro do JEI o mod so achava em ingles, ignorava id, tooltip, apelido e
 * filtro de mod, e nao ordenava nada. Era outra busca, com o mesmo nome.
 *
 * <p>Agora as entradas saem do mesmo {@link CreativeIndexBuilder#fill} que monta o indice do
 * criativo. Item por item, campo por campo. O que o menu do Better Search acha, o JEI acha, e
 * toda opcao da tela de configuracao vale nos dois lugares porque quem decide e o mesmo motor.
 */
public final class JeiIndexBuilder {

    private JeiIndexBuilder() {
    }

    public static SearchIndex<IIngredientListElementInfo<?>> build(List<IIngredientListElementInfo<?>> source,
                                                     LanguageTable languages,
                                                     SearchSettings settings,
                                                     Player player) {
        long start = System.nanoTime();
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        boolean englishSearched = CreativeIndexBuilder.englishSearched(codes);

        List<SearchIndex.Entry<IIngredientListElementInfo<?>>> entries = new ArrayList<>(source.size());
        for (IIngredientListElementInfo<?> element : source) {
            try {
                EntryBuilder<IIngredientListElementInfo<?>> builder = new EntryBuilder<>(element);
                fill(builder, element, languages, codes, settings, player,
                        englishSearched);
                if (!builder.isEmpty()) {
                    entries.add(builder.build());
                }
            } catch (Throwable t) {
                // Um ingrediente problematico de algum mod nao pode derrubar a lista inteira.
                BetterSearch.LOGGER.debug("[{}] ingrediente do JEI ignorado no indice: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }

        BetterSearch.LOGGER.info("[{}] indice do JEI pronto: {} de {} ingredientes em {} ms",
                BetterSearch.MOD_NAME, entries.size(), source.size(),
                (System.nanoTime() - start) / 1_000_000);
        return new SearchIndex<>(entries);
    }

    /**
     * A lista do JEI nao e so de itens: tem fluido e tem tipo proprio de mod. Quando o
     * ingrediente e um ItemStack - que e a esmagadora maioria - ele passa pelo mesmo caminho do
     * criativo. Para o resto sobra o que o JEI sabe dizer de qualquer tipo: nome e id.
     */
    private static <V> void fill(EntryBuilder<IIngredientListElementInfo<?>> builder,
                                 IIngredientListElementInfo<V> element,
                                 LanguageTable languages,
                                 List<String> codes,
                                 SearchSettings settings,
                                 Player player,
                                 boolean englishSearched) {
        V ingredient = element.getElement().getIngredient();

        if (ingredient instanceof ItemStack) {
            ItemStack stack = (ItemStack) ingredient;
            CreativeIndexBuilder.fill(builder, stack, languages, codes, settings, player,
                    englishSearched);
            return;
        }

        /*
         * 1.18.2: aqui nao existe IIngredientHelper a mao, mas nao faz falta - o proprio
         * IIngredientListElementInfo ja carrega o nome e o id prontos. Nas versoes novas o JEI trocou
         * este tipo por IListElement, que so tem o ingrediente, e ai o helper voltou a ser
         * necessario.
         */
        builder.add(element.getName(), SearchField.SOURCE_NATIVE);

        /*
         * Aqui e getResourceId(), e ele devolve String - nao ResourceLocation.
         *
         * Da 1.18.2 em diante o metodo e getResourceLocation() e ja entrega o objeto partido em
         * namespace e caminho. Na 7.8 vem o texto cru ("minecraft:diamond_axe"), entao a divisao
         * e nossa. Conferido com javap no IIngredientListElementInfo do jar de verdade:
         *
         *     public abstract java.lang.String getResourceId();
         *
         * Sem os dois pontos (alguns mods registram id sem namespace), tratamos tudo como
         * caminho e nao inventamos um namespace que nao esta escrito.
         */
        String id = element.getResourceId();
        if (id != null && !id.isEmpty()) {
            int dois = id.indexOf(':');
            String espaco = dois > 0 ? id.substring(0, dois) : "";
            String caminho = dois >= 0 ? id.substring(dois + 1) : id;
            if (!espaco.isEmpty()) {
                builder.modId(espaco);
            }
            if (settings.searchItemIds) {
                String texto = espaco.isEmpty() ? caminho.replace('_', ' ')
                        : espaco + ' ' + caminho.replace('_', ' ');
                builder.addNormalized(texto, SearchField.SOURCE_ID);
            }
        }
    }
}
