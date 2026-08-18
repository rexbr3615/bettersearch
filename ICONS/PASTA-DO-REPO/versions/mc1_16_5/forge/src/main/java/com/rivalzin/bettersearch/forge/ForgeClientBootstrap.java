package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.BetterSearchClient;
import com.rivalzin.bettersearch.client.ConfigIo;
import com.rivalzin.bettersearch.client.LanguageReloadListener;
import com.rivalzin.bettersearch.client.gui.BetterSearchConfigScreen;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * Gemeo exato do {@code BetterSearchNeoForge} e do {@code BetterSearchFabric}, com a API do
 * Forge 1.20.1.
 *
 * <p>Faz as mesmas quatro coisas, na mesma ordem: acha a pasta de configuracao, carrega a
 * configuracao, registra o listener que le os arquivos de idioma e liga as duas portas de
 * entrada da tela (o botao na lista de mods e o atalho Alt+O).
 *
 * <p>Todo o resto do mod - motor de busca, telas, mixins - e <b>o mesmo arquivo</b> que o
 * Fabric compila. E por isso que esta classe e tao curta: a camada de loader e 2% do mod.
 */
final class ForgeClientBootstrap {

    private ForgeClientBootstrap() {
    }

    static void init() {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve(BetterSearch.MOD_ID + ".json");
        SearchSettings settings = ConfigIo.loadOrCreate(configFile);
        BetterSearchClient.setConfigFile(configFile);
        BetterSearchClient.setSettings(settings);

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(ForgeClientBootstrap::aoPrepararCliente);
        // 1.18.2: a tecla nao vem por evento; registra-se direto (veja BetterSearchForgeKeys).
        BetterSearchForgeKeys.register();

        // Duas portas de entrada para a mesma tela: o botao "Config" ao lado do mod na lista
        // de mods, e o atalho Alt+O, que nao depende de nada.
        /*
         * Na 1.16.5 o ponto de extensao do botao de config e o ExtensionPoint.CONFIGGUIFACTORY,
         * e o valor e um BiFunction<Minecraft, Screen, Screen> cru. O ConfigGuiHandler (1.18.2)
         * e o ConfigScreenHandler (1.19.2+) sao nomes que so vieram depois. Conferido com javap:
         * ExtensionPoint.CONFIGGUIFACTORY existe no jar da 1.16.5 com exatamente esse tipo.
         */
        ModLoadingContext.get().registerExtensionPoint(
                ExtensionPoint.CONFIGGUIFACTORY,
                () -> (minecraft, parent) -> new BetterSearchConfigScreen(parent));
        MinecraftForge.EVENT_BUS.addListener(BetterSearchForgeKeys::onClientTick);

        BetterSearch.LOGGER.info("[{}] carregado. Configuracao: {}", BetterSearch.MOD_NAME, configFile);
    }

    /*
     * O RegisterClientReloadListenersEvent so nasceu na 1.19. Na 1.16.5 o listener entra
     * direto no gerenciador de recursos, que aqui e um ReloadableResourceManager.
     */
    private static void aoPrepararCliente(FMLClientSetupEvent evento) {
        // No FMLClientSetupEvent o Minecraft ja existe, o que nao e verdade na construcao do
        // mod - por isso o registro do listener acontece aqui e nao no init().
        ((ReloadableResourceManager) Minecraft.getInstance().getResourceManager())
                .registerReloadListener(new LanguageReloadListener());
    }
}
