package com.rivalzin.bettersearch.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Faz o <b>Alt + O</b> ganhar do <b>O</b> na 26.2.
 *
 * <p><b>O problema.</b> A 26.2 estreou a tela de amigos e a amarrou no <b>O</b> - a mesma
 * tecla do atalho da configuracao deste mod. Pior: ela nao passa pelo caminho normal de
 * atalhos, e sim pelo {@code handleGlobalKeyPress}, que roda antes de tudo, vale em qualquer
 * tela e compara a tecla com {@code matches(key)} - um metodo que <b>ignora modificadores</b>.
 * Resultado: segurar Alt nao adiantava nada, e o O abria os amigos por cima da nossa tela.
 *
 * <p><b>O conserto.</b> Enquanto o Alt estiver pressionado, este gancho cancela o tratamento
 * global <i>daquela tecla especifica</i> e devolve {@code false}, como se o vanilla nao
 * tivesse nada a ver com ela. O nosso atalho continua no caminho de sempre e abre a tela.
 *
 * <p>Repare no que ele <b>nao</b> faz:
 * <ul>
 *   <li>nao mexe no O sozinho - sem Alt, a tela de amigos abre normalmente;</li>
 *   <li>nao mexe em Alt + qualquer outra tecla - o Alt + tela cheia e o Alt + captura de
 *       tela do vanilla continuam funcionando;</li>
 *   <li>nao chuta que o atalho e o "O": ele pergunta qual tecla esta <b>de fato</b> vinculada
 *       ao mod. Se voce remapear para P, o conserto vai junto - e o O volta a ser so dos
 *       amigos.</li>
 * </ul>
 *
 * <p>So existe na 26.2: {@code handleGlobalKeyPress} e a tela de amigos nao existem em
 * nenhuma versao anterior. Com {@code require = 0}, se a Mojang mexer nesse metodo o mod
 * segue funcionando - o O e que volta a abrir os amigos.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFriendsKeyMixin {

    @Inject(method = "handleGlobalKeyPress", at = @At("HEAD"), cancellable = true, require = 0)
    private void bettersearch$altWinsOverGlobalKeys(InputConstants.Key key,
                                                    boolean controlDown,
                                                    CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (!minecraft.hasAltDown()) {
            return;
        }
        // O mesmo nome que BetterSearchKeys (NeoForge) e BetterSearchFabricKeys registram.
        // Escrito a mao de proposito: e uma classe de mixin, e nada aqui deve virar um campo
        // enxertado dentro do Minecraft.
        KeyMapping openConfig = KeyMapping.get("key.bettersearch.open_config");
        if (openConfig != null && openConfig.matches(key)) {
            cir.setReturnValue(false);
        }
    }
}
