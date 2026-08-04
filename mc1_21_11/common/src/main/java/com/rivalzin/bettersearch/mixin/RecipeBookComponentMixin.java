package com.rivalzin.bettersearch.mixin;

import com.rivalzin.bettersearch.client.RecipeSearch;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.searchtree.SearchTree;
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

    @Redirect(
            method = "updateCollections",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/searchtree/SearchTree;search(Ljava/lang/String;)Ljava/util/List;"))
    private List<RecipeCollection> bettersearch$searchRecipes(SearchTree<RecipeCollection> tree, String query) {
        List<RecipeCollection> ours = RecipeSearch.search(query);
        return ours != null ? ours : tree.search(query);
    }
}
