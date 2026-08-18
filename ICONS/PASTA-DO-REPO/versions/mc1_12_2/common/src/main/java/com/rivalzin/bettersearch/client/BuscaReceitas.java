package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.recipebook.RecipeList;
import net.minecraft.client.util.RecipeBookClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Busca do livro de receitas na 1.12.2.
 *
 * <p>O valor indexado e a {@link RecipeList} - o mesmo tipo que a arvore vanilla de receitas
 * guarda - e os textos sao os das SAIDAS de cada receita, preenchidos pelo MESMO
 * {@link IndiceCriativo#preencher} do criativo. Buscar "espada" no livro e no criativo tem de
 * achar as mesmas coisas pelos mesmos textos; duas fontes de texto divergiriam com o tempo.
 *
 * <p>A fonte e {@link RecipeBookClient#ALL_RECIPES} - a lista estatica que o proprio jogo
 * monta e que a arvore vanilla tambem indexa (conferido com javap). O indice remonta quando o
 * tamanho dela muda (mods registram receita depois do login) ou quando o idioma muda.
 */
public final class BuscaReceitas {

    private static volatile SearchIndex<RecipeList> indice;
    private static volatile String idiomaDoIndice = "";
    private static volatile int marcaDoIndice = -1;
    private static volatile int tamanhoDaFonte = -1;
    private static volatile boolean montando;

    private BuscaReceitas() {
    }

    /** Nossa resposta, ou {@code null} para "deixa a busca original agir". */
    public static List<RecipeList> search(String consulta) {
        if (consulta == null || !Estado.settings().enabled || !Estado.settings().searchRecipeBook) {
            return null;
        }
        garantirIndice();
        SearchIndex<RecipeList> atual = indice;
        if (atual == null) {
            return null;
        }
        try {
            SearchQuery query = SearchQuery.parse(consulta, Estado.settings());
            if (query.isEmpty()) {
                return null;
            }
            return atual.search(query, Estado.settings());
        } catch (Throwable t) {
            BetterSearch.LOGGER.error("[{}] erro na busca de receitas; voltando para a original",
                    BetterSearch.MOD_NAME, t);
            return null;
        }
    }

    private static void garantirIndice() {
        List<RecipeList> fonte = RecipeBookClient.ALL_RECIPES;
        if (fonte == null || fonte.isEmpty()) {
            return;
        }
        Linguas.garantir(Estado.settings());
        String idioma = Minecraft.getMinecraft().gameSettings.language;
        int marca = Linguas.marca() + Estado.marca() * 100_000;
        if (montando || (indice != null && idiomaDoIndice.equals(idioma)
                && tamanhoDaFonte == fonte.size() && marcaDoIndice == marca)) {
            return;
        }
        montando = true;

        // Copia rasa na thread do cliente; o trabalho caro (normalizar) vai para fundo.
        final List<RecipeList> copia = new ArrayList<>(fonte);
        final String idiomaDaMontagem = idioma;

        Thread trabalho = new Thread(() -> {
            try {
                long inicio = System.nanoTime();
                List<SearchIndex.Entry<RecipeList>> entradas = new ArrayList<>(copia.size());
                for (RecipeList lista : copia) {
                    try {
                        EntryBuilder<RecipeList> builder = new EntryBuilder<>(lista);
                        for (IRecipe receita : lista.getRecipes()) {
                            ItemStack saida = receita.getRecipeOutput();
                            if (saida != null && !saida.isEmpty()) {
                                IndiceCriativo.preencher(builder, saida, Estado.settings(), null);
                            }
                        }
                        if (!builder.isEmpty()) {
                            entradas.add(builder.build());
                        }
                    } catch (Throwable t) {
                        BetterSearch.LOGGER.debug("[{}] receita ignorada no indice: {}",
                                BetterSearch.MOD_NAME, t.toString());
                    }
                }
                indice = new SearchIndex<>(entradas);
                idiomaDoIndice = idiomaDaMontagem;
                marcaDoIndice = marca;
                tamanhoDaFonte = copia.size();
                BetterSearch.LOGGER.info("[{}] indice de receitas pronto (1.12.2): {} listas em {} ms",
                        BetterSearch.MOD_NAME, entradas.size(), (System.nanoTime() - inicio) / 1_000_000);
            } catch (Throwable t) {
                BetterSearch.LOGGER.error("[{}] falha ao montar o indice de receitas",
                        BetterSearch.MOD_NAME, t);
            } finally {
                montando = false;
            }
        }, "BetterSearch-Receitas-1.12.2");
        trabalho.setDaemon(true);
        trabalho.start();
    }
}
