# Better Search

Barras de busca do Minecraft que **adivinham o que você quis dizer**.
Minecraft **26.2**, **26.1–26.1.2**, **1.21.11**, **1.21.9–1.21.10**, **1.21.1** e **1.20.1**,
no NeoForge, no Fabric e no Forge — só cliente, não precisa estar no servidor.

Atua em três lugares, cada um com liga/desliga próprio:

| Onde | O que ganha | Padrão |
|---|---|---|
| **Menu Criativo** | tudo: acentos, erro de digitação, pedaço de palavra, outros idiomas, relevância | ligado |
| **Livro de Receitas** | o mesmo, buscando pelo nome do item que a receita produz | ligado |
| **Sugestões de comando** | corrige a palavra errada do comando; nomes de jogadores tolerando erro; IDs de item pelo nome traduzido | ligado |

O mod **não mexe no JEI**, nem em nenhuma outra mod: ele só melhora as barras de busca do
próprio Minecraft.

```
/gamemode criativo -> creative        (comando não tem tradução; o mod acha a opção certa)
/gamerule ki       -> keepInventory
bau              -> Baú, Baú Armadilha
nether sword     -> Espada de Netherite
netheritesword   -> Espada de Netherite
dimaond          -> Diamante, Espada de Diamante
pomme            -> Maçã          (jogo em português, palavra em francês)
crafting table   -> Bancada de Trabalho
ds               -> Espada de Diamante
@create eixo     -> só itens do Create
```

## O que ele faz

| | |
|---|---|
| **Ignora acentos** | `bau` = `baú`, `acucar` = `açúcar`, `perola` = `pérola` |
| **Pedaço de palavra** | `nether sword` acha *Espada de Netherite*, `dia` acha *Diamante* |
| **Ordem livre** | `sword netherite` = `netherite sword` |
| **Sem espaço** | `bancadadetrabalho` acha *Bancada de Trabalho* |
| **Erro de digitação** | `dimaond`, `swrod`, `netherrite` funcionam |
| **Iniciais** | `ct` acha *Crafting Table* |
| **Qualquer idioma** | com o jogo em PT-BR, `apple`, `pomme`, `manzana`, `apfel` e `苹果` acham a Maçã |
| **Frases misturadas** | `pomme dourada` acha a Maçã Dourada |
| **Por id** | `diamond_sword`, `minecraft:sugar` |
| **Por mod** | `@create engrenagem` |
| **Ordem por relevância** | o item mais parecido vem primeiro, não o que estiver antes na aba |

Continua funcionando como antes: `#tag` (busca por tag) segue sendo tratado pelo próprio
Minecraft, e encantamentos/poções continuam encontráveis pelo texto da tooltip
(`sharpness`, `afiação`, `visão noturna`).

## Instalar

1. Instale o loader da sua versão e jogue o `.jar` correspondente na pasta `mods`:

| Minecraft | Arquivo | Serve para |
|---|---|---|
| 26.2 | `bettersearch-neoforge-26.2-*.jar` | NeoForge 26.2.x |
| 26.2 | `bettersearch-fabric-26.2-*.jar` | Fabric + Fabric API |
| 26.1 / 26.1.1 / 26.1.2 | `bettersearch-neoforge-26.1.2-*.jar` | NeoForge 26.1.x |
| 26.1 / 26.1.1 / 26.1.2 | `bettersearch-fabric-26.1.2-*.jar` | Fabric + Fabric API |
| 1.21.11 | `bettersearch-neoforge-1.21.11-*.jar` | NeoForge 21.11.x |
| 1.21.11 | `bettersearch-fabric-1.21.11-*.jar` | Fabric + Fabric API |
| 1.21.9 / 1.21.10 | `bettersearch-neoforge-1.21.9-*.jar` | NeoForge 21.9.x e 21.10.x |
| 1.21.9 / 1.21.10 | `bettersearch-fabric-1.21.9-*.jar` | Fabric + Fabric API |
| 1.21.1 | `bettersearch-neoforge-1.21.1-*.jar` | NeoForge 21.1.x |
| 1.21.1 | `bettersearch-fabric-1.21.1-*.jar` | Fabric + Fabric API |
| 1.20.1 | `bettersearch-forge-1.20.1-*.jar` | **Forge 47.x e NeoForge 20.1** (o mesmo arquivo) |
| 1.20.1 | `bettersearch-fabric-1.20.1-*.jar` | Fabric + Fabric API |

No Fabric, o botão de configuração aparece no **Mod Menu**, se você o tiver; o atalho
**Alt + O** funciona em todos os loaders, com ou sem Mod Menu.

Só isso. Na primeira vez que a aba de busca abre, o mod monta o índice em segundo plano
(alguns décimos de segundo); enquanto isso a busca original continua funcionando normalmente.

## Compilar

```bash
./gradlew dist                        # constroi TODOS os loaders -> build/dist/
./gradlew coreTest                    # testa o algoritmo, sem abrir o jogo
./gradlew :mc1_21_1:neoforge:runClient  # abre o Minecraft com o mod (NeoForge)
./gradlew :mc1_21_1:fabric:runClient    # idem, no Fabric
./gradlew :mc1_20_1:forge:build         # só o jar do Forge/NeoForge 1.20.1
```

Precisa de **JDK 25** (é ele que roda o Gradle) e de internet na primeira execução. Os JDKs
de cada versão — 17 para a 1.20.1, 21 para a 1.21.x, 25 para a 26.x — o Gradle baixa sozinho.

O projeto é multi-loader: `core/` guarda o algoritmo (Java puro, compartilhado por todas as
versões), `mc1_21_1/common/` guarda o resto do mod, e `mc1_21_1/neoforge/` e
`mc1_21_1/fabric/` têm só o punhado de arquivos que cada loader exige. Veja
[PORTING.md](PORTING.md) para acrescentar uma versão.

## Configuração

**Tudo se ajusta dentro do jogo**: menu principal → **Mods** → **Better Search** → **Config**.

A tela tem quatro abas, uma lista de opções à esquerda e um painel à direita que explica a
opção sob o cursor e mostra **uma foto dela funcionando**. Cada opção é um **interruptor**
ou um **slider** — nenhum campo de texto, então não dá para digitar um valor inválido.
A explicação vem em três camadas: o nome da opção, a frase do painel e a dica que aparece
ao parar o cursor sobre o controle (é lá que ficam os detalhes técnicos). Cada linha tem
um botão **↺** que
restaura só aquela opção, e o rodapé traz **Padrões**, **Desfazer** e **Concluído**
(os dois primeiros só acendem quando há algo para reverter). As mudanças são salvas e
aplicadas ao fechar a tela.

**Geral**

| Opção | Padrão | O que faz |
|---|---|---|
| Ativar o Better Search | ligado | desligado devolve a busca original do Minecraft |
| Ordenar por relevância | ligado | ou mantém a ordem das abas do criativo |
| Limite de resultados | sem limite | quantos itens uma busca pode devolver |

**Correspondência**

| Opção | Padrão | O que faz |
|---|---|---|
| Tolerância a erros | Normal | Desligada / Baixa / Normal / Alta |
| Tamanho mínimo da palavra | 4 letras | palavras menores precisam estar escritas certas |
| Casar iniciais | ligado | `bdt` acha *Bancada de Trabalho* |
| Ignorar espaços | ligado | `maçadourada` acha *Maçã Dourada* |
| Buscar nas dicas | ligado | acha `afiação`, `visão noturna` |
| Buscar por ID | ligado | `redstone_torch` |
| Filtro de mod | ligado | `@create engrenagem` |
| Quando corrigir erros | Equilibrado | Nunca / Raramente / Equilibrado / Frequente / Sempre |
| Quando misturar idiomas | Equilibrado | idem — quanto mais alto, mais o mod insiste |

**Idiomas**

| Opção | Padrão | O que faz |
|---|---|---|
| Busca entre idiomas | ligado | `pomme` acha a maçã |
| Idiomas estrangeiros exatos | ligado | evita ruído quando muitos idiomas estão ligados |
| Frases entre idiomas | ligado | `pomme dourada` |
| **Idiomas ativos** | 18 ligados | abre a lista com **cada idioma do jogo**, um interruptor por linha |

**Avançado** — liga e desliga cada tela separadamente

| Opção | Padrão | O que faz |
|---|---|---|
| Menu Criativo | ligado | a busca principal do mod |
| Livro de Receitas | ligado | acha a receita pelo nome do resultado |
| Correção de comandos | ligado | `/gamemode criativo` sugere `creative` |
| Sugestão de Jogadores | ligado | `/msg Steev` sugere *Steve* |
| Sugestão de ID de Item | ligado | `/give @p bau` sugere `minecraft:chest` |
| Limite de Sugestões | 12 | quantas entradas o mod pode acrescentar ao autocompletar |

A lista de idiomas mostra todos os que o seu Minecraft tem (inclusive os que vierem de
resource packs), um interruptor por linha, com uma **barra de busca** (que também ignora
acentos: `portugues` acha `Português`) e botões **Todos / Nenhum / Padrão**. O idioma que
você está jogando funciona sempre, mesmo desligado nessa lista.

Ligar ou desligar um idioma vale na hora — o índice é refeito sozinho, sem reiniciar o jogo.

Idiomas ligados por padrão: `en_us`, `es_es`, `es_mx`, `pt_br`, `pt_pt`, `fr_fr`, `de_de`,
`it_it`, `nl_nl`, `pl_pl`, `ru_ru`, `uk_ua`, `tr_tr`, `sv_se`, `zh_cn`, `zh_tw`, `ja_jp`, `ko_kr`.

Quem preferir editar na mão: `config/bettersearch.json`, lido na inicialização do jogo.

## Como funciona

**Índice** (uma vez, em segundo plano). Para cada item da aba de busca o mod guarda o nome
no seu idioma, o nome em cada idioma configurado, o id e as linhas de tooltip que
carregam informação de verdade. Cada texto é normalizado (minúsculo, sem acento,
separadores viram espaço), tem os limites de palavra marcados e ganha uma assinatura de
64 bits com os caracteres que contém. Textos repetidos entre idiomas são guardados uma vez só.

**Busca** (a cada tecla). O texto digitado passa pela mesma normalização e é comparado em
camadas, da mais forte para a mais fraca — exato, prefixo do texto, palavra exata, prefixo
de palavra, sem espaços, substring, iniciais e, por último, distância de edição
(Damerau-Levenshtein, que também entende letras trocadas de lugar). A primeira camada que
casa vence, então o caso comum custa um `equals`.

A camada cara (erro de digitação) só roda se as outras devolveram poucos resultados, e é
protegida por um filtro de uma instrução (`AND` + `popcount` das assinaturas) que descarta
a maioria dos itens antes de qualquer conta. A pontuação final mistura a camada alcançada,
o idioma (o seu vale mais que inglês, que vale mais que os outros), a ordem das palavras e
quanto do nome do item a busca cobriu.

**Não trava o jogo.** Se o índice ainda está sendo montado, se a configuração desliga o mod
ou se algo dá errado, o mod simplesmente não faz nada e os resultados da busca original
ficam na tela.

## Estrutura

```
core/       Java puro — normalização, algoritmo, índice, pontuação. Zero Minecraft.
client/     Ponte com o jogo — lê os arquivos de idioma, monta o índice a partir dos itens.
client/gui/ Telas de configuração (só classes vanilla, servem em qualquer loader).
mixin/      Um único gancho, no fim de refreshSearchResults.
neoforge/   Ponto de entrada do NeoForge (~40 linhas).
tools/      Teste do algoritmo que roda sem o Minecraft.
```

Para portar para Fabric ou outra versão, veja [PORTING.md](PORTING.md).

## Licença

MIT.
