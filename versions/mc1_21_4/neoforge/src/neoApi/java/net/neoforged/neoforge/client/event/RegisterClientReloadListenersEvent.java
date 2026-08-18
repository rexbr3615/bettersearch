package net.neoforged.neoforge.client.event;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.bus.api.Event;

/**
 * ESBOCO DE COMPILACAO - compila junto do main (precisa dos tipos reais do Minecraft no
 * classpath) e e BARRADO na porta do jar pelo exclude com trava (veja o build.gradle).
 *
 * <p>A linha 21.4 do NeoForge renomeou o evento de reload NO MEIO dela: os builds de
 * 2024/2025 tem este RegisterClientReloadListenersEvent, o 21.4.157 de 2026 tem so o
 * AddClientReloadListenersEvent - e o velho foi REMOVIDO, entao nao ha jar unico contra o
 * qual compilar os dois caminhos. Este esboco existe para o caminho velho compilar; em jogo
 * a classe REAL e usada (ligacao por nome), e a RegistroReloadVelho so e carregada depois de
 * um Class.forName confirmar que ela existe naquele build.
 *
 * <p>A forma daqui nao foi adivinhada: e a MESMA classe que o modulo mc1_21_1 compila contra
 * o NeoForge 21.1.233 de verdade e que roda em campo - assinatura identica
 * (registerReloadListener(PreparableReloadListener)).
 */
public class RegisterClientReloadListenersEvent extends Event {

    public void registerReloadListener(PreparableReloadListener listener) {
        throw new UnsupportedOperationException("esboco de compilacao");
    }
}
