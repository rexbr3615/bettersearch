package com.rivalzin.bettersearch.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.CommandFuzzy;
import com.rivalzin.bettersearch.core.QuickMatcher;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Acrescenta sugestoes as listinhas de autocompletar do chat e dos comandos.
 *
 * <p>Tres coisas acontecem aqui:
 * <ul>
 *   <li><b>Nomes de jogadores</b> com tolerancia a erro: {@code /msg Steev} passa a propor
 *       {@code Steve}. O Minecraft calcula essa lista no proprio cliente (ele conhece a lista
 *       de jogadores), entao funciona tambem em servidor.</li>
 *   <li><b>IDs de item pelo nome traduzido</b>: {@code /give @p bau} propoe
 *       {@code minecraft:chest}.</li>
 *   <li><b>Correcao da palavra errada</b>: {@code /gamemode criativo} passa a propor
 *       {@code creative}. Vale para o nome do comando, para literais no meio ({@code add},
 *       {@code set}), para gamerules e ate para o valor de um {@code type=} dentro de um
 *       seletor - porque quem lista os candidatos e o proprio Brigadier, nao uma tabela
 *       escrita a mao aqui.</li>
 * </ul>
 *
 * <p>Para nao sugerir item onde nao cabe, olhamos qual argumento esta sendo completado:
 * o Brigadier diz qual e o no pai da posicao do cursor, e conferimos o tipo dos argumentos
 * possiveis ali. Assim {@code /time set bau} nao vira uma lista de itens.
 *
 * <p>As sugestoes originais vem sempre primeiro; as nossas sao acrescentadas depois. Se
 * qualquer coisa der errado, devolvemos a lista original intacta.
 */
public final class CommandSearch {

    private static final int MIN_WORD_LENGTH = 2;

    /**
     * Ha uma correcao de comando sendo oferecida agora?
     *
     * <p>Serve para uma coisa so: o mixin pinta de dourado, em vez de vermelho, o pedaco do
     * comando que o jogo nao entendeu <b>quando existe conserto a um TAB de distancia</b>.
     * Sem conserto, o vermelho continua vermelho - ele esta certo.
     */
    private static volatile boolean correctionOffered;

    private CommandSearch() {
    }

    public static boolean isEnabled() {
        SearchSettings settings = BetterSearchClient.settings();
        return BetterSearchClient.isEnabled()
                && (settings.searchPlayerNames || settings.searchCommandItems
                    || settings.fixCommandErrors);
    }

    /** Vermelho do Minecraft: "isto nao existe e voce esta sem saida". */
    private static final Style STUCK = Style.EMPTY.withColor(ChatFormatting.RED);

    /** Dourado do mod: "isto ainda nao vale, mas o conserto esta a um TAB daqui". */
    private static final Style FIXABLE = Style.EMPTY.withColor(ChatFormatting.GOLD);

    /**
     * Cor do trecho que o jogo nao entendeu, usada pelo mixin de {@code formatText}.
     *
     * <p>As opcoes sao conferidas aqui, e nao so na hora de gravar a flag: desligar a
     * correcao no menu devolve o vermelho no quadro seguinte, sem depender de nada.
     */
    public static Style unparsedStyle() {
        return correctionOffered
                && BetterSearchClient.isEnabled()
                && BetterSearchClient.settings().fixCommandErrors
                ? FIXABLE : STUCK;
    }

    /** Caminho dos comandos: sabemos exatamente que argumento esta sendo completado. */
    public static Suggestions augmentCommand(ParseResults<ClientSuggestionProvider> parse,
                                             int cursor, Suggestions original) {
        try {
            SearchSettings settings = BetterSearchClient.settings();
            if (!isEnabled() || parse == null || original == null) {
                return original;
            }
            String input = parse.getReader().getString();
            int safeCursor = Math.max(0, Math.min(cursor, input.length()));

            SuggestionContext<ClientSuggestionProvider> context =
                    parse.getContext().findSuggestionContext(safeCursor);
            int start = Math.max(0, Math.min(context.startPos, safeCursor));
            String word = input.substring(start, safeCursor);
            if (word.length() < MIN_WORD_LENGTH) {
                return original;
            }

            boolean wantsItems = false;
            boolean wantsPlayers = false;
            for (CommandNode<ClientSuggestionProvider> child : context.parent.getChildren()) {
                if (child instanceof ArgumentCommandNode<?, ?> argument) {
                    ArgumentType<?> type = argument.getType();
                    if (isItemLike(type)) {
                        wantsItems = true;
                    } else if (isPlayerLike(type)) {
                        wantsPlayers = true;
                    }
                }
            }

            List<String> additions = new ArrayList<>();
            if (wantsItems && settings.searchCommandItems) {
                List<Identifier> ids = CommandItemIndex.search(word);
                if (ids != null) {
                    for (Identifier id : ids) {
                        additions.add(id.toString());
                    }
                }
            }
            if (wantsPlayers && settings.searchPlayerNames) {
                additions.addAll(matchNames(onlinePlayerNames(), word, settings));
            }
            return merge(original, additions, start, safeCursor, settings);
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] sugestoes de comando inalteradas: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return original;
        }
    }

    // ------------------------------------------------------------------ correcao de comandos

    /**
     * O caminho dos comandos, agora em duas etapas.
     *
     * <p>Primeiro o acrescimo normal (nomes de jogador, ids de item). Se depois disso a lista
     * ainda estiver vazia, o jogo nao soube o que sugerir - e ai entra a correcao.
     *
     * <p>Ela devolve um {@code CompletableFuture} porque precisa perguntar ao Brigadier quais
     * opcoes seriam validas naquele ponto, e essa pergunta as vezes vai ate o servidor (nomes
     * de time, objetivos de placar...). Esperar a resposta travaria a thread principal, entao
     * a espera e encadeada em vez de bloqueante.
     */
    public static CompletableFuture<Suggestions> augmentCommandAsync(
            ParseResults<ClientSuggestionProvider> parse, int cursor, Suggestions original) {
        try {
            Suggestions merged = augmentCommand(parse, cursor, original);
            SearchSettings settings = BetterSearchClient.settings();
            if (merged == null || !merged.isEmpty() || parse == null
                    || !BetterSearchClient.isEnabled() || !settings.fixCommandErrors) {
                correctionOffered = false;
                return CompletableFuture.completedFuture(merged == null ? original : merged);
            }
            return correct(parse, cursor, merged);
        } catch (Throwable t) {
            correctionOffered = false;
            BetterSearch.LOGGER.debug("[{}] correcao de comando ignorada: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return CompletableFuture.completedFuture(original);
        }
    }

    private static CompletableFuture<Suggestions> correct(ParseResults<ClientSuggestionProvider> parse,
                                                          int cursor, Suggestions original) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            correctionOffered = false;
            return CompletableFuture.completedFuture(original);
        }
        String input = parse.getReader().getString();
        int safeCursor = Math.max(0, Math.min(cursor, input.length()));

        int[] span = wrongWord(input, safeCursor, parse);
        if (span == null) {
            correctionOffered = false;
            return CompletableFuture.completedFuture(original);
        }
        final int start = span[0];
        final int end = span[1];
        final String word = input.substring(start, end);

        // A pergunta que interessa: "com esta palavra fora do caminho, o que caberia aqui?"
        // O Brigadier responde isso sozinho, entao vale para o nome do comando, para um
        // literal no meio (add, set), para um gamerule e ate para o valor de um type= dentro
        // de um seletor - sem lista nenhuma escrita a mao.
        CommandDispatcher<ClientSuggestionProvider> dispatcher = minecraft.player.connection.getCommands();
        StringReader reader = new StringReader(input.substring(0, start));
        if (reader.canRead() && reader.peek() == '/') {
            reader.skip();
        }
        ParseResults<ClientSuggestionProvider> stub =
                dispatcher.parse(reader, minecraft.player.connection.getSuggestionsProvider());

        return dispatcher.getCompletionSuggestions(stub, start)
                .thenApply(pool -> build(word, start, end, pool, original))
                .exceptionally(t -> {
                    correctionOffered = false;
                    return original;
                });
    }

    private static Suggestions build(String word, int start, int end,
                                     Suggestions pool, Suggestions original) {
        if (pool == null || pool.isEmpty()) {
            correctionOffered = false;
            return original;
        }
        List<Suggestion> options = pool.getList();
        List<String> texts = new ArrayList<>(options.size());
        for (Suggestion option : options) {
            texts.add(option.getText());
        }
        List<String> chosen = CommandFuzzy.best(word, texts);
        if (chosen.isEmpty()) {
            correctionOffered = false;
            return original;
        }
        StringRange range = StringRange.between(start, end);
        List<Suggestion> corrected = new ArrayList<>(chosen.size());
        for (String text : chosen) {
            corrected.add(new Suggestion(range, text));
        }
        correctionOffered = true;
        return new Suggestions(range, corrected);
    }

    /**
     * Qual palavra esta errada: {@code [inicio, fim]}, ou {@code null} se nao houver uma.
     *
     * <p>Quando o comando nao compila, o Brigadier guarda em que posicao ele desistiu - e e
     * essa a palavra a consertar, mesmo que o cursor ja tenha passado dela. Sem isto,
     * {@code /gamemode criativo @a} ficaria vermelho para sempre: o cursor esta no
     * {@code @a}, que esta certo, enquanto o erro ficou tres palavras atras.
     */
    private static int[] wrongWord(String input, int cursor, ParseResults<ClientSuggestionProvider> parse) {
        int at = cursor;
        Map<CommandNode<ClientSuggestionProvider>, CommandSyntaxException> errors = parse.getExceptions();
        if (errors != null && !errors.isEmpty()) {
            int deepest = -1;
            for (CommandSyntaxException error : errors.values()) {
                deepest = Math.max(deepest, error.getCursor());
            }
            if (deepest >= 0) {
                at = Math.min(deepest, input.length());
            }
        }
        int start = CommandFuzzy.wordStart(input, at);
        int end = Math.max(CommandFuzzy.wordEnd(input, at), Math.min(cursor, input.length()));
        if (end > input.length()) {
            end = input.length();
        }
        // Nunca invadir a palavra seguinte: o fim so pode passar do cursor se ainda estiver
        // dentro da mesma palavra.
        end = Math.min(end, CommandFuzzy.wordEnd(input, start));
        if (end - start < MIN_WORD_LENGTH) {
            return null;
        }
        return new int[]{start, end};
    }

    /** Caminho do chat comum (sem barra): a lista e sempre de nomes de jogadores/times. */
    public static Suggestions augmentChat(String input, int cursor, Suggestions original) {
        correctionOffered = false; // sem comando na linha, nao ha nada para consertar
        try {
            SearchSettings settings = BetterSearchClient.settings();
            if (!BetterSearchClient.isEnabled() || !settings.searchPlayerNames || original == null) {
                return original;
            }
            int safeCursor = Math.max(0, Math.min(cursor, input.length()));
            int start = lastWordIndex(input, safeCursor);
            String word = input.substring(start, safeCursor);
            if (word.length() < MIN_WORD_LENGTH) {
                return original;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.player == null) {
                return original;
            }
            Collection<String> pool = minecraft.player.connection.getSuggestionsProvider().getCustomTabSugggestions();
            return merge(original, matchNames(pool, word, settings), start, safeCursor, settings);
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] sugestoes de chat inalteradas: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return original;
        }
    }

    // ------------------------------------------------------------------ auxiliares

    private static boolean isItemLike(ArgumentType<?> type) {
        return type instanceof ItemArgument
                || type instanceof ItemPredicateArgument
                || type instanceof BlockStateArgument
                || type instanceof BlockPredicateArgument;
    }

    private static boolean isPlayerLike(ArgumentType<?> type) {
        return type instanceof EntityArgument
                || type instanceof GameProfileArgument
                || type instanceof ScoreHolderArgument;
    }

    private static Collection<String> onlinePlayerNames() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return List.of();
        }
        return minecraft.player.connection.getSuggestionsProvider().getOnlinePlayerNames();
    }

    /** Nomes que casam, do mais parecido para o menos. */
    private static List<String> matchNames(Collection<String> pool, String word, SearchSettings settings) {
        QuickMatcher.Session session = new QuickMatcher.Session(word, settings);
        List<String> matched = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        for (String candidate : pool) {
            int score = session.score(candidate);
            if (score != QuickMatcher.NO_MATCH) {
                int at = 0;
                while (at < scores.size() && scores.get(at) >= score) {
                    at++;
                }
                matched.add(at, candidate);
                scores.add(at, score);
            }
        }
        return matched;
    }

    private static Suggestions merge(Suggestions original, List<String> additions,
                                     int start, int cursor, SearchSettings settings) {
        if (additions.isEmpty()) {
            return original;
        }
        List<Suggestion> existing = original.getList();
        Set<String> seen = new HashSet<>(existing.size() + additions.size());
        for (Suggestion suggestion : existing) {
            seen.add(suggestion.getText());
        }

        StringRange range = existing.isEmpty() ? StringRange.between(start, cursor) : original.getRange();
        List<Suggestion> merged = new ArrayList<>(existing);
        int added = 0;
        for (String text : additions) {
            if (added >= settings.commandSuggestionLimit) {
                break;
            }
            if (seen.add(text)) {
                merged.add(new Suggestion(range, text));
                added++;
            }
        }
        return added == 0 ? original : new Suggestions(range, merged);
    }

    /** Inicio da ultima palavra antes do cursor (equivalente ao que o chat vanilla faz). */
    private static int lastWordIndex(String text, int cursor) {
        int index = cursor;
        while (index > 0 && !Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }
        return index;
    }
}
