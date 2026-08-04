package com.rivalzin.bettersearch.client;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Traducoes de itens em varios idiomas ao mesmo tempo.
 *
 * <p>O jogo so mantem carregado o idioma selecionado. Aqui lemos direto os
 * {@code assets/<namespace>/lang/<codigo>.json} de todos os pacotes de recursos - exatamente
 * como o {@code ClientLanguage} faz - para os idiomas configurados. E o que permite escrever
 * "pomme" com o jogo em portugues.
 *
 * <p>So guardamos chaves de item/bloco ({@code item.*} e {@code block.*}), o que descarta
 * ~80% de cada arquivo e mantem o uso de memoria baixo.
 *
 * <p>Esta classe toca Minecraft apenas atraves de {@code ResourceManager} e
 * {@code Identifier}, que existem em qualquer loader.
 */
public final class LanguageTable {

    public static final LanguageTable EMPTY = new LanguageTable(Map.of(), List.of(), Set.of());

    private final Map<String, Map<String, String>> byLanguage;
    private final List<String> order;
    private final Set<String> requested;

    private LanguageTable(Map<String, Map<String, String>> byLanguage, List<String> order, Set<String> requested) {
        this.byLanguage = byLanguage;
        this.order = order;
        this.requested = requested;
    }

    /**
     * Quais idiomas esta configuracao pede. Guardar o pedido - e nao so o resultado -
     * permite saber se a tabela precisa ser relida quando o usuario liga um idioma novo.
     */
    public static Set<String> requestFor(SearchSettings settings) {
        if (!settings.crossLanguage) {
            return Set.of();
        }
        if (settings.indexesAllLanguages()) {
            return Set.of("*");
        }
        return new LinkedHashSet<>(settings.languages);
    }

    /** A tabela ja cobre exatamente o que esta configuracao pede? */
    public boolean matchesRequest(SearchSettings settings) {
        return requested.equals(requestFor(settings));
    }

    /** Codigos de idioma carregados, na ordem em que devem ser indexados. */
    public List<String> languageCodes() {
        return order;
    }

    /** Traducao de {@code key} em {@code language}, ou {@code null}. */
    public String get(String language, String key) {
        Map<String, String> map = byLanguage.get(language);
        return map == null ? null : map.get(key);
    }

    public boolean isEmpty() {
        return byLanguage.isEmpty();
    }

    public int entryCount() {
        int total = 0;
        for (Map<String, String> map : byLanguage.values()) {
            total += map.size();
        }
        return total;
    }

    /**
     * Le os idiomas pedidos. Deve ser chamado na fase de "prepare" de um reload listener
     * (ou seja, fora da thread principal) porque le arquivos.
     */
    public static LanguageTable load(ResourceManager resourceManager, SearchSettings settings) {
        if (!settings.crossLanguage) {
            return EMPTY;
        }

        Set<String> request = requestFor(settings);
        Set<String> codes = settings.indexesAllLanguages()
                ? discoverAllLanguages(resourceManager)
                : new LinkedHashSet<>(settings.languages);
        codes.remove("*");
        if (codes.isEmpty()) {
            return new LanguageTable(Map.of(), List.of(), request);
        }

        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        List<String> namespaces = new ArrayList<>(resourceManager.getNamespaces());

        for (String code : codes) {
            Map<String, String> translations = new HashMap<>(2048);
            for (String namespace : namespaces) {
                Identifier location = Identifier.tryBuild(namespace, "lang/" + code + ".json");
                if (location == null) {
                    continue;
                }
                for (Resource resource : resourceManager.getResourceStack(location)) {
                    readInto(resource, translations);
                }
            }
            if (!translations.isEmpty()) {
                result.put(code, translations);
                order.add(code);
            }
        }

        BetterSearch.LOGGER.info("[{}] {} idiomas indexados ({} traducoes de itens): {}",
                BetterSearch.MOD_NAME, order.size(),
                result.values().stream().mapToInt(Map::size).sum(), order);
        return new LanguageTable(result, List.copyOf(order), request);
    }

    private static Set<String> discoverAllLanguages(ResourceManager resourceManager) {
        Set<String> codes = new LinkedHashSet<>();
        try {
            for (Identifier location : resourceManager
                    .listResources("lang", rl -> rl.getPath().endsWith(".json")).keySet()) {
                String path = location.getPath();
                int slash = path.lastIndexOf('/');
                String code = path.substring(slash + 1, path.length() - ".json".length());
                if (!code.isEmpty()) {
                    codes.add(code);
                }
            }
        } catch (Exception e) {
            BetterSearch.LOGGER.warn("[{}] nao consegui listar os idiomas disponiveis", BetterSearch.MOD_NAME, e);
        }
        return codes;
    }

    private static void readInto(Resource resource, Map<String, String> out) {
        try (InputStream in = resource.open();
             Reader charReader = new InputStreamReader(in, StandardCharsets.UTF_8);
             JsonReader json = new JsonReader(charReader)) {
            json.setLenient(true);
            if (json.peek() != JsonToken.BEGIN_OBJECT) {
                return;
            }
            json.beginObject();
            while (json.hasNext()) {
                String key = json.nextName();
                if (json.peek() != JsonToken.STRING) {
                    json.skipValue();
                    continue;
                }
                String value = json.nextString();
                if (isInteresting(key)) {
                    out.put(key, value);
                }
            }
            json.endObject();
        } catch (Exception e) {
            // Um arquivo de idioma quebrado de algum mod nao pode derrubar a busca inteira.
            BetterSearch.LOGGER.debug("[{}] arquivo de idioma ignorado: {}", BetterSearch.MOD_NAME, e.toString());
        }
    }

    /**
     * Guardamos so o que pode ser nome de item. As chaves de traducao geradas por
     * {@code Item#getDescriptionId} sempre comecam com {@code item.} ou {@code block.}.
     */
    private static boolean isInteresting(String key) {
        return key.startsWith("item.") || key.startsWith("block.");
    }
}
