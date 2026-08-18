package com.rivalzin.bettersearch.client;

import net.minecraft.client.util.SearchTree;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Uma arvore de busca do jogo, embrulhada - o gancho da 1.12.2 inteiro, SEM Mixin.
 *
 * <p>Nas outras versoes o gancho e um {@code @Redirect} na chamada {@code search}. Aqui nao
 * precisa: o {@code SearchTreeManager.register} e publico e aceita trocar a arvore registrada.
 * Este embrulho entra no lugar da vanilla, responde {@code search} com o nosso resultado e
 * delega TODO o resto para a arvore original - inclusive {@code recalculate}, que o gerente
 * chama a cada F3+T, e {@code add}, que o jogo usa para popular.
 *
 * <p>Generica porque o jogo tem DUAS arvores com o mesmo contrato: a de itens (criativo) e a
 * de receitas (livro de receitas). O mesmo embrulho serve as duas; muda so quem responde.
 *
 * <p>Isso importa porque o Forge 1.12.2 nao traz o Mixin embutido: o caminho de mixin exigiria
 * embarca-lo no jar (shadow + TweakClass), a parte mais fragil do modding dessa era. Um
 * register publico e um problema a menos - conferido com javap.
 *
 * <p>As duas funcoes passadas ao construtor da superclasse nunca sao usadas: os indices
 * internos DESTA instancia ficam vazios de proposito, porque add/recalculate vao direto na
 * arvore embrulhada.
 */
public final class ArvoreEnvolvida<T> extends SearchTree<T> {

    private final SearchTree<T> vanilla;
    private final Function<String, List<T>> nossaBusca;

    public ArvoreEnvolvida(SearchTree<T> vanilla, Function<String, List<T>> nossaBusca) {
        super(valor -> Collections.<String>emptyList(),
                valor -> Collections.<ResourceLocation>emptyList());
        this.vanilla = vanilla;
        this.nossaBusca = nossaBusca;
    }

    @Override
    public void recalculate() {
        vanilla.recalculate();
    }

    @Override
    public void add(T valor) {
        vanilla.add(valor);
    }

    @Override
    public List<T> search(String consulta) {
        List<T> nossa = nossaBusca.apply(consulta);
        return nossa != null ? nossa : vanilla.search(consulta);
    }
}
