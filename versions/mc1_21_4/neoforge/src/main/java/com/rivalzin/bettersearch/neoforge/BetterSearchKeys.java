package com.rivalzin.bettersearch.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import com.rivalzin.bettersearch.client.BetterSearchClient;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * Atalho de teclado para abrir a configuracao: <b>Alt + O</b>.
 *
 * <p>A tela de configuracao e inteiramente deste mod - o botao na lista de mods e apenas
 * uma porta de entrada. Por isso da para abri-la direto por uma tecla, sem depender de
 * nenhum outro mod nem de passar pela lista.
 *
 * <p>Registrado como {@code KeyMapping}, entao aparece sozinho em <b>Opcoes &gt; Controles</b>,
 * na categoria do mod, e pode ser remapeado por la. O contexto {@code IN_GAME} evita que a
 * tecla dispare enquanto voce digita no chat ou esta em outra tela.
 */
public final class BetterSearchKeys {

    public static final String CATEGORY = "key.categories.bettersearch";

    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.bettersearch.open_config",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY);

    private BetterSearchKeys() {
    }

    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG);
    }

    static void onClientTick(ClientTickEvent.Post event) {
        // consumeClick devolve true uma vez por pressionada, entao segurar a tecla nao
        // reabre a tela repetidamente.
        while (OPEN_CONFIG.consumeClick()) {
            BetterSearchClient.openConfigScreen();
        }
    }
}
