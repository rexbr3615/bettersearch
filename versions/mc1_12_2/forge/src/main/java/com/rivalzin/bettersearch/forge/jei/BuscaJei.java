package com.rivalzin.bettersearch.forge.jei;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.Estado;
import com.rivalzin.bettersearch.client.Linguas;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.IngredientFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * O indice do Better Search sobre a lista de ingredientes do JEI.
 *
 * <p>E a BuscaCriativa com outra fonte: em vez de enumerar o criativo, le a {@code elementList}
 * do proprio filtro (reflexao so de LEITURA; o campo e final e ninguem aqui o troca). O valor
 * de cada entrada e a POSICAO do elemento na lista - o mesmo numero que as arvores do JEI
 * devolvem, conferido no bytecode do addIngredient (put recebe elementList.size()).
 *
 * <p>As mesmas regras de ouro: nunca travar o jogo (indice em thread de fundo; enquanto nao
 * esta pronto, {@code null} = so os resultados do JEI), nunca quebrar a busca (excecao =
 * {@code null}), e reconstruir quando idioma, configuracao ou a propria lista mudarem - a
 * lista do JEI CRESCE em jogo, entao o tamanho dela faz parte do carimbo.
 *
 * <p>Textos indexados por elemento, espelhando o indice do criativo: nome exibido, nomes nos
 * outros idiomas (quando o ingrediente e um ItemStack, pela chave de traducao + ".name"), id
 * de registro e as linhas de tooltip que o proprio JEI ja preparou ({@code getTooltipStrings}
 * e seguro fora da thread principal - o construtor de fundo do proprio JEI as usa de la).
 */
public final class BuscaJei {

    private static final int MAX_LINHAS_TOOLTIP = 6;

    private static volatile SearchIndex<Integer> indice;
    private static volatile String idiomaDoIndice = "";
    private static volatile int marcaDoIndice = -1;
    private static volatile int tamanhoDoIndice = -1;
    private static volatile boolean montando;

    private static Field campoLista;

    // So a thread do cliente toca nestes tres (o JEI busca sempre nela).
    private static SearchIndex<Integer> indiceDoCache;
    private static String palavraDoCache;
    private static int[] resultadoDoCache;

    private BuscaJei() {
    }

    /** Indices nossos para esta palavra, ou {@code null} para "so o que o JEI achou". */
    static int[] buscar(String palavra, IngredientFilter filtro) {
        SearchSettings settings = Estado.settings();
        if (palavra == null || palavra.isEmpty() || !settings.enabled || !settings.searchJei) {
            return null;
        }
        try {
            garantirIndice(filtro, settings);
            SearchIndex<Integer> atual = indice;
            if (atual == null) {
                return null;
            }
            if (atual == indiceDoCache && palavra.equals(palavraDoCache) && resultadoDoCache != null) {
                return resultadoDoCache;
            }
            SearchQuery query = SearchQuery.parse(palavra, settings);
            if (query.isEmpty()) {
                return null;
            }
            List<Integer> achados = atual.search(query, settings);
            int[] resultado = new int[achados.size()];
            for (int i = 0; i < resultado.length; i++) {
                resultado[i] = achados.get(i);
            }
            indiceDoCache = atual;
            palavraDoCache = palavra;
            resultadoDoCache = resultado;
            return resultado;
        } catch (Throwable t) {
            BetterSearch.LOGGER.error("[{}] erro na busca do JEI; voltando para a original",
                    BetterSearch.MOD_NAME, t);
            return null;
        }
    }

    private static void garantirIndice(IngredientFilter filtro, SearchSettings settings) throws Exception {
        Linguas.garantir(settings);
        if (campoLista == null) {
            campoLista = IngredientFilter.class.getDeclaredField("elementList");
            campoLista.setAccessible(true);
        }
        List<?> lista = (List<?>) campoLista.get(filtro);
        if (lista == null) {
            return;
        }
        String idioma = Minecraft.getMinecraft().gameSettings.language;
        int marca = Linguas.marca() + Estado.marca() * 100_000;
        int tamanho = lista.size();
        if (montando || (indice != null && idiomaDoIndice.equals(idioma)
                && marcaDoIndice == marca && tamanhoDoIndice == tamanho)) {
            return;
        }
        montando = true;

        // Copia rasa na thread do cliente; a leitura dos textos vai para o fundo, como o
        // proprio JEI faz no IngredientFilterBackgroundBuilder.
        final List<Object> copia = new ArrayList<>(lista);
        final String idiomaDaMontagem = idioma;

        Thread trabalho = new Thread(() -> {
            try {
                indice = montar(copia, Estado.settings());
                idiomaDoIndice = idiomaDaMontagem;
                marcaDoIndice = marca;
                tamanhoDoIndice = copia.size();
            } catch (Throwable t) {
                BetterSearch.LOGGER.error("[{}] falha ao montar o indice do JEI",
                        BetterSearch.MOD_NAME, t);
            } finally {
                montando = false;
            }
        }, "BetterSearch-Indice-JEI-1.12.2");
        trabalho.setDaemon(true);
        trabalho.start();
    }

    private static SearchIndex<Integer> montar(List<Object> elementos, SearchSettings settings) {
        long inicio = System.nanoTime();
        List<SearchIndex.Entry<Integer>> entradas = new ArrayList<>(elementos.size());
        for (int i = 0; i < elementos.size(); i++) {
            try {
                IIngredientListElement<?> elemento = (IIngredientListElement<?>) elementos.get(i);
                EntryBuilder<Integer> builder = new EntryBuilder<>(i);
                preencher(builder, elemento, settings);
                if (!builder.isEmpty()) {
                    entradas.add(builder.build());
                }
            } catch (Throwable t) {
                BetterSearch.LOGGER.debug("[{}] ingrediente ignorado no indice do JEI: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }
        SearchIndex<Integer> novo = new SearchIndex<>(entradas);
        BetterSearch.LOGGER.info("[{}] indice do JEI pronto (1.12.2): {} ingredientes em {} ms",
                BetterSearch.MOD_NAME, entradas.size(), (System.nanoTime() - inicio) / 1_000_000);
        return novo;
    }

    private static void preencher(EntryBuilder<Integer> builder, IIngredientListElement<?> elemento,
                                  SearchSettings settings) {
        // 1) Nome exibido, como o JEI mostra (ja cobre nomes customizados por mods).
        builder.add(elemento.getDisplayName(), SearchField.SOURCE_NATIVE);

        Object ingrediente = elemento.getIngredient();
        if (ingrediente instanceof ItemStack) {
            ItemStack stack = (ItemStack) ingrediente;

            // 1b) O mesmo nome nos outros idiomas - identico ao indice do criativo.
            if (settings.crossLanguage) {
                String chave = stack.getTranslationKey() + ".name";
                for (String codigo : Linguas.codigosAtivos(settings)) {
                    String traduzido = Linguas.get(codigo, chave);
                    if (traduzido != null) {
                        builder.add(traduzido, "en_us".equalsIgnoreCase(codigo)
                                ? SearchField.SOURCE_ENGLISH
                                : SearchField.SOURCE_FOREIGN);
                    }
                }
            }

            // 2) Id de registro: "minecraft diamond sword".
            if (settings.searchItemIds) {
                ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
                if (id != null) {
                    builder.modId(id.getNamespace());
                    builder.addNormalized(id.getNamespace() + ' ' + id.getPath().replace('_', ' '),
                            SearchField.SOURCE_ID);
                }
            }
        } else if (settings.searchItemIds) {
            // Fluido, encantamento... - o id que o proprio JEI guarda para ele.
            builder.add(elemento.getResourceId(), SearchField.SOURCE_ID);
        }

        // 3) Tooltip que o JEI ja preparou (livros encantados, pocoes, itens de mod).
        if (settings.searchTooltips) {
            int usadas = 0;
            for (String linha : elemento.getTooltipStrings()) {
                builder.add(linha, SearchField.SOURCE_TOOLTIP);
                if (++usadas >= MAX_LINHAS_TOOLTIP) {
                    break;
                }
            }
        }
    }
}
