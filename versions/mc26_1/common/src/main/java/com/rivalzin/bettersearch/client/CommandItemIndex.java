package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Indice de <b>IDs de item</b>, pesquisavel pelo nome traduzido.
 *
 * <p>E o que permite escrever {@code /give @p bau} e receber {@code minecraft:chest} na
 * listinha de sugestoes. Diferente do indice do criativo, este cobre o registro inteiro
 * (nao so a aba de busca), porque um comando aceita qualquer item existente.
 *
 * <p>Montado sob demanda e fora da thread principal: quem nunca liga a opcao nunca paga
 * por ele.
 */
public final class CommandItemIndex {

    private static final AsyncIndex<Identifier> INDEX = new AsyncIndex<>("IDs de item");

    private CommandItemIndex() {
    }

    public static void invalidate() {
        INDEX.invalidate();
    }

    /**
     * @return os IDs que casam com o texto, ou {@code null} enquanto o indice nao existir
     */
    public static List<Identifier> search(String rawQuery) {
        SearchSettings settings = BetterSearchClient.settings();
        if (!BetterSearchClient.isEnabled() || !settings.searchCommandItems) {
            return null;
        }

        final LanguageTable languages = BetterSearchClient.languages();
        final SearchSettings snapshot = settings.copy();
        int size = BuiltInRegistries.ITEM.size();

        SearchIndex<Identifier> index = INDEX.get(BuiltInRegistries.ITEM, size,
                BetterSearchClient.languageStamp(), () -> build(languages, snapshot));
        if (index == null) {
            return null;
        }
        try {
            SearchQuery query = SearchQuery.parse(rawQuery, settings);
            if (query.isEmpty()) {
                return null;
            }
            return index.search(query, settings);
        } catch (Throwable t) {
            BetterSearch.LOGGER.error("[{}] erro na busca de IDs de item", BetterSearch.MOD_NAME, t);
            return null;
        }
    }

    private static SearchIndex<Identifier> build(LanguageTable languages, SearchSettings settings) {
        List<String> codes = new ArrayList<>();
        for (String code : languages.languageCodes()) {
            if (settings.indexesLanguage(code)) {
                codes.add(code);
            }
        }

        List<SearchIndex.Entry<Identifier>> entries = new ArrayList<>(BuiltInRegistries.ITEM.size());
        for (Item item : BuiltInRegistries.ITEM) {
            try {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                if (id == null) {
                    continue;
                }
                EntryBuilder<Identifier> builder = new EntryBuilder<>(id);
                builder.modId(id.getNamespace());

                String descriptionId = item.getDescriptionId();
                builder.add(Component.translatable(descriptionId).getString(), SearchField.SOURCE_NATIVE);
                for (String code : codes) {
                    String translated = languages.get(code, descriptionId);
                    if (translated != null) {
                        builder.add(translated, code.equals("en_us")
                                ? SearchField.SOURCE_ENGLISH
                                : SearchField.SOURCE_FOREIGN);
                    }
                }
                builder.addNormalized(id.getNamespace() + ' ' + id.getPath().replace('_', ' '),
                        SearchField.SOURCE_ID);
                entries.add(builder.build());
            } catch (Throwable t) {
                BetterSearch.LOGGER.debug("[{}] item ignorado no indice de comandos: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }
        BetterSearch.LOGGER.info("[{}] indice de IDs de item pronto: {} itens",
                BetterSearch.MOD_NAME, entries.size());
        return new SearchIndex<>(entries);
    }
}
