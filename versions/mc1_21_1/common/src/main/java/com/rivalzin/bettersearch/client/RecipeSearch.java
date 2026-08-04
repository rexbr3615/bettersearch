package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Busca do livro de receitas.
 *
 * <p>Cada grupo de receitas e indexado pelo nome do item que ele produz, nos mesmos idiomas
 * configurados para o inventario criativo. Assim procurar "bau" (ou "coffre", ou "chest",
 * ou "bua") no livro de receitas acha a receita do bau.
 *
 * <p>O indice e montado assim que a tela do inventario abre - e nao na primeira letra
 * digitada. A razao e simples: a busca do livro so e reexecutada quando o texto muda, entao
 * um indice que ficasse pronto <i>depois</i> da ultima tecla nunca seria usado, e a busca
 * pareceria nao funcionar.
 */
public final class RecipeSearch {

    private static final AsyncIndex<RecipeCollection> INDEX = new AsyncIndex<>("receitas");
    private static boolean loggedActive;

    private RecipeSearch() {
    }

    public static void invalidate() {
        INDEX.invalidate();
        loggedActive = false;
    }

    /** Comeca a montar o indice, se ainda nao existir. Barato quando ja esta pronto. */
    public static void prepare() {
        ensureIndex();
    }

    /**
     * @return os grupos de receitas que casam, ou {@code null} para "use a busca original"
     */
    public static List<RecipeCollection> search(String rawQuery) {
        SearchIndex<RecipeCollection> index = ensureIndex();
        if (index == null || index.size() == 0) {
            return null;
        }
        try {
            SearchSettings settings = BetterSearchClient.settings();
            SearchQuery query = SearchQuery.parse(rawQuery, settings);
            if (query.isEmpty()) {
                return null;
            }
            if (!loggedActive) {
                loggedActive = true;
                BetterSearch.LOGGER.info("[{}] busca do livro de receitas ativa ({} grupos indexados)",
                        BetterSearch.MOD_NAME, index.size());
            }
            return index.search(query, settings);
        } catch (Throwable t) {
            BetterSearch.LOGGER.error("[{}] erro na busca de receitas", BetterSearch.MOD_NAME, t);
            return null;
        }
    }

    private static SearchIndex<RecipeCollection> ensureIndex() {
        SearchSettings settings = BetterSearchClient.settings();
        if (!BetterSearchClient.isEnabled() || !settings.searchRecipeBook) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }

        ClientRecipeBook book = minecraft.player.getRecipeBook();
        List<RecipeCollection> collections = book.getCollections();
        if (collections.isEmpty()) {
            return null; // o servidor ainda nao mandou as receitas
        }

        final List<RecipeCollection> snapshot = List.copyOf(collections);
        final LanguageTable languages = BetterSearchClient.languages();
        final SearchSettings snapshotSettings = settings.copy();

        return INDEX.get(collections, collections.size(), BetterSearchClient.languageStamp(),
                () -> build(snapshot, languages, snapshotSettings));
    }

    private static SearchIndex<RecipeCollection> build(List<RecipeCollection> collections,
                                                       LanguageTable languages,
                                                       SearchSettings settings) {
        List<String> codes = new ArrayList<>();
        for (String code : languages.languageCodes()) {
            if (settings.indexesLanguage(code)) {
                codes.add(code);
            }
        }

        List<SearchIndex.Entry<RecipeCollection>> entries = new ArrayList<>(collections.size());
        int skipped = 0;
        for (RecipeCollection collection : collections) {
            try {
                EntryBuilder<RecipeCollection> builder = new EntryBuilder<>(collection);
                for (RecipeHolder<?> holder : collection.getRecipes()) {
                    // Cada grupo carrega o proprio RegistryAccess: e mais correto (e mais
                    // seguro fora da thread principal) do que pegar o do mundo.
                    ItemStack result = holder.value().getResultItem(collection.registryAccess());
                    if (result.isEmpty()) {
                        continue;
                    }
                    builder.add(result.getHoverName().getString(), SearchField.SOURCE_NATIVE);

                    String descriptionId = result.getDescriptionId();
                    for (String code : codes) {
                        String translated = languages.get(code, descriptionId);
                        if (translated != null) {
                            builder.add(translated, code.equals("en_us")
                                    ? SearchField.SOURCE_ENGLISH
                                    : SearchField.SOURCE_FOREIGN);
                        }
                    }

                    if (settings.searchItemIds) {
                        ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.getItem());
                        if (id != null) {
                            builder.modId(id.getNamespace());
                            builder.addNormalized(id.getNamespace() + ' ' + id.getPath().replace('_', ' '),
                                    SearchField.SOURCE_ID);
                        }
                    }
                }
                if (builder.isEmpty()) {
                    skipped++;
                } else {
                    entries.add(builder.build());
                }
            } catch (Throwable t) {
                skipped++;
                BetterSearch.LOGGER.debug("[{}] grupo de receitas ignorado: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }
        BetterSearch.LOGGER.info("[{}] indice de receitas pronto: {} grupos ({} sem resultado utilizavel)",
                BetterSearch.MOD_NAME, entries.size(), skipped);
        return new SearchIndex<>(entries);
    }
}
