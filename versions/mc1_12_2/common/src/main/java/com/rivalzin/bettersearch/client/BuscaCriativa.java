package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado da busca do criativo na 1.12.2: indice atual e a busca em si.
 *
 * <p>Regra de ouro herdada das outras versoes: <b>nunca travar o jogo</b>. Enquanto o indice
 * nao existe, {@code search} devolve {@code null} e a {@link ArvoreEnvolvida} deixa a busca
 * original agir. A montagem roda numa thread de fundo; so a ENUMERACAO dos itens acontece na
 * thread do cliente (getSubItems de item de mod nao tem garantia nenhuma de aguentar outra
 * thread), e ela e barata - o caro e normalizar texto, e isso vai para fundo.
 *
 * <p>Configuracao ainda e a padrao ({@code new SearchSettings()}): o arquivo de configuracao
 * e a tela vem nas proximas etapas. Os padroes ligam tolerancia a erro, prefixo, iniciais e
 * ids - o suficiente para a etapa 2 ser util de verdade.
 */
public final class BuscaCriativa {

    private static volatile SearchIndex<ItemStack> indice;
    private static volatile String idiomaDoIndice = "";
    private static volatile int marcaDoIndice = -1;
    private static volatile boolean montando;

    // So a thread do cliente toca nestes tres (a busca acontece toda nela).
    private static SearchIndex<ItemStack> indiceDoCache;
    private static String consultaDoCache;
    private static List<ItemStack> resultadoDoCache;

    private BuscaCriativa() {
    }

    /** Nossa resposta, ou {@code null} para "deixa a busca original agir". */
    public static List<ItemStack> search(String consulta) {
        if (consulta == null || !Estado.settings().enabled || !Estado.settings().searchCreative) {
            return null;
        }
        garantirIndice();
        SearchIndex<ItemStack> atual = indice;
        if (atual == null) {
            return null;
        }
        if (atual == indiceDoCache && consulta.equals(consultaDoCache) && resultadoDoCache != null) {
            return resultadoDoCache;
        }
        try {
            SearchQuery query = SearchQuery.parse(consulta, Estado.settings());
            if (query.isEmpty()) {
                return null;
            }
            List<ItemStack> resultado = atual.search(query, Estado.settings());
            indiceDoCache = atual;
            consultaDoCache = consulta;
            resultadoDoCache = resultado;
            return resultado;
        } catch (Throwable t) {
            BetterSearch.LOGGER.error("[{}] erro na busca do criativo; voltando para a original",
                    BetterSearch.MOD_NAME, t);
            return null;
        }
    }

    /**
     * Monta (ou remonta) o indice se for preciso. Chamado da thread do cliente.
     *
     * <p>Remonta quando o idioma do jogo muda: os nomes indexados sao os exibidos, e depois de
     * trocar para outro idioma a busca acharia os nomes antigos - o mesmo cuidado que as
     * versoes novas tem com o languageStamp.
     */
    private static void garantirIndice() {
        Linguas.garantir(Estado.settings());
        String idioma = Minecraft.getMinecraft().gameSettings.language;
        int marca = Linguas.marca() + Estado.marca() * 100_000;
        if (montando || (indice != null && idiomaDoIndice.equals(idioma) && marcaDoIndice == marca)) {
            return;
        }
        montando = true;

        // Enumeracao na thread do cliente, de proposito (veja o javadoc da classe).
        final NonNullList<ItemStack> fonte = NonNullList.create();
        for (Item item : Item.REGISTRY) {
            try {
                item.getSubItems(CreativeTabs.SEARCH, fonte);
            } catch (Throwable t) {
                BetterSearch.LOGGER.debug("[{}] item de mod ignorado na enumeracao: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }
        final List<ItemStack> copia = new ArrayList<>(fonte);
        final String idiomaDaMontagem = idioma;

        Thread trabalho = new Thread(() -> {
            try {
                SearchIndex<ItemStack> novo = IndiceCriativo.montar(copia, Estado.settings());
                indice = novo;                    // volatile: publicacao segura
                idiomaDoIndice = idiomaDaMontagem;
                marcaDoIndice = marca;
            } catch (Throwable t) {
                BetterSearch.LOGGER.error("[{}] falha ao montar o indice do criativo",
                        BetterSearch.MOD_NAME, t);
            } finally {
                montando = false;
            }
        }, "BetterSearch-Indice-1.12.2");
        trabalho.setDaemon(true);
        trabalho.start();
    }
}
