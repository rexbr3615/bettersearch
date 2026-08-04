# Portar o Better Search

## Como o projeto está montado

```
BetterSearch/
├── core/                       ← o algoritmo. Java puro, ZERO Minecraft.
│   └── src/main/java/…/core/       Compartilhado por TODAS as versões e loaders.
├── tools/                      ← o teste do algoritmo (146 verificações)
├── mc1_21_1/                   ← tudo do Minecraft 1.21.1
│   ├── common/                     client/, client/gui/, mixin/, lang, texturas
│   ├── neoforge/                   2 arquivos + neoforge.mods.toml
│   └── fabric/                     4 arquivos + fabric.mod.json
├── mc1_21_9/                   ← Minecraft 1.21.9 **e 1.21.10** (o mesmo jar serve às duas)
│   ├── common/                     idem, com as diferenças de 1.21.2/1.21.6/1.21.9 resolvidas
│   ├── neoforge/                   2 arquivos + neoforge.mods.toml
│   └── fabric/                     4 arquivos + fabric.mod.json
├── mc1_21_11/                  ← Minecraft 1.21.11 — a 1.21.9 com `Identifier` no lugar de
│   │                              `ResourceLocation`. Última versão ofuscada e última em Java 21
│   ├── common/ neoforge/ fabric/
├── mc26_1/                     ← Minecraft 26.1 · 26.1.1 · 26.1.2 (Java 25, SEM ofuscação)
├── mc26_2/                     ← Minecraft 26.2
├── mc1_20_1/                   ← tudo do Minecraft 1.20.1 (Java 17)
│   ├── common/                     a mesma coisa, com as 6 diferenças da 1.20.1 resolvidas
│   ├── fabric/                     4 arquivos + fabric.mod.json
│   └── forge/                      3 arquivos + mods.toml (serve também ao NeoForge 20.1)
├── build.gradle                ← coreTest e dist
├── settings.gradle             ← lista os alvos
└── gradle.properties           ← todas as versões, em um lugar só
```

**O `core/` e o `common/` não são subprojetos Gradle.** Cada módulo de loader os inclui como
pasta de fonte e compila tudo dentro do próprio jar:

```groovy
sourceSets {
    main {
        java.srcDir rootProject.file('core/src/main/java')
        java.srcDir rootProject.file('mc1_21_1/common/src/main/java')
        resources.srcDir rootProject.file('mc1_21_1/common/src/main/resources')
    }
}
```

Foi de propósito. A alternativa — um subprojeto `core` com dependência entre projetos —
exigiria *shadow* ou *jarJar* para embutir as classes no jar final, e qualquer erro nisso só
aparece em tempo de execução, com o mod já no jogo. Assim **o que compilou, rodou**.

## Quem depende de quê

| Pasta | Depende de | Muda ao portar? |
|---|---|---|
| `core/` | nada (Java puro) | **nunca** |
| `common/client/` | `Minecraft`, `ItemStack`, `ResourceManager` | só se a API vanilla mudar |
| `common/client/gui/` | `Screen`, `GuiGraphics`, `AbstractWidget`, `Font` | **é a parte cara** |
| `common/mixin/` | nomes internos de 3 classes vanilla | sempre conferir |
| `neoforge/`, `fabric/` | o loader | **sim** — mas são só 6 arquivos curtos |

Regra prática: **se você precisou tocar em `core/`, provavelmente está fazendo errado.**

## A decisão que faz o Fabric sair de graça

```groovy
mappings loom.officialMojangMappings()
```

O NeoForge usa os nomes oficiais da Mojang. Escolhendo o mesmo no Loom, `Screen` continua
`Screen` e `GuiGraphics` continua `GuiGraphics` nos dois lados — então as 2.197 linhas de
`common/` e os 3 mixins são **literalmente o mesmo arquivo**. Com Yarn, cada classe teria
outro nome e seria preciso manter duas cópias de tudo, para sempre.

## O que cada loader precisa ter (e só isso)

| Precisa | NeoForge 21.1 | Forge 1.20.1 | Fabric |
|---|---|---|---|
| Ponto de entrada | `@Mod(dist = CLIENT)` + construtor | `@Mod` + `DistExecutor` (a 1.20.1 não tem `dist`) | `ClientModInitializer` |
| Pasta de config | `FMLPaths.CONFIGDIR` | igual | `FabricLoader…getConfigDir()` |
| Listener de recarga | vanilla, direto | vanilla, direto | precisa de `IdentifiableResourceReloadListener` (um id) |
| Tecla | `KeyMapping` com `KeyModifier.ALT` | igual | `KeyBindingHelper` (26.x: `KeyMappingHelper`) + Alt conferido na mão |
| Botão de config | `IConfigScreenFactory` | `ConfigScreenHandler.ConfigScreenFactory` | `ModMenuApi` (Mod Menu, opcional) |
| Metadados | `neoforge.mods.toml` | `META-INF/mods.toml` | `fabric.mod.json` |
| Mixin | nada a mais | **refmap** (veja abaixo) | o Loom cuida |

## Forge 1.20.1 — um jar só para dois loaders

Não existe pasta `mc1_20_1/neoforge`, e isso é de propósito.

Na 1.20.1 o NeoForge ainda era um fork recém-saído do Forge 47: mesmos pacotes
`net.minecraftforge.*`, mesmo `META-INF/mods.toml`, mesmo id de mod (`forge`) e os mesmos
nomes SRG em tempo de execução. A separação — pacotes `net.neoforged.*`, mapeamentos da
Mojang, `neoforge.mods.toml` — só veio na 20.2 e na 20.5.

Duas linhas fazem o jar servir aos dois:

```properties
forge_version_1201=1.20.1-47.1.47   # última versão que os dois têm em comum
```
```toml
versionRange = "[47,)"              # e não "[47.4,)": o NeoForge parou no 47.1.106
```

Compilar contra o ponto de bifurcação é o que dá a garantia: o compilador só deixa usar API
que existe nos dois lados. Rodar depois em Forge 47.4.x é seguro, porque o Forge não quebra
compatibilidade binária dentro da linha 47.

**O refmap.** Essa é a diferença que mais dói. O NeoForge 21.1 e o Fabric rodam com nomes
legíveis (ou o Loom os traduz); o Forge 1.20.1 roda **ofuscado**, com nomes SRG. O Mixin não
tem como achar `refreshSearchResults` lá dentro — ele precisa de um mapa, o *refmap*, gerado
em tempo de compilação pelo processador de anotações:

```groovy
dependencies { annotationProcessor 'org.spongepowered:mixin:0.8.5:processor' }
mixin { add sourceSets.main, 'bettersearch.refmap.json'
        config 'bettersearch-forge.mixins.json' }
jar  { manifest.attributes(['MixinConfigs': 'bettersearch-forge.mixins.json']) }
```

Sem o `MixinConfigs` no manifesto, o mod compila, instala, aparece na lista e **não faz
nada** — os mixins nunca carregam. O bloco `[[mixins]]` do `mods.toml` não substitui isso:
ele só existe do NeoForge 20.2 em diante.

É por causa do refmap que a 1.20.1 tem **dois** arquivos de configuração de mixin: o do
Fabric não pode declarar `refmap` (quem escreve lá é o Loom) e o do Forge precisa declarar.
Fora essa linha, são idênticos — e o `tools/verify.sh` falha se um sair de sincronia com o
outro.

## Acrescentar uma versão nova

1. `cp -r mc1_21_1 mc1_XX_X` e ajuste os dois `build.gradle` de dentro (os `srcDir` apontam
   para a pasta nova) e o `settings.gradle` da raiz.
2. Ponha as versões novas no `gradle.properties`.
3. `./gradlew :mc1_XX_X:neoforge:build` — deixe o Gradle baixar o jar da versão.
4. **Confira os 3 mixins contra o jar de verdade** antes de qualquer outra coisa:
   ```bash
   javap -p -c -cp <merged.jar> net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen | grep refreshSearchResults
   javap -p -c -cp <merged.jar> net.minecraft.client.gui.components.CommandSuggestions       | grep -E "updateCommandInfo|formatText|UNPARSED_STYLE"
   javap -p -c -cp <merged.jar> net.minecraft.client.gui.screens.recipebook.RecipeBookComponent | grep -E "updateCollections|tick"
   ```
   Se um alvo sumiu, é ali que o port vai doer.
5. `bash tools/verify.sh` — compila os dois loaders e roda as 146 verificações.

## As armadilhas conhecidas, por versão

| A partir de | O que quebra |
|---|---|
| **1.20.1** (para trás) | Java 17; sem `DataComponents` (tooltip vira NBT); sem `Item.TooltipContext`; `new ResourceLocation(...)` em vez de `fromNamespaceAndPath`; busca do criativo usa `SearchRegistry` |
| **1.21.2** | `GuiGraphics#blit` ganhou `Function<ResourceLocation, RenderType>` — afeta o painel de prévias; livro de receitas refeito (`RecipeDisplayEntry` no lugar de `RecipeHolder`) |
| **1.21.6** | renderer reescrito: `blit` passa a pedir um `RenderPipeline` e o `RenderSystem.enableBlend/defaultBlendFunc/disableBlend` **deixa de existir** |
| **1.21.9** | clique e tecla viram objetos (`MouseButtonEvent`, `KeyEvent`) — muda `onClick`; `Screen.hasAltDown()` → `Minecraft#hasAltDown`; categoria de tecla vira `KeyMapping.Category` (chave de idioma `key.category.<mod>.<nome>`); `ItemStack#getDescriptionId` some; NeoForge renomeia `RegisterClientReloadListenersEvent` → `AddClientReloadListenersEvent`, que agora exige um id por listener |
| **1.21.11** | dois renomes, e só: `net.minecraft.resources.ResourceLocation` → `…resources.Identifier` (mesmo pacote, mesmos métodos) e `net.minecraft.Util` → `net.minecraft.util.Util`. `RenderPipelines` **não** se mexeu. É jar próprio porque o binário muda, não porque a lógica muda |
| **26.1** | `GuiGraphics` → `GuiGraphicsExtractor`; `Screen#render` → `extractRenderState`; `renderBackground` → `extractBackground`; `renderWidget` → `extractWidgetRenderState`; `drawString` → `text`, `drawCenteredString` → `centeredText`. `CommandSuggestions#updateUsageInfo` passou a receber `(ParseResults, Suggestions)`. Java 25, Gradle 9.1+. A Mojang tirou a ofuscação: adeus Parchment e adeus refmap |
| **26.2** | a tela atual saiu do `Minecraft` e foi para o `Gui`: `minecraft.setScreen(x)` → `minecraft.gui.setScreen(x)`, `minecraft.screen` → `minecraft.gui.screen()` |

## O ciclo de verificação que funciona

Sem abrir o Minecraft, `tools/verify.sh` faz três coisas:

1. **compila cada loader contra o jar real** que o Gradle baixou, com `-Xlint:all`;
2. roda as **146 verificações** do algoritmo (Java puro — valem em qualquer versão);
3. valida todo JSON de idioma, de mixin e o `fabric.mod.json`.

O que ele **não** pega: comportamento em tempo de execução. Foi por isso que o bug do TAB no
chat e o do índice sendo descartado a cada mudança de opção só apareceram jogando. Para
esses, o caminho é ler o código decompilado (`neoforge-*-sources.jar`) e conferir a hipótese
no bytecode com `javap` — não chutar.
