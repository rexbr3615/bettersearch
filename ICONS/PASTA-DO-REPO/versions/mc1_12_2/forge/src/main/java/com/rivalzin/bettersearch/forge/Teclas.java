package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.client.gui.BetterSearchConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

/**
 * Alt+O abre as opcoes - o mesmo atalho das outras versoes.
 *
 * <p>Aqui o teclado e o do LWJGL 2 ({@code Keyboard.KEY_O}, nao GLFW), e o construtor com
 * {@code KeyModifier} ja existe neste Forge - conferido com javap. O evento so dispara fora
 * de telas (KeyInputEvent e assim), que e exatamente a regra das outras versoes: o atalho so
 * age quando nenhuma tela esta aberta.
 */
public final class Teclas {

    private final KeyBinding abrir = new KeyBinding("key.bettersearch.open",
            KeyConflictContext.IN_GAME, KeyModifier.ALT, Keyboard.KEY_O,
            "key.categories.bettersearch");

    public Teclas() {
        ClientRegistry.registerKeyBinding(abrir);
    }

    @SubscribeEvent
    public void aoTeclar(InputEvent.KeyInputEvent evento) {
        if (abrir.isPressed() || comboNaMarra()) {
            Minecraft.getMinecraft().displayGuiScreen(new BetterSearchConfigScreen(null));
        }
    }

    /**
     * Alt+O detectado na unha, alem do caminho oficial.
     *
     * <p>O sistema de modificadores do Forge desta era e notoriamente cheio de manha com ALT
     * (o teste em campo confirmou: rebindar para O puro funcionava, ALT+O nao). Aqui a
     * combinacao e lida direto do teclado do LWJGL: o evento atual tem que SER o O descendo
     * (getEventKey + getEventKeyState - borda de subida, sem repeticao) com um dos dois Alt
     * segurados. So vale enquanto o atalho continua no padrao: se voce rebindou, o caminho
     * oficial e o unico - senao a tecla antiga continuaria abrindo o menu por fora.
     */
    private boolean comboNaMarra() {
        if (abrir.getKeyCode() != Keyboard.KEY_O || abrir.getKeyModifier() != KeyModifier.ALT) {
            return false; // rebindado: respeita so o oficial
        }
        return Keyboard.getEventKey() == Keyboard.KEY_O
                && Keyboard.getEventKeyState()
                && (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU));
    }
}
