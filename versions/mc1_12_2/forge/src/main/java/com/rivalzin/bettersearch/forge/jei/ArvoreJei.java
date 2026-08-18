package com.rivalzin.bettersearch.forge.jei;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.suffixtree.CombinedSearchTrees;
import mezz.jei.suffixtree.ISearchTree;

/**
 * A arvore combinada do JEI, com a nossa busca somada.
 *
 * <p>O JEI quebra o texto digitado em palavras e pergunta esta arvore palavra por palavra,
 * cruzando (intersecao) os resultados depois. Devolver a UNIAO "deles + nossos" por palavra
 * preserva essa semantica: "diamnod sowrd" acha a espada porque cada palavra, corrigida,
 * acha o seu conjunto, e a intersecao fica com o JEI. Ordenacao por relevancia nao se aplica
 * aqui - o resultado e um conjunto de indices, e quem ordena (por ordem de registro) e o JEI,
 * como sempre fez nesta versao.
 *
 * <p>Nada e mutado: o conjunto devolvido pela arvore original pode ser interno dela, entao a
 * uniao nasce num conjunto NOSSO. Os unicos membros do fastutil usados sao os que o proprio
 * JEI ja exercita neste runtime (construtor (int), addAll, add via superinterface - provado
 * no bytecode dele pela secao 22 do verify).
 */
final class ArvoreJei extends CombinedSearchTrees {

    private final CombinedSearchTrees original;
    private final IngredientFilter filtro;

    ArvoreJei(CombinedSearchTrees original, IngredientFilter filtro) {
        this.original = original;
        this.filtro = filtro;
    }

    @Override
    public IntSet search(String palavra) {
        IntSet deles = original.search(palavra);
        int[] nossos = BuscaJei.buscar(palavra, filtro);
        if (nossos == null || nossos.length == 0) {
            return deles;
        }
        IntSet uniao = new IntOpenHashSet(nossos.length + (deles == null ? 0 : deles.size()));
        if (deles != null) {
            uniao.addAll(deles);
        }
        for (int indice : nossos) {
            uniao.add(indice);
        }
        return uniao;
    }

    /** Arvores novas (o JEI registra por modo de busca) continuam indo para a original. */
    @Override
    public void addSearchTree(ISearchTree searchTree) {
        original.addSearchTree(searchTree);
    }
}
