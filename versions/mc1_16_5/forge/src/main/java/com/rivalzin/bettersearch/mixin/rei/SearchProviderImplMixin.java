package com.rivalzin.bettersearch.mixin.rei;

import com.rivalzin.bettersearch.client.ReiSearch;
import me.shedaniel.rei.api.client.search.SearchFilter;
import me.shedaniel.rei.impl.client.search.SearchProviderImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * O primeiro dos dois ganchos no REI: decide QUEM passa na busca.
 *
 * <p>O REI monta um {@code SearchFilter} por texto digitado. Aqui esse filtro sai embrulhado: o
 * original continua valendo (quem ele aprovaria continua passando, entao nada se perde) e o
 * embrulho acrescenta o que o Better Search achou - erro de digitacao, outro idioma, id, iniciais,
 * apelido.
 *
 * <p>ATENCAO a assinatura: aqui o {@code createFilter} tem UM argumento so.
 *
 * <p>Da 1.18.2 em diante ele e {@code createFilter(String, InputMethod&lt;?&gt;)} - o REI ganhou
 * suporte a metodo de entrada (pinyin, kana) e passou a receber isso junto. Na linha 6.5, que e a
 * da 1.16.5, o {@code InputMethod} nem existe como classe. Conferido com javap no jar de verdade:
 *
 * <pre>public me.shedaniel.rei.api.client.search.SearchFilter createFilter(java.lang.String);</pre>
 *
 * <p>Copiar a assinatura da 1.18.2 aqui daria o mesmo tipo de falha silenciosa de sempre: o Mixin
 * nao acha o metodo, pula o gancho, e a busca do REI continua sendo a dele sem nada no log.
 *
 * <p>{@code remap = false} nao e opcional: classe de mod nao passa por ofuscacao. {@code require = 0}
 * e o seguro - se o shedaniel renomear o metodo, o Mixin avisa no log, pula o gancho e a busca do
 * REI volta a ser a dele. Nao vira crash.
 */
@Mixin(value = SearchProviderImpl.class, remap = false)
public abstract class SearchProviderImplMixin {

    @Inject(method = "createFilter(Ljava/lang/String;)Lme/shedaniel/rei/api/client/search/SearchFilter;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void bettersearch$wrap(String searchTerm, CallbackInfoReturnable<SearchFilter> cir) {
        SearchFilter original = cir.getReturnValue();
        if (original == null) {
            return;
        }
        SearchFilter wrapped = ReiSearch.wrap(original);
        if (wrapped != null && wrapped != original) {
            cir.setReturnValue(wrapped);
        }
    }
}
