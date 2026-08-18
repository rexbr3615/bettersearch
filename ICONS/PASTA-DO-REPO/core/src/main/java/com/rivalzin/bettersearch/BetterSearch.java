package com.rivalzin.bettersearch;

/** Constantes compartilhadas. Nao depende de loader nenhum. */
public final class BetterSearch {

    public static final String MOD_ID = "bettersearch";
    public static final String MOD_NAME = "Better Search";

    /*
     * Antes daqui esta linha era LoggerFactory.getLogger(MOD_NAME), do slf4j.
     *
     * Funcionava em oito dos nove alvos e derrubava o Forge 1.16.5 com
     * NoClassDefFoundError: org/slf4j/LoggerFactory - naquele classpath ha log4j-api,
     * log4j-core e log4j-slf4j18-impl, mas nao ha slf4j-api. E como o campo e estatico, o erro
     * estourava no <clinit> desta classe: o mod nem chegava a carregar.
     *
     * O Registro procura o que existir (log4j, depois slf4j, depois System.out) sem depender
     * de nenhum dos dois em compilacao. Veja a explicacao inteira la.
     */
    public static final Registro LOGGER = Registro.criar(MOD_NAME);

    private BetterSearch() {
    }
}
