package com.rivalzin.bettersearch.fabric;

import com.rivalzin.bettersearch.client.gui.BetterSearchConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Botao de configuracao dentro do Mod Menu - o equivalente Fabric do
 * {@code IConfigScreenFactory} do NeoForge.
 *
 * <p>O Mod Menu e <b>opcional</b>: entra so em tempo de compilacao. Sem ele o mod carrega
 * normalmente e a tela continua acessivel pelo Alt+O, que nao depende de mod nenhum.
 */
public final class BetterSearchModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return BetterSearchConfigScreen::new;
    }
}
