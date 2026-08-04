package com.rivalzin.bettersearch.tools;

import com.rivalzin.bettersearch.core.CommandFuzzy;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Teste do nucleo de busca SEM Minecraft. Compila e roda com um JDK puro:
 *
 * <pre>
 *   javac -d build/coretest $(find src/main/java/com/rivalzin/bettersearch/core -name '*.java') tools/BetterSearchCoreTest.java
 *   java -cp build/coretest com.rivalzin.bettersearch.tools.BetterSearchCoreTest
 * </pre>
 *
 * Util para validar mudancas no algoritmo antes de abrir o jogo, e para conferir o
 * comportamento depois de portar para outro loader.
 */
public final class BetterSearchCoreTest {

    private static int failures = 0;
    private static int checks = 0;

    public static void main(String[] args) {
        SearchSettings settings = new SearchSettings();
        SearchIndex<String> index = buildIndex(settings);

        section("Acentos (o jogo esta em pt_br)");
        expectFirst(index, settings, "bau", "chest");
        expectFirst(index, settings, "baú", "chest");
        expectFirst(index, settings, "BAU", "chest");
        expectFirst(index, settings, "acucar", "sugar");
        expectFirst(index, settings, "maca", "apple");
        expectFirst(index, settings, "perola", "ender_pearl");

        section("Prefixo de palavra (nao precisa escrever o nome inteiro)");
        expectFirst(index, settings, "nether sword", "netherite_sword");
        expectFirst(index, settings, "esp netherite", "netherite_sword");
        expectFirst(index, settings, "dia", "diamond");
        expectContains(index, settings, "sword", "netherite_sword", "diamond_sword");

        section("Ordem livre das palavras");
        expectFirst(index, settings, "sword netherite", "netherite_sword");
        expectFirst(index, settings, "trabalho bancada", "crafting_table");

        section("Sem espacos / juntando palavras");
        expectFirst(index, settings, "netheritesword", "netherite_sword");
        expectFirst(index, settings, "bancadadetrabalho", "crafting_table");

        section("Erros de digitacao");
        expectFirst(index, settings, "dimaond", "diamond");
        expectFirst(index, settings, "espada de dimante", "diamond_sword");
        expectFirst(index, settings, "netherrite", "netherite_sword");
        expectFirst(index, settings, "encrenagem", "create:cogwheel");
        expectFirst(index, settings, "cofre", "chest"); // "coffre" (fr) com 1 erro

        section("Iniciais");
        expectFirst(index, settings, "ct", "crafting_table");

        section("Outros idiomas (jogo em pt_br)");
        expectFirst(index, settings, "pomme", "apple");            // frances
        expectFirst(index, settings, "apple", "apple");            // ingles
        expectFirst(index, settings, "crafting table", "crafting_table");
        expectFirst(index, settings, "werkbank", "crafting_table"); // alemao
        expectFirst(index, settings, "manzana", "apple");          // espanhol
        expectFirst(index, settings, "苹果", "apple");              // chines
        expectFirst(index, settings, "golden apple", "golden_apple");

        section("Ids e filtro de mod");
        expectFirst(index, settings, "diamond_sword", "diamond_sword");
        expectFirst(index, settings, "minecraft:sugar", "sugar");
        expectOnlyMod(index, settings, "@create", "create");
        expectFirst(index, settings, "@create cog", "create:cogwheel");

        section("Frases misturando dois idiomas");
        expectFirst(index, settings, "swrod de diamante", "diamond_sword");
        expectFirst(index, settings, "pomme dourada", "golden_apple");

        section("Tooltip (livro encantado)");
        expectContains(index, settings, "afiacao", "enchanted_book");
        expectContains(index, settings, "sharpness", "enchanted_book");

        section("Ranking: o mais especifico vem primeiro");
        expectOrder(index, settings, "maca", "apple", "golden_apple");
        expectOrder(index, settings, "bau", "chest", "trapped_chest");

        section("Sem falso positivo");
        expectEmpty(index, settings, "zzzzqqq");
        expectNotContains(index, settings, "sugar", "chest");

        section("Tolerancia desligada");
        SearchSettings noTypos = settings.copy();
        noTypos.typoTolerance = 0;
        expectEmpty(index, noTypos, "dimaond");
        expectFirst(index, noTypos, "diamond", "diamond");

        section("Opcoes da tela de configuracao realmente mudam o algoritmo");
        SearchSettings noInitials = settings.copy();
        noInitials.matchInitials = false;
        expectNotContains(index, noInitials, "ct", "crafting_table");
        expectFirst(index, settings, "ct", "crafting_table");

        SearchSettings noSpaceMatching = settings.copy();
        noSpaceMatching.ignoreSpaces = false;
        expectNotContains(index, noSpaceMatching, "bancadadetrabalho", "crafting_table");

        SearchSettings longWordsOnly = settings.copy();
        longWordsOnly.minTypoLength = 8;
        expectNotContains(index, longWordsOnly, "dimaond", "diamond");      // 7 letras: exige acerto
        expectFirst(index, longWordsOnly, "netherrite", "netherite_sword"); // 10 letras: ainda tolera

        SearchSettings noMixing = settings.copy();
        noMixing.crossFieldMatching = false;
        expectNotContains(index, noMixing, "pomme dourada", "golden_apple");

        SearchSettings noIds = settings.copy();
        noIds.searchItemIds = false;
        expectNotContains(index, noIds, "minecraft:sugar", "sugar");

        SearchSettings noTooltips = settings.copy();
        noTooltips.searchTooltips = false;
        expectNotContains(index, noTooltips, "afiacao", "enchanted_book");

        SearchSettings limited = settings.copy();
        limited.maxResults = 1;
        expectFirst(index, limited, "sword", "diamond_sword");
        expectCount(index, limited, "sword", 1);

        section("Sem busca entre idiomas");
        SearchSettings noCross = settings.copy();
        noCross.crossLanguage = false;
        SearchIndex<String> nativeOnly = buildIndex(noCross);
        expectEmpty(nativeOnly, noCross, "pomme");
        expectFirst(nativeOnly, noCross, "maca", "apple");

        section("Regressao: ligar/desligar idioma tem efeito imediato");
        // Sintoma relatado: com o frances ligado, "pomme" traz maca E batata,
        // porque batata em frances e "pomme de terre".
        expectContains(index, settings, "pomme", "apple", "potato");

        // Com apenas pt_br e en_us marcados, o frances nao pode mais casar nada.
        SearchSettings onlyPtEn = settings.copy();
        onlyPtEn.languages = new ArrayList<>(List.of("pt_br", "en_us"));
        SearchIndex<String> ptEn = buildIndex(onlyPtEn);
        expectEmpty(ptEn, onlyPtEn, "pomme");
        expectEmpty(ptEn, onlyPtEn, "coffre");
        expectFirst(ptEn, onlyPtEn, "apple", "apple");
        expectFirst(ptEn, onlyPtEn, "maca", "apple");
        expectFirst(ptEn, onlyPtEn, "batata", "potato");

        // Ligar um idioma novo volta a encontrar.
        SearchSettings withFrench = onlyPtEn.copy();
        withFrench.languages.add("fr_fr");
        SearchIndex<String> ptEnFr = buildIndex(withFrench);
        expectFirst(ptEnFr, withFrench, "pomme", "apple");

        // O coringa "*" liga todos.
        SearchSettings wildcard = settings.copy();
        wildcard.languages = new ArrayList<>(List.of("*"));
        SearchIndex<String> all = buildIndex(wildcard);
        expectFirst(all, wildcard, "kartoffel", "potato");

        // Desligar a busca entre idiomas desliga tudo, mesmo com a lista cheia.
        SearchSettings crossOff = settings.copy();
        crossOff.crossLanguage = false;
        SearchIndex<String> noForeign = buildIndex(crossOff);
        expectEmpty(noForeign, crossOff, "pomme");
        expectFirst(noForeign, crossOff, "maca", "apple");

        section("AUDITORIA: cada nivel de tolerancia muda mesmo o resultado");
        // "espada" com 1 erro (espda) e com 2 erros (espdaa)
        SearchSettings tolOff = settings.copy();    tolOff.typoTolerance = 0;
        SearchSettings tolLow = settings.copy();    tolLow.typoTolerance = 1;
        SearchSettings tolNormal = settings.copy(); tolNormal.typoTolerance = 2;
        SearchSettings tolHigh = settings.copy();   tolHigh.typoTolerance = 3;

        // "espoda" = 1 erro em "espada"; "espodo" = 2 erros.
        // (Cuidado ao escolher exemplos: "espdaa" parece 2 erros mas e so 1 - uma troca de
        // letras vizinhas conta como uma operacao so no Damerau-Levenshtein.)
        expectEmpty(index, tolOff, "espoda");                        // Desligada: 0 erros
        expectContains(index, tolLow, "espoda", "diamond_sword");    // Baixa: 1 erro
        expectContains(index, tolNormal, "espoda", "diamond_sword"); // Normal: 1 erro
        expectEmpty(index, tolLow, "espodo");                        // 2 erros: Baixa nao
        expectEmpty(index, tolNormal, "espodo");                     // 2 erros: Normal nao (< 8 letras)
        expectContains(index, tolHigh, "espodo", "diamond_sword");   // Alta: 2 erros

        // Palavra longa: Normal ganha o segundo erro, Baixa nao.
        expectContains(index, tolNormal, "netherrrite", "netherite_sword"); // 11 letras, 2 erros
        expectEmpty(index, tolLow, "netherrrite");

        section("AUDITORIA: tamanho minimo para erros");
        SearchSettings minShort = settings.copy(); minShort.minTypoLength = 3;
        SearchSettings minLong = settings.copy();  minLong.minTypoLength = 8;
        // "pedar" = "Pedra" com as duas ultimas letras trocadas (1 erro), e nao e prefixo.
        expectContains(index, minShort, "pedar", "stone");  // 5 letras, 1 erro -> acha Pedra
        expectEmpty(index, minLong, "pedar");               // exige 8+ letras para tolerar erro
        expectContains(index, minLong, "netherrite", "netherite_sword"); // 10 letras: ainda tolera

        section("AUDITORIA: limite para corrigir erros (fuzzyThreshold)");
        SearchSettings noFuzzyPass = settings.copy(); noFuzzyPass.fuzzyThreshold = 0;
        expectEmpty(index, noFuzzyPass, "espoda");          // com 0, a passada de erro nunca roda
        expectContains(index, settings, "espoda", "diamond_sword");

        section("AUDITORIA: limite para misturar idiomas (crossFieldThreshold)");
        SearchSettings noCrossPass = settings.copy(); noCrossPass.crossFieldThreshold = 0;
        expectNotContains(index, noCrossPass, "pomme dourada", "golden_apple");
        expectFirst(index, settings, "pomme dourada", "golden_apple");

        section("AUDITORIA: iniciais agora funcionam com palavras de ligacao");
        expectFirst(index, settings, "bt", "crafting_table");   // Bancada de Trabalho -> "bdt"
        expectFirst(index, settings, "bdt", "crafting_table");
        expectFirst(index, settings, "ed", "diamond_sword");    // Espada de Diamante -> "edd"
        expectNotContains(index, settings, "tb", "crafting_table"); // ordem errada nao casa

        section("AUDITORIA: mudar opcao de busca NAO exige remontar o indice");
        SearchSettings a = new SearchSettings();
        SearchSettings b = a.copy();
        b.typoTolerance = 0;
        b.minTypoLength = 9;
        b.matchInitials = false;
        b.ignoreSpaces = false;
        b.sortByRelevance = false;
        b.maxResults = 10;
        b.fuzzyThreshold = 0;
        b.foreignStrictOnly = false;
        b.crossFieldMatching = false;
        expectFalse(b.affectsIndex(a), "opcoes de busca nao invalidam o indice");
        SearchSettings c = a.copy();
        c.crossLanguage = false;
        expectTrue(c.affectsIndex(a), "desligar idiomas invalida o indice");
        SearchSettings d = a.copy();
        d.languages = new ArrayList<>(List.of("en_us"));
        expectTrue(d.affectsIndex(a), "trocar a lista de idiomas invalida o indice");
        SearchSettings e = a.copy();
        e.searchTooltips = false;
        expectTrue(e.affectsIndex(a), "tooltips invalidam o indice");

        section("QuickMatcher (nomes de jogadores)");
        expectLoose(settings, "JourneyMap", "jorney", true);      // erro de digitacao
        expectLoose(settings, "JourneyMap", "journey map", true); // com espaco
        // Iniciais precisam de separador: "JourneyMap" e uma palavra so, entao "jm" nao casa.
        // (Se um dia quisermos isso, seria preciso separar camelCase na normalizacao.)
        expectLoose(settings, "JourneyMap", "jm", false);
        expectLoose(settings, "Journey Map", "jm", true);          // iniciais
        expectLoose(settings, "Create", "creat", true);           // prefixo
        expectLoose(settings, "Create", "sodium", false);
        expectLoose(settings, "Steve", "steev", true);            // nome de jogador errado
        expectLoose(settings, "Notch", "steev", false);
        expectLoose(settings, "Herobrine", "hero", true);
        expectLoose(settings, "Águia", "aguia", true);            // acento

        commandCorrection();
        easterEggs();

        benchmark(settings);

        System.out.println();
        if (failures == 0) {
            System.out.println("OK - " + checks + " verificacoes passaram.");
        } else {
            System.out.println("FALHOU - " + failures + " de " + checks + " verificacoes.");
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ apelidos secretos

    /**
     * Os apelidos secretos sao so mais um campo de texto no item - nada de caso especial no
     * motor. Este teste prova exatamente isso: monta um indice com os apelidos colados e
     * confere que a busca normal os encontra, com prefixo e erro de digitacao inclusos.
     *
     * <p>A tabela de verdade vive em {@code client/EasterEggs.java} (precisa dos ids do
     * Minecraft); aqui ela e repetida de proposito, para o teste falhar se as duas
     * divergirem sem querer.
     */
    private static void easterEggs() {
        section("Apelidos secretos");
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("minecraft:crafting_table", List.of("workbench"));
        aliases.put("minecraft:pig_spawn_egg", List.of("technoblade"));
        aliases.put("minecraft:potato", List.of("technoblade"));
        aliases.put("minecraft:golden_helmet", List.of("technoblade"));
        aliases.put("minecraft:red_bed", List.of("technoblade"));
        aliases.put("minecraft:spider_spawn_egg", List.of("venomextreme", "venoninho", "venom extreme"));
        aliases.put("minecraft:gold_ingot", List.of("venomextreme", "venoninho", "venom extreme"));
        aliases.put("minecraft:arrow", List.of("venomextreme", "venoninho", "venom extreme"));
        aliases.put("minecraft:cat_spawn_egg", List.of("rival", "rivalzin"));
        aliases.put("minecraft:music_disc_wait", List.of("rival", "rivalzin"));

        List<SearchIndex.Entry<String>> entries = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : aliases.entrySet()) {
            EntryBuilder<String> builder = new EntryBuilder<>(entry.getKey());
            builder.modId("minecraft");
            builder.add(entry.getKey().substring("minecraft:".length()).replace('_', ' '),
                    SearchField.SOURCE_NATIVE);
            for (String alias : entry.getValue()) {
                builder.add(alias, SearchField.SOURCE_NATIVE);
            }
            entries.add(builder.build());
        }
        // Alguns itens sem apelido, para provar que eles NAO aparecem.
        for (String plain : List.of("minecraft:stone", "minecraft:oak_log", "minecraft:bucket")) {
            EntryBuilder<String> builder = new EntryBuilder<>(plain);
            builder.modId("minecraft");
            builder.add(plain.substring("minecraft:".length()).replace('_', ' '), SearchField.SOURCE_NATIVE);
            entries.add(builder.build());
        }
        SearchIndex<String> eggs = new SearchIndex<>(entries);
        SearchSettings settings = new SearchSettings();

        expectExactly(eggs, settings, "workbench", "minecraft:crafting_table");
        expectExactly(eggs, settings, "technoblade", "minecraft:pig_spawn_egg", "minecraft:potato",
                "minecraft:golden_helmet", "minecraft:red_bed");
        expectExactly(eggs, settings, "venomextreme", "minecraft:spider_spawn_egg",
                "minecraft:gold_ingot", "minecraft:arrow");
        expectExactly(eggs, settings, "venoninho", "minecraft:spider_spawn_egg",
                "minecraft:gold_ingot", "minecraft:arrow");
        expectExactly(eggs, settings, "venom extreme", "minecraft:spider_spawn_egg",
                "minecraft:gold_ingot", "minecraft:arrow");
        expectExactly(eggs, settings, "rival", "minecraft:cat_spawn_egg", "minecraft:music_disc_wait");
        expectExactly(eggs, settings, "rivalzin", "minecraft:cat_spawn_egg", "minecraft:music_disc_wait");

        // Herdam tudo do motor: prefixo e erro de digitacao valem tambem aqui.
        expectContains(eggs, settings, "techno", "minecraft:potato");
        expectContains(eggs, settings, "tecnhoblade", "minecraft:red_bed");
        expectContains(eggs, settings, "rivalzim", "minecraft:cat_spawn_egg");

        // E nao contaminam nada: pedra continua sendo so pedra.
        expectNotContains(eggs, settings, "stone", "minecraft:potato");
    }

    /** O resultado tem de ser exatamente este conjunto, sem sobra nem falta. */
    private static void expectExactly(SearchIndex<String> index, SearchSettings s,
                                      String query, String... expected) {
        checks++;
        List<String> got = run(index, s, query);
        boolean ok = got.size() == expected.length;
        for (String e : expected) {
            ok &= got.contains(e);
        }
        report(ok, query, expected.length + " itens exatos", got);
    }

    // ------------------------------------------------------------------ correcao de comandos

    private static final List<String> ROOT_COMMANDS = List.of(
            "advancement", "attribute", "ban", "ban-ip", "banlist", "bossbar", "clear", "clone",
            "damage", "data", "datapack", "debug", "defaultgamemode", "deop", "difficulty",
            "effect", "enchant", "execute", "experience", "fill", "fillbiome", "forceload",
            "function", "gamemode", "gamerule", "give", "help", "item", "kick", "kill", "list",
            "locate", "loot", "me", "msg", "op", "pardon", "particle", "place", "playsound",
            "publish", "random", "recipe", "reload", "return", "ride", "save-all", "save-off",
            "save-on", "say", "schedule", "scoreboard", "seed", "setblock", "setidletimeout",
            "setworldspawn", "spawnpoint", "spectate", "spreadplayers", "stop", "stopsound",
            "summon", "tag", "team", "teammsg", "teleport", "tell", "tellraw", "tick", "time",
            "title", "tp", "trigger", "w", "weather", "whitelist", "worldborder", "xp");

    private static final List<String> GAME_MODES =
            List.of("survival", "creative", "adventure", "spectator");

    private static final List<String> GAME_RULES = List.of(
            "announceAdvancements", "commandBlockOutput", "disableRaids", "doDaylightCycle",
            "doEntityDrops", "doFireTick", "doImmediateRespawn", "doInsomnia", "doMobLoot",
            "doMobSpawning", "doTileDrops", "doWeatherCycle", "drowningDamage", "fallDamage",
            "fireDamage", "keepInventory", "logAdminCommands", "maxEntityCramming",
            "mobGriefing", "naturalRegeneration", "randomTickSpeed", "reducedDebugInfo",
            "sendCommandFeedback", "showDeathMessages", "spawnRadius", "universalAnger");

    private static final List<String> ENTITY_TYPES = List.of(
            "minecraft:zombie", "minecraft:zombie_horse", "minecraft:zombie_villager",
            "minecraft:zoglin", "minecraft:skeleton", "minecraft:creeper", "minecraft:cow",
            "minecraft:pig", "minecraft:sheep", "minecraft:villager", "minecraft:enderman",
            "minecraft:spider", "minecraft:wither_skeleton");

    private static final List<String> TIME_VALUES = List.of("day", "night", "noon", "midnight");

    private static final List<String> EFFECTS = List.of(
            "minecraft:speed", "minecraft:slowness", "minecraft:haste", "minecraft:strength",
            "minecraft:jump_boost", "minecraft:regeneration", "minecraft:invisibility",
            "minecraft:night_vision", "minecraft:water_breathing", "minecraft:fire_resistance");

    private static void commandCorrection() {
        section("Correcao de comandos - nome do comando");
        expectFix("gemamode", ROOT_COMMANDS, "gamemode");
        expectFix("gamemod", ROOT_COMMANDS, "gamemode");
        expectFix("gamerules", ROOT_COMMANDS, "gamerule");
        expectFix("sumon", ROOT_COMMANDS, "summon");
        expectFix("efect", ROOT_COMMANDS, "effect");
        expectFix("tellrow", ROOT_COMMANDS, "tellraw");
        expectFix("wheater", ROOT_COMMANDS, "weather");
        expectFix("scoreboad", ROOT_COMMANDS, "scoreboard");
        expectFix("tpp", ROOT_COMMANDS, "tp");

        section("Correcao de comandos - palavra em outro idioma");
        expectFix("criativo", GAME_MODES, "creative");
        expectFix("creativo", GAME_MODES, "creative");
        expectFix("craetive", GAME_MODES, "creative");
        expectFix("espectador", GAME_MODES, "spectator");
        expectFix("aventura", GAME_MODES, "adventure");
        expectFix("survivel", GAME_MODES, "survival");
        expectFix("zumbi", ENTITY_TYPES, "minecraft:zombie");
        expectFix("creper", ENTITY_TYPES, "minecraft:creeper");
        expectFix("regeneracao", EFFECTS, "minecraft:regeneration");
        expectFix("nightvision", EFFECTS, "minecraft:night_vision");

        section("Correcao de comandos - gamerules e iniciais");
        expectFix("keepInvetory", GAME_RULES, "keepInventory");
        expectFix("keepinventory", GAME_RULES, "keepInventory");
        expectFix("ki", GAME_RULES, "keepInventory");
        expectFix("ddc", GAME_RULES, "doDaylightCycle");
        expectFix("mobgrifing", GAME_RULES, "mobGriefing");
        expectFix("randomtickspeed", GAME_RULES, "randomTickSpeed");

        section("Correcao de comandos - sempre sobra uma saida");
        // Nenhuma destas e erro de digitacao; ainda assim tem de vir uma opcao, a mais
        // proxima. E o caso do /gamemode sobrevivencia.
        expectFix("sobrevivencia", GAME_MODES, "survival");
        expectFix("dia", TIME_VALUES, "day");
        expectOneFallback("velocidade", EFFECTS);
        expectOneFallback("aranha", ENTITY_TYPES);
        expectOneFallback("xyzqwk", ROOT_COMMANDS);

        section("Correcao de comandos - quando nao ha o que adivinhar");
        expectNoFix("64", GAME_MODES);              // numero nao e palavra
        expectNoFix("a", ROOT_COMMANDS);            // uma letra e ambigua demais
        expectNoFix("gamemode", List.of());         // sem candidatos, sem sugestao

        section("Correcao de comandos - sem teto de resultados");
        // "zombi" se parece com varios; todos devem aparecer, nao so tres.
        expectAtLeast("zombi", ENTITY_TYPES, 4);
        expectAtLeast("do", GAME_RULES, 9);

        section("Correcao de comandos - recorte da palavra");
        expectSpan("/gamemode criativo @a", 18, 10, 18);
        expectSpan("/gamerule keepInvetory true", 21, 10, 22);
        expectSpan("/execute if entity @e[type=zumbi]", 32, 27, 32);
        expectSpan("/gemamode", 9, 1, 9);
    }

    private static void expectFix(String word, List<String> pool, String expected) {
        checks++;
        List<String> got = CommandFuzzy.best(word, pool);
        report(!got.isEmpty() && got.get(0).equals(expected), word, expected + " em 1o lugar", got);
    }

    private static void expectNoFix(String word, List<String> pool) {
        checks++;
        List<String> got = CommandFuzzy.best(word, pool);
        report(got.isEmpty(), word, "nenhuma sugestao", got);
    }

    /** Nada era parecido, entao tem de vir exatamente uma opcao - a menos diferente. */
    private static void expectOneFallback(String word, List<String> pool) {
        checks++;
        List<String> got = CommandFuzzy.best(word, pool);
        report(got.size() == 1, word, "exatamente 1 (a mais proxima)", got);
    }

    private static void expectAtLeast(String word, List<String> pool, int minimum) {
        checks++;
        List<String> got = CommandFuzzy.best(word, pool);
        report(got.size() >= minimum, word, minimum + " opcoes ou mais", got);
    }

    private static void expectSpan(String input, int at, int start, int end) {
        checks++;
        int gotStart = CommandFuzzy.wordStart(input, at);
        int gotEnd = CommandFuzzy.wordEnd(input, gotStart);
        if (gotStart != start || gotEnd != end) {
            fail("recorte de \"" + input + "\" em " + at + ": esperado [" + start + "," + end
                    + "], veio [" + gotStart + "," + gotEnd + "]");
        }
    }

    // ------------------------------------------------------------------ dados de teste

    /** O jogo esta em pt_br: {@code nativeName} e o nome exibido, {@code translations} sao os demais. */
    private record Item(String id, String modId, String nativeName,
                        Map<String, String> translations, List<String> tooltip) {
    }

    private static Map<String, String> t(String... codeThenName) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < codeThenName.length; i += 2) {
            map.put(codeThenName[i], codeThenName[i + 1]);
        }
        return map;
    }

    private static final List<Item> ITEMS = List.of(
            new Item("apple", "minecraft", "Maçã",
                    t("en_us", "Apple", "fr_fr", "Pomme", "de_de", "Apfel", "es_es", "Manzana",
                            "zh_cn", "苹果", "ja_jp", "りんご", "it_it", "Mela"), List.of()),
            new Item("golden_apple", "minecraft", "Maçã Dourada",
                    t("en_us", "Golden Apple", "fr_fr", "Pomme dorée", "de_de", "Goldener Apfel",
                            "es_es", "Manzana dorada", "zh_cn", "金苹果"), List.of()),
            // Batata em frances e "pomme de terre": e por isso que, com o frances ligado,
            // procurar "pomme" traz maca E batata.
            new Item("potato", "minecraft", "Batata",
                    t("en_us", "Potato", "fr_fr", "Pomme de terre", "de_de", "Kartoffel"), List.of()),
            new Item("chest", "minecraft", "Baú",
                    t("en_us", "Chest", "fr_fr", "Coffre", "de_de", "Truhe", "es_es", "Cofre",
                            "zh_cn", "箱子"), List.of()),
            new Item("trapped_chest", "minecraft", "Baú Armadilha",
                    t("en_us", "Trapped Chest", "fr_fr", "Coffre piégé", "de_de", "Redstone-Truhe"), List.of()),
            new Item("crafting_table", "minecraft", "Bancada de Trabalho",
                    t("en_us", "Crafting Table", "fr_fr", "Établi", "de_de", "Werkbank",
                            "es_es", "Mesa de trabajo", "zh_cn", "工作台", "ja_jp", "作業台"), List.of()),
            new Item("netherite_sword", "minecraft", "Espada de Netherite",
                    t("en_us", "Netherite Sword", "fr_fr", "Épée en Netherite",
                            "de_de", "Netheritschwert"), List.of()),
            new Item("diamond_sword", "minecraft", "Espada de Diamante",
                    t("en_us", "Diamond Sword", "fr_fr", "Épée en diamant",
                            "de_de", "Diamantschwert", "zh_cn", "钻石剑"), List.of()),
            new Item("diamond", "minecraft", "Diamante",
                    t("en_us", "Diamond", "fr_fr", "Diamant", "zh_cn", "钻石"), List.of()),
            new Item("sugar", "minecraft", "Açúcar",
                    t("en_us", "Sugar", "fr_fr", "Sucre", "de_de", "Zucker", "es_es", "Azúcar"), List.of()),
            new Item("ender_pearl", "minecraft", "Pérola do Ender",
                    t("en_us", "Ender Pearl", "fr_fr", "Perle de l'Ender", "de_de", "Enderperle"), List.of()),
            new Item("stone", "minecraft", "Pedra",
                    t("en_us", "Stone", "fr_fr", "Pierre", "de_de", "Stein", "es_es", "Piedra"), List.of()),
            new Item("oak_planks", "minecraft", "Tábuas de Carvalho",
                    t("en_us", "Oak Planks", "fr_fr", "Planches de chêne",
                            "de_de", "Eichenholzbretter"), List.of()),
            new Item("enchanted_book", "minecraft", "Livro Encantado",
                    t("en_us", "Enchanted Book", "fr_fr", "Livre enchanté",
                            "de_de", "Verzaubertes Buch"),
                    List.of("Afiação IV", "Sharpness IV")),
            new Item("create:cogwheel", "create", "Engrenagem",
                    t("en_us", "Cogwheel", "fr_fr", "Roue dentée", "de_de", "Zahnrad"), List.of()),
            new Item("create:shaft", "create", "Eixo",
                    t("en_us", "Shaft", "fr_fr", "Arbre", "de_de", "Welle"), List.of()));

    /**
     * Monta o indice do mesmo jeito que o CreativeIndexBuilder: um idioma so entra se
     * {@link SearchSettings#indexesLanguage} disser que ele esta ligado AGORA.
     */
    private static SearchIndex<String> buildIndex(SearchSettings settings) {
        List<SearchIndex.Entry<String>> entries = new ArrayList<>();
        for (Item item : ITEMS) {
            EntryBuilder<String> b = new EntryBuilder<>(item.id());
            b.modId(item.modId());
            b.add(item.nativeName(), SearchField.SOURCE_NATIVE);
            for (Map.Entry<String, String> translation : item.translations().entrySet()) {
                if (!settings.indexesLanguage(translation.getKey())) {
                    continue;
                }
                b.add(translation.getValue(), translation.getKey().equals("en_us")
                        ? SearchField.SOURCE_ENGLISH
                        : SearchField.SOURCE_FOREIGN);
            }
            b.add(item.id().contains(":") ? item.id() : "minecraft:" + item.id(), SearchField.SOURCE_ID);
            for (String line : item.tooltip()) {
                b.add(line, SearchField.SOURCE_TOOLTIP);
            }
            entries.add(b.build());
        }
        return new SearchIndex<>(entries);
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> run(SearchIndex<String> index, SearchSettings settings, String query) {
        return index.search(SearchQuery.parse(query, settings), settings);
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }

    private static void expectFirst(SearchIndex<String> index, SearchSettings s, String query, String expected) {
        checks++;
        List<String> got = run(index, s, query);
        boolean ok = !got.isEmpty() && got.get(0).equals(expected);
        report(ok, query, expected + " em 1o lugar", got);
    }

    private static void expectContains(SearchIndex<String> index, SearchSettings s, String query, String... expected) {
        checks++;
        List<String> got = run(index, s, query);
        boolean ok = true;
        for (String e : expected) {
            ok &= got.contains(e);
        }
        report(ok, query, "contem " + String.join(", ", expected), got);
    }

    private static void expectNotContains(SearchIndex<String> index, SearchSettings s, String query, String unwanted) {
        checks++;
        List<String> got = run(index, s, query);
        report(!got.contains(unwanted), query, "nao contem " + unwanted, got);
    }

    private static void expectOrder(SearchIndex<String> index, SearchSettings s, String query, String before, String after) {
        checks++;
        List<String> got = run(index, s, query);
        int i = got.indexOf(before);
        int j = got.indexOf(after);
        report(i >= 0 && j >= 0 && i < j, query, before + " antes de " + after, got);
    }

    private static void expectTrue(boolean value, String what) {
        checks++;
        report(value, what, "verdadeiro", List.of());
    }

    private static void expectFalse(boolean value, String what) {
        checks++;
        report(!value, what, "falso", List.of());
    }

    private static void expectLoose(SearchSettings s, String text, String query, boolean expected) {
        checks++;
        boolean got = com.rivalzin.bettersearch.core.QuickMatcher.matches(text, query, s);
        report(got == expected, query,
                (expected ? "casa com " : "nao casa com ") + '"' + text + '"',
                got ? List.of(text) : List.of());
    }

    private static void expectCount(SearchIndex<String> index, SearchSettings s, String query, int expected) {
        checks++;
        List<String> got = run(index, s, query);
        report(got.size() == expected, query, expected + " resultado(s)", got);
    }

    private static void expectEmpty(SearchIndex<String> index, SearchSettings s, String query) {
        checks++;
        List<String> got = run(index, s, query);
        report(got.isEmpty(), query, "nenhum resultado", got);
    }

    private static void expectOnlyMod(SearchIndex<String> index, SearchSettings s, String query, String modId) {
        checks++;
        List<String> got = run(index, s, query);
        boolean ok = !got.isEmpty();
        for (String id : got) {
            ok &= id.startsWith(modId + ":");
        }
        report(ok, query, "so itens de " + modId, got);
    }

    /** Falha simples, sem lista de resultados (usada pela correcao de comandos). */
    private static void fail(String message) {
        failures++;
        System.out.println("FALHA  " + message);
    }

    private static void report(boolean ok, String query, String expectation, List<String> got) {
        if (!ok) {
            failures++;
        }
        System.out.printf("%s  %-24s -> %-34s %s%n",
                ok ? "  ok" : "FALHA", '"' + query + '"', expectation, preview(got));
    }

    private static String preview(List<String> got) {
        int n = Math.min(4, got.size());
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(got.get(i));
        }
        if (got.size() > n) {
            sb.append(", +").append(got.size() - n);
        }
        return sb.append(']').toString();
    }

    // ------------------------------------------------------------------ desempenho

    private static void benchmark(SearchSettings settings) {
        section("Desempenho (indice sintetico de 20.000 itens x 6 idiomas)");
        List<SearchIndex.Entry<String>> entries = new ArrayList<>(20_000);
        String[] parts = {"diamond", "netherite", "copper", "amethyst", "deepslate", "cherry",
                "sculk", "prismarine", "blackstone", "warped", "crimson", "bamboo"};
        String[] kinds = {"sword", "block", "stairs", "slab", "door", "trapdoor", "button",
                "pressure plate", "fence", "wall", "helmet", "chestplate"};
        for (int i = 0; i < 20_000; i++) {
            String en = parts[i % parts.length] + " " + kinds[(i / parts.length) % kinds.length] + " " + i;
            EntryBuilder<String> b = new EntryBuilder<>("item" + i);
            b.modId("testmod");
            b.add(en, SearchField.SOURCE_NATIVE);
            b.add("bloco de teste " + i, SearchField.SOURCE_ENGLISH);
            b.add("bloc de test " + i, SearchField.SOURCE_FOREIGN);
            b.add("testblock " + i, SearchField.SOURCE_FOREIGN);
            b.add("测试方块 " + i, SearchField.SOURCE_FOREIGN);
            b.add("testmod:item_" + i, SearchField.SOURCE_ID);
            entries.add(b.build());
        }
        SearchIndex<String> big = new SearchIndex<>(entries);

        String[] queries = {"d", "di", "dia", "diam", "diamo", "diamond", "diamond s",
                "diamond sword", "netherrite", "xyzabc", "deepslat stiars"};
        for (String q : queries) {
            SearchQuery parsed = SearchQuery.parse(q, settings);
            for (int i = 0; i < 3; i++) {
                big.search(parsed, settings); // aquecimento
            }
            long start = System.nanoTime();
            int size = 0;
            for (int i = 0; i < 10; i++) {
                size = big.search(parsed, settings).size();
            }
            double ms = (System.nanoTime() - start) / 10.0 / 1_000_000.0;
            System.out.printf("  %-18s %6.2f ms  (%d resultados)%n", '"' + q + '"', ms, size);
        }
    }
}
