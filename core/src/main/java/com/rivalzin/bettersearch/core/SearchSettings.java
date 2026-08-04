package com.rivalzin.bettersearch.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuracao do mod. POJO puro (sem Gson, sem NeoForge, sem Minecraft) para que a mesma
 * classe sirva a qualquer loader; a serializacao fica na camada de plataforma.
 */
public final class SearchSettings {

    /** Idiomas indexados por padrao alem do idioma atual do jogo. */
    public static final List<String> DEFAULT_LANGUAGES = List.of(
            "en_us", "es_es", "es_mx", "pt_br", "pt_pt", "fr_fr", "de_de", "it_it",
            "nl_nl", "pl_pl", "ru_ru", "uk_ua", "tr_tr", "sv_se",
            "zh_cn", "zh_tw", "ja_jp", "ko_kr");

    /** Liga/desliga o mod inteiro (todas as buscas voltam a ser as originais). */
    public boolean enabled = true;

    // ---- Onde o mod atua (aba "Avancado") ---------------------------------------------

    /** Barra de busca do inventario criativo. */
    public boolean searchCreative = true;

    /** Barra de busca do livro de receitas. */
    public boolean searchRecipeBook = true;

    /** Nomes de jogadores nas sugestoes de comando e do chat, tolerando erro de digitacao. */
    public boolean searchPlayerNames = true;

    /**
     * Sugere IDs de item a partir do nome traduzido: {@code /give @p bau} propoe
     * {@code minecraft:chest}.
     */
    public boolean searchCommandItems = true;

    /**
     * Corrige a palavra errada de um comando: {@code /gamemode criativo} passa a oferecer
     * {@code creative}.
     *
     * <p>Nao tem nada para ajustar - o comparador e fixo, mais tolerante que o da busca de
     * itens, e mostra no maximo tres opcoes. So da para ligar e desligar.
     */
    public boolean fixCommandErrors = true;

    /** Quantas sugestoes o mod pode acrescentar por vez. */
    public int commandSuggestionLimit = 12;

    /** Tolerancia a erros de digitacao: 0 = desligada, 1 = baixa, 2 = normal, 3 = alta. */
    public int typoTolerance = 2;

    /**
     * Palavras menores que isto precisam estar escritas corretamente.
     *
     * <p>E o UNICO limite de tamanho do sistema: o nivel de tolerancia decide quantos erros
     * sao aceitos, este valor decide a partir de que tamanho eles passam a ser aceitos.
     */
    public int minTypoLength = 4;

    /** Casar iniciais: "ds" acha "Diamond Sword". */
    public boolean matchInitials = true;

    /** Ignorar espacos: "netheritesword" acha "Netherite Sword". */
    public boolean ignoreSpaces = true;

    /** Procura tambem o nome do item em outros idiomas ("pomme" acha a maca). */
    public boolean crossLanguage = true;

    /**
     * Idiomas extras indexados. Use {@code ["*"]} para indexar TODOS os idiomas disponiveis
     * (mais cobertura, bem mais memoria).
     */
    public List<String> languages = new ArrayList<>(DEFAULT_LANGUAGES);

    /**
     * Se {@code true}, nomes em idiomas estrangeiros so casam de forma estrita
     * (exato / prefixo / substring) - sem tolerancia a erro de digitacao.
     * Evita ruido vindo de 18 idiomas ao mesmo tempo.
     */
    public boolean foreignStrictOnly = true;

    /** Ordena os resultados por relevancia em vez da ordem das abas. */
    public boolean sortByRelevance = true;

    /** Indexa as linhas de tooltip de itens com componentes (livros encantados, pocoes...). */
    public boolean searchTooltips = true;

    /** Permite procurar por id ("minecraft:diamond_sword", "diamond_sword"). */
    public boolean searchItemIds = true;

    /** Permite filtrar por mod com "@": {@code @create sword}. */
    public boolean searchModIds = true;

    /**
     * A segunda passada (com tolerancia a erro) so roda se a passada estrita devolver
     * menos que este numero de itens. Mantem a digitacao instantanea em modpacks gigantes.
     */
    public int fuzzyThreshold = 60;

    /**
     * Ultima tentativa: deixa cada palavra da consulta casar com um idioma diferente do
     * mesmo item ("espada de netherite" + "sword" misturados). Roda so quando quase nada
     * foi encontrado, entao nao atrapalha as buscas normais.
     */
    public boolean crossFieldMatching = true;

    /** Limite de resultados abaixo do qual a passada cruzada acima e acionada. */
    public int crossFieldThreshold = 20;

    /** Limite de resultados (0 = sem limite). */
    public int maxResults = 0;

    public SearchSettings copy() {
        SearchSettings s = new SearchSettings();
        s.enabled = enabled;
        s.searchCreative = searchCreative;
        s.searchRecipeBook = searchRecipeBook;
        s.searchPlayerNames = searchPlayerNames;
        s.searchCommandItems = searchCommandItems;
        s.fixCommandErrors = fixCommandErrors;
        s.commandSuggestionLimit = commandSuggestionLimit;
        s.typoTolerance = typoTolerance;
        s.minTypoLength = minTypoLength;
        s.matchInitials = matchInitials;
        s.ignoreSpaces = ignoreSpaces;
        s.crossLanguage = crossLanguage;
        s.languages = new ArrayList<>(languages);
        s.foreignStrictOnly = foreignStrictOnly;
        s.sortByRelevance = sortByRelevance;
        s.searchTooltips = searchTooltips;
        s.searchItemIds = searchItemIds;
        s.searchModIds = searchModIds;
        s.fuzzyThreshold = fuzzyThreshold;
        s.crossFieldMatching = crossFieldMatching;
        s.crossFieldThreshold = crossFieldThreshold;
        s.maxResults = maxResults;
        return s;
    }

    /** Corrige valores invalidos vindos do arquivo de configuracao. */
    public void sanitize() {
        typoTolerance = clamp(typoTolerance, 0, 3);
        minTypoLength = clamp(minTypoLength, 3, 10);
        commandSuggestionLimit = clamp(commandSuggestionLimit, 1, 50);
        fuzzyThreshold = clamp(fuzzyThreshold, 0, 100_000);
        crossFieldThreshold = clamp(crossFieldThreshold, 0, 100_000);
        maxResults = Math.max(0, maxResults);
        if (languages == null) {
            // Ausente no arquivo -> primeira execucao, usa a lista padrao.
            languages = new ArrayList<>(DEFAULT_LANGUAGES);
        } else {
            // Lista vazia e uma escolha valida ("nenhum idioma extra"), nao um erro.
            List<String> cleaned = new ArrayList<>();
            for (String raw : languages) {
                if (raw == null) {
                    continue;
                }
                String code = raw.trim().toLowerCase(java.util.Locale.ROOT);
                if (!code.isEmpty() && !cleaned.contains(code)) {
                    cleaned.add(code);
                }
            }
            languages = cleaned;
        }
    }

    public boolean indexesAllLanguages() {
        return languages.contains("*");
    }

    /**
     * Este idioma deve ser pesquisado agora?
     *
     * <p>Conferir isto na hora de montar o indice - e nao so na hora de ler os arquivos -
     * e o que faz desligar um idioma ter efeito imediato. Sem esta checagem, um idioma
     * carregado na inicializacao continuaria sendo pesquisado depois de desmarcado.
     */
    public boolean indexesLanguage(String code) {
        return crossLanguage && (indexesAllLanguages() || languages.contains(code));
    }

    /**
     * Mudar esta opcao obriga a remontar o indice?
     *
     * <p>Isto importa muito mais do que parece. Remontar o indice leva de decimos de segundo
     * a alguns segundos, e enquanto ele nao fica pronto o mod devolve a busca original -
     * ou seja, logo depois de mexer numa opcao o mod parecia simplesmente nao funcionar.
     *
     * <p>So quatro opcoes mudam o que e <i>guardado</i> no indice. Todo o resto (tolerancia
     * a erro, iniciais, espacos, limites, ordenacao) e decidido na hora da busca e portanto
     * vale <b>imediatamente</b>, sem remontar nada.
     */
    public boolean affectsIndex(SearchSettings other) {
        return crossLanguage != other.crossLanguage
                || searchTooltips != other.searchTooltips
                || searchItemIds != other.searchItemIds
                || !java.util.Objects.equals(languages, other.languages);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Usado pela tela de configuracao para saber se "Desfazer" e "Padroes" fazem diferenca. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SearchSettings s)) {
            return false;
        }
        return enabled == s.enabled
                && searchCreative == s.searchCreative
                && searchRecipeBook == s.searchRecipeBook
                && searchPlayerNames == s.searchPlayerNames
                && searchCommandItems == s.searchCommandItems
                && fixCommandErrors == s.fixCommandErrors
                && commandSuggestionLimit == s.commandSuggestionLimit
                && typoTolerance == s.typoTolerance
                && minTypoLength == s.minTypoLength
                && matchInitials == s.matchInitials
                && ignoreSpaces == s.ignoreSpaces
                && crossLanguage == s.crossLanguage
                && foreignStrictOnly == s.foreignStrictOnly
                && sortByRelevance == s.sortByRelevance
                && searchTooltips == s.searchTooltips
                && searchItemIds == s.searchItemIds
                && searchModIds == s.searchModIds
                && fuzzyThreshold == s.fuzzyThreshold
                && crossFieldMatching == s.crossFieldMatching
                && crossFieldThreshold == s.crossFieldThreshold
                && maxResults == s.maxResults
                && java.util.Objects.equals(languages, s.languages);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(enabled, searchCreative, searchRecipeBook,
                searchPlayerNames, searchCommandItems, fixCommandErrors, commandSuggestionLimit,
                typoTolerance, minTypoLength, matchInitials, ignoreSpaces,
                crossLanguage, foreignStrictOnly, sortByRelevance, searchTooltips, searchItemIds,
                searchModIds, fuzzyThreshold, crossFieldMatching, crossFieldThreshold, maxResults, languages);
    }

    @Override
    public String toString() {
        return "SearchSettings{enabled=" + enabled
                + ", typoTolerance=" + typoTolerance
                + ", crossLanguage=" + crossLanguage
                + ", languages=" + Arrays.toString(languages.toArray())
                + ", foreignStrictOnly=" + foreignStrictOnly
                + ", sortByRelevance=" + sortByRelevance
                + ", searchTooltips=" + searchTooltips
                + '}';
    }
}
