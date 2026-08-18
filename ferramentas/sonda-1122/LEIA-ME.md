# Sonda da 1.12.2

Mesma disciplina da 1.16.5: **nenhuma linha do port existe antes de a ferramenta de build ser
provada nesta máquina.** Três perguntas, uma por sonda:

| sonda | pergunta | se PASSAR |
|---|---|---|
| **A** RetroFuturaGradle 2.0.2 | monta 1.12.2 no Gradle 9.6.1 daqui? | temos ferramenta; exporta o `mc1122-a*.jar` para javap |
| **C** RFG + Fabric Loom juntos | os dois convivem num build só? | a 1.12.2 entra no build principal, `gradlew :versions:mc1_12_2:forge:build` |

## B (Architectury Loom): respondida e eliminada na rodada 1

O Loom aceitou a 1.12.2 e os mapeamentos MCP, mas foi buscar o `forge-1.12.2-*-userdev.jar` —
e esse classifier **nunca foi publicado** para a 1.12.2 (404 no `.sha1` dos builds 2860 e
2864, conferido direto no maven da Forge). O que existe lá é o `userdev3`, o formato FG3
retroportado, que é exatamente o que o RFG consome. Na 1.16.5 o `-userdev.jar` existe — por
isso o Loom serviu lá e não serve aqui. Falta de artefato obrigatório, não opinião.

Se A passar e C falhar, a 1.12.2 vira um sub-build com wrapper próprio — funciona igual por
fora, muda só a encanação.

O jar exportado (`mc1122-*.jar`, em nomes MCP) é o que permite escrever os mixins sem chutar:
`javap` em `GuiContainerCreative`, `SearchTree`, `GuiRecipeBook` — a mesma conta que pegou o
`MutableSearchTree` na 1.18.2 e o `getStage()` constante no REI 6.5.
