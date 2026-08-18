package com.rivalzin.bettersearch.mixin.jei;

import com.rivalzin.bettersearch.client.JeiSearch;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.search.IElementSearch;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.stream.Stream;

/**
 * O unico gancho no JEI.
 *
 * <p>{@code remap = false} nao e opcional: classe de mod nao passa por ofuscacao, e sem isso o
 * refmap da 1.20.1 tentaria traduzir estes nomes para SRG e nao acharia nada.
 *
 * <p>{@code require = 0} e o seguro. Se um dia o mezz renomear o metodo, o Mixin loga um aviso,
 * pula o gancho e a busca do JEI volta a ser a dele. Nao vira crash.
 *
 * <p>Continua no RETURN e nao no HEAD, e agora por um motivo diferente do da 1.5. La era porque o
 * mod so entrava quando o JEI voltava vazio. Aqui e para <b>somar</b>: o JEI procura em campos que
 * o mod nao guarda (tag, cor, aba do criativo, tooltip de qualquer item), entao o resultado dele
 * entra na lista final junto com o nosso. Quem manda na ordem e o {@link JeiSearch}.
 */
@Mixin(value = IngredientFilter.class, remap = false)
public abstract class IngredientFilterMixin {
    @Shadow
    @Final
    private IIngredientManager ingredientManager;

    @Shadow
    private IElementSearch elementSearch;

    @Inject(method = "getIngredientListUncached", at = @At("RETURN"), cancellable = true, require = 0)
    private void bettersearch$search(String filterText,
                                     CallbackInfoReturnable<Stream<ITypedIngredient<?>>> cir) {
        Stream<ITypedIngredient<?>> original = cir.getReturnValue();
        if (original == null) {
            return;
        }
        // Stream e de uso unico: depois de olhar para ele eu SEMPRE tenho que devolver um novo,
        // mesmo quando nao mudo nada. Materializar nao custa: quem chamou ja ia fazer toList().
        List<ITypedIngredient<?>> fromJei = original.toList();
        List<ITypedIngredient<?>> ours = JeiSearch.search(filterText, fromJei,
                elementSearch.getAllIngredients(), ingredientManager,
                (IngredientFilter) (Object) this);
        cir.setReturnValue((ours == null ? fromJei : ours).stream());
    }
}
