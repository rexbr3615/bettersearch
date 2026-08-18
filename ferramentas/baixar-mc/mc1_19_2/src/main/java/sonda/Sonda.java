package sonda;

import net.minecraft.client.Minecraft;

/**
 * Classe de sonda. Nao faz nada e nao entra em jar nenhum.
 *
 * <p>Ela toca em {@code Minecraft} de proposito: se este arquivo compilar, entao o Gradle
 * baixou e desofuscou o Minecraft 1.19.2 com sucesso e o ModDevGradle legacy funciona nesta
 * versao. Se nao compilar, descobrimos isso agora - e nao no meio do port.
 */
public final class Sonda {

    private Sonda() {
    }

    public static boolean deuCerto() {
        return Minecraft.getInstance() != null;
    }
}
