package com.rivalzin.bettersearch.client;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
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
import java.util.TreeSet;

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
 * {@code ResourceLocation}, que existem em qualquer loader.
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
     *
     * <p><b>Por que uma listagem, e nao uma busca caminho por caminho.</b> A versao antiga
     * perguntava a cada pacote de recursos "voce tem {@code lang/<idioma>.json}?" - uma vez
     * para cada idioma vezes cada namespace. Com 18 idiomas e 50 mods, ~900 perguntas por
     * recarga.
     *
     * <p>Um pacote de recursos <i>deve</i> responder "nao tenho" quando o arquivo nao existe,
     * mas nem todos respeitam isso: alguns lancam excecao. Como a leitura acontece durante a
     * carga de recursos, uma excecao dali derruba o jogo <b>antes do menu principal</b> - e o
     * mod que faz a pergunta somos nos, porque nenhum outro pede 18 idiomas de uma vez.
     *
     * <p>Pedindo a LISTA do que existe, so tocamos em arquivos que o proprio jogo garantiu
     * estarem la. Nenhum pacote e provocado com pergunta sobre arquivo ausente, e de quebra
     * as ~900 consultas viram uma so.
     */
    public static LanguageTable load(ResourceManager resourceManager, SearchSettings settings) {
        if (!settings.crossLanguage) {
            return EMPTY;
        }

        Set<String> request = requestFor(settings);
        // null = "todos os idiomas"; caso contrario, so os escolhidos.
        Set<String> wanted = settings.indexesAllLanguages() ? null : new LinkedHashSet<>(settings.languages);
        if (wanted != null) {
            wanted.remove("*");
            if (wanted.isEmpty()) {
                return new LanguageTable(Map.of(), List.of(), request);
            }
        }

        /*
         * A listagem anda pelos pacotes UM A UM, com isolamento por pacote - e nao pelo
         * listResourceStacks do jogo, que junta todos numa chamada so. O motivo tem nome:
         * o TranslationsPack do Chunks Fade In 2.0.7 lanca ArrayIndexOutOfBounds no proprio
         * listResources, e dentro do listResourceStacks a excecao dele derrubava a listagem
         * INTEIRA - um pacote podre custava os 18 idiomas (aconteceu no Titan Survival).
         * Assim, quem quebra perde so os proprios arquivos, com nome e sobrenome no log.
         * A ordem dos pacotes e a mesma do jogo (listPacks vai do fundo ao topo da pilha),
         * entao pacote por cima continua sobrescrevendo chave repetida, como antes.
         */
        Map<ResourceLocation, List<IoSupplier<InputStream>>> available = new LinkedHashMap<>();
        resourceManager.listPacks().forEach(pack -> {
            try {
                for (String namespace : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
                    pack.listResources(PackType.CLIENT_RESOURCES, namespace, "lang",
                            (location, supplier) -> {
                                if (location.getPath().endsWith(".json")) {
                                    available.computeIfAbsent(location, unused -> new ArrayList<>())
                                            .add(supplier);
                                }
                            });
                }
            } catch (Throwable t) {
                BetterSearch.LOGGER.warn("[{}] pacote de recursos '{}' quebrou ao listar idiomas; pulando so ele: {}",
                        BetterSearch.MOD_NAME, idSeguro(pack), t.toString());
            }
        });

        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, List<IoSupplier<InputStream>>> entry : available.entrySet()) {
            String code = languageCodeOf(entry.getKey().getPath());
            if (code == null || (wanted != null && !wanted.contains(code))) {
                continue;
            }
            Map<String, String> translations = result.computeIfAbsent(code, unused -> new HashMap<>(2048));
            try {
                for (IoSupplier<InputStream> resource : entry.getValue()) {
                    readInto(resource, translations);
                }
            } catch (Exception e) {
                // Um pacote mal-comportado atrapalha o proprio idioma dele, e mais nada.
                BetterSearch.LOGGER.debug("[{}] pacote de idioma ignorado ({}): {}",
                        BetterSearch.MOD_NAME, entry.getKey(), e.toString());
            }
        }
        result.values().removeIf(Map::isEmpty);

        // A ordem importa na hora de indexar, entao ela e deliberada: quando o usuario
        // escolheu os idiomas, respeitamos a ordem dele; em "todos", ordem alfabetica para
        // o resultado ser sempre o mesmo.
        List<String> order = new ArrayList<>();
        if (wanted != null) {
            for (String code : wanted) {
                if (result.containsKey(code)) {
                    order.add(code);
                }
            }
        } else {
            order.addAll(new TreeSet<>(result.keySet()));
        }

        BetterSearch.LOGGER.info("[{}] {} idiomas indexados ({} traducoes de itens): {}",
                BetterSearch.MOD_NAME, order.size(),
                result.values().stream().mapToInt(Map::size).sum(), order);
        return new LanguageTable(result, List.copyOf(order), request);
    }

    /** {@code "lang/pt_br.json"} -> {@code "pt_br"}, ou {@code null} se nao parecer idioma. */
    private static String languageCodeOf(String path) {
        if (!path.endsWith(".json")) {
            return null;
        }
        String code = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
        return code.isEmpty() ? null : code;
    }

    /** Ate o nome do pacote pode lancar num pacote quebrado; o log nao pode quebrar junto. */
    private static String idSeguro(PackResources pack) {
        try {
            return pack.packId();
        } catch (Throwable t) {
            return pack.getClass().getName();
        }
    }

    private static void readInto(IoSupplier<InputStream> resource, Map<String, String> out) {
        try (InputStream in = resource.get();
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
