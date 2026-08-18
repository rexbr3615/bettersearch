package com.rivalzin.bettersearch.mixin.emi;

import com.rivalzin.bettersearch.client.EmiSearchBridge;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.screen.EmiScreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

/**
 * O gancho da busca do EMI.
 *
 * <p>{@code @ModifyArg} e nao {@code @Inject}: o que se quer e trocar a lista que vai para o
 * {@code apply}, e nao interromper coisa alguma. Se o mod estiver desligado ou algo falhar, a
 * lista original passa intacta.
 *
 * <h2>A diferenca da 1.18.2</h2>
 *
 * <p>Aqui o {@code run()} chama {@code EmiSearch.apply} <b>uma vez so</b>, com um argumento -
 * conferido no bytecode do jar de verdade. Da 1.19.2 em diante ele chama duas vezes, com dois
 * argumentos, e o gancho precisa da ordinal 1 para nao pegar a chamada de consulta vazia. Por
 * isso ali e {@code ordinal = 1} e aqui e {@code ordinal = 0}.
 *
 * <p>A lista completa tambem vem de outro lugar: nas versoes novas ela e campo do worker; aqui
 * e o estatico {@code EmiScreenManager.getSearchSource()}, que e de onde o proprio
 * {@code run()} a tira.
 */
@Mixin(targets = "dev.emi.emi.search.EmiSearch$SearchWorker", remap = false)
public abstract class SearchWorkerMixin {

    @ModifyArg(method = "run",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Ldev/emi/emi/search/EmiSearch;apply(Ljava/util/List;)V"),
            index = 0, require = 0)
    private List<? extends EmiIngredient> bettersearch$augment(List<? extends EmiIngredient> result) {
        List<? extends EmiIngredient> ours = EmiSearchBridge.search(
                EmiSearchAccessor.bettersearch$query(), result, EmiScreenManager.getSearchSource());
        return ours == null ? result : ours;
    }
}
