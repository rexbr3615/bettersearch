package com.rivalzin.bettersearch.forge;

import com.mojang.blaze3d.platform.InputConstants;
import com.rivalzin.bettersearch.client.BetterSearchClient;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Atalho de teclado para abrir a configuracao: <b>Alt + O</b>.
 *
 * <p>Como no NeoForge, o Forge tem API de tecla modificadora ({@link KeyModifier}), entao o
 * "Alt +" aparece sozinho em <b>Opcoes &gt; Controles</b> e pode ser remapeado por la. No
 * Fabric isso precisou ser feito na mao ({@code AltKeyMapping}); aqui nao.
 *
 * <p>O contexto {@link KeyConflictContext#IN_GAME} evita que a tecla dispare enquanto voce
 * digita no chat ou esta em outra tela.
 */
public final class BetterSearchForgeKeys {

    public static final String CATEGORY = "key.categories.bettersearch";

    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.bettersearch.open_config",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY);

    private BetterSearchForgeKeys() {
    }

    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG);
    }

    static void onClientTick(TickEvent.ClientTickEvent event) {
        // Diferenca da 1.20.1: o evento de tick tem duas fases e chega duas vezes por tick.
        // Só a de fim interessa - na de inicio o estado do teclado ainda nao foi atualizado,
        // e sem esta guarda a tela abriria e fecharia no mesmo tique.
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // consumeClick devolve true uma vez por pressionada, entao segurar a tecla nao
        // reabre a tela repetidamente.
        while (OPEN_CONFIG.consumeClick()) {
            BetterSearchClient.openConfigScreen();
        }
    }
}
