package com.rivalzin.bettersearch.neoforge;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.LanguageReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

/**
 * Metade NOVA da linha 21.4 (21.4.157 em diante): o evento renomeado, com nome por listener.
 *
 * <p>Classe separada de proposito: ela cita o AddClientReloadListenersEvent pelo tipo, e nos
 * builds VELHOS da linha essa classe nem existe - carregar isto la seria NoClassDefFoundError.
 * Quem decide se esta classe carrega e o Class.forName do BetterSearchNeoForge, que sonda o
 * evento primeiro. O mesmo desenho de isolamento dos ganchos de visualizador.
 */
public final class RegistroReloadNovo {

    private RegistroReloadNovo() {
    }

    /** Chamado por reflexao pelo BetterSearchNeoForge - se mudar o nome, mude la tambem. */
    public static void instalar(IEventBus modEventBus) {
        /*
         * REFERENCIA DE METODO, nao lambda, de proposito: e assim que TODOS os listeners
         * deste projeto se registram, e e o caso que o resolvedor de tipos do bus le com
         * seguranca. Lambda tipada e exatamente onde ele pode falhar em descobrir o evento.
         */
        modEventBus.addListener(RegistroReloadNovo::aoRegistrar);
        BetterSearch.LOGGER.info("[{}] listener de reload registrado (evento novo, 21.4.157+)",
                BetterSearch.MOD_NAME);
    }

    private static void aoRegistrar(AddClientReloadListenersEvent event) {
        event.addListener(ResourceLocation.fromNamespaceAndPath(BetterSearch.MOD_ID, "languages"),
                new LanguageReloadListener());
    }
}
