package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tabela de idiomas da 1.12.2 - a versao {@code .lang} do LanguageTable das outras versoes.
 *
 * <p>Aqui os arquivos de idioma sao {@code lang/<codigo>.lang} no formato {@code chave=valor}
 * (o JSON so chegou na 1.13), e nao existe o {@code listResources} que as versoes novas usam
 * para descobrir o que ha: a unica pergunta possivel e "o dominio X tem o idioma Y?", uma por
 * vez, via {@code getAllResources} - que responde com excecao quando nao tem. Sao dominios
 * vezes idiomas perguntas (~60 x 18 num modpack grande), cada falha custa uma excecao, e por
 * isso isto roda numa thread de fundo - uma vez por LISTA de idiomas: trocar a lista no menu
 * derruba a tabela e a busca seguinte recarrega com a lista nova.
 *
 * <p>O parser e proprio (e minusculo: primeira {@code =} separa chave de valor, {@code #}
 * comenta) em vez de reaproveitar o {@code Locale} do jogo por um motivo de sobrevivencia:
 * ler o campo {@code properties} de um {@code Locale} nosso exigiria reflexao, e em producao
 * os nomes dos campos sao SRG ({@code field_...}), nao os nomes MCP que se ve no codigo -
 * reflexao por nome aqui funciona no ambiente de desenvolvimento e quebra silenciosamente no
 * jogo do jogador. Vinte linhas de parser nao tem esse risco.
 */
public final class Linguas {

    private static volatile Map<String, Map<String, String>> tabela;
    private static volatile int marca;
    private static volatile boolean carregando;
    /** Cresce a cada {@link #invalidar()}; uma carga que nasceu antes disso e descartada. */
    private static volatile int geracao;

    private Linguas() {
    }

    /** Derruba a tabela; a proxima busca recarrega com a lista de idiomas atual. */
    public static void invalidar() {
        geracao++;
        tabela = null;
    }

    /** Muda sempre que a tabela termina de carregar; os indices usam isto como chave de cache. */
    public static int marca() {
        return marca;
    }

    public static String get(String codigo, String chave) {
        Map<String, Map<String, String>> atual = tabela;
        if (atual == null) {
            return null;
        }
        Map<String, String> doIdioma = atual.get(codigo);
        return doIdioma == null ? null : doIdioma.get(chave);
    }

    /** Os idiomas que estao ligados AGORA, exceto o do proprio jogo (esse ja e o nome nativo). */
    public static List<String> codigosAtivos(SearchSettings settings) {
        Map<String, Map<String, String>> atual = tabela;
        if (atual == null) {
            return java.util.Collections.emptyList();
        }
        String doJogo = Minecraft.getMinecraft().gameSettings.language;
        List<String> fora = new ArrayList<>();
        for (String codigo : atual.keySet()) {
            if (settings.indexesLanguage(codigo) && !codigo.equalsIgnoreCase(doJogo)) {
                fora.add(codigo);
            }
        }
        return fora;
    }

    /**
     * Dispara o carregamento se ainda nao houve. Chamar da thread do cliente.
     *
     * <p>Dois cuidados que vieram de um bug de campo (Pixelmon Brasil): trocar a lista de
     * idiomas no menu derrubava a tabela, mas a flag {@code carregando} tinha ficado em
     * {@code true} desde a primeira carga - a recarga via a trava fechada e desistia PARA
     * SEMPRE. So reiniciar o jogo trazia os idiomas de volta. Por isso:
     *
     * <ul>
     *   <li>{@code carregando} volta a {@code false} num {@code finally} - com excecao ou
     *       sem, a trava reabre (secao 24 do verify);</li>
     *   <li>a carga carrega o numero da {@code geracao} em que nasceu: se o menu invalidar
     *       no MEIO da leitura, o resultado descreve a lista antiga e e descartado - a
     *       proxima busca recarrega com a lista atual.</li>
     * </ul>
     */
    public static void garantir(SearchSettings settings) {
        if (tabela != null || carregando || !settings.crossLanguage) {
            return;
        }
        carregando = true;
        final int geracaoDaCarga = geracao;
        final IResourceManager recursos = Minecraft.getMinecraft().getResourceManager();
        final List<String> pedidos = new ArrayList<>(settings.indexesAllLanguages()
                ? SearchSettings.DEFAULT_LANGUAGES : settings.languages);
        final List<String> dominios = new ArrayList<>(recursos.getResourceDomains());

        Thread trabalho = new Thread(() -> {
            try {
                long inicio = System.nanoTime();
                Map<String, Map<String, String>> nova = new LinkedHashMap<>();
                for (String codigo : pedidos) {
                    if ("*".equals(codigo)) {
                        continue;
                    }
                    Map<String, String> traducoes = new HashMap<>(2048);
                    for (String dominio : dominios) {
                        try {
                            for (IResource recurso : recursos.getAllResources(
                                    new ResourceLocation(dominio, "lang/" + codigo + ".lang"))) {
                                ler(recurso, traducoes);
                            }
                        } catch (Throwable semArquivo) {
                            // dominio sem este idioma - o caso comum, e por isso estamos em fundo
                        }
                    }
                    if (!traducoes.isEmpty()) {
                        nova.put(codigo, traducoes);
                    }
                }
                if (geracao != geracaoDaCarga) {
                    BetterSearch.LOGGER.debug("[{}] tabela de idiomas descartada: a lista mudou durante a leitura",
                            BetterSearch.MOD_NAME);
                    return;
                }
                tabela = nova;
                marca++;
                int total = 0;
                for (Map<String, String> m : nova.values()) {
                    total += m.size();
                }
                BetterSearch.LOGGER.info("[{}] {} idiomas indexados (1.12.2, {} traducoes) em {} ms: {}",
                        BetterSearch.MOD_NAME, nova.size(), total,
                        (System.nanoTime() - inicio) / 1_000_000, nova.keySet());
            } finally {
                carregando = false;
            }
        }, "BetterSearch-Linguas-1.12.2");
        trabalho.setDaemon(true);
        trabalho.start();
    }

    private static void ler(IResource recurso, Map<String, String> destino) {
        try (BufferedReader leitor = new BufferedReader(
                new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = leitor.readLine()) != null) {
                if (linha.isEmpty() || linha.charAt(0) == '#') {
                    continue;
                }
                int igual = linha.indexOf('=');
                if (igual > 0) {
                    destino.put(linha.substring(0, igual), linha.substring(igual + 1));
                }
            }
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] arquivo de idioma ignorado: {}",
                    BetterSearch.MOD_NAME, t.toString());
        }
    }
}
