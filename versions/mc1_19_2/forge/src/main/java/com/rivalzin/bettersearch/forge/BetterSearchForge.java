package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.BetterSearch;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/**
 * Ponto de entrada do Forge 1.20.1.
 *
 * <p>Na 1.20.1 a anotacao {@code @Mod} ainda nao aceita o parametro {@code dist}, entao nao da
 * para dizer "este mod e so de cliente" ali. O jeito oficial e este: o construtor nao toca em
 * nada de cliente e delega para {@link ForgeClientBootstrap}, uma classe separada que so e
 * carregada quando o jogo realmente e um cliente. Em um servidor dedicado, ela nunca entra na
 * memoria - e o mod fica inerte em vez de quebrar.
 *
 * <p>O mesmo jar roda no Forge e no NeoForge da 1.20.1: naquela versao o NeoForge ainda era um
 * fork recem-nascido do Forge 47, com os mesmos pacotes {@code net.minecraftforge.*} e os
 * mesmos nomes SRG em tempo de execucao.
 */
@Mod(BetterSearch.MOD_ID)
public final class BetterSearchForge {

    public BetterSearchForge() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ForgeClientBootstrap::init);
    }
}
