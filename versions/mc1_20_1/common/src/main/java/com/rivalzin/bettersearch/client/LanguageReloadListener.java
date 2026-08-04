package com.rivalzin.bettersearch.client;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Recarrega a tabela de idiomas junto com os recursos do jogo (F3+T, troca de idioma,
 * ativar/desativar resource pack ou mod).
 *
 * <p>A leitura pesada acontece em {@link #prepare}, que o Minecraft executa fora da thread
 * principal - o mesmo lugar onde o jogo carrega modelos e texturas.
 */
public class LanguageReloadListener extends SimplePreparableReloadListener<LanguageTable> {

    @Override
    protected LanguageTable prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return LanguageTable.load(resourceManager, BetterSearchClient.settings());
    }

    @Override
    protected void apply(LanguageTable table, ResourceManager resourceManager, ProfilerFiller profiler) {
        BetterSearchClient.onLanguagesLoaded(table);
    }
}
