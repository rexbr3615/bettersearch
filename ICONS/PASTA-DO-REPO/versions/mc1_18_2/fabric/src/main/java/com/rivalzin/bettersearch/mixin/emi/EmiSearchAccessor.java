package com.rivalzin.bettersearch.mixin.emi;

import dev.emi.emi.search.EmiSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Le o texto que o EMI esta buscando.
 *
 * <p>Na 1.18.2 o {@code SearchWorker} nao tem campo nenhum - conferido no bytecode. A consulta
 * mora num {@code private static String query} da propria {@code EmiSearch}, e o worker so a le.
 * Como o bytecode compara o texto guardado com esse campo <b>imediatamente antes</b> de aplicar
 * o resultado, ler o campo neste instante devolve exatamente a consulta que esta sendo aplicada.
 *
 * <p>Da 1.19.2 em diante isto some: la o worker recebe {@code query} e {@code source} pelo
 * construtor, e o gancho pega os dois por {@code @Shadow} sem precisar deste acessor.
 */
@Mixin(value = EmiSearch.class, remap = false)
public interface EmiSearchAccessor {

    @Accessor("query")
    static String bettersearch$query() {
        throw new AssertionError("substituido pelo Mixin");
    }
}
