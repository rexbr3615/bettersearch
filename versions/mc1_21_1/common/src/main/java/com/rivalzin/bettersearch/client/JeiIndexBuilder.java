package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.ingredients.IListElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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

    public static SearchIndex<IListElement<?>> build(List<IListElement<?>> source,
                                                     IIngredientManager manager,
                                                     LanguageTable languages,
                                                     SearchSettings settings,
                                                     Item.TooltipContext tooltipContext,
                                                     Player player) {
        long start = System.nanoTime();
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        boolean englishSearched = CreativeIndexBuilder.englishSearched(codes);

        List<SearchIndex.Entry<IListElement<?>>> entries = new ArrayList<>(source.size());
        for (IListElement<?> element : source) {
            try {
                EntryBuilder<IListElement<?>> builder = new EntryBuilder<>(element);
                fill(builder, element, manager, languages, codes, settings, tooltipContext, player,
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
    private static <V> void fill(EntryBuilder<IListElement<?>> builder,
                                 IListElement<V> element,
                                 IIngredientManager manager,
                                 LanguageTable languages,
                                 List<String> codes,
                                 SearchSettings settings,
                                 Item.TooltipContext tooltipContext,
                                 Player player,
                                 boolean englishSearched) {
        ITypedIngredient<V> typed = element.getTypedIngredient();
        V ingredient = typed.getIngredient();

        if (ingredient instanceof ItemStack stack) {
            CreativeIndexBuilder.fill(builder, stack, languages, codes, settings, tooltipContext, player,
                    englishSearched);
            return;
        }

        IIngredientHelper<V> helper = manager.getIngredientHelper(typed.getType());
        builder.add(helper.getDisplayName(ingredient), SearchField.SOURCE_NATIVE);

        ResourceLocation id = helper.getResourceLocation(ingredient);
        if (id != null) {
            builder.modId(id.getNamespace());
            if (settings.searchItemIds) {
                builder.addNormalized(id.getNamespace() + ' ' + id.getPath().replace('_', ' '),
                        SearchField.SOURCE_ID);
            }
        }
    }
}
