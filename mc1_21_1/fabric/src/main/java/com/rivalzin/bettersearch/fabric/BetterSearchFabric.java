package com.rivalzin.bettersearch.fabric;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.BetterSearchClient;
import com.rivalzin.bettersearch.client.ConfigIo;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.PackType;

import java.nio.file.Path;

/**
 * Ponto de entrada do Fabric - gemeo exato do {@code BetterSearchNeoForge}.
 *
 * <p>Ele faz as mesmas quatro coisas, na mesma ordem, so que com a API do Fabric: acha a
 * pasta de configuracao, carrega a configuracao, registra o listener que le os arquivos de
 * idioma e liga o atalho de teclado. A tela de configuracao entra pelo Mod Menu, no
 * {@code BetterSearchModMenu}.
 *
 * <p>Todo o resto do mod - motor de busca, telas, mixins - e <b>o mesmo arquivo</b> que o
 * NeoForge compila. E por isso que este arquivo e tao curto: a camada de loader e 2% do mod.
 */
public final class BetterSearchFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Path configFile = FabricLoader.getInstance().getConfigDir()
                .resolve(BetterSearch.MOD_ID + ".json");
        SearchSettings settings = ConfigIo.loadOrCreate(configFile);
        BetterSearchClient.setConfigFile(configFile);
        BetterSearchClient.setSettings(settings);

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(new FabricLanguageReloadListener());

        BetterSearchFabricKeys.register();

        BetterSearch.LOGGER.info("[{}] carregado. Configuracao: {}", BetterSearch.MOD_NAME, configFile);
    }
}
