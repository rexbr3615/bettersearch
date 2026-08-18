package com.rivalzin.bettersearch.mixin.rei;

import com.rivalzin.bettersearch.client.ReiSearchBridge;
import me.shedaniel.rei.api.EntryRegistry;
import me.shedaniel.rei.api.EntryStack;
import me.shedaniel.rei.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * O gancho da busca do REI 5.x (a linha da 1.16.5 no Fabric).
 *
 * <p>O alvo e a <b>escrita do campo</b> {@code allStacks}, dentro do {@code updateSearch}. Foi
 * escolhido assim de propósito:
 *
 * <ul>
 *   <li>e o ponto onde a lista ja esta pronta e ainda nao foi usada - o
 *       {@code updateEntriesPosition()} vem logo depois, entao injetar no RETURN seria tarde
 *       demais e as posicoes ja estariam calculadas com a lista errada;</li>
 *   <li>e uma escrita de campo, e nao a n-esima chamada de um metodo. Se o corpo do
 *       {@code updateSearch} mudar de forma, o nome do campo provavelmente continua o mesmo.</li>
 * </ul>
 *
 * <p><b>Um @Redirect de escrita de campo SUBSTITUI a escrita.</b> Quem tem de guardar o valor
 * passa a ser este metodo - por isso o {@link EntryListWidgetAccessor}. Sem ele o campo nunca
 * seria preenchido e a lista do REI apareceria vazia, sem erro nenhum no log.
 *
 * <p>O {@code require = 0} e deliberado: se um dia este ponto sumir, o mod perde a busca dentro
 * do REI e continua funcionando em todo o resto. O contrario derrubaria o jogo na inicializacao,
 * que foi exatamente o que aconteceu na 1.7.1 deste mod.
 *
 * <p>{@code remap = false} porque {@code EntryListWidget} e classe de mod, nao do Minecraft.
 */
@Mixin(value = EntryListWidget.class, remap = false)
public abstract class EntryListWidgetMixin {

    /*
     * A assinatura tem QUATRO parametros e nao tres, e isso nao e escolha de estilo: num
     * @Redirect o Mixin exige que, se voce quiser os parametros do metodo alvo, venham TODOS.
     * O updateSearch e (String, boolean), entao os dois precisam estar aqui - com so o primeiro
     * a injecao falha na inicializacao.
     */
    @Redirect(
            method = "updateSearch(Ljava/lang/String;Z)V",
            at = @At(value = "FIELD",
                    target = "Lme/shedaniel/rei/gui/widget/EntryListWidget;"
                            + "allStacks:Ljava/util/List;",
                    opcode = 181),   // PUTFIELD
            require = 0)
    private void bettersearch$guardar(EntryListWidget self, List<EntryStack> doRei,
                                      String query, boolean ignorarUltima) {
        /*
         * A fonte e o getPreFilteredList do EntryRegistry, e NAO o self.getAllStacks().
         *
         * getAllStacks() aqui devolveria o resultado da busca ANTERIOR - o campo ainda nao foi
         * escrito. Indexar aquilo faria a busca enxergar cada vez menos itens a cada letra
         * digitada, degradando sozinha e sem erro. O getPreFilteredList e a mesma lista de onde
         * o proprio updateSearch parte (conferido no fonte do ramo 5.x do REI).
         */
        List<EntryStack> fonte = EntryRegistry.getInstance().getPreFilteredList();
        List<EntryStack> nossa = ReiSearchBridge.search(query, doRei, fonte);
        ((EntryListWidgetAccessor) self).bettersearch$setAllStacks(nossa == null ? doRei : nossa);
    }
}
