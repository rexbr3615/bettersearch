package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforma os itens da aba de busca do criativo em um {@link SearchIndex}.
 *
 * <p>Roda fora da thread principal (igual ao que o proprio jogo faz para montar as dicas da
 * arvore de busca vanilla). Todo o custo - normalizar texto, achar limites de palavra,
 * gerar tooltips - e pago uma vez aqui; digitar depois so faz comparacoes.
 */
public final class CreativeIndexBuilder {

    /** Quantas linhas de tooltip, no maximo, entram no indice por item. */
    private static final int MAX_TOOLTIP_LINES = 6;

    private CreativeIndexBuilder() {
    }

    public static SearchIndex<ItemStack> build(List<ItemStack> stacks,
                                               LanguageTable languages,
                                               SearchSettings settings,
                                               Item.TooltipContext tooltipContext,
                                               Player player) {
        long start = System.nanoTime();
        List<SearchIndex.Entry<ItemStack>> entries = new ArrayList<>(stacks.size());

        // So os idiomas que estao ligados AGORA. A tabela pode conter idiomas carregados
        // antes de o usuario desmarca-los, e usa-la crua faria "pomme" continuar achando
        // a maca (e a batata, que em frances e "pomme de terre") com o frances desligado.
        List<String> codes = new ArrayList<>();
        for (String code : languages.languageCodes()) {
            if (settings.indexesLanguage(code)) {
                codes.add(code);
            }
        }

        // Os apelidos secretos em ingles so entram se o ingles for mesmo pesquisado - seja
        // porque o jogo esta em ingles, seja porque o idioma esta ligado na lista.
        boolean englishSearched = codes.contains("en_us")
                || "en_us".equals(LanguageCatalog.currentCode());

        for (ItemStack stack : stacks) {
            try {
                entries.add(buildEntry(stack, languages, codes, settings, tooltipContext, player,
                        englishSearched));
            } catch (Throwable t) {
                // Um item problematico de algum mod nao pode impedir a busca dos outros.
                BetterSearch.LOGGER.debug("[{}] item ignorado no indice: {}", BetterSearch.MOD_NAME, t.toString());
            }
        }

        SearchIndex<ItemStack> index = new SearchIndex<>(entries);
        BetterSearch.LOGGER.info("[{}] indice pronto: {} itens em {} ms",
                BetterSearch.MOD_NAME, entries.size(), (System.nanoTime() - start) / 1_000_000);
        return index;
    }

    private static SearchIndex.Entry<ItemStack> buildEntry(ItemStack stack,
                                                           LanguageTable languages,
                                                           List<String> codes,
                                                           SearchSettings settings,
                                                           Item.TooltipContext tooltipContext,
                                                           Player player,
                                                           boolean englishSearched) {
        EntryBuilder<ItemStack> builder = new EntryBuilder<>(stack);

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        builder.modId(id.getNamespace());

        // 1) Nome no idioma do jogo (inclui nomes customizados de itens de mods).
        builder.add(stack.getHoverName().getString(), SearchField.SOURCE_NATIVE);

        // 2) O mesmo nome nos outros idiomas. Textos repetidos sao descartados pelo builder,
        //    entao "TNT" em 18 idiomas ocupa o espaco de um.
        if (settings.crossLanguage && !codes.isEmpty()) {
            String descriptionId = stack.getDescriptionId();
            for (String code : codes) {
                String translated = languages.get(code, descriptionId);
                if (translated != null) {
                    builder.add(translated, code.equals("en_us")
                            ? SearchField.SOURCE_ENGLISH
                            : SearchField.SOURCE_FOREIGN);
                }
            }
        }

        // 3) Id do item: "minecraft diamond sword" (os separadores viram espaco na normalizacao).
        if (settings.searchItemIds) {
            builder.addNormalized(id.getNamespace() + ' ' + id.getPath().replace('_', ' '),
                    SearchField.SOURCE_ID);
        }

        // 4) Linhas extras de tooltip - so para itens que carregam dados (livros encantados,
        //    pocoes, flechas com efeito, discos...). Itens comuns nao ganham nada com isso
        //    e gerar tooltip para todos seria caro.
        if (settings.searchTooltips && hasExtraData(stack)) {
            List<Component> lines = stack.getTooltipLines(tooltipContext, player, TooltipFlag.Default.NORMAL);
            int limit = Math.min(lines.size(), MAX_TOOLTIP_LINES + 1);
            for (int i = 1; i < limit; i++) { // a linha 0 e o proprio nome, ja indexado
                builder.add(lines.get(i).getString(), SearchField.SOURCE_TOOLTIP);
            }
        }

        // 5) Apelidos secretos. SOURCE_NATIVE de proposito: sao palavras para digitar de
        //    verdade, entao merecem a mesma tolerancia a erro do nome do item.
        for (String alias : EasterEggs.aliasesFor(id.toString(), englishSearched)) {
            builder.add(alias, SearchField.SOURCE_NATIVE);
        }

        return builder.build();
    }

    /**
     * O item carrega componentes diferentes do padrao? Se sim, a tooltip dele tem informacao
     * de verdade (encantamento, efeito, autor...) e vale indexar.
     */
    private static boolean hasExtraData(ItemStack stack) {
        return !stack.getComponentsPatch().isEmpty();
    }
}
