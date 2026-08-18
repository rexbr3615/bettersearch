package com.rivalzin.bettersearch.forge.jei;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.Estado;
import mezz.jei.Internal;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.suffixtree.CombinedSearchTrees;

import java.lang.reflect.Field;

/**
 * Poe a {@link ArvoreJei} dentro do filtro do JEI - o mesmo desenho do gancho vanilla.
 *
 * <p>Por que aqui NAO precisa de Mixin, ao contrario da 1.16.5: nesta linha o
 * {@code getIngredientListUncached} e privado, mas a busca de cada palavra passa por um campo
 * NAO-final ({@code combinedSearchTrees}) numa classe publica nao-final com construtor
 * publico - tudo lido com javap no jar 4.16.5.1027 do pack. Trocar o campo por uma subclasse
 * que faz a UNIAO preserva o JEI inteiro: prefixos (@mod, #tooltip), exclusao com "-",
 * modo de edicao, itens escondidos - continua tudo dele.
 *
 * <p>Chamado a cada tique (via reflexao, do GanchoDeBusca) e idempotente. Isso tambem cobre
 * o {@code modesChanged()} do JEI, que RECONSTROI o campo do zero quando o jogador mexe na
 * configuracao dele: a arvore nova aparece sem o embrulho e o tique seguinte reembrulha -
 * exatamente como o gancho vanilla sobrevive ao re-registro das arvores pelo proprio JEI.
 *
 * <p>Classe so carregada com o JEI presente (Loader.isModLoaded + Class.forName no chamador);
 * a secao 9 do verify.sh vigia que nenhuma classe de carga certa alcance esta.
 */
public final class IntegracaoJei {

    private static Field campoArvores;
    private static boolean anunciado;
    private static int marcaAplicada = -1;

    private IntegracaoJei() {
    }

    /** Chamado por reflexao pelo GanchoDeBusca - se mudar o nome, mude la tambem. */
    public static void instalar() throws Exception {
        IngredientFilter filtro = Internal.getIngredientFilter();
        if (filtro == null) {
            return; // JEI ainda subindo
        }
        if (campoArvores == null) {
            campoArvores = IngredientFilter.class.getDeclaredField("combinedSearchTrees");
            campoArvores.setAccessible(true);
        }
        Object atual = campoArvores.get(filtro);
        boolean trocou = false;
        if (atual != null && !(atual instanceof ArvoreJei)) {
            campoArvores.set(filtro, new ArvoreJei((CombinedSearchTrees) atual, filtro));
            trocou = true;
            if (!anunciado) {
                anunciado = true;
                BetterSearch.LOGGER.info("[{}] busca do JEI ligada na 1.12.2 (arvore embrulhada, sem mixin)",
                        BetterSearch.MOD_NAME);
            }
        }
        /*
         * Menu mexido -> derruba o cache de resultados do JEI (filterCached), senao ligar ou
         * desligar "Busca no JEI" so faria efeito quando o texto digitado mudasse.
         */
        int marca = Estado.marca();
        if (trocou || marca != marcaAplicada) {
            marcaAplicada = marca;
            filtro.invalidateCache();
        }
    }
}
