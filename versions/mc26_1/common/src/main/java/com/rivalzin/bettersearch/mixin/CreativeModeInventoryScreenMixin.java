package com.rivalzin.bettersearch.mixin;

import com.rivalzin.bettersearch.client.BetterSearchClient;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
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
 * <p>Para portar: os unicos nomes ligados a versao/mapeamento estao aqui
 * ({@code refreshSearchResults}, {@code searchBox}, {@code selectedTab}).
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {

    @Shadow
    private EditBox searchBox;

    @Shadow
    private static CreativeModeTab selectedTab;

    @Inject(method = "refreshSearchResults", at = @At("RETURN"))
    private void bettersearch$refreshSearchResults(CallbackInfo ci) {
        if (!BetterSearchClient.isEnabled()) {
            return;
        }
        EditBox box = this.searchBox;
        CreativeModeTab tab = selectedTab;
        if (box == null || tab == null || !box.isVisible()) {
            return;
        }

        Collection<ItemStack> pool = tab.getDisplayItems();
        String query = box.getValue();

        // Consulta vazia mostra a aba inteira, e '#' e busca por tag: os dois continuam
        // sendo trabalho da busca original. Aproveitamos para ja preparar o indice.
        if (query.isEmpty() || query.charAt(0) == '#') {
            BetterSearchClient.prepare(pool);
            return;
        }

        List<ItemStack> results = BetterSearchClient.search(query, pool);
        if (results == null) {
            return; // indice ainda montando ou mod desligado -> resultado original
        }

        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
        CreativeModeInventoryScreen.ItemPickerMenu menu = screen.getMenu();
        menu.items.clear();
        menu.items.addAll(results);
        menu.scrollTo(0.0F);
    }
}
