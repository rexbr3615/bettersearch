package com.rivalzin.bettersearch.mixin.jei;

import com.rivalzin.bettersearch.client.JeiSearch;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.ingredients.IngredientFilter;
import mezz.jei.common.search.IElementSearch;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * O unico gancho no JEI.
 *
 * <p>{@code remap = false} nao e opcional: classe de mod nao passa por ofuscacao, e sem isso o
 * refmap tentaria traduzir estes nomes para SRG e nao acharia nada.
 *
 * <p>{@code require = 0} e o seguro. Se um dia o mezz renomear o metodo, o Mixin loga um aviso,
 * pula o gancho e a busca do JEI volta a ser a dele. Nao vira crash.
 *
 * <h2>As tres diferencas da 1.18.2</h2>
 *
 * <p>Esta e a versao do JEI mais distante das outras seis que o mod cobre, e as diferencas nao
 * sao cosmeticas:
 *
 * <ul>
 *   <li>a classe mora em {@code mezz.jei.common.ingredients}, e nao em {@code mezz.jei.gui};</li>
 *   <li>{@code getIngredientListUncached} devolve uma <b>lista</b>, e nao um {@code Stream} -
 *       o que na verdade simplifica: nao ha stream de uso unico para materializar;</li>
 *   <li>a busca por elemento entrega {@code IListElementInfo}, que ja carrega nome e id
 *       prontos. Nas versoes novas isso virou {@code IListElement}, que so tem o ingrediente.</li>
 * </ul>
 */
@Mixin(value = IngredientFilter.class, remap = false)
public abstract class IngredientFilterMixin {

    @Shadow
    @Final
    private IElementSearch elementSearch;

    @Inject(method = "getIngredientListUncached", at = @At("RETURN"), cancellable = true, require = 0)
    private void bettersearch$search(String filterText,
                                     CallbackInfoReturnable<List<ITypedIngredient<?>>> cir) {
        List<ITypedIngredient<?>> fromJei = cir.getReturnValue();
        if (fromJei == null) {
            return;
        }
        List<ITypedIngredient<?>> ours = JeiSearch.search(filterText, fromJei,
                elementSearch.getAllIngredients(), (IngredientFilter) (Object) this);
        if (ours != null) {
            cir.setReturnValue(ours);
        }
    }
}
