package com.rivalzin.bettersearch.mixin.rei;

import com.rivalzin.bettersearch.client.ReiSearch;
import me.shedaniel.rei.api.client.search.SearchFilter;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.impl.client.search.AsyncSearchManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * O segundo gancho no REI: decide EM QUE ORDEM aparece.
 *
 * <p>Aqui esta a diferenca de fundo entre esta versao e a da 1.18.2. La o gancho mora no
 * {@code EntryListSearchManager.copyAndOrder}. Na linha 6.5 essa classe <b>nao existe</b>, e a
 * palavra "copyAndOrder" nao aparece em classe nenhuma do jar - procurei nas 4 mil. Quem entrega
 * a lista ja filtrada e o {@link AsyncSearchManager#get()}.
 *
 * <p>E ele cobre os dois caminhos, nao so um: o {@code getAsync(Consumer)}, que e o que a tela do
 * REI chama, e {@code CompletableFuture.supplyAsync(this::get)}. Isso nao foi deduzido do nome -
 * esta escrito no bootstrap do invokedynamic do proprio metodo:
 *
 * <pre>#169 REF_invokeVirtual AsyncSearchManager.get:()Ljava/util/List;</pre>
 *
 * <p>Sai mais simples que na 1.18.2 de quebra: o filtro que vale agora e um campo <i>desta</i>
 * classe, entao basta um {@code @Shadow} local - nao e preciso alcancar campo privado de outro
 * objeto. E a lista ja vem como {@code List&lt;EntryStack&lt;?&gt;&gt;}, sem o
 * {@code HashedEntryStackWrapper} no meio, entao o "desembrulhar" e a identidade.
 *
 * <p>So age com "ordenar por relevancia" ligado no menu. Desligado, {@code reorder} devolve
 * {@code null} e a ordem escolhida na configuracao do REI fica exatamente como estava.
 */
@Mixin(value = AsyncSearchManager.class, remap = false)
public abstract class AsyncSearchManagerMixin {

    @Shadow
    private SearchFilter filter;

    @Inject(method = "get()Ljava/util/List;", at = @At("RETURN"), cancellable = true, require = 0)
    private void bettersearch$order(CallbackInfoReturnable<List<EntryStack<?>>> cir) {
        List<EntryStack<?>> found = cir.getReturnValue();
        if (found == null || filter == null) {
            return;
        }
        /*
         * A pontuacao e pedida ao filtro que esta valendo AGORA, e nao a um campo global nosso.
         * Sem isso, uma consulta poderia ser ordenada com a pontuacao da anterior no meio da
         * digitacao - e aqui o risco e maior que na 1.18.2, porque este metodo roda numa thread
         * de trabalho (supplyAsync), podendo haver duas buscas no ar ao mesmo tempo.
         */
        List<EntryStack<?>> ours = ReiSearch.reorder(filter, found, entry -> entry);
        if (ours != null) {
            cir.setReturnValue(ours);
        }
    }
}
