package com.rivalzin.bettersearch.fabric;

import com.rivalzin.bettersearch.client.gui.BetterSearchConfigScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.mojang.blaze3d.platform.InputConstants;
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

    /** A tela que estava aberta quando o tique ANTERIOR terminou (nulo = jogo aberto). */
    private static Screen telaAoFimDoTiqueAnterior;
    private static boolean abrirNoProximoTique;
    private static boolean oPressionadoAntes;

    private BetterSearchFabricKeys() {
    }

    /**
     * BARREIRA 2 (usada pelo MinecraftSetScreenMixin): com Alt pressionado e o nosso atalho
     * no padrao, a tela de escolha de shaders do Iris nao abre. Por NOME de classe, sem
     * dependencia do Iris - sem ele instalado, nunca da match.
     */
    public static boolean deveBarrar(Screen tela) {
        if (tela == null || !Screen.hasAltDown() || !OPEN_CONFIG.isDefault()) {
            return false;
        }
        String nome = tela.getClass().getName();
        return (nome.startsWith("net.irisshaders.") || nome.startsWith("net.coderbot.iris."))
                && nome.contains("ShaderPackScreen");
    }

    public static void register() {
        KeyBindingHelper.registerKeyBinding(OPEN_CONFIG);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            /*
             * TRES BARREIRAS, porque o campo provou que uma nao basta. O mapa de teclas do
             * vanilla tem UM dono por tecla fisica: com o Iris tambem no "O", o clique nem
             * chega ao nosso KeyMapping - consumeClick fica surdo e qualquer logica em cima
             * dele nunca roda. Dai:
             *
             *  1. a MARRA CRUA (a da 1.12.2, edicao GLFW): o teclado e lido direto, com
             *     borda de subida propria - surdez no mapa de teclas nao nos afeta. So
             *     enquanto o atalho esta no padrao; rebindou, vale so o caminho oficial.
             *  2. o MinecraftSetScreenMixin: com Alt pressionado, a ShaderPackScreen do
             *     Iris e barrada na porta do setScreen - o "O puro" dele fica mudo
             *     enquanto o Alt estiver no chao.
             *  3. a abertura ADIADA: se ainda assim alguem ocupar a vaga no mesmo tique,
             *     o tique seguinte poe a nossa tela por cima.
             *
             * A regra do projeto segue intacta: o atalho so vale com o JOGO aberto (a
             * decisao olha a tela do fim do tique ANTERIOR - bau aberto ignora Alt+O).
             */
            if (abrirNoProximoTique) {
                abrirNoProximoTique = false;
                if (!(client.screen instanceof BetterSearchConfigScreen)) {
                    client.setScreen(new BetterSearchConfigScreen(null));
                }
            }

            boolean oAgora = InputConstants.isKeyDown(client.getWindow().getWindow(), GLFW.GLFW_KEY_O);
            if (oAgora && !oPressionadoAntes && OPEN_CONFIG.isDefault()
                    && Screen.hasAltDown() && telaAoFimDoTiqueAnterior == null) {
                if (!(client.screen instanceof BetterSearchConfigScreen)) {
                    client.setScreen(new BetterSearchConfigScreen(null));
                }
                abrirNoProximoTique = true; // barreira 3 armada para o mesmo aperto
            }
            oPressionadoAntes = oAgora;

            // Caminho oficial (vale sozinho quando o atalho foi rebindado ou nao ha conflito).
            // consumeClick devolve true uma vez por pressionada.
            while (OPEN_CONFIG.consumeClick()) {
                if (Screen.hasAltDown() && telaAoFimDoTiqueAnterior == null) {
                    abrirNoProximoTique = true;
                }
            }
            telaAoFimDoTiqueAnterior = client.screen;
        });
    }
}
