package mezz.jei.suffixtree;

import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * ESBOCO DE COMPILACAO - nunca empacotado.
 *
 * <p>javap no jar real: classe publica, NAO final, construtor vazio publico,
 * {@code search(String)} e {@code addSearchTree(ISearchTree)} publicos. E por a classe nao
 * ser final - e o campo que a guarda nao ser final - que o gancho inteiro funciona sem Mixin.
 */
public class CombinedSearchTrees implements ISearchTree {

    public CombinedSearchTrees() {
    }

    @Override
    public IntSet search(String word) {
        throw new UnsupportedOperationException("esboco de compilacao");
    }

    public void addSearchTree(ISearchTree searchTree) {
        throw new UnsupportedOperationException("esboco de compilacao");
    }
}
