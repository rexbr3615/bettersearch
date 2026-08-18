package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista dos idiomas que o jogo conhece, com o nome bonitinho de cada um.
 *
 * <p>A lista vem do proprio {@code LanguageManager}, entao ela sempre bate com a tela
 * "Idiomas" do Minecraft - inclusive idiomas adicionados por resource packs. Nada e escrito
 * na mao aqui, o que evita codigos errados e mantem o mod correto em qualquer versao.
 */
public final class LanguageCatalog {

    /* Era um record. Classe comum porque a 1.16.5 roda em Java 8 - veja o PORTING.md. */
    /** {@code code} por exemplo {@code pt_br}; {@code displayName} por exemplo "Português (Brasil)". */
    public static final class Entry {

        private final String code;
        private final String displayName;

        public Entry(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String code() {
            return code;
        }

        public String displayName() {
            return displayName;
        }
    }

    private LanguageCatalog() {
    }

    public static List<Entry> available() {
        List<Entry> out = new ArrayList<>();
        try {
            // 1.19.2: getLanguages() devolve um conjunto ordenado de LanguageInfo, e nao um
            // mapa por codigo - o codigo vem de dentro do proprio objeto. E os acessores ainda
            // usam o prefixo "get"; o formato de record so chegou depois.
            for (LanguageInfo info : Minecraft.getInstance().getLanguageManager().getLanguages()) {
                out.add(new Entry(info.getCode(), info.getName() + " (" + info.getRegion() + ")"));
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
            // getSelected() devolve o LanguageInfo, nao a string do codigo.
            return Minecraft.getInstance().getLanguageManager().getSelected().getCode();
        } catch (Throwable t) {
            return "en_us";
        }
    }
}
