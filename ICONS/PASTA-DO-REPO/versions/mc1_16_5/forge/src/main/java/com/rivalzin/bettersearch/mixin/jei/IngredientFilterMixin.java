package com.rivalzin.bettersearch.mixin.jei;

import com.rivalzin.bettersearch.client.JeiSearch;
import mezz.jei.ingredients.IIngredientListElementInfo;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.search.IElementSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Busca do JEI 7.8 (a linha da 1.16.5).
 *
 * <p>O alvo e o mesmo da 1.18.2 - {@code getIngredientListUncached}, onde o JEI resolve o texto
 * digitado antes de guardar em cache. Trocar so aqui deixa o resto do JEI intacto: modo de
 * edicao, ordenacao, itens escondidos por outro mod, tudo continua sendo trabalho dele.
 *
 * <p><b>Tres diferencas para a 1.18.2, todas lidas com javap e nenhuma deduzida:</b>
 *
 * <ul>
 *   <li>o pacote e {@code mezz.jei.ingredients}, e nao {@code mezz.jei.common.ingredients}: a
 *       separacao em "common" so aconteceu depois;</li>
 *   <li>o metodo devolve {@code List<IIngredientListElementInfo<?>>}, e nao
 *       {@code List<ITypedIngredient<?>>} - o {@code ITypedIngredient} nem existe nesta linha;</li>
 *   <li>e a que mais importa aqui: o campo {@code elementSearch} <b>nao e final</b>.</li>
 * </ul>
 *
 * <p>Sobre o {@code @Final}: na 1.18.2 o campo e {@code private final} e o mixin de la usa
 * {@code @Shadow @Final}. Aqui o javap mostra
 *
 * <pre>private mezz.jei.search.IElementSearch elementSearch;</pre>
 *
 * <p>sem o final. Anotar {@code @Final} num campo que nao e final faz o Mixin recusar a classe
 * inteira na inicializacao - e, com {@code required: false} no arquivo de configuracao, isso nao
 * viraria crash: viraria gancho silenciosamente ausente, que e o pior desfecho possivel.
 *
 * <p>{@code remap = false} porque classe de mod nao passa por ofuscacao. {@code require = 0}
 * porque sem o JEI instalado nao ha nada em que injetar.
 */
@Mixin(value = IngredientFilter.class, remap = false)
public abstract class IngredientFilterMixin {

    @Shadow
    private IElementSearch elementSearch;

    @Inject(method = "getIngredientListUncached(Ljava/lang/String;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void bettersearch$search(String filterText,
                                     CallbackInfoReturnable<List<IIngredientListElementInfo<?>>> cir) {
        List<IIngredientListElementInfo<?>> fromJei = cir.getReturnValue();
        if (fromJei == null) {
            return;
        }
        if (elementSearch == null) {
            return;
        }
        List<IIngredientListElementInfo<?>> ours = JeiSearch.search(filterText, fromJei,
                elementSearch.getAllIngredients(), (IngredientFilter) (Object) this);
        if (ours != null) {
            cir.setReturnValue(ours);
        }
    }
}
