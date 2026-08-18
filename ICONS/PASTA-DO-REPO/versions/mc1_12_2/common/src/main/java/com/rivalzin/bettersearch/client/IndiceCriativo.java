package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforma os itens do criativo em um {@link SearchIndex} - a versao 1.12.2 do
 * CreativeIndexBuilder das outras versoes, na lingua MCP.
 *
 * <p>As diferencas em relacao a 1.16.5+, todas conferidas com javap no jar remapeado:
 * o registro e {@code Item.REGISTRY.getNameForObject}, o nome vem de
 * {@code stack.getDisplayName()} (aqui e o texto pronto, nao um Component), "tem dados?" e
 * {@code hasTagCompound()} (NBT; componentes so na 1.20.5+) e a tooltip e
 * {@code getTooltip(EntityPlayer, ITooltipFlag)}.
 *
 * <p>Busca entre idiomas ainda nao entra nesta etapa: o formato aqui e {@code .lang} e o
 * carregador proprio vem na etapa dos idiomas, usando o parser do proprio jogo
 * ({@code Locale.loadLocaleDataFiles} - ja conferido que existe).
 */
public final class IndiceCriativo {

    private static final int MAX_LINHAS_TOOLTIP = 6;

    private IndiceCriativo() {
    }

    public static SearchIndex<ItemStack> montar(List<ItemStack> fonte, SearchSettings settings) {
        long inicio = System.nanoTime();
        List<SearchIndex.Entry<ItemStack>> entradas = new ArrayList<>(fonte.size());
        EntityPlayer jogador = Minecraft.getMinecraft().player;

        for (ItemStack stack : fonte) {
            try {
                EntryBuilder<ItemStack> builder = new EntryBuilder<>(stack);
                preencher(builder, stack, settings, jogador);
                entradas.add(builder.build());
            } catch (Throwable t) {
                // Um item problematico de algum mod nao pode impedir a busca dos outros.
                BetterSearch.LOGGER.debug("[{}] item ignorado no indice: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }

        SearchIndex<ItemStack> indice = new SearchIndex<>(entradas);
        BetterSearch.LOGGER.info("[{}] indice do criativo pronto (1.12.2): {} itens em {} ms",
                BetterSearch.MOD_NAME, entradas.size(), (System.nanoTime() - inicio) / 1_000_000);
        return indice;
    }

    /** Publico e generico no valor: o criativo indexa o ItemStack, o livro indexa a RecipeList
     *  pelas saidas - os DOIS pelos mesmos textos, senao as buscas divergem (licao da 1.5). */
    static void preencher(EntryBuilder<?> builder, ItemStack stack,
                                  SearchSettings settings, EntityPlayer jogador) {
        ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
        if (id != null) {
            builder.modId(id.getNamespace());
        }

        // 1) Nome no idioma do jogo (inclui nomes customizados de itens de mods).
        builder.add(stack.getDisplayName(), SearchField.SOURCE_NATIVE);

        // 1b) O mesmo nome nos outros idiomas. A chave dos .lang e a de traducao + ".name"
        //     (tile.stone.name, item.swordDiamond.name) - getTranslationKey conferido no jar.
        //     Textos repetidos sao descartados pelo builder, entao "TNT" 18 vezes ocupa 1.
        if (settings.crossLanguage) {
            String chave = stack.getTranslationKey() + ".name";
            for (String codigo : Linguas.codigosAtivos(settings)) {
                String traduzido = Linguas.get(codigo, chave);
                if (traduzido != null) {
                    builder.add(traduzido, "en_us".equalsIgnoreCase(codigo)
                            ? SearchField.SOURCE_ENGLISH
                            : SearchField.SOURCE_FOREIGN);
                }
            }
        }

        // 2) Id do item: "minecraft diamond sword".
        if (settings.searchItemIds && id != null) {
            builder.addNormalized(id.getNamespace() + ' ' + id.getPath().replace('_', ' '),
                    SearchField.SOURCE_ID);
        }

        // 3) Tooltip - so para itens que carregam NBT (livros encantados, pocoes...).
        if (settings.searchTooltips && jogador != null && stack.hasTagCompound()) {
            List<String> linhas = stack.getTooltip(jogador, ITooltipFlag.TooltipFlags.NORMAL);
            int limite = Math.min(linhas.size(), MAX_LINHAS_TOOLTIP + 1);
            for (int i = 1; i < limite; i++) { // a linha 0 e o proprio nome, ja indexado
                builder.add(linhas.get(i), SearchField.SOURCE_TOOLTIP);
            }
        }
    }
}
