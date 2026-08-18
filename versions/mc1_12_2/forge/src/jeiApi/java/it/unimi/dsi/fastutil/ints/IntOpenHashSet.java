package it.unimi.dsi.fastutil.ints;

/**
 * ESBOCO DE COMPILACAO - nunca empacotado. O construtor (int) e exatamente o que o
 * CombinedSearchTrees do JEI invoca ("<init>":(I)V no bytecode dele).
 */
public class IntOpenHashSet implements IntSet {

    public IntOpenHashSet(int expected) {
    }

    @Override
    public boolean add(int k) {
        throw new UnsupportedOperationException("esboco de compilacao");
    }

    @Override
    public boolean addAll(IntCollection c) {
        throw new UnsupportedOperationException("esboco de compilacao");
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException("esboco de compilacao");
    }
}
