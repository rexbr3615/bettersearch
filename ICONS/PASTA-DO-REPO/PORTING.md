# Portar o Better Search

## Como o projeto está montado

```
BetterSearch/
├── core/                       ← o algoritmo. Java puro, ZERO Minecraft.
│   └── src/main/java/…/core/       Compartilhado por TODAS as versões e loaders.
├── tools/                      ← o teste do algoritmo (157 verificações)
├── versions/                   ← TUDO o que depende de versão fica aqui dentro
│   ├── mc1_18_2/                   Minecraft 1.18.2 (Java 17) - o port mais antigo
│   ├── mc1_19_2/                   Minecraft 1.19.2 (Java 17)
│   ├── mc1_20_1/                   Minecraft 1.20.1 (Java 17)
│   │   ├── common/                     client/, client/gui/, mixin/, lang, texturas
│   │   ├── fabric/                     4 arquivos + fabric.mod.json
│   │   └── forge/                      3 arquivos + mods.toml (serve também ao NeoForge 20.1)
│   ├── mc1_21_1/                   a primeira versão portada
│   ├── mc1_21_9/                   1.21.9 **e 1.21.10** (o mesmo jar serve às duas)
│   ├── mc1_21_11/                  a 1.21.9 com `Identifier` no lugar de `ResourceLocation`;
│   │                               última versão ofuscada e última em Java 21
│   ├── mc26_1/                     26.1 · 26.1.1 · 26.1.2 (Java 25, SEM ofuscação)
│   └── mc26_2/                     26.2
│                                   (cada uma tem common/ + as pastas dos loaders)
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
        java.srcDir file('../common/src/main/java')          // relativo ao próprio módulo
        resources.srcDir file('../common/src/main/resources')
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

1. `cp -r versions/mc1_21_1 versions/mc1_XX_X` e some duas linhas no `settings.gradle` da
   raiz. Os `srcDir` de dentro são relativos ao módulo, então **não precisam ser tocados**.
2. Ponha as versões novas no `gradle.properties`.
3. `./gradlew :versions:mc1_XX_X:neoforge:build` — deixe o Gradle baixar o jar da versão.
4. **Confira os 3 mixins contra o jar de verdade** antes de qualquer outra coisa:
   ```bash
   javap -p -c -cp <merged.jar> net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen | grep refreshSearchResults
   javap -p -c -cp <merged.jar> net.minecraft.client.gui.components.CommandSuggestions       | grep -E "updateCommandInfo|formatText|UNPARSED_STYLE"
   javap -p -c -cp <merged.jar> net.minecraft.client.gui.screens.recipebook.RecipeBookComponent | grep -E "updateCollections|tick"
   ```
   Se um alvo sumiu, é ali que o port vai doer.
5. `bash tools/verify.sh` — compila os dois loaders e roda as 157 verificações.

## As armadilhas conhecidas, por versão

| A partir de | O que quebra |
|---|---|
| **1.18.2** | Tudo o que a 1.19.2 muda, mais: **`Component.translatable/literal/empty` não existem** (chegaram na 1.19) — é `new TranslatableComponent` / `new TextComponent`; `rebuildWidgets()` → `init(Minecraft, w, h)`; `ResourceManager.listResourceStacks` → `listResources` + `getResources`; `Resource.open()` → `getInputStream()`; sem `CommonComponents.EMPTY`; `getCustomTabSugggestions()` → `getOnlinePlayerNames()`; no Forge é `ConfigGuiHandler.ConfigGuiFactory` e a tecla se registra por `ClientRegistry`, sem `RegisterKeyMappingsEvent`. **JEI:** outro pacote (`mezz.jei.common.ingredients`), devolve `List` e não `Stream`, e o elemento é `IListElementInfo` (que já traz nome e id prontos). **Livro de receitas:** `Minecraft.getSearchTree` devolve `MutableSearchTree` (subinterface que sumiu na 1.19), então o `@Redirect` do `updateCollections` mira nesse nome e **não** em `SearchTree` — o javac aceita os dois, o Mixin não. **EMI:** só existe para Fabric; o `SearchWorker` não tem campo nenhum, `apply` recebe um argumento só e é chamado **uma vez** (ordinal 0), a consulta sai de um `private static` da `EmiSearch` por `@Accessor` e a lista de `EmiScreenManager.getSearchSource()` |
| **1.19.2** | `selectedTab` é um **int**, índice em `CreativeModeTab.TABS`; não existe `tab.getDisplayItems()` (a lista da aba de busca sai de `Registry.ITEM` + `fillItemCategory`); `BuiltInRegistries` → `Registry`; `GuiGraphics` → `PoseStack` + os estáticos de `GuiComponent`; `renderWidget` → `renderButton`; `updateWidgetNarration` → `updateNarration`; `x`/`y` são campos públicos e `isHovered` também; não existe `Tooltip` nem `setTooltip`; `Button.builder` só chega na 1.19.4; `EditBox.setHint` → `setSuggestion`; `LanguageManager.getLanguages()` devolve `SortedSet<LanguageInfo>` e os acessores têm prefixo `get`; `recipe.getResultItem()` sem `RegistryAccess`. **Armadilha:** o jar do Forge oferece `ResourceLocation.fromNamespaceAndPath` e marca o construtor para remoção — mas aquilo é patch do Forge e não existe no Fabric |
| **1.20.1** (para trás) | Java 17; sem `DataComponents` (tooltip vira NBT); sem `Item.TooltipContext`; `new ResourceLocation(...)` em vez de `fromNamespaceAndPath`; busca do criativo usa `SearchRegistry` |
| **1.21.2** | `GuiGraphics#blit` ganhou `Function<ResourceLocation, RenderType>` — afeta o painel de prévias; livro de receitas refeito (`RecipeDisplayEntry` no lugar de `RecipeHolder`) |
| **1.21.6** | renderer reescrito: `blit` passa a pedir um `RenderPipeline` e o `RenderSystem.enableBlend/defaultBlendFunc/disableBlend` **deixa de existir** |
| **1.21.9** | clique e tecla viram objetos (`MouseButtonEvent`, `KeyEvent`) — muda `onClick`; `Screen.hasAltDown()` → `Minecraft#hasAltDown`; categoria de tecla vira `KeyMapping.Category` (chave de idioma `key.category.<mod>.<nome>`). **Correções datadas pelo porte da 1.21.4:** `ItemStack#getDescriptionId` já não existe na 1.21.4 (saiu na reforma 1.21.2, não aqui — use `getItemName()` + `TranslatableContents`); e o rename `RegisterClientReloadListenersEvent` → `AddClientReloadListenersEvent` (com id por listener) foi **retroportado para os builds tardios da linha 21.4** — não é seguro amarrar código a nenhum dos dois nomes dentro daquela linha |
| **1.21.11** | dois renomes, e só: `net.minecraft.resources.ResourceLocation` → `…resources.Identifier` (mesmo pacote, mesmos métodos) e `net.minecraft.Util` → `net.minecraft.util.Util`. `RenderPipelines` **não** se mexeu. É jar próprio porque o binário muda, não porque a lógica muda |
| **26.1** | `GuiGraphics` → `GuiGraphicsExtractor`; `Screen#render` → `extractRenderState`; `renderBackground` → `extractBackground`; `renderWidget` → `extractWidgetRenderState`; `drawString` → `text`, `drawCenteredString` → `centeredText`. `CommandSuggestions#updateUsageInfo` passou a receber `(ParseResults, Suggestions)`. Java 25, Gradle 9.1+. A Mojang tirou a ofuscação: adeus Parchment e adeus refmap |
| **26.2** | a tela atual saiu do `Minecraft` e foi para o `Gui`: `minecraft.setScreen(x)` → `minecraft.gui.setScreen(x)`, `minecraft.screen` → `minecraft.gui.screen()` |

## A armadilha que o compilador nunca pega: o número da versão

Compilar não prova que a dependência existe. A verificação compila contra o **jar** que está
no disco; o `gradlew build` de quem for compilar resolve uma **coordenada Maven**. São coisas
diferentes, e um número errado passa aqui e quebra lá.

Foi o que aconteceu no port da 1.18.2: escrevi `jei_version_1182=9.7.2.1002`,
`rei_version_1182=8.3.564` e `fabric_api_version_1182=0.77.0+1.19.2` de cabeça, com os jars
certos baixados na pasta ao lado. Nenhum dos três existia — o JEI da 1.18.2 é da linha
**10.2**, o REI é **8.4.778**, e a Fabric API `0.77.0+1.19.2` é artefato de outra versão do
jogo. Os 16 alvos compilavam com zero avisos o tempo todo.

Duas regras saíram daí:

- **Número de versão se lê, não se deduz.** Do `META-INF/mods.toml` ou do `fabric.mod.json`
  de dentro do jar, ou do `maven-metadata.xml` do repositório. Nunca do que "faz sentido".
- **O nome do artefato também muda com o tempo.** Na 1.18.2 o EMI é `dev.emi:emi`, sem
  sufixo — `emi-fabric`/`emi-forge` só nasceram na 1.1.x, quando ele ganhou versão de Forge.
  E o EMI carimba o loader no fim da versão *do jar* (`1.1.24+1.20.1+forge`) enquanto no Maven
  o loader está no *nome do artefato* e a versão vai sem sufixo: os dois estão certos, só não
  são a mesma string.

A **seção 10** do `verify.sh` existe por causa disso: ela compara cada propriedade de versão
com o que está escrito dentro do jar correspondente.

## O `core/` é Java 8 — de propósito

O `core/` (1.788 linhas, o algoritmo) é o **mesmo arquivo** em todas as versões, e é isso que
faz uma correção no algoritmo valer para as nove de uma vez. Da 1.16.5 para trás o Minecraft
roda em Java 8, então o `core/` foi escrito em sintaxe Java 8 — que também é sintaxe Java 25
válida. **Uma cópia serve a todas**; duas cópias seriam duas vezes o mesmo bug para consertar.

O custo real foi pequeno: 12 pontos, todos mecânicos.

| Era | Virou | Onde |
|---|---|---|
| 2 `record` | classe final com construtor e acessores | `MatchPolicy`, `CommandFuzzy.Scored` |
| 4 `switch` de expressão (com `->`) | `switch` clássico com `break`/`return` | `SearchIndex` ×2 + `sourceBonus`, `SearchQuery.maxDistance` |
| 1 `switch` de statement com rótulo múltiplo | rótulos separados | `TextNormalizer` (14 casos) |
| 1 `instanceof` com padrão | `instanceof` + cast | `SearchSettings.equals` |
| 2 `List.of` | `Collections.emptyList()` / `unmodifiableList(Arrays.asList(...))` | `SearchIndex`, `SearchSettings` |

Nada dependia do `equals`/`hashCode` que o `record` dava de graça — os dois só são lidos. As
168 verificações passam igual antes e depois, e os 16 alvos modernos continuam com zero erro e
zero aviso.

A **seção 13** do `verify.sh` existe para isso não regredir: ela compila o `core/` com
`--release 8`. Sem ela, um `record` escrito daqui a três meses passaria nos 16 alvos modernos e
só quebraria no dia em que alguém fosse compilar a 1.16.5.

## 1.16.5 — o que a sonda já provou (e a armadilha do `--release`)

O ModDevGradle para na 1.17, então a 1.16.5 precisa de outra ferramenta. Isso foi testado, não
suposto, e o resultado até agora:

| | Resultado |
|---|---|
| **ForgeGradle 5.1.77** | **fora.** Ele mesmo diz: `Found Gradle version Gradle 9.6.1. Versions Gradle 8.0 and newer are not supported.` |
| **Architectury Loom 1.17.491** | configura Forge 1.16.5 no Gradle 9.6.1 sem reclamar |
| **Fabric Loom 1.17.17** | aceita a 1.16.5 e baixa os mapeamentos oficiais da Mojang |
| **os dois no mesmo build** | configuram lado a lado; a propriedade `loom.platform` **não** vaza para o módulo Fabric |

Dois detalhes que custaram uma rodada cada:

- O Architectury Loom escolhe Forge ou Fabric por **`loom.platform=forge`** no `gradle.properties`,
  e não pelo `build.gradle`. Sem isso ele sobe em modo Fabric e a configuração `forge` nem existe
  — o erro que aparece é `Could not find method forge()`, que parece incompatibilidade e não é.
  Num build de vários módulos, marque isso **por projeto** (via `gradle.beforeProject` no
  `settings.gradle`), nunca no `gradle.properties` da raiz, ou os 8 módulos Fabric quebram.
- **`options.release` não funciona com toolchain Java 8.** A flag `--release` nasceu no JDK 9;
  com a toolchain em 8, quem compila é o javac do JDK 8 e ele responde `invalid flag: --release`
  antes de compilar qualquer coisa. Com toolchain 8 não há o que configurar — o bytecode já sai
  em Java 8. (A alternativa é toolchain 17 + `options.release = 8`; aí a flag é válida.)

## 1.16.5 — concluída, e o que ela ensinou

Nove versões, dois visualizadores nos dois loaders. O que essa versão custou caro está aqui
porque **nada disso o compilador pega**.

### Um Minecraft, dois REIs diferentes

| | Fabric 1.16.5 | Forge 1.16.5 |
|---|---|---|
| REI | **5.12.385** — anterior à reescrita da API | **6.5.436** — API moderna |
| onde entra | `EntryListWidget.allStacks` (PUTFIELD) | `SearchProviderImpl.createFilter` |
| ordenar | não se aplica | `AsyncSearchManager.get()` |

O REI do Fabric funcionar não fazia o do Forge funcionar. São duas bases de código, dois
ganchos, duas seções de verificação (16 e 19).

### A armadilha mais cara: assinatura existe, significado não

O REI 6.5 não tem `isReloading()`. Traduzi para `getStage() != END`. Compilou, e a seção 19
aprovou — porque ela só conferia que o método **existe**. O corpo:

```
public ReloadStage getStage();
   0: getstatic  // Field ReloadStage.START
   3: areturn
```

Constante. O guarda era sempre verdadeiro, o índice nunca era montado, a busca do REI não
acontecia — sem crash e sem uma linha no log. Por isso a seção 19 passou a **desmontar o corpo**
dos métodos que o código usa como pergunta.

### As outras diferenças, todas lidas com javap

| | 1.18.2 | 1.16.5 |
|---|---|---|
| `createFilter` | `(String, InputMethod<?>)` | `(String)` |
| ordenar (REI) | `EntryListSearchManager.copyAndOrder` | não existe |
| JEI | `mezz.jei.common.ingredients` | `mezz.jei.ingredients` |
| elemento (JEI) | `ITypedIngredient` | `IIngredientListElementInfo` |
| id (JEI) | `getResourceLocation()` → objeto | `getResourceId()` → **String** |
| `elementSearch` | `private final` | `private`, **sem final** |
| Mixin do loader | 0.8.5 | **0.8.4** — pedir 0.8.5 é crash antes do menu |
| log | slf4j existe | **não existe** no Forge — só log4j |
| refmap | ModDevGradle precisa do processador | Architectury Loom **não** — declarar quebra o build |

O `elementSearch` merece nota: `@Shadow @Final` num campo que não é final faz o Mixin recusar a
classe inteira, e com `required: false` isso não vira crash — vira gancho ausente e silencioso.
Mesmo desfecho do `getStage()`.

### E o carregador de idiomas

No Forge 1.16.5 o `FMLClientSetupEvent` roda **dentro** da primeira carga de recursos: a lista de
listeners já tinha sido tirada quando o nosso foi registrado. A tabela só era montada num F3+T.
A saída não foi registrar mais cedo (durante o setup a carga ainda está correndo) e sim ler sob
demanda na primeira vez que alguém precisa dela — e, se vier vazia, **não** marcar prontidão,
senão o `matchesRequest` conclui "já está do jeito pedido" e o estado trava vazio para sempre.

### O que a 1.16.5 não tem, e por quê

- **JEI no Fabric**: o JEI 7.8 só existe para Forge nesta versão.
- **EMI**: não existe para 1.16.5 em loader nenhum. O primeiro lançamento foi na 1.18.2 —
  conferido na API do Modrinth. A opção continua no menu, como nas outras versões.

## 1.12.2 — concluída, e o que ela ensinou

A mais antiga do projeto, Forge só, e a única montada **sem Mixin nenhum** — nem para o
vanilla, nem para o visualizador. Três embrulhos no lugar deles, todos religados a cada
tique de cliente (idempotente, sobrevive a qualquer re-registro):

- **criativo e livro de receitas**: o `SearchTreeManager.register` desta era é público, então
  a árvore vanilla é trocada por uma subclasse que delega tudo e responde a busca
  (`ArvoreEnvolvida`);
- **JEI 4.16**: o `getIngredientListUncached` é privado (na 7.8 era público), mas cada
  palavra digitada passa por `combinedSearchTrees` — campo **não-final**, classe pública
  não-final com construtor público. A `ArvoreJei` devolve a **união** "deles + nossos" por
  palavra; a interseção entre palavras, os prefixos (`@mod`, `#tooltip`), a exclusão com
  `-` e o modo de edição continuam do JEI. O índice (`BuscaJei`) lê a `elementList` dele por
  reflexão de leitura e usa a posição como id — o mesmo número que as árvores dele devolvem,
  provado no bytecode do `addIngredient` (`put` recebe `size()`).

O ferramental é outro mundo: **RetroFuturaGradle** (nexus da GTNH; o plugin é compilado para
Java 25, daí o `gradle/gradle-daemon-jvm.properties` da raiz), MCP `stable_39` — todo alvo
lido com javap do jar remapeado, nunca traduzido de cabeça — e recursos que **moram no
módulo forge/** (srcDir de resources do common não chegava ao jar; fato de disco em vez de
comportamento de plugin).

As armadilhas que valem dinheiro:

- **`.lang` passa por `String.format` sempre**, com ou sem argumento: um `%tab` literal numa
  tradução vira `Format error:` na tela. `%` só como `%s`/`%d` de verdade ou `%%` (seção 23
  do verify).
- **JEI fora de Maven**: o 4.16.5.1027 do pack não está em repositório declarável. A saída
  foi um sourceSet `jeiApi` de **esboços de compilação** (mezz + fastutil, membro por membro
  lidos com javap do jar real), `compileOnly` do output — nunca empacotado; o publicar tem
  trava contra vazamento e a seção 22 reconfere esboço por esboço contra o jar.
- **menu em `GuiScreen`**: o `mouseClicked` desta era continua varrendo a `buttonList` depois
  do `actionPerformed`, então TODA reconstrução de widgets é adiada para o começo do quadro
  seguinte; roda do mouse é `handleMouseInput` + LWJGL; dica de widget é desenhada na mão.
  Fora isso o menu é o mesmo das outras versões, arquivo por arquivo.
- **Alt+O**: o sistema de modificadores do Forge desta era falha com ALT; além do caminho
  oficial há a leitura direta do teclado (só enquanto o atalho está no padrão).

### O que a 1.12.2 não tem, e por quê

- **REI e EMI**: nunca existiram para 1.12.2. É a única versão em que os interruptores deles
  **não aparecem** no menu — por ordem do dono do mod, já que não governariam nada.
- **NEI**: o port do covers1624 roda **em cima do JEI**, então o gancho do JEI já o cobre —
  por isso a opção se chama "JEI & NEI" nesta versão.

## 1.21.4 — concluída, e o que ela ensinou

Base 1.21.1, livro de receitas da 1.21.9 (`RecipeDisplayEntry`, a reforma da 1.21.2), blit na
forma `RenderType::guiTextured` — três rodadas de campo no Titan Survival ensinaram quatro
lições que valem mais que o port:

- **linha "estável" não é linha imutável.** O NeoForge retroportou para os builds tardios da
  21.4 o rename do evento de reload (`Register…` → `AddClientReloadListenersEvent`) e
  **removeu** o nome velho. Um jar que cite qualquer um dos dois pelo tipo quebra na outra
  metade da linha. Solução: duas classes registradoras isoladas (padrão dos ganchos de
  visualizador), escolhidas por `Class.forName`, com o evento velho compilado contra esboço
  (que compila junto do `main` — precisa dos tipos reais — e é barrado do jar por `exclude`
  com trava que quebra o build se vazar).
- **deprecated pode ser MURO, não aviso.** O caminho vanilla
  (`ReloadableResourceManager.registerReloadListener`) compila limpo e lança
  `UnsupportedOperationException` em jogo: o NeoForge congela a lista depois do boot.
  "Assinatura existir não é significar" vale para o RUNTIME também — o teste de campo é a
  única prova.
- **pacote podre de terceiro envenena a listagem inteira.** O `TranslationsPack` do Chunks
  Fade In estoura `ArrayIndexOutOfBounds` no próprio `listResources`, e o
  `listResourceStacks` do jogo propaga — um mod quebrado custava os 18 idiomas. A tabela de
  idiomas agora anda pelos pacotes UM A UM (`listPacks` + `listResources` por namespace),
  com isolamento e nome do culpado no log. De quebra, o cinto de segurança: se o listener de
  reload não se registrar por qualquer motivo, o primeiro tique dispara a mesma carga.
- **no Fabric, o mapa de teclas tem UM dono por tecla física.** Com o Iris também no "O", o
  clique nunca chega ao nosso `KeyMapping` — `consumeClick` fica surdo e qualquer lógica em
  cima dele é letra morta (no NeoForge o `KeyModifier.ALT` resolve tudo). O Alt+O do Fabric
  virou três barreiras: leitura crua do teclado com borda própria (a marra da 1.12.2, edição
  GLFW), um mixin em `Minecraft.setScreen` que **proíbe** a `ShaderPackScreen` do Iris
  enquanto o Alt está pressionado (por NOME de classe, sem dependência; rebindou, desliga),
  e a abertura adiada de um tique como faxina. As demais versões Fabric têm o mesmo conflito
  LATENTE com Iris — a marra ainda não foi levada até elas; se aparecer em campo, o desenho
  está aqui.

### O que a 1.21.4 não tem, e por quê

- **JEI no Fabric**: o JEI 20.x da 1.21.4 só publica artefatos NeoForge (maven do JEI,
  conferido) — como na 1.16.5, o gancho mora só no módulo neoforge.
- **EMI**: não existe para 1.21.4 em loader nenhum (API do Modrinth, lista vazia). O gancho
  compila contra o jar 1.21.1, pronto para um port de terceiro, como na 1.21.9.

## A outra armadilha: `common/` é compartilhado, a dependência não é

O `common/` entra como pasta de fonte nos **dois** módulos de uma versão. A dependência do JEI,
do EMI e do REI, não — ela é declarada por módulo, no `build.gradle` de cada um.

Enquanto os três visualizadores existem para os dois loaders, ninguém percebe a diferença. Na
1.18.2 ela aparece: **o EMI só tem lançamento de Fabric**. O módulo Forge não tem contra o que
compilar — e o jar de Fabric do EMI 0.7.3 também não serve, porque traz o Minecraft em nomes
*intermediary* (`net.minecraft.class_1799` no lugar de `ItemStack`). Mesmo assim os três
arquivos do gancho continuavam entrando na compilação dele: `package dev.emi does not exist`,
19 erros, build morto.

E o `verify.sh` tinha dito que estava tudo certo — porque o classpath dele era **escrito à
mão** e mais generoso que o build de verdade: o jar do EMI da 1.19.2 entrava ali para os dois
alvos da 1.18.2. Verificação que é mais permissiva que o build não verifica nada.

**A correção.** Os quatro arquivos do gancho do EMI saíram do `common/` e foram para
`versions/mc1_18_2/fabric/src/main/` — o módulo que consegue compilá-los. A opção no menu
continua aparecendo nas duas, porque ela mora no `core/` e não depende de mod nenhum.

Dá para resolver isso com um `exclude` no `sourceSets` do módulo Forge, e foi a primeira coisa
que eu tentei. **Não faça.** `exclude` é comportamento do Gradle, e comportamento do Gradle não
dá para conferir sem rodar o Gradle — que é justamente o que este projeto não consegue fazer
offline. Uma verificação que não consegue provar a própria correção não vale nada. Mover o
arquivo de pasta é um fato do disco: o `find` enxerga, o `javac` enxerga, o `verify.sh` enxerga.

**As duas mudanças na verificação:**

- A **seção 1** parou de receber classpath escrito à mão. Ela lê o `build.gradle` de cada
  módulo e monta o classpath a partir do que aquele módulo declara de verdade — comentário
  citando `modCompileOnly` não conta como declaração. Com isso ela reproduz o erro real:
  `package dev.emi.emi.api.stack does not exist`, igualzinho ao do `gradlew build`.
- A **seção 11** é a regra dita direto: junte os `.java` que o módulo compila (`core/` +
  `common/` + a pasta dele), veja quais mods opcionais eles **importam**, e cobre a declaração
  correspondente no `build.gradle`. Também cobra o inverso (declarar e não usar) e impede que
  o `MixinConfigs` ou o `fabric.mod.json` anunciem um arquivo de mixin que não vai entrar no
  jar. Nenhuma dessas contas depende de comportamento de plugin.

## A armadilha que só aparece com o jogo ligado: o dono da chamada

O Mixin **não compara tipos, compara o descritor escrito na instrução**. Isso vira um problema
sempre que uma interface estende outra.

Na 1.18.2 o `Minecraft.getSearchTree` devolve `MutableSearchTree<T>` — subinterface de
`SearchTree` com `add`/`clear`/`refresh`, que deixou de existir na 1.19. O bytecode do
`RecipeBookComponent.updateCollections` traz, portanto:

```
invokeinterface net/minecraft/client/searchtree/MutableSearchTree.search:(Ljava/lang/String;)Ljava/util/List;
```

O `@Redirect` herdado da 1.19.2 mirava em `Lnet/minecraft/client/searchtree/SearchTree;search…`.
O **javac aceita as duas**, porque uma estende a outra e a chamada `tree.search(query)` compila
igual. O Mixin não aceita: `failed injection check, (0/1) succeeded`, e o jogo **não abre**.

Esse é o único erro do projeto que atravessa 16 compilações limpas com `-Xlint:all` e só se
manifesta com o Minecraft ligado. E ele não é raro em port: toda vez que a Mojang funde ou
divide uma interface, o dono da chamada muda sem que o código-fonte mude uma vírgula.

A **seção 12** existe por isso. Ela desmonta a classe atacada no jar de verdade e cobra o
descritor exato de cada `@Redirect` obrigatório — o que o `PORTING.md` mandava fazer na mão no
passo 4, e que na 1.18.2 eu fiz pela metade. Quando falha, ela ainda diz qual é o nome certo:

```
RecipeBookComponentMixin.java: INVOKE de 'updateCollections' mira em
      net/minecraft/client/searchtree/SearchTree.search:(Ljava/lang/String;)Ljava/util/List;
    que NAO aparece no bytecode de …RecipeBookComponent
    o que existe ali e: net/minecraft/client/searchtree/MutableSearchTree.search:(…)
```

Ganchos com `require = 0` (o do `UNPARSED_STYLE`, os do JEI/EMI/REI) ficam de fora de propósito:
se eles sumirem, o mod perde aquele detalhe e continua rodando.

## O ciclo de verificação que funciona

Sem abrir o Minecraft, `tools/verify.sh` cobre doze frentes. As três primeiras são a base:

1. **compila cada loader contra o jar real** que o Gradle baixou, com `-Xlint:all`;
2. roda as **168 verificações** do algoritmo (Java puro — valem em qualquer versão);
3. valida todo JSON de idioma, de mixin e o `fabric.mod.json`.

As outras nove existem porque cada uma nasceu de um bug que passou: sincronia dos mixins do
Forge (4), `compatibilityLevel` igual dentro de uma versão (5), os ganchos do JEI, EMI e REI
conferidos com `javap` no jar de verdade (6, 7, 8), nenhuma classe sempre-carregada
alcançando um mod opcional (9), os números de versão batendo com os jars (10) e cada módulo
compilando só o gancho do mod que ele declara (11) e o descritor de cada `@Redirect`
conferido no bytecode real (12).

O que ele **não** pega: comportamento em tempo de execução. Foi por isso que o bug do TAB no
chat e o do índice sendo descartado a cada mudança de opção só apareceram jogando. Para
esses, o caminho é ler o código decompilado (`neoforge-*-sources.jar`) e conferir a hipótese
no bytecode com `javap` — não chutar.
