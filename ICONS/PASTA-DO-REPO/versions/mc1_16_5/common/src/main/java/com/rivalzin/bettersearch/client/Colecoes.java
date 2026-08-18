package com.rivalzin.bettersearch.client;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * As fabricas {@code List.of}, {@code Set.of} e {@code Map.of} em versao Java 8.
 *
 * <p>Elas chegaram no Java 9, e a 1.16.5 roda em Java 8. Sem isto seriam 26 lugares neste
 * modulo escrevendo {@code Collections.unmodifiableList(Arrays.asList(...))} na mao - o que
 * alem de feio esconde a intencao. Aqui o nome diz o que e, uma vez so, e o resto do codigo
 * fica parecido com o das outras oito versoes.
 *
 * <p>Como as originais, tudo o que sai daqui e <b>imutavel</b> e mantem a ordem de insercao.
 */
final class Colecoes {

    private Colecoes() {
    }

    @SafeVarargs
    static <T> List<T> lista(T... itens) {
        return itens.length == 0
                ? Collections.<T>emptyList()
                : Collections.unmodifiableList(Arrays.asList(itens));
    }

    @SafeVarargs
    static <T> Set<T> conjunto(T... itens) {
        if (itens.length == 0) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<T>(Arrays.asList(itens)));
    }

    static <K, V> Map<K, V> mapaVazio() {
        return Collections.emptyMap();
    }

    static <K, V> Map.Entry<K, V> par(K chave, V valor) {
        return new AbstractMap.SimpleImmutableEntry<K, V>(chave, valor);
    }

    @SafeVarargs
    static <K, V> Map<K, V> mapa(Map.Entry<K, V>... pares) {
        Map<K, V> saida = new LinkedHashMap<K, V>();
        for (Map.Entry<K, V> par : pares) {
            saida.put(par.getKey(), par.getValue());
        }
        return Collections.unmodifiableMap(saida);
    }
}
