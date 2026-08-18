package it.unimi.dsi.fastutil.ints;

/**
 * ESBOCO DE COMPILACAO - nunca empacotado.
 *
 * <p>No fastutil verdadeiro {@code add}, {@code addAll} e {@code size} moram na
 * superinterface IntCollection; declara-los aqui muda apenas ONDE a resolucao comeca, nao o
 * resultado - a JVM sobe pelas superinterfaces, exatamente como ja faz para o
 * {@code IntList.add:(I)Z} que o proprio JEI emite (prova de que essa resolucao funciona
 * no fastutil deste runtime).
 */
public interface IntSet extends IntCollection {

    boolean add(int k);

    boolean addAll(IntCollection c);

    int size();
}
