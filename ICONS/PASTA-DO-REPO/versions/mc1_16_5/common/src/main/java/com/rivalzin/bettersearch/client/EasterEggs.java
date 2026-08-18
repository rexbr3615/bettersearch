package com.rivalzin.bettersearch.client;

import java.util.List;
import java.util.Map;

/**
 * Apelidos secretos: palavras que fazem certos itens aparecerem na busca do Criativo.
 *
 * <p>Sao so mais um texto pesquisavel colado no item, do mesmo jeito que o nome dele em
 * outro idioma. Nao ha atalho nem caso especial em lugar nenhum do motor de busca: por isso
 * eles herdam tudo de graca - tolerancia a erro, prefixo, ordenacao por relevancia - e nao
 * custam nada quando ninguem os digita.
 *
 * <p>Nao tem opcao propria no menu. Somem junto com o mod inteiro e mais nada.
 */
public final class EasterEggs {

    /**
     * Apelidos que valem sempre.
     *
     * <p>A tabela e por <b>item</b>, e nao por palavra, porque e assim que o indice e
     * montado: um item de cada vez. Uma mesma palavra em varios itens faz os varios
     * aparecerem juntos.
     *
     * <p>{@code Map.ofEntries} e nao {@code Map.of} porque este ultimo para em dez pares.
     */
    private static final Map<String, List<String>> ALWAYS = Colecoes.mapa(
            // Technoblade never dies.
            Colecoes.par("minecraft:pig_spawn_egg", Colecoes.lista("technoblade")),
            Colecoes.par("minecraft:potato", Colecoes.lista("technoblade")),
            Colecoes.par("minecraft:golden_helmet", Colecoes.lista("technoblade")),
            Colecoes.par("minecraft:red_bed", Colecoes.lista("technoblade")),

            Colecoes.par("minecraft:spider_spawn_egg", Colecoes.lista("venomextreme", "venoninho", "venom extreme")),
            Colecoes.par("minecraft:gold_ingot", Colecoes.lista("venomextreme", "venoninho", "venom extreme")),
            Colecoes.par("minecraft:arrow", Colecoes.lista("venomextreme", "venoninho", "venom extreme")),

            Colecoes.par("minecraft:cat_spawn_egg", Colecoes.lista("rival", "rivalzin")),
            Colecoes.par("minecraft:music_disc_wait", Colecoes.lista("rival", "rivalzin")),

            Colecoes.par("minecraft:fox_spawn_egg", Colecoes.lista("spacey", "spaceybubs", "xspaceybubs")),
            Colecoes.par("minecraft:brush", Colecoes.lista("spacey", "spaceybubs", "xspaceybubs")),
            Colecoes.par("minecraft:yellow_dye", Colecoes.lista("spacey", "spaceybubs", "xspaceybubs")));

    /**
     * Apelidos que so entram com o ingles ligado - sao nomes ingleses, e apareceriam do nada
     * para quem desligou o idioma de proposito.
     */
    private static final Map<String, List<String>> ENGLISH = Colecoes.mapa(
            // Como a Bancada de Trabalho se chamava antes da 1.13.
            Colecoes.par("minecraft:crafting_table", Colecoes.lista("workbench")));

    private EasterEggs() {
    }

    /** Apelidos deste item, ou lista vazia. */
    public static List<String> aliasesFor(String itemId, boolean englishSearched) {
        List<String> always = ALWAYS.get(itemId);
        List<String> english = englishSearched ? ENGLISH.get(itemId) : null;
        if (english == null) {
            return always == null ? java.util.Collections.emptyList() : always;
        }
        if (always == null) {
            return english;
        }
        return java.util.stream.Stream.concat(always.stream(), english.stream()).collect(java.util.stream.Collectors.toList());
    }
}
