package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.Estado;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Bootstrap da 1.12.2 - etapa 1: o mod EXISTE, carrega e loga. Nada mais.
 *
 * <p>Aqui e outro mundo: sem mods.toml (o metadado e o mcmod.info em JSON), sem
 * DistExecutor, sem barramento de registro moderno. O {@code @Mod} e a anotacao classica com
 * {@code @Mod.EventHandler} - atributos conferidos com javap no jar remapeado
 * ({@code modid/name/version/clientSideOnly/acceptedMinecraftVersions}), nao de memoria.
 *
 * <p>{@code clientSideOnly = true} e o equivalente do {@code "environment": "client"} do
 * Fabric e do {@code clientSideOnly} do mods.toml: num servidor dedicado o Forge simplesmente
 * nao carrega o mod - mesma politica das outras oito versoes.
 *
 * <p>O log passa pelo {@link BetterSearch#LOGGER} de sempre (o Registro): o classpath da
 * 1.12.2 traz log4j-core 2.17.1 - visto na propria sonda - entao ele acha o log4j na primeira
 * tentativa. E o mesmo caminho ja provado no Forge 1.16.5, que tambem nao tem slf4j.
 */
@Mod(modid = BetterSearch.MOD_ID,
        name = BetterSearch.MOD_NAME,
        version = "1.3.0",   // regra do projeto: adicionar versao de Minecraft nao muda o mod_version
        clientSideOnly = true,
        acceptedMinecraftVersions = "[1.12.2]")
public final class BetterSearchForge {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // O proprio Forge diz onde mora a config; o formato e o mesmo das outras versoes.
        Estado.carregar(new java.io.File(event.getModConfigurationDirectory(),
                "bettersearch.json").toPath());
        MinecraftForge.EVENT_BUS.register(new GanchoDeBusca());
        MinecraftForge.EVENT_BUS.register(new Teclas());
        BetterSearch.LOGGER.info("[{}] carregado na 1.12.2. Backend de log: {}",
                BetterSearch.MOD_NAME, BetterSearch.LOGGER.backend());
    }
}
