package com.rivalzin.bettersearch.neoforge;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.BetterSearchClient;
import com.rivalzin.bettersearch.client.ConfigIo;
import com.rivalzin.bettersearch.client.gui.BetterSearchConfigScreen;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.nio.file.Path;

/**
 * Ponto de entrada do NeoForge - de proposito, o arquivo mais curto do mod.
 *
 * <p>Ele faz exatamente quatro coisas:
 * <ol>
 *   <li>descobre onde fica a pasta de configuracao;</li>
 *   <li>carrega a configuracao;</li>
 *   <li>registra o listener que le os arquivos de idioma;</li>
 *   <li>liga a tela de configuracao ao botao da lista de mods.</li>
 * </ol>
 *
 * <p>Todo o resto do mod nao conhece NeoForge. Portar para Fabric e reescrever este arquivo
 * (veja PORTING.md).
 */
@Mod(value = BetterSearch.MOD_ID, dist = Dist.CLIENT)
public final class BetterSearchNeoForge {

    public BetterSearchNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve(BetterSearch.MOD_ID + ".json");
        SearchSettings settings = ConfigIo.loadOrCreate(configFile);
        BetterSearchClient.setConfigFile(configFile);
        BetterSearchClient.setSettings(settings);

        modEventBus.addListener(BetterSearchKeys::onRegisterKeyMappings);
        registrarReloadListener(modEventBus);

        // Duas portas de entrada para a mesma tela: o botao "Config" ao lado do mod na
        // lista de mods, e o atalho Alt+O, que nao depende de nada.
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, modListScreen) -> new BetterSearchConfigScreen(modListScreen));
        NeoForge.EVENT_BUS.addListener(BetterSearchKeys::onClientTick);
        NeoForge.EVENT_BUS.addListener(BetterSearchNeoForge::cintoDosIdiomas);

        BetterSearch.LOGGER.info("[{}] carregado. Configuracao: {}", BetterSearch.MOD_NAME, configFile);
    }

    /*
     * O listener de reload e registrado pelo EVENTO - mas a linha 21.4 tem DOIS eventos,
     * porque o NeoForge renomeou no meio dela (RegisterClientReloadListenersEvent nos builds
     * de 2024/2025; AddClientReloadListenersEvent no 21.4.157, com o velho REMOVIDO). E o
     * caminho vanilla direto nao serve de ponte: o 21.4.157 congela a lista de listeners
     * depois do boot - registerReloadListener lanca UnsupportedOperationException, provado
     * por crash em campo no Titan Survival. "Assinatura existir nao e significar", de novo:
     * o deprecated era o aviso, e desta vez ele falava serio.
     *
     * Cada metade da linha tem a propria classe registradora, carregada por nome SO depois
     * de o Class.forName confirmar que o evento daquela metade existe neste build - o mesmo
     * isolamento dos ganchos de visualizador. Sem nenhum dos dois (um build futuro?), o mod
     * segue inteiro; so a tabela de idiomas deixa de se renovar no F3+T, e o log avisa.
     */
    /** Primeiro tique = recursos da inicializacao prontos; garante os idiomas mesmo se
     *  nenhum evento de reload tiver aceitado o listener. Veja BetterSearchClient. */
    private static void cintoDosIdiomas(ClientTickEvent.Post event) {
        com.rivalzin.bettersearch.client.BetterSearchClient.ensureLanguagesLoaded();
    }

    private static void registrarReloadListener(IEventBus modEventBus) {
        String[][] metades = {
                {"net.neoforged.neoforge.client.event.AddClientReloadListenersEvent",
                 "com.rivalzin.bettersearch.neoforge.RegistroReloadNovo"},
                {"net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent",
                 "com.rivalzin.bettersearch.neoforge.RegistroReloadVelho"},
        };
        for (String[] metade : metades) {
            try {
                Class.forName(metade[0]); // este build tem este evento?
                Class.forName(metade[1]).getMethod("instalar", IEventBus.class).invoke(null, modEventBus);
                return;
            } catch (ClassNotFoundException outraMetade) {
                // tenta a proxima
            } catch (Throwable t) {
                BetterSearch.LOGGER.warn("[{}] falha ao registrar o listener de reload: {}",
                        BetterSearch.MOD_NAME, t.toString());
                return;
            }
        }
        BetterSearch.LOGGER.warn("[{}] nenhum evento de reload conhecido neste NeoForge; F3+T nao renovara a tabela de idiomas",
                BetterSearch.MOD_NAME);
    }
}
