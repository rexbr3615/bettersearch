package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Estado do mod no cliente: configuracao, tabela de idiomas e indice atual.
 *
 * <p>Regra de ouro: <b>nunca travar o jogo</b>. Se o indice ainda esta sendo montado, se a
 * configuracao desliga o mod ou se algo explode, todos os metodos publicos devolvem
 * {@code null} e o mixin simplesmente deixa a busca original do Minecraft agir.
 *
 * <p>Depende so de classes que existem em qualquer loader ({@code Minecraft}, {@code Util},
 * {@code ItemStack}); nada de NeoForge aqui.
 */
public final class BetterSearchClient {

    private static SearchSettings settings = new SearchSettings();
    private static volatile LanguageTable languages = LanguageTable.EMPTY;
    private static Path configFile;

    private static volatile SearchIndex<ItemStack> index;
    private static Object indexedSource;
    private static int indexedSize = -1;
    private static long indexedStamp = -1;

    private static long languageStamp;
    private static boolean building;
    private static boolean resourcesReady;
    private static boolean disabledByError;

    private static Object pendingSource;
    private static int pendingSize;

    private static String cachedQuery;
    private static List<ItemStack> cachedResults;

    private BetterSearchClient() {
    }

    // ------------------------------------------------------------------ ciclo de vida

    public static SearchSettings settings() {
        return settings;
    }

    /** Tabela de idiomas atual, compartilhada por todos os indices do mod. */
    public static LanguageTable languages() {
        return languages;
    }

    /** Muda sempre que os idiomas ou a configuracao mudam; invalida todos os indices. */
    public static long languageStamp() {
        return languageStamp;
    }

    public static void setSettings(SearchSettings newSettings) {
        newSettings.sanitize();
        SearchSettings previous = settings;
        settings = newSettings;

        // O cache do ultimo resultado sempre morre: qualquer opcao pode mudar o que sai.
        cachedQuery = null;
        cachedResults = null;

        // O INDICE, nao. Remonta-lo leva de decimos de segundo a alguns segundos, e nesse
        // meio tempo o mod devolve a busca original - era por isso que, logo depois de mexer
        // numa opcao, parecia que nada tinha mudado. Agora so as quatro opcoes que alteram o
        // conteudo do indice o descartam; o resto vale na hora.
        if (previous == null || newSettings.affectsIndex(previous)) {
            invalidate();
        }
        reloadLanguagesIfNeeded();
    }

    /**
     * O usuario ligou um idioma que ainda nao foi lido do disco? Entao relemos a tabela.
     *
     * <p>Trocar idiomas na tela de configuracao nao dispara um reload de recursos, que e
     * quando a tabela normalmente e montada. Sem isto, ligar um idioma novo so teria efeito
     * depois de um F3+T ou de reiniciar o jogo.
     *
     * <p>Desligar um idioma tem efeito na hora e nao depende disto: quem monta o indice ja
     * confere {@code settings.indexesLanguage(...)} para cada idioma.
     */
    private static void reloadLanguagesIfNeeded() {
        if (!resourcesReady || languages.matchesRequest(settings)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        final ResourceManager resourceManager = minecraft.getResourceManager();
        final SearchSettings snapshot = settings.copy();
        CompletableFuture
                .supplyAsync(() -> LanguageTable.load(resourceManager, snapshot), Util.backgroundExecutor())
                .whenComplete((table, error) -> minecraft.execute(() -> {
                    if (error != null) {
                        BetterSearch.LOGGER.error("[{}] falha ao recarregar os idiomas",
                                BetterSearch.MOD_NAME, error);
                        return;
                    }
                    onLanguagesLoaded(table);
                }));
    }

    /** Onde a configuracao mora em disco. Definido pela camada de plataforma na inicializacao. */
    /**
     * Abre a tela de configuracao, se der.
     *
     * <p>Fica aqui, e nao em cada loader, porque a regra ("so quando nenhuma tela estiver
     * aberta") tem de ser a mesma nos dois - senao o atalho se comporta diferente no Fabric
     * e no NeoForge por puro descuido.
     */
    public static void openConfigScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.screen == null) {
            minecraft.setScreen(new com.rivalzin.bettersearch.client.gui.BetterSearchConfigScreen(null));
        }
    }

    public static void setConfigFile(Path file) {
        configFile = file;
    }

    /**
     * Aplica o que o usuario escolheu na tela de configuracao e grava no disco.
     * O indice e descartado, entao a proxima busca ja usa os valores novos.
     */
    public static void applyAndSave(SearchSettings newSettings) {
        setSettings(newSettings.copy());
        if (configFile != null) {
            ConfigIo.save(configFile, settings);
        }
    }

    /** Chamado quando os recursos (e portanto os idiomas) sao recarregados. */
    public static void onLanguagesLoaded(LanguageTable table) {
        languages = table;
        resourcesReady = true;
        languageStamp++;
        invalidate();
    }

    public static void invalidate() {
        index = null;
        indexedSource = null;
        indexedSize = -1;
        indexedStamp = -1;
        // Importante: zerar tambem o "pendente", senao o proximo ensureIndex acharia que ja
        // existe uma montagem em andamento para esta lista e nunca reconstruiria o indice.
        pendingSource = null;
        pendingSize = -1;
        cachedQuery = null;
        cachedResults = null;
        RecipeSearch.invalidate();
        CommandItemIndex.invalidate();

        // Os indices do JEI, do EMI e do REI NAO sao invalidados por chamada daqui, e isso e
        // deliberado. Esta classe carrega sempre, com ou sem aqueles mods instalados; tocar
        // naquelas classes obrigaria a JVM a resolve-las, e sem o mod correspondente no pack
        // isso vira NoClassDefFoundError antes mesmo do jogo abrir.
        //
        // Em vez disso o contador abaixo sobe. Os tres guardam o indice com o carimbo junto e
        // conferem sozinhos a cada busca, entao um carimbo novo ja significa "remonte". Quem
        // nao esta instalado nao tem indice para remontar, e nenhuma classe daquele mod chega
        // a ser mencionada.
        languageStamp++;
    }

    public static boolean isEnabled() {
        return settings.enabled && !disabledByError;
    }

    // ------------------------------------------------------------------ busca

    /**
     * Comeca a montar o indice, se necessario. Chamado quando a aba de busca abre, para que
     * o indice ja esteja pronto quando a primeira letra for digitada.
     */
    public static void prepare(Collection<ItemStack> displayItems) {
        if (isEnabled() && settings.searchCreative && displayItems != null) {
            ensureIndex(displayItems);
        }
    }

    /**
     * @return os itens que casam com a consulta, ou {@code null} para "use a busca original".
     */
    public static List<ItemStack> search(String rawQuery, Collection<ItemStack> displayItems) {
        if (!isEnabled() || !settings.searchCreative || rawQuery == null || displayItems == null) {
            return null;
        }
        SearchIndex<ItemStack> current = ensureIndex(displayItems);
        if (current == null) {
            return null;
        }
        if (rawQuery.equals(cachedQuery) && cachedResults != null) {
            return cachedResults;
        }
        try {
            SearchQuery query = SearchQuery.parse(rawQuery, settings);
            if (query.isEmpty()) {
                return null;
            }
            List<ItemStack> results = current.search(query, settings);
            cachedQuery = rawQuery;
            cachedResults = results;
            return results;
        } catch (Throwable t) {
            disabledByError = true;
            BetterSearch.LOGGER.error("[{}] erro na busca; voltando para a busca original",
                    BetterSearch.MOD_NAME, t);
            return null;
        }
    }

    // ------------------------------------------------------------------ indice

    private static SearchIndex<ItemStack> ensureIndex(Collection<ItemStack> source) {
        SearchIndex<ItemStack> current = index;
        boolean fresh = current != null
                && indexedSource == source
                && indexedSize == source.size()
                && indexedStamp == languageStamp;
        if (fresh) {
            return current;
        }
        if (!building && (pendingSource != source || pendingSize != source.size())) {
            startBuild(source);
        }
        return null;
    }

    private static void startBuild(Collection<ItemStack> source) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return; // ainda nao ha mundo; tentamos de novo na proxima chamada
        }

        final List<ItemStack> snapshot = List.copyOf(source);
        final LanguageTable table = languages;
        final SearchSettings snapshotSettings = settings.copy();
        final long stamp = languageStamp;
        final Item.TooltipContext tooltipContext = Item.TooltipContext.of(minecraft.level);

        building = true;
        pendingSource = source;
        pendingSize = source.size();

        CompletableFuture
                .supplyAsync(() -> CreativeIndexBuilder.build(
                        snapshot, table, snapshotSettings, tooltipContext, player), Util.backgroundExecutor())
                .whenComplete((built, error) -> minecraft.execute(() -> {
                    building = false;
                    if (error != null) {
                        BetterSearch.LOGGER.error("[{}] falha ao montar o indice; usando a busca original",
                                BetterSearch.MOD_NAME, error);
                        disabledByError = true;
                        return;
                    }
                    index = built;
                    indexedSource = pendingSource;
                    indexedSize = pendingSize;
                    indexedStamp = stamp;
                    cachedQuery = null;
                    cachedResults = null;
                }));
    }
}
