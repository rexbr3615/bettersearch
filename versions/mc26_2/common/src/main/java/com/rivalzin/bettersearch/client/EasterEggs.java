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
    private static final Map<String, List<String>> ALWAYS = Map.ofEntries(
            // Technoblade never dies.
            Map.entry("minecraft:pig_spawn_egg", List.of("technoblade")),
            Map.entry("minecraft:potato", List.of("technoblade")),
            Map.entry("minecraft:golden_helmet", List.of("technoblade")),
            Map.entry("minecraft:red_bed", List.of("technoblade")),

            Map.entry("minecraft:spider_spawn_egg", List.of("venomextreme", "venoninho", "venom extreme")),
            Map.entry("minecraft:gold_ingot", List.of("venomextreme", "venoninho", "venom extreme")),
            Map.entry("minecraft:arrow", List.of("venomextreme", "venoninho", "venom extreme")),

            Map.entry("minecraft:cat_spawn_egg", List.of("rival", "rivalzin")),
            Map.entry("minecraft:music_disc_wait", List.of("rival", "rivalzin")),

            Map.entry("minecraft:fox_spawn_egg", List.of("spacey", "spaceybubs", "xspaceybubs")),
            Map.entry("minecraft:brush", List.of("spacey", "spaceybubs", "xspaceybubs")),
            Map.entry("minecraft:yellow_dye", List.of("spacey", "spaceybubs", "xspaceybubs")));

    /**
     * Apelidos que so entram com o ingles ligado - sao nomes ingleses, e apareceriam do nada
     * para quem desligou o idioma de proposito.
     */
    private static final Map<String, List<String>> ENGLISH = Map.of(
            // Como a Bancada de Trabalho se chamava antes da 1.13.
            "minecraft:crafting_table", List.of("workbench"));

    private EasterEggs() {
    }

    /** Apelidos deste item, ou lista vazia. */
    public static List<String> aliasesFor(String itemId, boolean englishSearched) {
        List<String> always = ALWAYS.get(itemId);
        List<String> english = englishSearched ? ENGLISH.get(itemId) : null;
        if (english == null) {
            return always == null ? List.of() : always;
        }
        if (always == null) {
            return english;
        }
        return java.util.stream.Stream.concat(always.stream(), english.stream()).toList();
    }
}
