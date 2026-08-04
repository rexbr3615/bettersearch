package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lista dos idiomas que o jogo conhece, com o nome bonitinho de cada um.
 *
 * <p>A lista vem do proprio {@code LanguageManager}, entao ela sempre bate com a tela
 * "Idiomas" do Minecraft - inclusive idiomas adicionados por resource packs. Nada e escrito
 * na mao aqui, o que evita codigos errados e mantem o mod correto em qualquer versao.
 */
public final class LanguageCatalog {

    /** @param code por exemplo {@code pt_br}; @param displayName por exemplo "Português (Brasil)" */
    public record Entry(String code, String displayName) {
    }

    private LanguageCatalog() {
    }

    public static List<Entry> available() {
        List<Entry> out = new ArrayList<>();
        try {
            Map<String, LanguageInfo> languages = Minecraft.getInstance().getLanguageManager().getLanguages();
            for (Map.Entry<String, LanguageInfo> entry : languages.entrySet()) {
                LanguageInfo info = entry.getValue();
                out.add(new Entry(entry.getKey(), info.name() + " (" + info.region() + ")"));
            }
        } catch (Throwable t) {
            BetterSearch.LOGGER.warn("[{}] nao consegui listar os idiomas do jogo", BetterSearch.MOD_NAME, t);
        }
        if (out.isEmpty()) {
            for (String code : SearchSettings.DEFAULT_LANGUAGES) {
                out.add(new Entry(code, code));
            }
        }
        return out;
    }

    /** Codigo do idioma em uso no jogo (usado so para informar o usuario). */
    public static String currentCode() {
        try {
            return Minecraft.getInstance().getLanguageManager().getSelected();
        } catch (Throwable t) {
            return "en_us";
        }
    }
}
