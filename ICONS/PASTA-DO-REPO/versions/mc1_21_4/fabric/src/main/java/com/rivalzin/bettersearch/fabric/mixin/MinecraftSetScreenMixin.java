package com.rivalzin.bettersearch.fabric.mixin;

import com.rivalzin.bettersearch.fabric.BetterSearchFabricKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BARREIRA 2 do Alt+O no Fabric: com o Alt fisicamente pressionado, a tela de escolha de
 * shaders do Iris fica PROIBIDA de abrir - o setScreen dela e cancelado na porta.
 *
 * <p>So no Fabric, porque so aqui e preciso: nao ha sistema de modificadores, e o mapa de
 * teclas do vanilla tem UM dono por tecla fisica - com o Iris no "O", o clique nunca chega
 * ao nosso atalho e a tela dele abre por cima. No NeoForge o KeyModifier.ALT ja resolve.
 *
 * <p>A decisao mora em {@link BetterSearchFabricKeys#deveBarrar}: exige Alt pressionado,
 * nosso atalho no padrao (rebindou, a barreira desliga) e o nome da classe da tela vindo do
 * Iris (net.irisshaders / net.coderbot.iris) contendo ShaderPackScreen - por NOME, sem
 * dependencia de compilacao, entao sem Iris instalado este codigo nunca da match em nada.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftSetScreenMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void bettersearch$barrarShaderPackComAlt(Screen screen, CallbackInfo ci) {
        if (BetterSearchFabricKeys.deveBarrar(screen)) {
            ci.cancel();
        }
    }
}
