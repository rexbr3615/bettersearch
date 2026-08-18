package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforma a lista de entradas do REI em um {@link SearchIndex}.
 *
 * <p>Irmao gemeo do {@link JeiIndexBuilder}, e de proposito: as entradas saem do mesmo
 * {@link CreativeIndexBuilder#fill} que monta o indice do criativo, entao o que o menu do Better
 * Search acha, o REI acha, com os mesmos campos e as mesmas opcoes.
 */
public final class ReiIndexBuilder {

    private ReiIndexBuilder() {
    }

    public static SearchIndex<EntryStack<?>> build(List<EntryStack<?>> source,
                                                   LanguageTable languages,
                                                   SearchSettings settings,
                                                   Player player) {
        long start = System.nanoTime();
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        boolean englishSearched = CreativeIndexBuilder.englishSearched(codes);

        List<SearchIndex.Entry<EntryStack<?>>> entries = new ArrayList<>(source.size());
        for (EntryStack<?> stack : source) {
            try {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                EntryBuilder<EntryStack<?>> builder = new EntryBuilder<>(stack);
                Object value = stack.getValue();
                if (value instanceof ItemStack && !((ItemStack) value).isEmpty()) {
                    ItemStack item = (ItemStack) value;
                    CreativeIndexBuilder.fill(builder, item, languages, codes, settings,
                            player, englishSearched);
                } else {
                    fillOther(builder, stack, settings);
                }
                if (!builder.isEmpty()) {
                    entries.add(builder.build());
                }
            } catch (Throwable t) {
                // Uma entrada problematica de algum mod nao pode derrubar a lista inteira.
                BetterSearch.LOGGER.debug("[{}] entrada do REI ignorada no indice: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }

        BetterSearch.LOGGER.info("[{}] indice do REI pronto: {} de {} entradas em {} ms",
                BetterSearch.MOD_NAME, entries.size(), source.size(),
                (System.nanoTime() - start) / 1_000_000);
        return new SearchIndex<>(entries);
    }

    /** Fluido, ou o tipo proprio de algum mod: sobra o que o REI sabe dizer de qualquer um. */
    private static void fillOther(EntryBuilder<EntryStack<?>> builder, EntryStack<?> stack,
                                  SearchSettings settings) {
        builder.add(stack.asFormatStrippedText().getString(), SearchField.SOURCE_NATIVE);

        ResourceLocation id = stack.getIdentifier();
        if (id != null) {
            builder.modId(id.getNamespace());
            if (settings.searchItemIds) {
                builder.addNormalized(id.getNamespace() + ' ' + id.getPath().replace('_', ' '),
                        SearchField.SOURCE_ID);
            }
        }
    }
}
