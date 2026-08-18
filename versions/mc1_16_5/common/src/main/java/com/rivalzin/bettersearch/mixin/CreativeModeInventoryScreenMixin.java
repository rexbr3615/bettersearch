package com.rivalzin.bettersearch.mixin;

import com.rivalzin.bettersearch.client.BetterSearchClient;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Unico ponto de contato com o codigo do Minecraft.
 *
 * <p>A injecao acontece no <b>RETURN</b> de {@code refreshSearchResults}: deixamos a busca
 * original rodar normalmente e so trocamos a lista de itens depois. Isso e proposital:
 *
 * <ul>
 *   <li>nao precisamos replicar nada do que o metodo faz (limpar tags visiveis, zerar o
 *       scroll, tratar {@code #tag}), entao ha bem menos coisa para quebrar em outra versao;</li>
 *   <li>se o indice ainda estiver sendo montado, ou se o mod estiver desligado, os resultados
 *       da busca original ja estao la e nada acontece - nunca ha uma tela vazia;</li>
 *   <li>o custo extra e desprezivel: a arvore de busca vanilla e O(tamanho da consulta).</li>
 * </ul>
 *
 * <h2>As duas diferencas da 1.19.2</h2>
 *
 * <p><b>{@code selectedTab} e um int.</b> Ate a 1.19.2 as abas do criativo eram um vetor fixo
 * ({@code CreativeModeTab.TABS}) e o campo guardava o indice. So na 1.19.3, com a reforma das
 * abas, ele passou a guardar a propria aba.
 *
 * <p><b>Nao existe {@code tab.getDisplayItems()}.</b> Aquele metodo veio junto com a mesma
 * reforma. Aqui a lista completa da aba de busca e montada percorrendo o registro e chamando
 * {@code fillItemCategory} em cada item - que e literalmente o que o metodo original faz
 * quando a busca esta vazia. Como isso custa caro para fazer a cada tecla, a lista fica
 * guardada: no caminho de consulta vazia ela vem de graca (o proprio jogo acabou de encher
 * {@code menu.items} com tudo), e so e montada na mao se a primeira coisa que o jogador fizer
 * for digitar.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {

    @Shadow
    private EditBox searchBox;

    @Shadow
    private static int selectedTab;

    /** A aba de busca inteira. Muda so quando o registro muda, entao vale guardar. */
    @Unique
    private static List<ItemStack> bettersearch$pool;

    @Unique
    private static int bettersearch$poolRegistrySize = -1;

    @Inject(method = "refreshSearchResults", at = @At("RETURN"))
    private void bettersearch$refreshSearchResults(CallbackInfo ci) {
        if (!BetterSearchClient.isEnabled()) {
            return;
        }
        EditBox box = this.searchBox;
        if (box == null || !box.isVisible()) {
            return;
        }
        // TABS e um vetor fixo nesta versao; selectedTab e o indice dentro dele.
        if (selectedTab < 0 || selectedTab >= CreativeModeTab.TABS.length
                || CreativeModeTab.TABS[selectedTab] != CreativeModeTab.TAB_SEARCH) {
            return;
        }

        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
        CreativeModeInventoryScreen.ItemPickerMenu menu = screen.getMenu();
        String query = box.getValue();

        // Consulta vazia mostra a aba inteira, e '#' e busca por tag: os dois continuam
        // sendo trabalho da busca original. Aproveitamos para ja preparar o indice.
        if (query.isEmpty()) {
            // O jogo acabou de encher menu.items com a aba inteira: essa E a nossa lista.
            bettersearch$remember(new ArrayList<>(menu.items));
            BetterSearchClient.prepare(bettersearch$pool);
            return;
        }
        if (query.charAt(0) == '#') {
            BetterSearchClient.prepare(bettersearch$pool());
            return;
        }

        List<ItemStack> results = BetterSearchClient.search(query, bettersearch$pool());
        if (results == null) {
            return; // indice ainda montando ou mod desligado -> resultado original
        }

        menu.items.clear();
        menu.items.addAll(results);
        menu.scrollTo(0.0F);
    }

    @Unique
    @SuppressWarnings("deprecation") // Registry.ITEM: depreciado pelo Forge, nao pela Mojang
    private static void bettersearch$remember(List<ItemStack> pool) {
        bettersearch$pool = pool;
        bettersearch$poolRegistrySize = Registry.ITEM.keySet().size();
    }

    /**
     * A lista guardada, montada na mao se ainda nao existir.
     *
     * <p>O tamanho do registro entra na conta porque uma troca de mundo pode trazer outro
     * conjunto de itens; sem isso a busca continuaria devolvendo a lista do mundo anterior.
     */
    @Unique
    @SuppressWarnings("deprecation") // Registry.ITEM: depreciado pelo Forge, nao pela Mojang
    private static List<ItemStack> bettersearch$pool() {
        List<ItemStack> cached = bettersearch$pool;
        if (cached != null && bettersearch$poolRegistrySize == Registry.ITEM.keySet().size()) {
            return cached;
        }
        NonNullList<ItemStack> montada = NonNullList.create();
        for (Item item : Registry.ITEM) {
            item.fillItemCategory(CreativeModeTab.TAB_SEARCH, montada);
        }
        bettersearch$remember(montada);
        return montada;
    }
}
