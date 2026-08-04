package com.rivalzin.bettersearch.neoforge;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.BetterSearchClient;
import com.rivalzin.bettersearch.client.ConfigIo;
import com.rivalzin.bettersearch.client.LanguageReloadListener;
import com.rivalzin.bettersearch.client.gui.BetterSearchConfigScreen;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
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

        modEventBus.addListener(BetterSearchNeoForge::onRegisterClientReloadListeners);
        modEventBus.addListener(BetterSearchKeys::onRegisterKeyMappings);

        // Duas portas de entrada para a mesma tela: o botao "Config" ao lado do mod na
        // lista de mods, e o atalho Alt+O, que nao depende de nada.
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, modListScreen) -> new BetterSearchConfigScreen(modListScreen));
        NeoForge.EVENT_BUS.addListener(BetterSearchKeys::onClientTick);

        BetterSearch.LOGGER.info("[{}] carregado. Configuracao: {}", BetterSearch.MOD_NAME, configFile);
    }

    private static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new LanguageReloadListener());
    }
}
