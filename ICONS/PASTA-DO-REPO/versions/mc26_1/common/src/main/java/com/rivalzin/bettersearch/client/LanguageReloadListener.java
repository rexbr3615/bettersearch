package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
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
        /*
         * Ultima linha de defesa, e ela existe por um motivo concreto.
         *
         * Este metodo roda DENTRO da carga de recursos do jogo. O que escapar daqui nao
         * derruba o mod: derruba a carga inteira - e, se isso acontece na inicializacao, o
         * jogo nem chega ao menu principal. Foi exatamente assim que um pacote de recursos
         * mal-comportado de outro mod tirou o Minecraft do ar em 26.2, 1.21.10 e 1.21.1.
         *
         * A causa daquele caso ja esta corrigida no LanguageTable, mas a regra fica: seja o
         * que for que der errado aqui, o jogo abre. O mod so fica sem busca entre idiomas.
         */
        try {
            return LanguageTable.load(resourceManager, BetterSearchClient.settings());
        } catch (Throwable t) {
            BetterSearch.LOGGER.error("[{}] falha ao ler os idiomas - a busca entre idiomas fica"
                    + " desligada nesta sessao, mas o resto do mod continua funcionando",
                    BetterSearch.MOD_NAME, t);
            return LanguageTable.EMPTY;
        }
    }

    @Override
    protected void apply(LanguageTable table, ResourceManager resourceManager, ProfilerFiller profiler) {
        BetterSearchClient.onLanguagesLoaded(table);
    }
}
