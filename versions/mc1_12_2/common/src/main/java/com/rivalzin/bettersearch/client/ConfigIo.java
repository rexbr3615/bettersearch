package com.rivalzin.bettersearch.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchSettings;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Leitura/escrita da configuracao em JSON.
 *
 * <p>JSON de proposito: e o unico formato que funciona igual em NeoForge, Fabric, Quilt ou
 * qualquer outro loader, sem depender da API de configuracao de nenhum deles. A camada de
 * plataforma so precisa dizer <em>onde</em> fica a pasta de configuracao.
 */
public final class ConfigIo {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final String[] HELP = {
            "Better Search - makes search bars smarter and better.",
            "You do NOT need to edit this file: everything is in the game, on the",
            "  mod list (Better Search -> Config) or with the Alt + O shortcut.",
            "enabled: false restores Minecraft's original search everywhere.",
            "searchCreative / searchRecipeBook: where the mod is allowed to act.",
            "searchPlayerNames / searchCommandItems: extra chat and command suggestions.",
            "fixCommandErrors: '/gamemode criativo' suggests 'creative'. Nothing to tune.",
            "commandSuggestionLimit: how many entries the mod may add to a suggestion list.",
            "typoTolerance: 0 = off, 1 = low, 2 = normal, 3 = high.",
            "minTypoLength: words shorter than this must be spelled correctly.",
            "matchInitials: 'obwc' finds 'Oak Boat with Chest'.",
            "ignoreSpaces: 'goldenapple' finds 'Golden Apple'.",
            "searchTooltips: also search the lines under the item name (potions, books).",
            "searchItemIds: allow searching by id, e.g. redstone_torch.",
            "searchModIds: allow the @mod filter, e.g. @create cogwheel.",
            "crossLanguage: also search the item name in other languages ('pomme').",
            "languages: which languages are indexed. Use [\"*\"] for ALL of them.",
            "foreignStrictOnly: other languages only match exact spelling (avoids noise).",
            "crossFieldMatching: allow one search to mix words from different languages.",
            "fuzzyThreshold / crossFieldThreshold: how few results justify running the more",
            "  permissive passes. Higher = tries harder, slightly more time per keystroke.",
            "sortByRelevance: order by best match instead of creative tab order.",
            "maxResults: 0 = unlimited.",
            "searchJei / searchEmi / searchRei: use this same search inside JEI's, EMI's and",
            "  REI's item lists. There is no separate tuning for them on purpose: every option",
            "  above applies there too, because it is the same index and the same matcher."
    };

    private ConfigIo() {
    }

    /** Le o arquivo; se nao existir (ou estiver corrompido), cria um com os valores padrao. */
    public static SearchSettings loadOrCreate(Path file) {
        SearchSettings settings = new SearchSettings();
        if (Files.exists(file)) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                /*
                 * JsonParser.parseReader e ESTATICO so a partir do Gson 2.8.6. O Minecraft
                 * 1.16.5 vem com o Gson 2.8.0 - conferido no classpath da sonda - e la o
                 * unico caminho e o metodo de instancia, hoje depreciado.
                 *
                 * Nas outras oito versoes o Gson e novo e a forma estatica funciona; aqui
                 * nao. Por isso este arquivo diverge, e so nesta linha.
                 */
                @SuppressWarnings("deprecation")
                JsonElement parsed = new JsonParser().parse(reader);
                if (parsed != null && parsed.isJsonObject()) {
                    SearchSettings read = GSON.fromJson(parsed, SearchSettings.class);
                    if (read != null) {
                        settings = read;
                    }
                }
            } catch (Exception e) {
                BetterSearch.LOGGER.warn("[{}] configuracao invalida em {}; usando os valores padrao",
                        BetterSearch.MOD_NAME, file, e);
                settings = new SearchSettings();
            }
        }
        settings.sanitize();
        save(file, settings);
        return settings;
    }

    public static void save(Path file, SearchSettings settings) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject out = new JsonObject();
            JsonArray help = new JsonArray();
            for (String line : HELP) {
                help.add(line);
            }
            out.add("_ajuda", help);
            for (Map.Entry<String, JsonElement> entry : GSON.toJsonTree(settings).getAsJsonObject().entrySet()) {
                out.add(entry.getKey(), entry.getValue());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write(GSON.toJson(out));
            }
        } catch (Exception e) {
            BetterSearch.LOGGER.warn("[{}] nao consegui salvar a configuracao em {}",
                    BetterSearch.MOD_NAME, file, e);
        }
    }
}
