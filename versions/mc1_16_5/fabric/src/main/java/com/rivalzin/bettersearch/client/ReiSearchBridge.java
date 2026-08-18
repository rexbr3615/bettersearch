package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import me.shedaniel.rei.api.EntryStack;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A busca do Better Search dentro da lista do REI da linha <b>5.x</b> (a da 1.16.5 no Fabric).
 *
 * <p>Esta classe existe separada da {@code ReiSearch} das versoes novas porque o REI reescreveu
 * a API inteira depois desta linha. La o mod recebe um {@code SearchFilter} - um Predicate que
 * ele mesmo entrega - e devolve a ordem por {@code copyAndOrder}. Aqui nada disso existe: a
 * filtragem e um metodo estatico ({@code SearchArgument.canSearchTermsBeAppliedTo}) e o
 * resultado aterrissa num campo privado, {@code allStacks}, dentro do {@code updateSearch}.
 *
 * <p>Entao o formato do gancho aqui e o mesmo do JEI e do EMI, e nao o do REI moderno:
 * interceptamos a lista pronta e devolvemos a nossa, <b>somada</b> a dele. O que o REI achou
 * sozinho nunca se perde - ele procura em campos que o mod nao indexa (tag, por exemplo), entao
 * a lista final e sempre um superconjunto da dele.
 *
 * <p>Tudo conferido com javap no jar de verdade (RoughlyEnoughItems-runtime-5.12.385) e contra
 * o fonte publicado no ramo 5.x do repositorio do REI. Nenhum nome aqui foi deduzido.
 */
public final class ReiSearchBridge {

    /**
     * Sintaxe que e do REI e nao minha: {@code #} tooltip, {@code $} tag. Vendo qualquer uma
     * delas eu devolvo a lista dele intacta.
     *
     * <p>O {@code @} fica de fora: filtro de mod os dois temos, e o nosso perdoa erro no nome.
     * Os tres prefixos foram lidos das classes TooltipArgument, TagArgument e ModArgument do
     * proprio jar.
     */
    private static final String REI_SYNTAX = "#$";

    private static volatile SearchIndex<EntryStack> index;
    private static volatile int indexedSize = -1;
    private static volatile long indexedStamp = Long.MIN_VALUE;

    private ReiSearchBridge() {
    }

    /**
     * @param query    o texto digitado
     * @param resultado o que o REI achou sozinho, que nunca e jogado fora
     * @param fonte    a lista completa de onde o REI partiu
     * @return a lista que substitui a do REI, ou {@code null} para deixar a dele em paz
     */
    public static List<EntryStack> search(String query, List<EntryStack> resultado,
                                          List<EntryStack> fonte) {
        try {
            SearchSettings settings = BetterSearchClient.settings();
            if (!BetterSearchClient.isEnabled() || !settings.searchRei) {
                return null;
            }
            if (query == null || query.trim().isEmpty() || fonte == null || fonte.isEmpty()) {
                return null;
            }
            if (usaSintaxeDoRei(query)) {
                return null;
            }

            SearchIndex<EntryStack> pronto = montarIndice(fonte, settings);
            if (pronto == null) {
                return null;
            }
            SearchQuery parsed = SearchQuery.parse(query, settings);
            if (parsed.isEmpty()) {
                return null;
            }

            List<EntryStack> nossos = pronto.search(parsed, settings);
            if (resultado == null || resultado.isEmpty()) {
                return nossos.isEmpty() ? null : Collections.unmodifiableList(new ArrayList<EntryStack>(nossos));
            }

            List<EntryStack> juntos = new ArrayList<EntryStack>(nossos.size() + resultado.size());
            if (settings.sortByRelevance) {
                Set<EntryStack> vistos = new HashSet<EntryStack>(nossos);
                juntos.addAll(nossos);
                for (EntryStack pilha : resultado) {
                    if (vistos.add(pilha)) {
                        juntos.add(pilha);
                    }
                }
            } else {
                Set<EntryStack> doRei = new HashSet<EntryStack>(resultado);
                juntos.addAll(resultado);
                for (EntryStack pilha : nossos) {
                    if (!doRei.contains(pilha)) {
                        juntos.add(pilha);
                    }
                }
            }
            return Collections.unmodifiableList(juntos);
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] busca do REI inalterada: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return null;
        }
    }

    /**
     * Monta o indice na hora.
     *
     * <p>O {@code updateSearch} do REI 5.x roda na thread do jogo, entao esta montagem custa um
     * engasgo na primeira busca de cada sessao - do mesmo tamanho do que o proprio REI ja gasta
     * montando a lista dele. Nas seguintes o indice e reaproveitado enquanto o tamanho da lista
     * e o carimbo de idioma nao mudarem.
     */
    private static SearchIndex<EntryStack> montarIndice(List<EntryStack> fonte,
                                                        SearchSettings settings) {
        long carimbo = BetterSearchClient.languageStamp();
        SearchIndex<EntryStack> atual = index;
        if (atual != null && indexedSize == fonte.size() && indexedStamp == carimbo) {
            return atual;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }

        long inicio = System.nanoTime();
        LanguageTable idiomas = BetterSearchClient.languages();
        List<String> codigos = CreativeIndexBuilder.activeCodes(idiomas, settings);
        boolean inglesBuscado = CreativeIndexBuilder.englishSearched(codigos);

        List<SearchIndex.Entry<EntryStack>> entradas =
                new ArrayList<SearchIndex.Entry<EntryStack>>(fonte.size());
        for (EntryStack pilha : fonte) {
            try {
                EntryBuilder<EntryStack> builder = new EntryBuilder<EntryStack>(pilha);
                ItemStack item = itemDe(pilha);
                if (item != null && !item.isEmpty()) {
                    CreativeIndexBuilder.fill(builder, item, idiomas, codigos, settings,
                            minecraft.player, inglesBuscado);
                } else {
                    preencherOutro(builder, pilha, settings);
                }
                if (!builder.isEmpty()) {
                    entradas.add(builder.build());
                }
            } catch (Throwable t) {
                BetterSearch.LOGGER.debug("[{}] entrada do REI ignorada no indice: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }

        SearchIndex<EntryStack> montado = new SearchIndex<EntryStack>(entradas);
        BetterSearch.LOGGER.info("[{}] indice do REI pronto: {} de {} entradas em {} ms",
                BetterSearch.MOD_NAME, entradas.size(), fonte.size(),
                (System.nanoTime() - inicio) / 1000000);
        index = montado;
        indexedSize = fonte.size();
        indexedStamp = carimbo;
        return montado;
    }

    /** Fluido, ou o tipo proprio de algum mod: sobra o que o REI sabe dizer de qualquer um. */
    private static void preencherOutro(EntryBuilder<EntryStack> builder, EntryStack pilha,
                                       SearchSettings settings) {
        // asFormattedText vem da interface TextRepresentable, que o EntryStack estende.
        builder.add(pilha.asFormattedText().getString(), SearchField.SOURCE_NATIVE);

        Optional<ResourceLocation> id = pilha.getIdentifier();
        if (id != null && id.isPresent()) {
            ResourceLocation local = id.get();
            builder.modId(local.getNamespace());
            if (settings.searchItemIds) {
                builder.addNormalized(local.getNamespace() + ' '
                        + local.getPath().replace('_', ' '), SearchField.SOURCE_ID);
            }
        }
    }

    private static ItemStack itemDe(EntryStack pilha) {
        try {
            // O getItemStack e um metodo default do EntryStack; para tipo FLUID ele devolve
            // pilha vazia, e por isso o chamador testa isEmpty em vez de confiar no tipo.
            return pilha.getType() == EntryStack.Type.ITEM ? pilha.getItemStack() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean usaSintaxeDoRei(String query) {
        for (String pedaco : query.split("\\s+")) {
            if (!pedaco.isEmpty() && REI_SYNTAX.indexOf(pedaco.charAt(0)) >= 0) {
                return true;
            }
        }
        return false;
    }
}
