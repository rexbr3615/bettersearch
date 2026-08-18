package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.ArvoreEnvolvida;
import com.rivalzin.bettersearch.client.BuscaCriativa;
import com.rivalzin.bettersearch.client.BuscaReceitas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ISearchTree;
import net.minecraft.client.util.SearchTree;
import net.minecraft.client.util.SearchTreeManager;
import net.minecraft.client.gui.recipebook.RecipeList;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Method;

/**
 * Poe a {@link ArvoreEnvolvida} no lugar da arvore de itens do jogo.
 *
 * <p>Por que num tique, e nao no proprio preInit: a ordem entre "os mods carregam" e "o
 * Minecraft registra as arvores de busca" e exatamente o tipo de coisa que ja mordeu este
 * projeto uma vez (o listener de idiomas do Forge 1.16.5, que chegou tarde para a primeira
 * carga). Conferir a cada tique e idempotente: quando a arvore vanilla aparecer, embrulha;
 * se ja esta embrulhada, nao faz nada - e um instanceof por tique, custo zero.
 */
public final class GanchoDeBusca {

    /** Decidido uma vez: no preInit (quando este objeto nasce) a lista de mods ja e final. */
    private final boolean temJei = Loader.isModLoaded("jei");
    private Method instalarJei;
    private boolean jeiDesistiu;

    @SubscribeEvent
    public void aoTicar(TickEvent.ClientTickEvent evento) {
        if (evento.phase != TickEvent.Phase.END) {
            return;
        }
        SearchTreeManager gerente = Minecraft.getMinecraft().getSearchTreeManager();
        if (gerente == null) {
            return;
        }
        ISearchTree<ItemStack> itens = gerente.get(SearchTreeManager.ITEMS);
        if (!(itens instanceof ArvoreEnvolvida) && itens instanceof SearchTree) {
            gerente.register(SearchTreeManager.ITEMS,
                    new ArvoreEnvolvida<>((SearchTree<ItemStack>) itens, BuscaCriativa::search));
            BetterSearch.LOGGER.info("[{}] busca do criativo ligada na 1.12.2 (arvore embrulhada, sem mixin)",
                    BetterSearch.MOD_NAME);
        }
        ISearchTree<RecipeList> receitas = gerente.get(SearchTreeManager.RECIPES);
        if (!(receitas instanceof ArvoreEnvolvida) && receitas instanceof SearchTree) {
            gerente.register(SearchTreeManager.RECIPES,
                    new ArvoreEnvolvida<>((SearchTree<RecipeList>) receitas, BuscaReceitas::search));
            BetterSearch.LOGGER.info("[{}] busca do livro de receitas ligada na 1.12.2 (mesma arvore embrulhada)",
                    BetterSearch.MOD_NAME);
        }
        instalarGanchoDoJei();
    }

    /**
     * O gancho do JEI, pelo mesmo caminho idempotente do tique.
     *
     * <p>Por reflexao com o nome em TEXTO, de proposito: esta classe carrega em todo pack,
     * e citar a IntegracaoJei pelo tipo faria a secao 9 do verify acusar - com razao, pois
     * a resolucao ansiosa de uma JVM mais estrita derrubaria o jogo sem o JEI instalado.
     * O Class.forName so roda com o JEI presente, e a classe dele so e resolvida aqui.
     */
    private void instalarGanchoDoJei() {
        if (!temJei || jeiDesistiu) {
            return;
        }
        try {
            if (instalarJei == null) {
                instalarJei = Class.forName("com.rivalzin.bettersearch.forge.jei.IntegracaoJei")
                        .getMethod("instalar");
            }
            instalarJei.invoke(null);
        } catch (Throwable t) {
            // Uma vez so: se o JEI do pack for de outra linha, o resto do mod segue intacto.
            jeiDesistiu = true;
            BetterSearch.LOGGER.warn("[{}] nao consegui ligar a busca no JEI desta instalacao: {}",
                    BetterSearch.MOD_NAME, t.toString());
        }
    }
}
