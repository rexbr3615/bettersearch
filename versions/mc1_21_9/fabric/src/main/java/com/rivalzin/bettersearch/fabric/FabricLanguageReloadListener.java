package com.rivalzin.bettersearch.fabric;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.LanguageReloadListener;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

/**
 * O listener de idiomas do mod, com a identidade que o Fabric exige.
 *
 * <p>Unica diferenca entre os dois loaders neste ponto: o Fabric quer que todo listener de
 * recarga tenha um id, para poder ordenar as dependencias entre eles. O NeoForge aceita o
 * listener vanilla direto. A logica de ler os idiomas nao muda uma linha - ela esta na
 * classe de cima, compartilhada.
 */
public final class FabricLanguageReloadListener extends LanguageReloadListener
        implements IdentifiableResourceReloadListener {

    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(BetterSearch.MOD_ID, "languages");

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}
