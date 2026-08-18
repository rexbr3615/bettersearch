package com.rivalzin.bettersearch.fabric;

import com.rivalzin.bettersearch.client.BetterSearchClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * Atalho <b>Alt + O</b> no Fabric.
 *
 * <p>O Fabric nao tem API de tecla modificadora, entao o Alt e conferido na mao no tick e o
 * rotulo da tela de Controles e ajustado pelo {@link AltKeyMapping}. Resultado: mesmo atalho,
 * mesmo texto "Alt + O" que o NeoForge mostra.
 */
public final class BetterSearchFabricKeys {

    public static final String CATEGORY = "key.categories.bettersearch";

    public static final KeyMapping OPEN_CONFIG = new AltKeyMapping(
            "key.bettersearch.open_config",
            GLFW.GLFW_KEY_O,
            CATEGORY);

    private BetterSearchFabricKeys() {
    }

    public static void register() {
        KeyBindingHelper.registerKeyBinding(OPEN_CONFIG);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // consumeClick devolve true uma vez por pressionada; segurar a tecla nao
            // reabre a tela repetidamente.
            while (OPEN_CONFIG.consumeClick()) {
                if (Screen.hasAltDown()) {
                    BetterSearchClient.openConfigScreen();
                }
            }
        });
    }
}
