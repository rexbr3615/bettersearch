package com.rivalzin.bettersearch.mixin.emi;

import com.rivalzin.bettersearch.client.EmiSearchBridge;
import dev.emi.emi.api.stack.EmiIngredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

/**
 * O unico gancho no EMI.
 *
 * <p>O {@code run()} do worker chama {@code EmiSearch.apply} <b>duas vezes</b>: a primeira quando
 * a busca esta vazia (devolve a lista inteira) e a segunda com o resultado de verdade. O
 * {@code ordinal = 1} pega a segunda, que e a unica que interessa.
 *
 * <p>{@code ModifyArg} em vez de {@code Inject}: eu nao quero cancelar nada, quero trocar o
 * argumento que ja vai ser guardado em {@code EmiSearch.stacks}. E o menor gancho possivel.
 *
 * <p>Isto roda na thread de busca do EMI, e nao na do jogo. E de graca para os quadros: a lista
 * antiga continua na tela enquanto a nova nao chega, que e como o EMI ja funciona sozinho.
 */
@Mixin(targets = "dev.emi.emi.search.EmiSearch$SearchWorker", remap = false)
public abstract class SearchWorkerMixin {
    @Shadow
    @Final
    private String query;

    @Shadow
    @Final
    private List<? extends EmiIngredient> source;

    @ModifyArg(
            method = "run",
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Ldev/emi/emi/search/EmiSearch;apply("
                            + "Ldev/emi/emi/search/EmiSearch$SearchWorker;Ljava/util/List;)V"),
            index = 1,
            require = 0)
    private List<? extends EmiIngredient> bettersearch$augment(List<? extends EmiIngredient> result) {
        List<? extends EmiIngredient> ours = EmiSearchBridge.search(query, result, source);
        return ours == null ? result : ours;
    }
}
