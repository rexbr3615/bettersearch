package com.rivalzin.bettersearch.mixin;

import com.rivalzin.bettersearch.client.RecipeSearch;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.searchtree.MutableSearchTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Busca do livro de receitas.
 *
 * <p>Dois ganchos, cada um com um papel:
 *
 * <ul>
 *   <li>em {@code tick} pedimos para o indice ser montado. A tela do inventario chama isto
 *       todo tique, entao quando voce clica na lupa o indice ja esta pronto. Sem isso a
 *       busca so passaria a funcionar da terceira letra em diante - e, se voce parasse de
 *       digitar antes disso, nunca, porque o livro so refaz a busca quando o texto muda;</li>
 *   <li>em {@code updateCollections} trocamos apenas a pergunta feita a arvore de busca
 *       vanilla. Tudo o que o metodo faz em volta (receitas conhecidas, filtro de "posso
 *       fabricar", paginacao) continua sendo trabalho do proprio Minecraft.</li>
 * </ul>
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void bettersearch$prepareIndex(CallbackInfo ci) {
        RecipeSearch.prepare();
    }

    /*
     * ATENCAO ao dono da chamada: aqui e MutableSearchTree, e nao SearchTree.
     *
     * Na 1.18.2 o Minecraft.getSearchTree devolve MutableSearchTree<T> (uma subinterface de
     * SearchTree que ganhou add/clear/refresh). O javac aceitaria as duas, porque uma estende
     * a outra - mas o Mixin nao compara tipos, ele compara o descritor da instrucao invoke
     * que esta escrita no bytecode, e ali esta MutableSearchTree.search. Com SearchTree o
     * gancho da 0/1 e o jogo nao abre: "failed injection check".
     *
     * Da 1.19 em diante a MutableSearchTree deixou de existir e o metodo passou a devolver
     * SearchTree - por isso a versao da 1.19.2 para cima usa o outro nome. Conferido com
     * javap no updateCollections de cada jar, nao deduzido.
     */
    @Redirect(
            method = "updateCollections",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/searchtree/MutableSearchTree;"
                            + "search(Ljava/lang/String;)Ljava/util/List;"))
    private List<RecipeCollection> bettersearch$searchRecipes(MutableSearchTree<RecipeCollection> tree,
                                                             String query) {
        List<RecipeCollection> ours = RecipeSearch.search(query);
        return ours != null ? ours : tree.search(query);
    }
}
