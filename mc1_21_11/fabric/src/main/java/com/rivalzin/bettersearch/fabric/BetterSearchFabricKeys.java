package com.rivalzin.bettersearch.fabric;

import com.rivalzin.bettersearch.client.BetterSearchClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Atalho <b>Alt + O</b> no Fabric.
 *
 * <p>O Fabric nao tem API de tecla modificadora, entao o Alt e conferido na mao no tick e o
 * rotulo da tela de Controles e ajustado pelo {@link AltKeyMapping}. Resultado: mesmo atalho,
 * mesmo texto "Alt + O" que o NeoForge mostra.
 */
public final class BetterSearchFabricKeys {

    /*
     * 1.21.9: a categoria virou um objeto com id proprio (veja o gemeo no NeoForge). Aqui ela
     * e criada pela via do vanilla, que alem de criar tambem a coloca na ordem de exibicao da
     * tela de Controles. O @SuppressWarnings e por causa do NeoForge, que marca este metodo
     * como obsoleto em favor de um evento proprio dele - evento que nao existe no Fabric.
     */
    @SuppressWarnings("deprecation")
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("bettersearch", "main"));

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
                // 1.21.9: Screen.hasAltDown() saiu; quem responde agora e o proprio jogo.
                if (client.hasAltDown()) {
                    BetterSearchClient.openConfigScreen();
                }
            }
        });
    }
}
