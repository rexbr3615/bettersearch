package com.rivalzin.bettersearch.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Base dos plugins que so aplicam um gancho quando o mod alvo esta instalado.
 *
 * <p><b>Nao use {@code Class.forName} para isso.</b> Ja usei, no primeiro gancho do JEI, e estava
 * errado por dois motivos. O primeiro: isto roda no bootstrap do Mixin, antes das classes de mod
 * ficarem alcancaveis por este carregador, entao a checagem falha mesmo com o mod instalado e o
 * gancho nunca e aplicado, silenciosamente. O segundo e pior: se funcionasse, teria carregado a
 * classe alvo ANTES do Mixin transformar ela, o que mata o gancho de vez.
 *
 * <p>Quem sabe a resposta e o loader, e ele ja esta de pe nesta altura. A cadeia de reflexao
 * abaixo cobre NeoForge, Forge e Fabric com um arquivo so.
 */
public abstract class ModPresencePlugin implements IMixinConfigPlugin {
    private final String modId;
    private final String targetResource;
    private boolean present;

    protected ModPresencePlugin(String modId, String targetResource) {
        this.modId = modId;
        this.targetResource = targetResource;
    }

    @Override
    public final void onLoad(String mixinPackage) {
        present = detect();
    }

    private boolean detect() {
        Boolean early = viaLoadingModList("net.neoforged.fml.loading.LoadingModList");
        if (early != null) {
            return early;
        }
        early = viaLoadingModList("net.minecraftforge.fml.loading.LoadingModList");
        if (early != null) {
            return early;
        }
        Boolean list = viaModList("net.neoforged.fml.ModList");
        if (list != null) {
            return list;
        }
        list = viaModList("net.minecraftforge.fml.ModList");
        if (list != null) {
            return list;
        }
        try {
            Class<?> loader = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = loader.getMethod("getInstance").invoke(null);
            Object loaded = loader.getMethod("isModLoaded", String.class).invoke(instance, modId);
            return Boolean.TRUE.equals(loaded);
        } catch (Throwable ignored) {
            // nao e Fabric, ou o loader mudou de nome
        }
        // ultimo recurso: procura o arquivo, sem carregar a classe
        return getClass().getClassLoader().getResource(targetResource) != null;
    }

    private Boolean viaLoadingModList(String className) {
        try {
            Class<?> type = Class.forName(className);
            Object instance = type.getMethod("get").invoke(null);
            if (instance == null) {
                return null;
            }
            return type.getMethod("getModFileById", String.class).invoke(instance, modId) != null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Boolean viaModList(String className) {
        try {
            Class<?> type = Class.forName(className);
            Object instance = type.getMethod("get").invoke(null);
            if (instance == null) {
                return null;
            }
            return Boolean.TRUE.equals(type.getMethod("isLoaded", String.class).invoke(instance, modId));
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public final boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return present;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }
}
