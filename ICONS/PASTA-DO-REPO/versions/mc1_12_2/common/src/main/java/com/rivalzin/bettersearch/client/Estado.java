package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.core.SearchSettings;

import java.nio.file.Path;

/**
 * Configuracao viva da 1.12.2: quem le, quem salva e o carimbo que invalida os indices.
 *
 * <p>E o BetterSearchClient das outras versoes reduzido ao que esta versao ja tem. O arquivo
 * e o MESMO formato ({@code bettersearch.json} via {@link ConfigIo}) - quem copiar a pasta de
 * config entre versoes leva as opcoes junto.
 */
public final class Estado {

    private static SearchSettings settings = new SearchSettings();
    private static Path arquivo;
    /** Cresce a cada mudanca de configuracao; os indices guardam o valor com que nasceram. */
    private static volatile int marca;

    private Estado() {
    }

    public static SearchSettings settings() {
        return settings;
    }

    public static int marca() {
        return marca;
    }

    public static void carregar(Path caminho) {
        arquivo = caminho;
        SearchSettings lidas = ConfigIo.loadOrCreate(caminho);
        if (lidas != null) {
            settings = lidas;
        }
        settings.sanitize();
    }

    /** Aplica e salva. Os indices se remontam sozinhos na proxima busca (pelo carimbo). */
    public static void aplicarESalvar(SearchSettings novas) {
        novas.sanitize();
        boolean idiomasMudaram = !novas.languages.equals(settings.languages)
                || novas.crossLanguage != settings.crossLanguage;
        settings = novas;
        marca++;
        if (idiomasMudaram) {
            // A tabela atual pode nao cobrir o pedido novo; derrubar forca releitura.
            Linguas.invalidar();
        }
        if (arquivo != null) {
            ConfigIo.save(arquivo, settings);
        }
    }
}
