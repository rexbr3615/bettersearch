package com.rivalzin.bettersearch.mixin.rei;

import com.rivalzin.bettersearch.client.ReiSearch;
import me.shedaniel.rei.impl.client.gui.widget.entrylist.EntryListSearchManager;
import me.shedaniel.rei.impl.client.search.AsyncSearchManager;
import me.shedaniel.rei.impl.common.util.HashedEntryStackWrapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * O segundo gancho no REI: decide EM QUE ORDEM aparece.
 *
 * <p>{@code copyAndOrder} e onde o REI aplica a ordenacao dele (registro, nome, grupos) na lista
 * ja filtrada. A pontuacao de cada entrada foi calculada na passada do filtro, entao aqui e so
 * reaproveitar - sem buscar de novo.
 *
 * <p>A pontuacao e pedida ao filtro que esta valendo AGORA, e nao a um campo global. Sem isso, uma
 * consulta poderia ser ordenada com a pontuacao da anterior no meio da digitacao.
 *
 * <p>So age com "ordenar por relevancia" ligado no menu. Desligado, {@code reorder} devolve
 * {@code null} e a ordem escolhida na configuracao do REI fica exatamente como estava.
 */
@Mixin(value = EntryListSearchManager.class, remap = false)
public abstract class EntryListSearchManagerMixin {

    @Shadow
    @Final
    private AsyncSearchManager searchManager;

    @Inject(method = "copyAndOrder", at = @At("RETURN"), cancellable = true, require = 0)
    private void bettersearch$order(List<HashedEntryStackWrapper> input,
                                    CallbackInfoReturnable<List<HashedEntryStackWrapper>> cir) {
        List<HashedEntryStackWrapper> ordered = cir.getReturnValue();
        if (ordered == null || searchManager == null) {
            return;
        }
        List<HashedEntryStackWrapper> ours = ReiSearch.reorder(
                searchManager.filter, ordered, HashedEntryStackWrapper::unwrap);
        if (ours != null) {
            cir.setReturnValue(ours);
        }
    }
}
