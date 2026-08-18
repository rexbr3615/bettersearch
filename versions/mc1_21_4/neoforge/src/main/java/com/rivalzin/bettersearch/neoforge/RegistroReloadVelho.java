package com.rivalzin.bettersearch.neoforge;

import com.rivalzin.bettersearch.client.LanguageReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/**
 * Metade VELHA da linha 21.4 (builds de 2024/2025): o evento com o nome original, sem id.
 *
 * <p>Compila contra o ESBOCO em src/neoApi (o 21.4.157 removeu a classe do jar); em jogo a
 * classe real do build velho e usada, por nome. So carregada apos o Class.forName do
 * BetterSearchNeoForge confirmar que o evento existe neste build - nos novos, nunca carrega.
 */
public final class RegistroReloadVelho {

    private RegistroReloadVelho() {
    }

    /** Chamado por reflexao pelo BetterSearchNeoForge - se mudar o nome, mude la tambem. */
    public static void instalar(IEventBus modEventBus) {
        // Referencia de metodo, nao lambda - veja o comentario no RegistroReloadNovo.
        modEventBus.addListener(RegistroReloadVelho::aoRegistrar);
        com.rivalzin.bettersearch.BetterSearch.LOGGER.info(
                "[{}] listener de reload registrado (evento velho, builds 2024/2025)",
                com.rivalzin.bettersearch.BetterSearch.MOD_NAME);
    }

    private static void aoRegistrar(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new LanguageReloadListener());
    }
}
