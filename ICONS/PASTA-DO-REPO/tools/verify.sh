#!/bin/bash
# Verificacao local do Better Search, sem abrir o Minecraft.
#
#   1. compila CADA alvo (versao x loader) contra o jar real daquela versao do Minecraft
#   2. compila e roda os testes do algoritmo (Java puro, sem versao e sem loader)
#   3. valida os JSON de idioma, de mixin e os metadados dos loaders
#   4. confere que os dois arquivos de mixin da 1.20.1 nao sairam de sincronia
#
# Os jars do Minecraft e as pastas de "stubs" (classes falsas das APIs que nao vem dentro do
# jar, como o Brigadier e o loader) sao passados por variavel de ambiente - veja abaixo.
#
# Uso: bash tools/verify.sh
set -u
cd "$(dirname "$0")/.."

# Todo o corpo roda dentro desta funcao por um motivo so: CODIGO DE SAIDA.
#
# Antes daqui, cada secao imprimia o seu erro e o script terminava com o codigo da ULTIMA
# secao - entao dava para ver "PROBLEMA" e "ERRO" na tela e o `echo $?` responder 0. Qualquer
# script que confiasse no codigo de saida (um publicar-*.ps1, um passo de CI) veria verde com
# o build quebrado na tela. Foi assim que a 1.16.5 forge quebrou e o verify "passou".
#
# Somar falha a falha em 18 secoes daria 18 lugares para esquecer um. Ler a propria saida no
# fim e um lugar so, e pega tambem a secao 1, que imprime PROBLEMA sem ter codigo de saida.
corpo() {

EXTRA_CP="${BS_EXTRA_CP:-}"

# jar do Minecraft por linha de versao (BS_JAR_<versao> sobrescreve)
JAR_1_21_1="${BS_JAR_1_21_1:-$(ls versions/mc1_21_1/neoforge/build/moddev/artifacts/neoforge-*-merged.jar 2>/dev/null | head -1)}"
JAR_1_21_9="${BS_JAR_1_21_9:-$(ls versions/mc1_21_9/neoforge/build/moddev/artifacts/neoforge-*-merged.jar 2>/dev/null | head -1)}"
JAR_1_21_11="${BS_JAR_1_21_11:-$(ls versions/mc1_21_11/neoforge/build/moddev/artifacts/neoforge-*-merged.jar 2>/dev/null | head -1)}"
JAR_1_20_1="${BS_JAR_1_20_1:-$(ls versions/mc1_20_1/fabric/build/mcjar/*.jar 2>/dev/null | head -1)}"
JAR_1_19_2="${BS_JAR_1_19_2:-$(ls versions/mc1_19_2/forge/build/moddev/artifacts/forge-*-merged.jar 2>/dev/null | head -1)}"
JAR_1_18_2="${BS_JAR_1_18_2:-$(ls versions/mc1_18_2/forge/build/moddev/artifacts/forge-*-merged.jar 2>/dev/null | head -1)}"
# A 1.16.5 nao usa o ModDevGradle: o jar dela sai do Architectury Loom, por outro caminho.
JAR_1_16_5="${BS_JAR_1_16_5:-}"
# 1.12.2: o jar sai do RetroFuturaGradle (pasta patchedMc empacotada pela sonda). Nomes MCP.
JAR_1_12_2="${BS_JAR_1_12_2:-}"
# Stubs proprios da 1.16.5: o jar merged de la ja traz o FML de verdade, entao os stubs da
# 1.19.2 nao servem - eles ESCONDERIAM o ModLoadingContext real e a assinatura nao bateria.
STUBS_FORGE_1165="${BS_STUBS_FORGE_1165:-}"
JAR_26_1="${BS_JAR_26_1:-$(ls versions/mc26_1/neoforge/build/moddev/artifacts/minecraft-patched-*-merged.jar 2>/dev/null | head -1)}"
JAR_26_2="${BS_JAR_26_2:-$(ls versions/mc26_2/neoforge/build/moddev/artifacts/minecraft-patched-*-merged.jar 2>/dev/null | head -1)}"

# Da 26.1 em diante o NeoForge e a Fabric API NAO vem dentro do jar do Minecraft: sao
# artefatos separados. Estas variaveis levam esses jars ao classpath.
CP_26_1_NEO="${BS_CP_26_1_NEO:-}"
# O jar do Minecraft que temos aqui e o do NeoForge (com os patches dele). Para CONFERIR o
# alvo Fabric e preciso por o NeoForge no classpath tambem, senao o javac reclama de classes
# de extensao que o proprio Minecraft passou a implementar. No build de verdade isso nao
# existe: o Loom entrega um Minecraft sem esses patches.
CP_26_1_FABRIC="${BS_CP_26_1_FABRIC:-}"
CP_26_2_NEO="${BS_CP_26_2_NEO:-}"
CP_26_2_FABRIC="${BS_CP_26_2_FABRIC:-}"

# Jars do JEI, um por linha de versao. So compilacao: o gancho da busca mira em classe interna
# do JEI e a unica forma honesta de conferir o nome dela e compilando contra o jar de verdade.
JEI_1_20_1="${BS_JEI_1_20_1:-}"
JEI_1_19_2="${BS_JEI_1_19_2:-}"
JEI_1_18_2="${BS_JEI_1_18_2:-}"
# O JEI da 1.16.5 e a linha 7.8, anterior a separacao em "common": outra forma, outro gancho.
JEI_1_16_5="${BS_JEI_1_16_5:-}"
JAR_1_21_4="${BS_JAR_1_21_4:-}"
JEI_1_21_4="${BS_JEI_1_21_4:-}"
REI_1_21_4="${BS_REI_1_21_4:-}"
JEI_1_12_2="${BS_JEI_1_12_2:-}"
JEI_1_21_1="${BS_JEI_1_21_1:-}"
JEI_1_21_9="${BS_JEI_1_21_9:-}"
JEI_1_21_11="${BS_JEI_1_21_11:-}"
JEI_26_1="${BS_JEI_26_1:-}"
JEI_26_2="${BS_JEI_26_2:-}"
REI_1_20_1="${BS_REI_1_20_1:-}"
REI_1_19_2="${BS_REI_1_19_2:-}"
REI_1_18_2="${BS_REI_1_18_2:-}"
# O REI da 1.16.5 no Fabric e a linha 5.x, anterior a reescrita da API - outro gancho, outra
# secao (a 16). E o jar dele e um involucro: o codigo real esta em jars aninhados dentro.
REI_1_16_5_FABRIC="${BS_REI_1_16_5_FABRIC:-}"
# E o REI da 1.16.5 no FORGE e a linha 6.5 - a API ja moderna, mas anterior ao InputMethod e ao
# EntryListSearchManager. Mesmo Minecraft, duas bases de codigo, dois ganchos: secao 19.
REI_1_16_5_FORGE="${BS_REI_1_16_5_FORGE:-}"
REI_1_21_1="${BS_REI_1_21_1:-}"
REI_1_21_9="${BS_REI_1_21_9:-}"
REI_1_21_11="${BS_REI_1_21_11:-}"
REI_26_1="${BS_REI_26_1:-}"
REI_26_2="${BS_REI_26_2:-}"

# O EMI so tem lancamento oficial para 1.20.1 e 1.21.1. Nas outras quatro o gancho existe
# assim mesmo, para o caso de alguem portar, e e compilado contra o jar da 1.21.1.
EMI_1_20_1="${BS_EMI_1_20_1:-}"
EMI_1_21_1="${BS_EMI_1_21_1:-}"
EMI_1_19_2="${BS_EMI_1_19_2:-}"
# O EMI da 1.18.2 so sai para Fabric, e o jar dele vem em intermediary - nao serve para
# compilar contra um Minecraft em nomes da Mojang. A API que o mod usa (getEmiStacks,
# getItemStack, getName, getId) e identica na 1.19.2, entao a conferencia de compilacao
# usa aquele jar. O que e proprio da 1.18.2 - o campo query, o descritor apply(List) e a
# ordinal 0 - foi conferido com javap no jar de verdade, e a secao 7 refaz essa conta.
EMI_1_18_2="${BS_EMI_1_18_2:-$EMI_1_19_2}"
# O jar de VERDADE da 1.18.2 (intermediary). Nao serve para compilar, mas serve - e e o
# unico que serve - para conferir o formato do gancho na secao 7.
BS_EMI_1_18_2_REAL="${BS_EMI_1_18_2_REAL:-}"
# Nas quatro versoes acima da 1.21.1 o EMI nao tem lancamento oficial. O gancho existe
# assim mesmo (alguem pode portar) e e compilado contra este mesmo jar.
EMI_PORT="${BS_EMI_PORT:-$EMI_1_21_1}"

# A 26.x compila em Java 25. Numa maquina sem JDK 25 da para conferir em um nivel mais baixo:
# o codigo do mod nao usa nada alem de Java 17, entao a checagem continua valendo.
RELEASE_CAP="${BS_RELEASE_CAP:-99}"

# pastas de stubs (vazias = alvo compila sem elas, o que normalmente da erro)
STUBS="${BS_STUBS:-}"                     # brigadier, GLFW, anotacoes do Mixin (1.21.1/1.20.1)
STUBS_2119="${BS_STUBS_2119:-}"           # idem, para a 1.21.9
STUBS_FABRIC="${BS_STUBS_FABRIC:-}"       # API do Fabric + Mod Menu
STUBS_FABRIC_2119="${BS_STUBS_FABRIC_2119:-}"
# A 1.21.11 precisa da propria copia: la o ResourceLocation chama-se Identifier, e o stub do
# listener do Fabric declara o tipo de retorno com esse nome.
STUBS_FABRIC_21111="${BS_STUBS_FABRIC_21111:-}"
# Na 26.x so o Mod Menu continua sendo stub: ele e compileOnly e nem sempre esta baixado.
STUBS_MODMENU="${BS_STUBS_MODMENU:-}"
STUBS_FORGE="${BS_STUBS_FORGE:-}"         # API do Forge 1.20.1
# A 1.19.2 precisa de bem menos: o jar merged do Forge 43 ja traz net.minecraftforge.client
# de verdade, entao so faltam fml, eventbus e distmarker. Quanto menos stub, mais real e a
# checagem - foi assim que se confirmou que ali o nome e ConfigScreenHandler, e nao
# ConfigGuiHandler como a idade da versao faria supor.
STUBS_FORGE_1192="${BS_STUBS_FORGE_1192:-}"
# com.mojang:bridge - o LanguageInfo da 1.19.2 implementa uma interface de fora do jar.
STUBS_BRIDGE="${BS_STUBS_BRIDGE:-}"
STUBS_NEO="${BS_STUBS_NEO:-}"             # FML e barramento de eventos do NeoForge
# REI 5.x da 1.16.5. Aqui o jar de verdade NAO da para por no classpath: ele vem em nomes
# intermediary (net.minecraft.class_1799) e o javac puro nao traduz - so o Loom traduz, e o
# Loom so roda no build de verdade. Entao este alvo compila contra um stub, e quem cobra que
# o stub nao mente e a secao 16, lendo o bytecode do jar publicado.
STUBS_REI512="${BS_STUBS_REI512:-}"
# Idem para o REI 6.5 da 1.16.5 Forge, pelo mesmo motivo (nomes MCP no jar publicado).
STUBS_REI65="${BS_STUBS_REI65:-}"
# JEI 7.8 da 1.16.5, mesmo motivo (nomes MCP no jar publicado). Cobrado pela secao 21.
STUBS_JEI781="${BS_STUBS_JEI781:-}"
# Teclado do LWJGL 2 (1.12.2). CUIDADO: static final int inlina - o valor do stub navega no
# jar. KEY_O=24 e o valor real do LWJGL 2; errar nao daria erro, so abriria na tecla errada.
STUBS_LWJGL2="${BS_STUBS_LWJGL2:-}"
# com.mojang.serialization.Codec - a 1.16.5 usa DataFixerUpper de fora do jar do Minecraft.
STUBS_DFU="${BS_STUBS_DFU:-}"

# Pasta vazia usada como -sourcepath. Sem isto o javac encontra os .java que vem DENTRO do jar
# do NeoForge e tenta compilar o Minecraft inteiro junto.
EMPTY=build/verify/.empty
mkdir -p "$EMPTY"

# Este modulo declara aquele mod como dependencia? So conta linha de dependencia de verdade:
# o "^[^/]*" impede que uma linha de COMENTARIO citando modCompileOnly passe por declaracao.
declara() {  # $1 build.gradle do modulo  $2 grupo maven
  [ -f "$1" ] && grep -qE "^[^/]*(mod)?[cC]ompileOnly.*$2" "$1"
}

compile() {  # $1 nome  $2 jar  $3 comum  $4 loader  $5 release  $6 stubs  $7 extras do MC
             # $8 jar do JEI  $9 jar do EMI  $10 jar do REI
  local name="$1" jar="$2" common="$3" loader="$4" release="$5" stubs="$6" extracp="${7:-}"
  local jarjei="${8:-}" jaremi="${9:-}" jarrei="${10:-}"
  [ "$release" -gt "$RELEASE_CAP" ] && release="$RELEASE_CAP"
  printf '  %-22s ' "$name"
  if [ -z "$jar" ] || [ ! -f "$jar" ]; then
    echo "(pulado: jar do Minecraft nao encontrado)"
    return
  fi
  local CP="$jar"
  [ -n "$extracp" ] && CP="$CP:$extracp"
  [ -n "$EXTRA_CP" ] && CP="$CP:$EXTRA_CP"

  # O build.gradle deste modulo. Daqui sai TUDO o que segue: quais jars de mod entram no
  # classpath e quais arquivos ficam de fora. Antes esta lista era escrita a mao aqui embaixo,
  # e por isso ela era mais generosa que o build de verdade: na 1.18.2 o modulo Forge nao
  # declara o EMI (nao existe EMI de Forge nessa versao), mas o jar do EMI entrava aqui assim
  # mesmo e os tres arquivos do gancho compilavam sem reclamar. O gradlew build de quem
  # compilou nao teve essa gentileza: 19 erros.
  local mod_gradle="${loader%/src/main/java}/build.gradle"
  local rotulo=""
  local par
  for par in "JEI|mezz\.jei|$jarjei" "EMI|dev\.emi|$jaremi" "REI|me\.shedaniel|$jarrei"; do
    local nome="${par%%|*}"; local resto="${par#*|}"
    local grupo="${resto%%|*}"; local jarmod="${resto#*|}"
    if declara "$mod_gradle" "$grupo"; then
      [ -n "$jarmod" ] && [ -f "$jarmod" ] && CP="$CP:$jarmod"
    else
      rotulo="$rotulo $nome"
    fi
  done

  local SRC
  SRC="$(find core/src/main/java "$common" "$loader" -name '*.java' 2>/dev/null)"
  # Sem isto, uma pasta com nome errado (ou o script rodando de outro diretorio) faz o find
  # devolver nada, o javac nao recebe arquivo nenhum, nao imprime nada - e a secao anuncia
  # "zero erros". Verde por nao ter compilado coisa alguma e o pior tipo de verde.
  if [ -z "$SRC" ]; then
    echo "PROBLEMA: nenhum .java encontrado em $common / $loader"
    return
  fi
  local dir
  for dir in $stubs; do
    [ -d "$dir" ] && SRC="$SRC $(find "$dir" -name '*.java')"
  done
  rm -rf "build/verify/$name" && mkdir -p "build/verify/$name"
  local out
  # So interessa o que aponta para arquivo NOSSO: o jar do NeoForge traz anotacoes de
  # bibliotecas que nao estao aqui e reclama sozinho.
  # shellcheck disable=SC2086
  out="$(javac -encoding UTF-8 -Xlint:all --release "$release" -sourcepath "$EMPTY" -implicit:none \
        -d "build/verify/$name" -cp "$CP" $SRC 2>&1 \
        | grep -E "^(core|versions|/)?[^ ]*\.java:[0-9]+: (error|warning):" \
        | grep -vE "^/(mnt|tmp|opt)/")"
  if [ -z "$out" ]; then
    if [ -n "$rotulo" ]; then
      echo "ok (zero erros, zero avisos; sem$rotulo - o modulo nao declara)"
    else
      echo "ok (zero erros, zero avisos)"
    fi
  else
    echo "PROBLEMA"
    echo "$out" | head -20 | sed 's/^/      /'
  fi
}

echo "=== 1. compilacao contra o Minecraft real ==="
compile "1.21.1 neoforge" "$JAR_1_21_1" versions/mc1_21_1/common/src/main/java versions/mc1_21_1/neoforge/src/main/java 21 "$STUBS" "" "$JEI_1_21_1" "$EMI_1_21_1" "$REI_1_21_1"
compile "1.21.1 fabric"   "$JAR_1_21_1" versions/mc1_21_1/common/src/main/java versions/mc1_21_1/fabric/src/main/java   21 "$STUBS $STUBS_FABRIC" "" "$JEI_1_21_1" "$EMI_1_21_1" "$REI_1_21_1"
compile "1.21.4 neoforge" "$JAR_1_21_4" versions/mc1_21_4/common/src/main/java versions/mc1_21_4/neoforge/src/main/java 21 "$STUBS versions/mc1_21_4/neoforge/src/neoApi/java" "" "$JEI_1_21_4" "$EMI_PORT" "$REI_1_21_4"
compile "1.21.4 fabric"   "$JAR_1_21_4" versions/mc1_21_4/common/src/main/java versions/mc1_21_4/fabric/src/main/java   21 "$STUBS $STUBS_FABRIC" "" "$JEI_1_21_4" "$EMI_PORT" "$REI_1_21_4"
compile "1.21.9 neoforge" "$JAR_1_21_9" versions/mc1_21_9/common/src/main/java versions/mc1_21_9/neoforge/src/main/java 21 "$STUBS_2119 $STUBS_NEO" "" "$JEI_1_21_9" "$EMI_PORT" "$REI_1_21_9"
compile "1.21.9 fabric"   "$JAR_1_21_9" versions/mc1_21_9/common/src/main/java versions/mc1_21_9/fabric/src/main/java   21 "$STUBS_2119 $STUBS_FABRIC_2119" "" "$JEI_1_21_9" "$EMI_PORT" "$REI_1_21_9"
compile "1.21.11 neoforge" "$JAR_1_21_11" versions/mc1_21_11/common/src/main/java versions/mc1_21_11/neoforge/src/main/java 21 "$STUBS_2119 $STUBS_NEO" "" "$JEI_1_21_11" "$EMI_PORT" "$REI_1_21_11"
compile "1.21.11 fabric"  "$JAR_1_21_11" versions/mc1_21_11/common/src/main/java versions/mc1_21_11/fabric/src/main/java  21 "$STUBS_2119 $STUBS_FABRIC_21111" "" "$JEI_1_21_11" "$EMI_PORT" "$REI_1_21_11"
compile "26.1 neoforge"   "$JAR_26_1"  versions/mc26_1/common/src/main/java   versions/mc26_1/neoforge/src/main/java   25 "$STUBS_2119 $STUBS_NEO"    "$CP_26_1_NEO" "$JEI_26_1" "$EMI_PORT" "$REI_26_1"
compile "26.1 fabric"     "$JAR_26_1"  versions/mc26_1/common/src/main/java   versions/mc26_1/fabric/src/main/java     25 "$STUBS_2119 $STUBS_MODMENU" "$CP_26_1_FABRIC" "$JEI_26_1" "$EMI_PORT" "$REI_26_1"
compile "26.2 neoforge"   "$JAR_26_2"  versions/mc26_2/common/src/main/java   versions/mc26_2/neoforge/src/main/java   25 "$STUBS_2119 $STUBS_NEO"    "$CP_26_2_NEO" "$JEI_26_2" "$EMI_PORT" "$REI_26_2"
compile "26.2 fabric"     "$JAR_26_2"  versions/mc26_2/common/src/main/java   versions/mc26_2/fabric/src/main/java     25 "$STUBS_2119 $STUBS_MODMENU" "$CP_26_2_FABRIC" "$JEI_26_2" "$EMI_PORT" "$REI_26_2"
compile "1.20.1 fabric"   "$JAR_1_20_1" versions/mc1_20_1/common/src/main/java versions/mc1_20_1/fabric/src/main/java   17 "$STUBS $STUBS_FABRIC" "" "$JEI_1_20_1" "$EMI_1_20_1" "$REI_1_20_1"
compile "1.20.1 forge"    "$JAR_1_20_1" versions/mc1_20_1/common/src/main/java versions/mc1_20_1/forge/src/main/java    17 "$STUBS $STUBS_FORGE" "" "$JEI_1_20_1" "$EMI_1_20_1" "$REI_1_20_1"
compile "1.19.2 fabric"   "$JAR_1_19_2" versions/mc1_19_2/common/src/main/java versions/mc1_19_2/fabric/src/main/java   17 "$STUBS $STUBS_FABRIC $STUBS_BRIDGE" "" "$JEI_1_19_2" "$EMI_1_19_2" "$REI_1_19_2"
compile "1.19.2 forge"    "$JAR_1_19_2" versions/mc1_19_2/common/src/main/java versions/mc1_19_2/forge/src/main/java    17 "$STUBS $STUBS_FORGE_1192 $STUBS_BRIDGE" "" "$JEI_1_19_2" "$EMI_1_19_2" "$REI_1_19_2"
compile "1.18.2 fabric"   "$JAR_1_18_2" versions/mc1_18_2/common/src/main/java versions/mc1_18_2/fabric/src/main/java   17 "$STUBS $STUBS_FABRIC $STUBS_BRIDGE" "" "$JEI_1_18_2" "$EMI_1_18_2" "$REI_1_18_2"
compile "1.18.2 forge"    "$JAR_1_18_2" versions/mc1_18_2/common/src/main/java versions/mc1_18_2/forge/src/main/java    17 "$STUBS $STUBS_FORGE_1192 $STUBS_BRIDGE" "" "$JEI_1_18_2" "$EMI_1_18_2" "$REI_1_18_2"
# A 1.16.5 compila em Java 8. No Forge dela nao ha gancho de visualizador nenhum: os tres jars
# vao vazios de proposito, e a secao 11 cobra que o build.gradle dela tambem nao declare nenhum.
# O jar do REI NAO entra no classpath: ele vem em nomes MCP (ResourceLocation, CompoundNBT) e
# este modulo compila em Mojang mappings. Quem traduz e o Loom, no build de verdade. Aqui quem
# responde por essas classes e o STUBS_REI65 - e a secao 19 cobra o stub contra o jar publicado.
compile "1.16.5 forge"    "$JAR_1_16_5" versions/mc1_16_5/common/src/main/java versions/mc1_16_5/forge/src/main/java     8 "$STUBS $STUBS_FORGE_1165 $STUBS_REI65 $STUBS_JEI781 $STUBS_BRIDGE" "" "" "" ""
# No Fabric dela ha um: o REI 5.x. O jar do REI nao entra no classpath (vem em intermediary),
# e por isso o ultimo argumento vai vazio - quem responde por essas classes e o STUBS_REI512.
# A secao 16 e que cobra o stub contra o bytecode do jar de verdade.
compile "1.16.5 fabric"   "$JAR_1_16_5" versions/mc1_16_5/common/src/main/java versions/mc1_16_5/fabric/src/main/java    8 "$STUBS $STUBS_FABRIC $STUBS_REI512 $STUBS_DFU $STUBS_BRIDGE" "" "" "" ""
# A 1.12.2 e a unica em nomes MCP. O jar patchedMc ja traz o Forge DENTRO (1509 classes),
# entao nao ha stub nenhum: quanto menos stub, mais real a checagem.
compile "1.12.2 forge"    "$JAR_1_12_2" versions/mc1_12_2/common/src/main/java versions/mc1_12_2/forge/src/main/java     8 "$STUBS_LWJGL2 versions/mc1_12_2/forge/src/jeiApi/java" "" "" "" ""

echo
echo "=== 2. testes do algoritmo (valem para toda versao e todo loader) ==="
rm -rf build/coreTest && mkdir -p build/coreTest
javac -encoding UTF-8 -d build/coreTest \
  $(find core/src/main/java/com/rivalzin/bettersearch/core -name '*.java') tools/*.java 2>&1 |
  grep -v JAVA_TOOL | head -15
java -Dfile.encoding=UTF-8 -cp build/coreTest \
  com.rivalzin.bettersearch.tools.BetterSearchCoreTest 2>&1 |
  grep -E "FALHA|OK -|FALHOU" | sed 's/^/  /'

echo
echo "=== 3. arquivos JSON ==="
for f in $(find core versions -name '*.json' -not -path '*/build/*' | sort); do
  if python3 -c "import json,sys; json.load(open('$f',encoding='utf-8'))" 2>/dev/null; then
    echo "  ok   $f"
  else
    echo "  ERRO $f"
  fi
done

echo
echo "=== 4. os dois arquivos de mixin do Forge continuam iguais aos do common ==="
# O Forge precisa da linha "refmap" (roda com nomes SRG) e o Fabric nao pode te-la (quem
# escreve la e o Loom). Por isso sao dois arquivos - e por isso eles podem sair de sincronia
# sem ninguem perceber. Tirando o refmap, tem de ser byte a byte o mesmo arquivo.
for versao in mc1_20_1 mc1_19_2 mc1_18_2; do
  COMMON_MIXINS="versions/$versao/common/src/main/resources/bettersearch.mixins.json"
  FORGE_MIXINS="versions/$versao/forge/src/main/resources/bettersearch-forge.mixins.json"
  [ -f "$FORGE_MIXINS" ] || continue
  if diff <(grep -v '"refmap"' "$FORGE_MIXINS") "$COMMON_MIXINS" > /tmp/bs-mixin-diff 2>&1; then
    echo "  ok   $versao: forge == common (+ refmap)"
  else
    echo "  ERRO $versao: os dois sairam de sincronia:"
    sed 's/^/      /' /tmp/bs-mixin-diff
  fi
done

echo
echo "=== 5. todo mixin de uma versao declara o mesmo compatibilityLevel ==="
python3 - <<'PYCHECK'
import json, pathlib, sys
falhas = 0
for versao in sorted(pathlib.Path("versions").iterdir()):
    base = versao / "common/src/main/resources"
    principal = base / "bettersearch.mixins.json"
    if not principal.exists():
        continue
    esperado = json.loads(principal.read_text(encoding="utf-8"))["compatibilityLevel"]
    arquivos = sorted(base.glob("*.mixins.json"))
    forge = versao / "forge/src/main/resources"
    if forge.exists():
        arquivos += sorted(forge.glob("*.mixins.json"))
    for arq in arquivos:
        achado = json.loads(arq.read_text(encoding="utf-8")).get("compatibilityLevel")
        if achado != esperado:
            print(f"  ERRO  {arq}: {achado}, esperava {esperado}")
            falhas += 1
    print(f"  ok   {versao.name}: {len(arquivos)} arquivos em {esperado}")
if falhas:
    print()
    print("  Um nivel acima do que o Mixin daquela versao conhece derruba o jogo na")
    print("  inicializacao, e isso NAO aparece na compilacao: e um campo de JSON.")
    sys.exit(1)
PYCHECK

echo
echo "=== 6. gancho do JEI existe nos jars de verdade ==="
for pair in "1.18.2:$JEI_1_18_2" "1.19.2:$JEI_1_19_2" "1.20.1:$JEI_1_20_1" "1.21.1:$JEI_1_21_1" "1.21.9:$JEI_1_21_9" \
            "1.21.11:$JEI_1_21_11" "26.1:$JEI_26_1" "26.2:$JEI_26_2"; do
  name="${pair%%:*}"; jar="${pair#*:}"
  printf '  %-9s ' "$name"
  if [ -z "$jar" ] || [ ! -f "$jar" ]; then echo "(pulado: jar do JEI nao informado)"; continue; fi
  # A 1.18.2 e a unica fora do padrao: a classe mora em mezz.jei.common.ingredients e o
  # metodo devolve List em vez de Stream. Conferir o pacote CERTO por versao e o que faz este
  # teste valer alguma coisa - um grep frouxo passaria nos dois e nao acusaria nada.
  if [ "$name" = "1.18.2" ]; then
    classe=mezz.jei.common.ingredients.IngredientFilter
    esperado="List<mezz.jei.api.ingredients.ITypedIngredient<?>> getIngredientListUncached"
  else
    classe=mezz.jei.gui.ingredients.IngredientFilter
    esperado="Stream<mezz.jei.api.ingredients.ITypedIngredient<?>> getIngredientListUncached"
  fi
  hit=$(javap -p -cp "$jar" "$classe" 2>/dev/null | grep -cF "$esperado")
  if [ "$hit" = "1" ]; then
    echo "ok ($classe)"
  else
    echo "ERRO: nao achei '$esperado' em $classe"
  fi
done

echo
echo "=== 7. gancho do EMI existe nos jars de verdade ==="
for pair in "1.18.2:$BS_EMI_1_18_2_REAL" "1.19.2:$EMI_1_19_2" "1.20.1:$EMI_1_20_1" "1.21.1:$EMI_1_21_1"; do
  name="${pair%%:*}"; jar="${pair#*:}"
  printf '  %-9s ' "$name"
  if [ -z "$jar" ] || [ ! -f "$jar" ]; then echo "(pulado: jar do EMI nao informado)"; continue; fi
  # A 1.18.2 tem outra forma: UMA chamada a apply, com um argumento so, e a consulta num campo
  # estatico da EmiSearch. Por isso o gancho de la usa ordinal 0 e um @Accessor; nas outras e
  # ordinal 1 com dois @Shadow. Este teste cobra cada versao pelo que ela e de verdade.
  if [ "$name" = "1.18.2" ]; then esperados=1; alvo="EmiSearch.apply:(Ljava/util/List;)V"; ordinal=0;
  else esperados=2; alvo="EmiSearch.apply:(Ldev/emi/emi/search/EmiSearch\$SearchWorker;Ljava/util/List;)V"; ordinal=1; fi
  hits=$(javap -p -c -cp "$jar" 'dev.emi.emi.search.EmiSearch$SearchWorker' 2>/dev/null \
         | grep -cF "$alvo")
  campo=ok
  if [ "$name" = "1.18.2" ]; then
    campo=$(javap -p -cp "$jar" dev.emi.emi.search.EmiSearch 2>/dev/null | grep -c "java.lang.String query;")
    [ "$campo" = "1" ] && campo=ok || campo="SEM o campo query"
  fi
  if [ "$hits" = "$esperados" ] && [ "$campo" = "ok" ]; then
    echo "ok ($esperados chamada(s) a apply; usamos a ordinal $ordinal)"
  else
    echo "ERRO: esperava $esperados chamada(s) a apply, achei $hits (campo query: $campo)"
  fi
done

echo
echo "=== 8. gancho do REI existe nos jars de verdade ==="
for pair in "1.18.2:$REI_1_18_2" "1.19.2:$REI_1_19_2" "1.20.1:$REI_1_20_1" "1.21.1:$REI_1_21_1" "1.21.9:$REI_1_21_9" \
            "1.21.11:$REI_1_21_11" "26.1:$REI_26_1" "26.2:$REI_26_2"; do
  name="${pair%%:*}"; jar="${pair#*:}"
  printf '  %-9s ' "$name"
  if [ -z "$jar" ] || [ ! -f "$jar" ]; then echo "pulado (sem jar)"; continue; fi
  work="$(mktemp -d)"
  unzip -qo "$jar" -d "$work" 'me/shedaniel/rei/impl/client/search/SearchProviderImpl*' \
      'me/shedaniel/rei/impl/client/gui/widget/entrylist/EntryListSearchManager*' 2>/dev/null || true
  filtro="$(cd "$work" && javap -p me.shedaniel.rei.impl.client.search.SearchProviderImpl 2>/dev/null \
      | grep -c 'SearchFilter createFilter(java.lang.String, me.shedaniel.rei.api.client.search.method.InputMethod')"
  ordem="$(cd "$work" && javap -p me.shedaniel.rei.impl.client.gui.widget.entrylist.EntryListSearchManager 2>/dev/null \
      | grep -c 'List<me.shedaniel.rei.impl.common.util.HashedEntryStackWrapper> copyAndOrder')"
  # O campo que o mixin de ordenacao faz @Shadow. Se ele sumir, o Mixin so reclama com o jogo
  # ja rodando: a compilacao nao tem como saber que este campo existe dentro do jar do REI.
  campo="$(cd "$work" && javap -p me.shedaniel.rei.impl.client.gui.widget.entrylist.EntryListSearchManager 2>/dev/null \
      | grep -c 'AsyncSearchManager searchManager')"
  rm -rf "$work"
  if [ "$filtro" -eq 1 ] && [ "$ordem" -eq 1 ] && [ "$campo" -eq 1 ]; then
    echo "ok (createFilter, copyAndOrder e o campo searchManager no lugar)"
  else
    echo "PROBLEMA (createFilter=$filtro copyAndOrder=$ordem searchManager=$campo)"
  fi
done

echo
echo "=== 9. nenhuma classe que sempre carrega alcanca mod opcional ==="
python3 - <<'PYCHECK'
import pathlib, struct, sys

# Pacotes de mods que podem NAO estar instalados no pack do jogador.
OPCIONAIS = {b"mezz/jei": "JEI", b"dev/emi": "EMI", b"me/shedaniel/rei": "REI"}

# Pacotes carregados SO pelo mixin do mod correspondente, que por sua vez so entra quando
# aquele mod esta mesmo instalado. Estes podem mencionar o que quiserem.
SO_COM_O_MOD = (
    "com/rivalzin/bettersearch/mixin/jei/",
    "com/rivalzin/bettersearch/mixin/emi/",
    "com/rivalzin/bettersearch/mixin/rei/",
)

def cita(dados, nome):
    """O nome interno aparece no pool de constantes com o tamanho na frente."""
    return struct.pack(">H", len(nome)) + nome in dados

raiz = pathlib.Path("build/verify")
if not raiz.is_dir():
    print("  pulado (nada compilado)")
    sys.exit(0)

falhas = 0
alvos = 0
isoladas = 0
for alvo in sorted(raiz.iterdir()):
    if not alvo.is_dir():
        continue
    classes = sorted(alvo.rglob("com/rivalzin/bettersearch/**/*.class"))
    if not classes:
        continue
    alvos += 1
    dados = {c.relative_to(alvo).as_posix()[:-6]: c.read_bytes() for c in classes}

    # Quem menciona um mod opcional. Sem propagacao: se a mencao fosse "pegando", a classe
    # culpada entraria na lista junto com a vitima e o erro passaria batido - foi exatamente
    # assim que a 1.7.1 saiu daqui achando que estava limpa.
    porta = {}
    for rel, b in dados.items():
        for pacote, nome in OPCIONAIS.items():
            if pacote in b:
                porta[rel] = nome
                break
    isoladas = len(porta)

    def parte_da_mesma(rel, alvo_rel):
        return rel == alvo_rel or rel.startswith(alvo_rel + "$")

    for rel, nome in sorted(porta.items()):
        culpados = []
        for outro, b in dados.items():
            if outro in porta or outro.startswith(SO_COM_O_MOD):
                continue
            if any(parte_da_mesma(outro, p) for p in porta):
                continue
            if cita(b, rel.encode()):
                culpados.append(outro)
        if culpados:
            print(f"  ERRO  {rel} ({nome}) e alcancado por [{alvo.name}]:")
            for culpado in sorted(culpados):
                print(f"          {culpado}")
            falhas += 1

if falhas:
    print()
    print("  As classes listadas carregam com ou sem o mod instalado. Mencionar algo que leva")
    print("  ate ele faz a JVM tentar resolver aquelas classes, e sem o mod no pack isso e")
    print("  NoClassDefFoundError na inicializacao - o jogo nem abre.")
    sys.exit(1)
print(f"  ok   {alvos} alvos conferidos, {isoladas} classes de integracao isoladas")
PYCHECK

echo
echo "=== 10. numero de versao no gradle.properties bate com o jar de verdade ==="
# Por que esta secao existe: no port da 1.18.2 eu escrevi quatro numeros de versao de cabeca
# (JEI 9.7.2.1002, REI 8.3.564, Fabric API 0.77.0+1.19.2) em vez de ler do jar que ja estava
# baixado aqui do lado. Nenhum deles existia. O compilador nao tem como perceber isso - ele
# compila contra o jar, nao contra a coordenada Maven - e o erro so aparece no gradlew build
# da outra pessoa. Aqui a conta e direta: o numero escrito na propriedade tem de ser o mesmo
# que esta escrito DENTRO do jar contra o qual a secao 6, 7 e 8 conferiram o gancho.
BS_PROPS="${BS_PROPS:-gradle.properties}" \
BS_PARES="jei_version_1182=$JEI_1_18_2;jei_version_1192=$JEI_1_19_2;jei_version_1201=$JEI_1_20_1;jei_version=$JEI_1_21_1;jei_version_2114=$JEI_1_21_4;jei_version_2119=$JEI_1_21_9;jei_version_21111=$JEI_1_21_11;jei_version_261=$JEI_26_1;jei_version_262=$JEI_26_2;rei_version_1182=$REI_1_18_2;rei_version_1192=$REI_1_19_2;rei_version_1201=$REI_1_20_1;rei_version=$REI_1_21_1;rei_version_2114=$REI_1_21_4;rei_version_2119=$REI_1_21_9;rei_version_21111=$REI_1_21_11;rei_version_261=$REI_26_1;rei_version_262=$REI_26_2;emi_version_1182=$BS_EMI_1_18_2_REAL;emi_version_1192=$EMI_1_19_2;emi_version_1201=$EMI_1_20_1;emi_version=$EMI_1_21_1" \
python3 - <<'PYVER'
import json, os, pathlib, re, sys, zipfile

props = {}
for linha in pathlib.Path(os.environ["BS_PROPS"]).read_text(encoding="utf-8").splitlines():
    linha = linha.strip()
    if linha and not linha.startswith("#") and "=" in linha:
        chave, valor = linha.split("=", 1)
        props[chave.strip()] = valor.strip()

def versao_do_jar(caminho):
    """Le a versao declarada pelo proprio mod, no formato de cada loader."""
    with zipfile.ZipFile(caminho) as z:
        nomes = set(z.namelist())
        for alvo in ("META-INF/mods.toml", "META-INF/neoforge.mods.toml"):
            if alvo in nomes:
                texto = z.read(alvo).decode("utf-8", "replace")
                # A primeira "version" solta e a do [[mods]]. loaderVersion e versionRange tem
                # nome proprio e nao entram no padrao abaixo.
                m = re.search(r'(?m)^\s*version\s*=\s*"([^"]+)"', texto)
                if m and not m.group(1).startswith("${"):
                    return m.group(1)
        if "fabric.mod.json" in nomes:
            # o json do Fabric as vezes tem comentario, que o json puro nao aceita
            bruto = z.read("fabric.mod.json").decode("utf-8", "replace")
            try:
                return json.loads(bruto).get("version")
            except ValueError:
                m = re.search(r'"version"\s*:\s*"([^"]+)"', bruto)
                return m.group(1) if m else None
    return None

falhas = 0
conferidos = 0
for par in os.environ["BS_PARES"].split(";"):
    chave, jar = par.split("=", 1)
    escrito = props.get(chave)
    if escrito is None:
        print(f"  ERRO  {chave} nao existe no gradle.properties")
        falhas += 1
        continue
    if not jar or not pathlib.Path(jar).is_file():
        continue
    real = versao_do_jar(jar)
    if real is None:
        print(f"  {chave:22s} pulado (o jar nao declara versao de forma legivel)")
        continue
    conferidos += 1
    # O EMI carimba o loader no fim da versao DO JAR (1.1.24+1.20.1+forge), mas no Maven quem
    # separa o loader e o nome do artefato (emi-forge, emi-neoforge) e a versao fica sem o
    # sufixo. Os dois estao certos; so nao sao a mesma string. Conferido no build da 1.20.1 e
    # da 1.21.1, que resolvem sem o sufixo. Nada alem disso e tolerado aqui.
    for sufixo in ("+forge", "+neoforge", "+fabric"):
        if real.endswith(sufixo):
            real = real[: -len(sufixo)]
            break
    if real != escrito:
        print(f"  ERRO  {chave}={escrito}, mas o jar diz {real}")
        print(f"          {jar}")
        falhas += 1

if falhas:
    print()
    print("  Numero de versao nao se deduz, se le. Abra o jar, pegue o que esta escrito nele e")
    print("  copie. Se o numero da propriedade nao existe no Maven, o gradlew build so quebra")
    print("  na maquina de quem for compilar - aqui nada acusa, porque a compilacao usa o jar.")
    sys.exit(1)
print(f"  ok   {conferidos} numero(s) de versao conferido(s) contra o jar correspondente")
PYVER

echo
echo "=== 11. o que o modulo importa esta declarado no build.gradle dele ==="
# Por que esta secao existe: o common/ e compartilhado pelos dois loaders, mas a dependencia
# e declarada por MODULO. Na 1.18.2 o EMI so tem lancamento de Fabric - o modulo Forge nao tem
# como declara-lo. Mesmo assim os arquivos do gancho do EMI estavam no common/ e entravam na
# compilacao dele: "package dev.emi does not exist", 19 erros, build morto na maquina de quem
# compilou. Aqui a regra e direta e nao depende de comportamento de plugin nenhum: junte os
# .java que o modulo compila (core + common + a pasta dele), veja quais mods opcionais eles
# importam, e cobre a declaracao correspondente.
python3 - <<'PYMOD'
import json, pathlib, re, sys

MODS = {"JEI": "mezz.jei", "EMI": "dev.emi", "REI": "me.shedaniel"}
CORE = pathlib.Path("core/src/main/java")

def declara(texto, grupo):
    """So linha de dependencia de verdade - comentario citando modCompileOnly nao vale."""
    return bool(re.search(r"^[^/]*(mod)?[cC]ompileOnly.*" + re.escape(grupo), texto, re.M))

falhas = 0
modulos = 0
notas = []
for gradle in sorted(pathlib.Path("versions").glob("*/*/build.gradle")):
    versao, loader = gradle.parts[1], gradle.parts[2]
    modulo = f"{versao} {loader}"
    texto = gradle.read_text(encoding="utf-8")
    modulos += 1

    # Exatamente as pastas de fonte do modulo - as mesmas tres do sourceSets dele.
    fontes = list(CORE.rglob("*.java"))
    for d in (pathlib.Path("versions") / versao / "common/src/main/java", gradle.parent / "src/main/java"):
        fontes += list(d.rglob("*.java")) if d.is_dir() else []
    # Idem para os recursos: e daqui que sai o que vai parar dentro do jar.
    recursos = set()
    for d in (pathlib.Path("versions") / versao / "common/src/main/resources", gradle.parent / "src/main/resources"):
        recursos |= {f.name for f in d.rglob("*.json")} if d.is_dir() else set()

    for nome, grupo in MODS.items():
        usam = [f for f in fontes if re.search(r"^import\s+" + re.escape(grupo) + r"\.", f.read_text(encoding="utf-8"), re.M)]
        json_mixin = f"bettersearch-{nome.lower()}.mixins.json"

        # Segundo jeito legitimo de suprir o pacote: ESBOCOS de compilacao num sourceSet
        # proprio (o caso do JEI na 1.12.2, cujo 4.16.5.1027 nao esta em Maven declaravel).
        # So conta se as duas metades existirem: a pasta com o pacote E o compileOnly do
        # output dela no build.gradle - uma sem a outra e exatamente o erro que esta secao
        # existe para pegar. A fidelidade esboco-por-esboco ao jar real e da secao 22.
        esbocado = False
        for candidato in sorted((gradle.parent / "src").glob("*/java")):
            conjunto = candidato.parent.name
            if conjunto != "main" and (candidato / grupo.replace(".", "/")).is_dir():
                # Linha de dependencia DE VERDADE, como no declara(): comentario nao conta -
                # foi exatamente o buraco que o teste de mutacao desta secao encontrou.
                esbocado = bool(re.search(
                    r"^[^/]*compileOnly\s+sourceSets\." + re.escape(conjunto) + r"\.output",
                    texto, re.M))
                break

        if declara(texto, grupo) or esbocado:
            if not usam:
                print(f"  ERRO  {modulo}: declara o {nome} e nao compila arquivo nenhum dele")
                falhas += 1
            elif esbocado:
                notas.append(f"{modulo} com {nome} por esbocos")
            continue

        notas.append(f"{modulo} sem {nome}")
        if usam:
            print(f"  ERRO  {modulo}: nao declara o {nome}, mas compila {len(usam)} arquivo(s) que importam {grupo}:")
            for f in sorted(usam)[:4]:
                print(f"          {f}")
            print(f"          sem a dependencia isso e 'package {grupo} does not exist' no build")
            falhas += 1
        if json_mixin in recursos:
            print(f"  ERRO  {modulo}: nao declara o {nome}, mas empacota {json_mixin}")
            falhas += 1
        m = re.search(r"'MixinConfigs'\s*:\s*'([^']*)'", texto)
        if m and json_mixin in m.group(1):
            print(f"  ERRO  {modulo}: MixinConfigs anuncia {json_mixin}, que nao entra no jar")
            falhas += 1
        fmj = gradle.parent / "src/main/resources/fabric.mod.json"
        if fmj.exists() and json_mixin in json.loads(fmj.read_text(encoding="utf-8")).get("mixins", []):
            print(f"  ERRO  {modulo}: fabric.mod.json anuncia {json_mixin}, que nao entra no jar")
            falhas += 1

if falhas:
    print()
    print("  O common/ e compartilhado, mas a dependencia e por modulo. Quando um visualizador")
    print("  nao existe para aquele loader naquela versao, os arquivos do gancho dele tem de sair")
    print("  do common/ e ir para a pasta do modulo que consegue compila-los. A opcao no menu")
    print("  continua aparecendo, porque ela mora no core/ e nao depende de mod nenhum.")
    sys.exit(1)
print(f"  ok   {modulos} modulos conferidos" + (f"; {', '.join(notas)}" if notas else ""))
PYMOD

echo
echo "=== 12. alvo de cada mixin vanilla existe no bytecode real ==="
# Por que esta secao existe: as secoes 6, 7 e 8 conferem os ganchos do JEI, do EMI e do REI com
# javap no jar de verdade, mas os mixins VANILLA nunca tiveram essa conta - o PORTING.md mandava
# fazer na mao, e na 1.18.2 eu fiz pela metade. O @Redirect do livro de receitas mirava em
# SearchTree.search; no bytecode da 1.18.2 quem esta escrito ali e MutableSearchTree.search
# (subinterface que sumiu na 1.19). O javac aceita as duas, porque uma estende a outra. O Mixin
# nao: ele compara o descritor da instrucao, da 0/1 e derruba o jogo na inicializacao.
BS_ALVOS="1.16.5=$JAR_1_16_5;1.18.2=$JAR_1_18_2;1.19.2=$JAR_1_19_2;1.20.1=$JAR_1_20_1;1.21.1=$JAR_1_21_1;1.21.4=$JAR_1_21_4;1.21.9=$JAR_1_21_9;1.21.11=$JAR_1_21_11;26.1=$JAR_26_1;26.2=$JAR_26_2" \
python3 - <<'PYVAN'
import os, pathlib, re, subprocess, sys

PASTAS = {"1.16.5": "mc1_16_5", "1.18.2": "mc1_18_2", "1.19.2": "mc1_19_2", "1.20.1": "mc1_20_1", "1.21.1": "mc1_21_1",
          "1.21.4": "mc1_21_4", "1.21.9": "mc1_21_9", "1.21.11": "mc1_21_11", "26.1": "mc26_1", "26.2": "mc26_2"}

def classe_alvo(fonte):
    """@Mixin(X.class) + o import de X = o nome completo da classe atacada."""
    m = re.search(r"@Mixin\(\s*(?:value\s*=\s*)?([A-Za-z_][\w]*)\.class", fonte)
    if not m:
        return None
    simples = m.group(1)
    imp = re.search(r"^import\s+([\w.]*\." + re.escape(simples) + r");", fonte, re.M)
    return imp.group(1) if imp else None

def ganchos(fonte):
    """(metodo, tipo, alvo, obrigatorio) de cada @Redirect/@ModifyArg com alvo escrito."""
    achados = []
    for bloco in re.findall(r"@(?:Redirect|ModifyArg|ModifyVariable|ModifyConstant)\((.*?)\)\s*(?:private|public|protected)",
                            fonte, re.S):
        met = re.search(r'method\s*=\s*"([^"]+)"', bloco)
        alvo = re.search(r'target\s*=\s*"((?:[^"]|"\s*\+\s*")+)"', bloco)
        if not (met and alvo):
            continue
        limpo = re.sub(r'"\s*\+\s*"', "", alvo.group(1))
        tipo = "FIELD" if re.search(r'value\s*=\s*"FIELD"', bloco) else "INVOKE"
        obrig = not re.search(r"require\s*=\s*0", bloco)
        achados.append((met.group(1), tipo, limpo, obrig))
    return achados

def injecoes(fonte):
    """@Inject so precisa que o METODO exista."""
    return [m for m in re.findall(r'@Inject\(\s*method\s*=\s*"([^"]+)"', fonte)]

falhas = 0
conferidos = 0
for versao, jar in (p.split("=", 1) for p in os.environ["BS_ALVOS"].split(";")):
    pasta = pathlib.Path("versions") / PASTAS[versao] / "common/src/main/java/com/rivalzin/bettersearch/mixin"
    if not jar or not pathlib.Path(jar).is_file() or not pasta.is_dir():
        print(f"  {versao:9s} (pulado: sem jar ou sem pasta de mixin)")
        continue
    problemas = []
    for arquivo in sorted(pasta.glob("*.java")):
        fonte = arquivo.read_text(encoding="utf-8")
        alvo_classe = classe_alvo(fonte)
        if not alvo_classe or not alvo_classe.startswith("net.minecraft"):
            continue   # os de mod opcional ja sao cobertos pelas secoes 6, 7 e 8
        try:
            asm = subprocess.run(["javap", "-p", "-c", "-cp", jar, alvo_classe],
                                 capture_output=True, text=True, timeout=180).stdout
        except Exception as e:
            problemas.append(f"{arquivo.name}: nao consegui desmontar {alvo_classe} ({e})")
            continue
        if not asm.strip():
            problemas.append(f"{arquivo.name}: a classe {alvo_classe} nao existe neste jar")
            continue

        for metodo in injecoes(fonte):
            nu = metodo.split("(")[0]
            if not re.search(r"\b" + re.escape(nu) + r"\(", asm):
                problemas.append(f"{arquivo.name}: @Inject em '{metodo}', que nao existe em {alvo_classe}")

        for metodo, tipo, alvo, obrig in ganchos(fonte):
            if not obrig:
                continue   # require = 0: se sumir, o mod so perde aquele detalhe
            conferidos += 1
            # "Lpacote/Classe;metodo(args)Retorno" -> como o javap escreve: "pacote/Classe.metodo:(args)Retorno"
            m = re.match(r"L([^;]+);([\w$]+)(\(.*\).+)$", alvo) or re.match(r"L([^;]+);([\w$]+):(.+)$", alvo)
            if not m:
                problemas.append(f"{arquivo.name}: nao entendi o alvo '{alvo}'")
                continue
            dono, membro, desc = m.groups()
            agulha = f"{dono}.{membro}:{desc}"
            # O javap omite o pacote quando o dono e a propria classe desmontada.
            curto = f"{membro}:{desc}"
            if agulha not in asm and not (dono == alvo_classe.replace(".", "/") and curto in asm):
                problemas.append(f"{arquivo.name}: {tipo} de '{metodo}' mira em")
                problemas.append(f"            {agulha}")
                problemas.append(f"          que NAO aparece no bytecode de {alvo_classe}")
                # ajuda a achar o nome certo
                iguais = sorted(set(re.findall(r"// (?:Interface)?Method ([\w/$]+\." + re.escape(membro) + r":\S+)", asm)))
                if iguais:
                    problemas.append(f"          o que existe ali e: {', '.join(iguais)}")
    if problemas:
        print(f"  {versao:9s} PROBLEMA")
        for p in problemas:
            print(f"      {p}")
        falhas += 1
    else:
        print(f"  {versao:9s} ok")

if falhas:
    print()
    print("  O Mixin nao compara tipos, compara o descritor escrito na instrucao. Um alvo que o")
    print("  javac aceita (porque a interface estende a outra) pode nao existir no bytecode - e")
    print("  ai o gancho da 0/1 e o jogo nem abre. Este e o unico erro do projeto que passa por")
    print("  compilacao limpa e so aparece com o Minecraft ligado.")
    sys.exit(1)
print(f"  {conferidos} alvo(s) de @Redirect obrigatorio conferido(s) no bytecode")
PYVAN

echo
echo "=== 13. o core/ continua compilando em Java 8 ==="
# Por que esta secao existe: da 1.16.5 para tras o Minecraft roda em Java 8. O core/ - as 1.788
# linhas do algoritmo - e o MESMO arquivo em todas as versoes, e e isso que faz uma correcao no
# algoritmo valer para as nove de uma vez. Sintaxe de Java 8 tambem e sintaxe de Java 25, entao
# escrever o core/ em Java 8 nao custa nada as versoes novas; o contrario, sim, custa a 1.16.5.
#
# Sem esta conta, um record ou um switch com seta escrito daqui a tres meses passaria em todos
# os 16 alvos modernos e so quebraria no dia em que alguem fosse compilar a 1.16.5.
# E com CLASSPATH VAZIO, o que e a segunda metade desta secao e nasceu de um crash de verdade.
#
# O core/BetterSearch.java chamava org.slf4j.LoggerFactory. Compilava nos dezoito alvos porque o
# slf4j vinha no BS_EXTRA_CP daqui - mas no Forge 1.16.5 ele NAO existe em execucao (o classpath
# de la tem log4j-api, log4j-core e log4j-slf4j18-impl, e nenhum slf4j-api; esta escrito na
# lista "Minecraft classPath" do relatorio de crash). Resultado: NoClassDefFoundError no
# <clinit> da classe de constantes, ou seja, o mod nao carregava de jeito nenhum.
#
# Emprestar biblioteca ao core/ na conferencia e a mesma armadilha que ja apareceu tres vezes
# hoje: o harness sendo mais generoso que a realidade. O core/ e o algoritmo, e algoritmo nao
# precisa de biblioteca - se ele compila com o classpath vazio, nao existe versao onde ele possa
# faltar alguma coisa.
saida="$(javac --release 8 -Xlint:all,-options -d "$(mktemp -d)" \
         $(find core/src/main/java -name '*.java') 2>&1 | grep -vE "^(Picked up|Note:)")"
if [ -z "$saida" ]; then
  echo "  ok   core/ compila em Java 8 com classpath VAZIO (zero erros, zero avisos)"
else
  echo "  PROBLEMA - o core/ deixou de caber em Java 8:"
  echo "$saida" | head -12 | sed 's/^/      /'
  echo
  echo "  record, switch com seta, var, instanceof com padrao e List.of/Map.of NAO existem no"
  echo "  Java 8. Troque por classe final, switch classico, tipo explicito e Collections/Arrays."
fi

echo
echo "=== 14. nenhuma versao usa API mais nova que as bibliotecas que ela recebe ==="
# Por que esta secao existe: o mod nao depende so do Minecraft - depende das bibliotecas que
# CADA versao do Minecraft carrega junto. A 1.16.5 traz Gson 2.8.0; a 1.18.2 em diante trazem
# 2.8.9+. O JsonParser.parseReader estatico so nasceu no 2.8.6.
#
# A secao 1 nao pega isso: ela compila com o Gson que estiver no BS_EXTRA_CP (o do Gradle, que
# e novo). O gradlew build de verdade usa o Gson da versao alvo - e foi assim que o
# "JsonParser.parseReader" passou aqui e quebrou a 1.16.5 na maquina de quem compilou.
#
# A lista abaixo cresce conforme a gente descobre. Cada linha e: versao;padrao;a partir de;motivo
BS_PROIBIDOS="mc1_16_5;JsonParser\.parse(Reader|String)\(;Gson 2.8.6;a 1.16.5 traz Gson 2.8.0 - use new JsonParser().parse(...)
mc1_16_5;\.getAsJsonArray\(\)\.asList\(;Gson 2.9;idem
mc1_12_2;JsonParser\.parse(Reader|String)\(;Gson 2.8.6;a 1.12.2 tambem traz Gson 2.8.0 (visto no classpath da sonda)
mc1_12_2;\.getAsJsonArray\(\)\.asList\(;Gson 2.9;idem" \
python3 - <<'PYLIB'
import os, pathlib, re, sys

falhas = 0
regras = 0
for linha in os.environ["BS_PROIBIDOS"].strip().splitlines():
    versao, padrao, desde, motivo = linha.split(";", 3)
    pasta = pathlib.Path("versions") / versao
    if not pasta.is_dir():
        continue
    regras += 1
    for fonte in sorted(pasta.rglob("*.java")):
        # _integracoes-pendentes nao entra em build nenhum
        if "_integracoes-pendentes" in fonte.as_posix():
            continue
        texto = fonte.read_text(encoding="utf-8")
        for n, l in enumerate(texto.splitlines(), 1):
            if l.lstrip().startswith(("*", "//", "/*")):
                continue   # comentario explicando a armadilha nao e uso
            if re.search(padrao, l):
                print(f"  ERRO  {fonte}:{n}")
                print(f"          usa API que so existe a partir do {desde}")
                print(f"          {motivo}")
                falhas += 1

if falhas:
    print()
    print("  Compilar aqui nao prova nada quando a biblioteca do ambiente e mais nova que a da")
    print("  versao alvo. Quem manda e o que aquela versao do Minecraft carrega junto.")
    sys.exit(1)
print(f"  ok   {regras} regra(s) de biblioteca conferida(s)")
PYLIB

echo
echo "=== 15. o id da Fabric API no depends existe na versao alvo ==="
# Por que esta secao existe: a Fabric API mudou o PROPRIO id no meio da vida. Ate a linha
# 0.5x ela se chama "fabric"; de 0.60 (Minecraft 1.19) em diante passou a ser "fabric-api",
# declarando "provides": ["fabric"] para nao quebrar quem dependia do nome antigo.
#
# Declarar "fabric-api" numa versao antiga faz o jogo abrir a janela "Incompatible mods found!"
# dizendo que a Fabric API esta faltando - com ela instalada e ligada na frente do jogador.
# Nada disso aparece na compilacao: e so metadado.
python3 - <<'PYFAB'
import json, pathlib, re, sys

falhas = 0
conferidos = 0
for fmj in sorted(pathlib.Path("versions").glob("*/fabric/src/main/resources/fabric.mod.json")):
    versao = fmj.parts[1]
    depends = json.loads(fmj.read_text(encoding="utf-8")).get("depends", {})
    usado = [k for k in depends if k in ("fabric", "fabric-api")]
    if not usado:
        continue
    conferidos += 1

    # A versao da API que este modulo declara, do gradle.properties.
    sufixo = versao.replace("mc", "").replace("_", "")
    props = pathlib.Path("gradle.properties").read_text(encoding="utf-8")
    # Primeiro a propriedade COM sufixo, e so depois a sem. Com o sufixo opcional num regex so,
    # o "(?:_1165)?" casava com a linha "fabric_api_version=" da 1.21.1, que vem antes no
    # arquivo - e a secao acusava a 1.16.5 usando a versao da API de outra versao.
    m = re.search(r"^fabric_api_version_%s=(\S+)" % sufixo, props, re.M)
    if not m:
        m = re.search(r"^fabric_api_version=(\S+)", props, re.M)
    if not m:
        continue
    api = m.group(1)
    menor = int(api.split(".")[1]) if api.split(".")[0] == "0" else 99
    esperado = "fabric" if menor < 60 else "fabric-api"

    if usado[0] != esperado:
        print(f"  ERRO  {versao}: depends usa '{usado[0]}', mas a Fabric API {api} se chama '{esperado}'")
        print(f"          o jogo abriria dizendo que a Fabric API esta faltando, com ela instalada")
        falhas += 1

if falhas:
    print()
    print("  Ate a linha 0.5x o id e 'fabric'; de 0.60 em diante e 'fabric-api'. Isto e metadado:")
    print("  nao aparece em compilacao nenhuma, so na cara do jogador quando ele abre o jogo.")
    sys.exit(1)
print(f"  ok   {conferidos} modulo(s) Fabric com o id certo para a versao da API")
PYFAB

echo
echo "=== 16. gancho do REI 5.x (1.16.5 Fabric) existe no jar de verdade ==="
# Secao propria, e nao um ramo da 8, porque aqui tudo e diferente: o REI 5.x e anterior a
# reescrita da API (nao existe SearchFilter nem createFilter), o alvo e a ESCRITA de um campo
# privado dentro do updateSearch, e o jar publicado e um involucro - o codigo real esta em
# jars aninhados (RoughlyEnoughItems-runtime e -api).
#
# A conta que importa e a ultima: o descritor que o mixin declara tem de aparecer no bytecode.
# O modulo compila contra um STUB (o jar de verdade vem em intermediary e o javac puro nao
# traduz), e stub e promessa. Isto aqui e a prova.
BS_REI512="$REI_1_16_5_FABRIC" \
BS_MIXIN512="versions/mc1_16_5/fabric/src/main/java/com/rivalzin/bettersearch/mixin/rei/EntryListWidgetMixin.java" \
python3 - <<'PYREI'
import os, pathlib, re, subprocess, sys, tempfile, zipfile

jar = os.environ["BS_REI512"]
if not jar or not pathlib.Path(jar).is_file():
    print("  (pulado: jar do REI 1.16.5 Fabric nao informado)")
    sys.exit(0)

trabalho = tempfile.mkdtemp()
# 1) o involucro: tirar de dentro os jars aninhados
with zipfile.ZipFile(jar) as z:
    aninhados = [n for n in z.namelist() if n.startswith("META-INF/jars/") and n.endswith(".jar")]
    for nome in aninhados:
        if "RoughlyEnoughItems" in nome:
            z.extract(nome, trabalho)
if not aninhados:
    print("  PROBLEMA: o jar nao tem META-INF/jars/ - o formato mudou")
    sys.exit(1)

# 2) desempacotar os aninhados num classpath so
classes = pathlib.Path(trabalho) / "classes"
classes.mkdir()
for nome in aninhados:
    if "RoughlyEnoughItems" not in nome:
        continue
    with zipfile.ZipFile(pathlib.Path(trabalho) / nome) as z:
        z.extractall(classes)

def desmontar(classe, com_codigo=False):
    cmd = ["javap", "-p"] + (["-c"] if com_codigo else []) + ["-cp", str(classes), classe]
    return subprocess.run(cmd, capture_output=True, text=True, timeout=180).stdout

falhas = 0

# --- as assinaturas que o stub promete ------------------------------------------------
ESPERADO = {
    "me.shedaniel.rei.gui.widget.EntryListWidget": [
        "java.util.List<me.shedaniel.rei.api.EntryStack> allStacks",
        "public void updateSearch(java.lang.String, boolean)",
        "public java.util.List<me.shedaniel.rei.api.EntryStack> getAllStacks()",
    ],
    "me.shedaniel.rei.api.EntryRegistry": [
        "java.util.List<me.shedaniel.rei.api.EntryStack> getPreFilteredList()",
    ],
    "me.shedaniel.rei.api.EntryStack": [
        "java.util.Optional<net.minecraft.class_2960> getIdentifier()",
        "me.shedaniel.rei.api.EntryStack$Type getType()",
        "boolean isEmpty()",
        "net.minecraft.class_1799 getItemStack()",
    ],
}
for classe, assinaturas in ESPERADO.items():
    saida = desmontar(classe)
    if not saida.strip():
        print(f"  ERRO  a classe {classe} nao existe no jar")
        falhas += 1
        continue
    for assinatura in assinaturas:
        if assinatura not in saida:
            print(f"  ERRO  {classe}: nao achei '{assinatura}'")
            falhas += 1

# --- a prova principal: o descritor do mixin aparece DENTRO do metodo que ele declara ---
#
# Nada aqui e escrito a mao: o metodo a inspecionar sai do proprio "method=" do mixin, e nao
# de um nome fixo. Se ficasse fixo, trocar o method= por um metodo que nao existe (ou que
# existe mas nao escreve no campo) passaria verde - foi assim ate esta versao da secao.
BASICOS = {"B": "byte", "C": "char", "D": "double", "F": "float", "I": "int",
           "J": "long", "S": "short", "Z": "boolean", "V": "void"}

def tipos(desc):
    """Descritor JVM dos parametros -> os nomes como o javap os escreve."""
    fora, i = [], 0
    while i < len(desc):
        dim = 0
        while desc[i] == "[":
            dim += 1
            i += 1
        if desc[i] == "L":
            fim = desc.index(";", i)
            nome = desc[i + 1:fim].replace("/", ".")
            i = fim + 1
        else:
            nome = BASICOS[desc[i]]
            i += 1
        fora.append(nome + "[]" * dim)
    return fora

def corpo_do_metodo(asm, nome, params):
    """Acha o corpo do metodo com ESTE nome e ESTES parametros no javap -c."""
    achados = []
    for m in re.finditer(r"^ {2}.*?\b" + re.escape(nome) + r"\(([^)]*)\);\s*$", asm, re.M):
        crus = re.sub(r"<[^<>]*>", "", m.group(1))          # tira os genericos
        tem = [p.strip() for p in crus.split(",") if p.strip()]
        achados.append(tem)
        if tem != params:
            continue
        resto = asm[m.end():]
        prox = re.search(r"^ {2}\S", resto, re.M)            # ate a proxima declaracao
        return resto[:prox.start()] if prox else resto, achados
    return None, achados

fonte = pathlib.Path(os.environ["BS_MIXIN512"])
if not fonte.exists():
    print(f"  ERRO  nao achei o mixin em {fonte}")
    falhas += 1
else:
    texto = fonte.read_text(encoding="utf-8")
    alvo = re.search(r'target\s*=\s*"((?:[^"]|"\s*\+\s*")+)"', texto)
    metodo = re.search(r'method\s*=\s*"([^"]+)"', texto)
    if not (alvo and metodo):
        print("  ERRO  nao consegui ler o target/method do mixin")
        falhas += 1
    else:
        limpo = re.sub(r'"\s*\+\s*"', "", alvo.group(1))
        m = re.match(r"L([^;]+);([\w$]+):(.+)$", limpo)
        mm = re.match(r"([\w$<>]+)\(([^)]*)\)(.+)$", metodo.group(1))
        if not m:
            print(f"  ERRO  nao entendi o target '{limpo}'")
            falhas += 1
        elif not mm:
            print(f"  ERRO  o method= tem de trazer o descritor completo, e veio '{metodo.group(1)}'")
            print("          sem descritor o Mixin escolhe pelo nome e uma sobrecarga errada passa batido")
            falhas += 1
        else:
            dono, campo, desc = m.groups()
            nome_alvo, params_alvo = mm.group(1), tipos(mm.group(2))
            asm = desmontar(dono.replace("/", "."), com_codigo=True)
            escrito = f"{nome_alvo}({', '.join(params_alvo)})"
            corpo, achados = corpo_do_metodo(asm, nome_alvo, params_alvo)
            if corpo is None:
                print(f"  ERRO  o mixin mira em {escrito}, que nao existe em {dono}")
                if achados:
                    print("          as sobrecargas com esse nome sao: "
                          + "; ".join(f"{nome_alvo}({', '.join(a)})" for a in achados[:4]))
                else:
                    print(f"          nao ha metodo nenhum chamado {nome_alvo} nessa classe")
                falhas += 1
            else:
                # O par que importa e (opcode, campo) na MESMA instrucao. Conferir os dois
                # separados deixa passar o caso em que o campo so e LIDO ali: o javap mostra
                # "Field allStacks" (getfield) e mostra putfield de outro campo, e as duas
                # buscas soltas dao verde. Em jogo isso e 0/1 e a busca nao acontece.
                OPCODES = {"178": "getstatic", "179": "putstatic",
                           "180": "getfield", "181": "putfield"}
                op = re.search(r"opcode\s*=\s*(\d+)", texto)
                op_esperado = OPCODES.get(op.group(1)) if op else None
                if op and not op_esperado:
                    print(f"  ERRO  opcode {op.group(1)} nao e de campo (178..181)")
                    falhas += 1
                instrucoes = re.findall(r"^\s*\d+:\s*(\w+)\s+#\d+\s+// Field (\S+)", corpo, re.M)
                alvo_campo = f"{campo}:{desc}"
                casa = [o for o, c in instrucoes
                        if c == alvo_campo and (op_esperado is None or o == op_esperado)]
                if not casa:
                    verbo = op_esperado or "mexe em"
                    print(f"  ERRO  nao ha {verbo} de '{alvo_campo}' dentro do {escrito}")
                    mesmo_campo = sorted(set(o for o, c in instrucoes if c == alvo_campo))
                    if mesmo_campo:
                        print(f"          o campo aparece ali, mas so como {', '.join(mesmo_campo)}"
                              f" - e o mixin pediu {op_esperado}")
                    else:
                        # so as instrucoes do mesmo tipo que o mixin pediu: sao essas que ele
                        # poderia ter mirado. Listar getstatic aqui so faria barulho.
                        campos = sorted(set(c for o, c in instrucoes
                                            if op_esperado is None or o == op_esperado))
                        rotulo = op_esperado or "instrucoes de campo"
                        print(f"          os {rotulo} que existem ali sao: "
                              + (", ".join(campos[:6]) if campos else "nenhum"))
                    falhas += 1

if falhas:
    print()
    print("  O modulo compila contra um stub, porque o jar do REI vem em intermediary e o javac")
    print("  puro nao traduz. Stub e promessa; esta secao e a prova. Se ela falha, o mod compila")
    print("  limpo e a busca no REI simplesmente nao acontece - sem erro nenhum no log.")
    sys.exit(1)
print("  ok   allStacks, updateSearch, getPreFilteredList e o EntryStack conferidos;")
print("       o PUTFIELD que o mixin mira esta dentro do updateSearch")
PYREI

echo
echo "=== 17. toda dependencia tem repositorio declarado no proprio modulo ==="
# Esta secao nasceu de um build quebrado de verdade: o modulo 1.16.5 fabric pedia
# me.shedaniel:RoughlyEnoughItems-runtime e nao declarava o maven do Shedaniel. O Gradle foi
# procurar no Maven Central, no maven.fabricmc.net e no libraries.minecraft.net - nenhum dos
# tres hospeda mod - e parou com "Could not find". As 16 secoes anteriores estavam todas
# verdes: elas conferem CODIGO, e isto e configuracao. Nao se ve compilando, so baixando.
#
# A tabela abaixo nao e palpite: cada linha diz de onde a coordenada foi lida de fato, e a
# propria secao cobra que a tabela continue casando com o projeto (entrada que ninguem usa
# vira erro, e grupo que nao esta na tabela tambem).
python3 - <<'PYREPO'
import pathlib, re, sys

# grupo -> host que o publica. Origem de cada linha:
#   me.shedaniel        maven-metadata.xml de RoughlyEnoughItems-runtime/-api em maven.shedaniel.me
#   mezz.jei            e o maven do BlameJared que os 8 modulos com JEI ja declaravam
#   dev.emi / com.terraformersmc  maven da TerraformersMC, idem
#   org.spongepowered   processador de anotacoes do Mixin, so nos modulos Forge/legacy
#   net.minecraftforge  maven da propria Forge, usado pelo Architectury Loom na 1.16.5
HOSPEDEIRO = {
    "me.shedaniel":       "maven.shedaniel.me",
    "mezz.jei":           "maven.blamejared.com",
    "dev.emi":            "maven.terraformersmc.com",
    "com.terraformersmc": "maven.terraformersmc.com",
    "org.spongepowered":  "repo.spongepowered.org",
    "net.minecraftforge": "maven.minecraftforge.net",
}
# Grupos que o PLUGIN de build resolve sozinho: o Loom/ModDev injeta os repositorios deles
# antes de qualquer dependencia ser resolvida. Nenhum modulo precisa declarar nada.
DO_PLUGIN = {"com.mojang", "net.fabricmc", "net.fabricmc.fabric-api",
             "net.neoforged", "org.parchmentmc"}
# Grupos de biblioteca comum: bastam o mavenCentral.
DO_CENTRAL = {"org.slf4j", "com.google.code.gson"}

falhas = 0
usados = set()
conferidos = 0
for bg in sorted(pathlib.Path("versions").glob("*/*/build.gradle")):
    texto = bg.read_text(encoding="utf-8")
    # comentario citando uma coordenada nao e dependencia
    texto = re.sub(r"/\*.*?\*/", "", texto, flags=re.S)
    texto = re.sub(r"^\s*//.*$", "", texto, flags=re.M)
    grupos = sorted(set(re.findall(r'["\']([a-z][\w.-]+):[\w.${}-]+:', texto)))
    hosts = set(re.findall(r"url\s*=?\s*['\"]https?://([^/'\"]+)", texto))
    tem_central = bool(re.search(r"\bmavenCentral\(\)", texto))
    modulo = str(bg.parent)
    conferidos += 1
    for g in grupos:
        if g in HOSPEDEIRO:
            precisa = HOSPEDEIRO[g]
            usados.add(g)
            if precisa not in hosts:
                print(f"  ERRO  {modulo}")
                print(f"          depende de {g}:* mas nao declara o repositorio {precisa}")
                print(f"          o Gradle so descobre isso na hora de baixar: 'Could not find'")
                falhas += 1
        elif g in DO_PLUGIN:
            pass
        elif g in DO_CENTRAL:
            if not tem_central:
                print(f"  ERRO  {modulo}: depende de {g}:* e nao declara mavenCentral()")
                falhas += 1
        else:
            print(f"  ERRO  {modulo}: grupo '{g}' nao esta na tabela desta secao.")
            print(f"          diga aqui em qual repositorio ele mora - senao ninguem confere.")
            falhas += 1

# Entrada que sobrou na tabela e entrada que ninguem mais conferiu: ela envelhece calada.
sobrando = sorted(set(HOSPEDEIRO) - usados)
if sobrando:
    print(f"  ERRO  a tabela tem {', '.join(sobrando)}, que nenhum modulo usa mais - tire de la")
    falhas += 1

if falhas:
    sys.exit(1)
print(f"  ok   {conferidos} modulos; toda coordenada tem repositorio (ou vem do plugin)")
PYREPO

echo
echo "=== 18. minVersion do Mixin nao pode ser maior do que o loader entrega ==="
# Esta secao nasceu de um crash de verdade num modpack Forge 1.16.5:
#
#   Mixin config bettersearch-forge.mixins.json requires mixin subsystem version 0.8.5
#   but 0.8.4 was found. The mixin config will not be applied.
#   Caused by: MixinInitialisationError
#
# O Forge da linha 36 (1.16.5) carrega Mixin 0.8.4. Nosso JSON pedia 0.8.5, e como ele e
# "required": true, isso nao vira aviso: vira crash antes do menu principal. No Fabric o mesmo
# arquivo passava, porque o Fabric Loader traz um Mixin mais novo - por isso o teste no
# EchoShift nao pegou.
#
# A regra aqui NAO e uma tabela de qual loader traz qual Mixin (nao tenho como provar isso
# offline para as nove versoes). E mais simples e mais forte: o minVersion tem de ser o minimo
# que o projeto REALMENTE usa. Nao usamos nada que exija 0.8.5 - so @Inject, @Redirect (com
# opcode), @Shadow, @Accessor, plugin de mixin e defaultRequire, tudo presente desde muito
# antes do 0.8.4. Pedindo o minimo, nao existe piso alto para tropecar.
BS_MIXIN_BASE="0.8.4" python3 - <<'PYMIN'
import json, os, pathlib, re, sys

BASE = os.environ["BS_MIXIN_BASE"]

# Teto PROVADO por evidencia, nao por documentacao. Cada linha diz de onde veio.
#   mc1_16_5 forge  0.8.4  -> launcher_log.txt do modpack Kimetsu no Yaiba (Forge 36.2.34):
#                             "SpongePowered MIXIN Subsystem Version=0.8.4
#                              Source=.../org/spongepowered/mixin/0.8.4/mixin-0.8.4.jar"
TETO = {
    ("mc1_16_5", "forge"): "0.8.4",
}

def num(v):
    return tuple(int(x) for x in v.split("."))

falhas = 0
por_versao = {}
arquivos = sorted(pathlib.Path("versions").glob("*/*/src/main/resources/**/*.mixins.json"))
arquivos += sorted(pathlib.Path("versions").glob("*/*/src/main/resources/*.mixins.json"))
for p in sorted(set(arquivos)):
    partes = p.parts
    versao, loader = partes[1], partes[2]
    try:
        dados = json.loads(p.read_text(encoding="utf-8"))
    except Exception as e:
        print(f"  ERRO  {p}: {e}")
        falhas += 1
        continue
    mv = dados.get("minVersion")
    if mv is None:
        print(f"  ERRO  {p} nao declara minVersion")
        print( "        sem ele o Mixin nao consegue avisar que o config e novo demais")
        falhas += 1
        continue
    por_versao.setdefault((versao, loader), {}).setdefault(mv, []).append(p.name)

    if num(mv) > num(BASE):
        print(f"  ERRO  {p}")
        print(f"          pede Mixin {mv}, mas o projeto so usa recurso de {BASE}")
        print( "          pedir mais do que se usa nao protege de nada e quebra loader antigo")
        falhas += 1
    teto = TETO.get((versao, loader))
    if teto and num(mv) > num(teto):
        print(f"  ERRO  {p}: pede {mv} e o loader de {versao}/{loader} entrega {teto}")
        print( "          com \"required\": true isso e crash na inicializacao, nao aviso")
        falhas += 1

# Um arquivo de uma versao pedindo piso diferente do outro e o cheiro do bug: foi assim que o
# bettersearch-rei.mixins.json da 1.16.5 ficou em 0.8.4 enquanto o principal seguia em 0.8.5.
for (versao, loader), mapa in sorted(por_versao.items()):
    if len(mapa) > 1:
        print(f"  ERRO  {versao}/{loader} tem minVersion divergente entre os arquivos:")
        for v, nomes in sorted(mapa.items()):
            print(f"          {v}: {', '.join(sorted(nomes))}")
        falhas += 1

if falhas:
    sys.exit(1)
total = sum(len(n) for m in por_versao.values() for n in m.values())
print(f"  ok   {total} arquivo(s) de mixin, todos em {BASE} e dentro do que o loader entrega")
PYMIN

echo
echo "=== 19. gancho do REI 6.5 (1.16.5 Forge) existe no jar de verdade ==="
# Secao propria porque a 1.16.5 tem DUAS bases de codigo do REI no mesmo Minecraft: 5.x no
# Fabric (secao 16) e 6.5 no Forge (aqui). E a 6.5 nao e igual a nenhuma das outras oito
# versoes que a secao 8 cobre - tres diferencas, todas lidas com javap e nao deduzidas:
#
#   createFilter          aqui tem UM argumento; da 1.18.2 em diante ganhou o InputMethod
#   copyAndOrder          nao existe: quem entrega a lista filtrada e AsyncSearchManager.get()
#   isReloading           nao existe: o equivalente e o getStage() do Reloadable
#
# O modulo compila contra stub. Stub e promessa; isto aqui e a prova.
BS_REI65="$REI_1_16_5_FORGE" python3 - <<'PYREI65'
import os, pathlib, re, subprocess, sys, tempfile, zipfile

jar = os.environ["BS_REI65"]
if not jar or not pathlib.Path(jar).is_file():
    print("  (pulado: jar do REI 1.16.5 Forge nao informado)")
    sys.exit(0)

trabalho = tempfile.mkdtemp()
with zipfile.ZipFile(jar) as z:
    z.extractall(trabalho)

def desmontar(classe, com_codigo=False):
    cmd = ["javap", "-p"] + (["-c"] if com_codigo else []) + ["-cp", trabalho, classe]
    return subprocess.run(cmd, capture_output=True, text=True, timeout=180).stdout

falhas = 0
# As fontes do gancho, usadas nas duas conferencias abaixo.
ALVOS_FONTE = [
    "versions/mc1_16_5/forge/src/main/java/com/rivalzin/bettersearch/client/ReiSearch.java",
    "versions/mc1_16_5/forge/src/main/java/com/rivalzin/bettersearch/client/ReiIndexBuilder.java",
    "versions/mc1_16_5/forge/src/main/java/com/rivalzin/bettersearch/mixin/rei/SearchProviderImplMixin.java",
    "versions/mc1_16_5/forge/src/main/java/com/rivalzin/bettersearch/mixin/rei/AsyncSearchManagerMixin.java",
]
ESPERADO = {
    "me.shedaniel.rei.impl.client.search.SearchProviderImpl": [
        # UM argumento. Com dois, o mixin nao acha o metodo e o gancho some sem erro no log.
        "public me.shedaniel.rei.api.client.search.SearchFilter createFilter(java.lang.String)",
    ],
    "me.shedaniel.rei.impl.client.search.AsyncSearchManager": [
        "public java.util.List<me.shedaniel.rei.api.common.entry.EntryStack<?>> get()",
        # o campo que o AsyncSearchManagerMixin faz @Shadow
        "private me.shedaniel.rei.api.client.search.SearchFilter filter",
    ],
    "me.shedaniel.rei.api.client.registry.entry.EntryRegistry": [
        "public abstract java.util.List<me.shedaniel.rei.api.common.entry.EntryStack<?>> getPreFilteredList()",
    ],
    "me.shedaniel.rei.api.client.search.SearchFilter": [
        "public abstract java.lang.String getFilter()",
        "public default void prepareFilter(java.util.Collection<me.shedaniel.rei.api.common.entry.EntryStack<?>>)",
    ],
    "me.shedaniel.rei.api.common.util.TextRepresentable": [
        "asFormatStrippedText()",
    ],
    "me.shedaniel.rei.api.common.registry.Reloadable": [
        "getStage()",
    ],
}
for classe, assinaturas in ESPERADO.items():
    saida = desmontar(classe)
    if not saida.strip():
        print(f"  ERRO  a classe {classe} nao existe no jar")
        falhas += 1
        continue
    for assinatura in assinaturas:
        if assinatura not in saida:
            print(f"  ERRO  {classe}: nao achei '{assinatura}'")
            falhas += 1

# ------------------------------------------------------------------------------------
# Metodo que e CONSTANTE nao serve como sinal de prontidao.
#
# Esta parte nasceu do pior bug desta versao. Eu troquei o isReloading() (que so existe do REI
# 8 em diante) por getStage() != END. A assinatura existia, o javac aceitou e a propria secao
# 19 aprovou - porque ela so conferia que o metodo EXISTE. So que o corpo do EntryRegistryImpl
# da 6.5 e uma constante:
#
#     public ReloadStage getStage();
#        0: getstatic  // Field ReloadStage.START
#        3: areturn
#
# Com isso "!= END" era sempre verdadeiro, o indice do REI nunca era montado, e a busca
# simplesmente nao acontecia - sem crash, sem log, sem nada para investigar.
#
# Entao agora a secao desmonta o corpo: se um metodo que o nosso codigo usa como pergunta
# devolve sempre a mesma constante, ele nao pode ser usado como pergunta.
CONSTANTES = {
    ("me.shedaniel.rei.impl.common.entry.type.EntryRegistryImpl", "getStage"): "ReloadStage",
}
for (classe, metodo), simbolo in CONSTANTES.items():
    asm = desmontar(classe, com_codigo=True)
    corpo = re.search(r"^ {2}.*\b" + re.escape(metodo) + r"\(\);(.*?)(?=^ {2}\S)", asm, re.S | re.M)
    if not corpo:
        continue
    instrucoes = re.findall(r"^\s*\d+:\s*(\w+)", corpo.group(1), re.M)
    constante = instrucoes and set(instrucoes) <= {"getstatic", "areturn", "ldc", "iconst_0",
                                                   "iconst_1", "ireturn", "aconst_null"}
    # So o CODIGO conta. O comentario que explica esta armadilha cita ReloadStage de
    # proposito, e ele nao pode ser o que faz a conferencia acusar - documentar o erro nao e
    # cometer o erro.
    def codigo(caminho):
        fonte = pathlib.Path(caminho).read_text(encoding="utf-8")
        fonte = re.sub(r"/\*.*?\*/", "", fonte, flags=re.S)
        return re.sub(r"^\s*//.*$", "", fonte, flags=re.M)
    usado = any(simbolo in codigo(c) for c in ALVOS_FONTE if pathlib.Path(c).exists())
    if constante and usado:
        print(f"  ERRO  {classe}.{metodo}() e uma CONSTANTE neste jar ({' '.join(instrucoes)}),")
        print(f"          e o nosso codigo cita {simbolo}. Constante nao responde pergunta:")
        print( "          o guarda vira sempre-verdadeiro ou sempre-falso e o gancho morre calado.")
        falhas += 1

# O contrario tambem importa: o codigo NAO pode usar o que so existe nas versoes novas.
# Se um dia estas aparecerem aqui, o gancho pode (e deve) voltar a ser o da secao 8.
NAO_DEVE_EXISTIR = {
    "me.shedaniel.rei.api.client.registry.entry.EntryRegistry": "isReloading()",
    "me.shedaniel.rei.impl.client.gui.widget.entrylist.EntryListSearchManager": "copyAndOrder",
}
for classe, marca in NAO_DEVE_EXISTIR.items():
    if marca in desmontar(classe):
        print(f"  aviso: {classe} agora tem {marca} - da para usar o gancho comum da secao 8")

# O SearchFilter tem de ser Predicate de um argumento so: o mixin embrulha ele.
sf = desmontar("me.shedaniel.rei.api.client.search.SearchFilter")
if "test(me.shedaniel.rei.api.common.entry.EntryStack, long)" in sf:
    print("  ERRO  este SearchFilter tem test(EntryStack, long) - o embrulho precisa sobrescrever")
    falhas += 1

# Prova principal: o metodo que o mixin declara em method= existe mesmo, com o descritor exato.
BASICOS = {"B": "byte", "C": "char", "D": "double", "F": "float", "I": "int",
           "J": "long", "S": "short", "Z": "boolean", "V": "void"}

def tipos(desc):
    fora, i = [], 0
    while i < len(desc):
        dim = 0
        while desc[i] == "[":
            dim += 1
            i += 1
        if desc[i] == "L":
            fim = desc.index(";", i)
            nome = desc[i + 1:fim].replace("/", ".")
            i = fim + 1
        else:
            nome = BASICOS[desc[i]]
            i += 1
        fora.append(nome + "[]" * dim)
    return fora

ALVOS = {
    "versions/mc1_16_5/forge/src/main/java/com/rivalzin/bettersearch/mixin/rei/SearchProviderImplMixin.java":
        "me.shedaniel.rei.impl.client.search.SearchProviderImpl",
    "versions/mc1_16_5/forge/src/main/java/com/rivalzin/bettersearch/mixin/rei/AsyncSearchManagerMixin.java":
        "me.shedaniel.rei.impl.client.search.AsyncSearchManager",
}
for caminho, dono in ALVOS.items():
    fonte = pathlib.Path(caminho)
    if not fonte.exists():
        print(f"  ERRO  nao achei o mixin em {caminho}")
        falhas += 1
        continue
    m = re.search(r'method\s*=\s*"([\w$<>]+)\(([^)]*)\)([^"]+)"', fonte.read_text(encoding="utf-8"))
    if not m:
        print(f"  ERRO  {fonte.name}: o method= precisa trazer o descritor completo")
        print( "          sem descritor o Mixin escolhe pelo nome e uma sobrecarga errada passa batido")
        falhas += 1
        continue
    nome, params = m.group(1), tipos(m.group(2))
    asm = desmontar(dono)
    escrito = f"{nome}({', '.join(params)})"
    achou = False
    for d in re.finditer(r"^ {2}.*?\b" + re.escape(nome) + r"\(([^)]*)\);\s*$", asm, re.M):
        crus = re.sub(r"<[^<>]*>", "", d.group(1))
        if [p.strip() for p in crus.split(",") if p.strip()] == params:
            achou = True
            break
    if not achou:
        print(f"  ERRO  {fonte.name} mira em {escrito}, que nao existe em {dono}")
        outras = sorted(set(re.findall(r"^ {2}.*?\b" + re.escape(nome) + r"\(([^)]*)\);", asm, re.M)))
        if outras:
            print(f"          as sobrecargas com esse nome sao: {'; '.join(nome + '(' + o + ')' for o in outras[:4])}")
        falhas += 1

if falhas:
    print()
    print("  O modulo compila contra stub porque o jar do REI vem em nomes MCP e o javac puro nao")
    print("  traduz. Se esta secao falha, o mod compila limpo e a busca no REI nao acontece.")
    sys.exit(1)
print("  ok   createFilter(String), AsyncSearchManager.get/filter, getPreFilteredList,")
print("       getStage e asFormatStrippedText conferidos no bytecode do 6.5.436")
PYREI65

echo
echo "=== 20. cada plugin de build so recebe o que ele aceita ==="
# Nasceu de um build quebrado de verdade. Eu declarei o processador de anotacoes do Mixin no
# modulo 1.16.5 forge por analogia com os outros modulos Forge - e derrubei os tres mixins que
# ja funcionavam em jogo:
#
#   error: No obfuscation mapping for @Inject target updateCommandInfo
#   error: No obfuscation mapping for @Redirect target updateCollections
#
# A razao: quem monta a 1.16.5 e o Architectury Loom, e ele remapeia os mixins sozinho no
# remapJar. O processador quer o mapa MCP->SRG, que o Loom nao passa para ele. Nos modulos do
# ModDevGradle e o contrario: sem o processador nao ha refmap e os mixins nao acham nada.
#
# Ou seja: a MESMA linha e obrigatoria num plugin e proibida no outro. Nada disso aparece
# compilando aqui - so quando o Gradle roda de verdade. Por isso e uma secao.
python3 - <<'PYPLUG'
import pathlib, re, sys

# plugin -> (precisa do processador do Mixin?, por que)
REGRA = {
    "dev.architectury.loom": (False, "o Architectury Loom remapeia os mixins no remapJar; o"
                                     " processador exigiria um mapa MCP->SRG que ele nao passa"),
    # So a linha LEGACY precisa. Da 1.20.2 em diante o NeoForge roda em nomes Mojang, sem SRG:
    # nao ha o que remapear, e nenhum dos cinco modulos modernos declara o processador.
    "net.neoforged.moddev.legacyforge": (True, "1.17-1.20.1 rodam em SRG; sem refmap os mixins"
                                               " nao acham o alvo em producao"),
    "net.neoforged.moddev": (False, "da 1.20.2 em diante o NeoForge roda em nomes Mojang - nao"
                                    " ha SRG, entao nao ha refmap a gerar"),
    # O Loom de Fabric tambem remapeia sozinho, e la o refmap quem escreve e ele.
    "net.fabricmc.fabric-loom": (False, "o Loom escreve o refmap na hora de remapear"),
    # 1.12.2. Ainda sem mixin nenhum; quando a etapa do mixin chegar, o "como remapear" sera
    # decidido la (LaunchWrapper/tweaker, outro mundo) - e esta linha muda JUNTO com a decisao.
    "com.gtnewhorizons.retrofuturagradle": (False, "modulo sem mixin por enquanto; o processador"
                                                   " nao tem o que gerar aqui"),
    "net.fabricmc.fabric-loom-remap": (False, "idem"),
}

falhas = 0
conferidos = 0
for bg in sorted(pathlib.Path("versions").glob("*/*/build.gradle")):
    texto = bg.read_text(encoding="utf-8")
    limpo = re.sub(r"/\*.*?\*/", "", texto, flags=re.S)
    limpo = re.sub(r"^\s*//.*$", "", limpo, flags=re.M)

    plugins = [p for p in REGRA if re.search(r"id\s+['\"]" + re.escape(p) + r"['\"]", limpo)]
    if not plugins:
        print(f"  ERRO  {bg.parent}: nao reconheci o plugin de build - acrescente a regra aqui")
        falhas += 1
        continue
    # o mais especifico vence (legacyforge contem moddev no nome)
    plugin = max(plugins, key=len)
    precisa, motivo = REGRA[plugin]
    tem = bool(re.search(r"annotationProcessor.*org\.spongepowered:mixin", limpo))
    conferidos += 1
    if precisa and not tem:
        print(f"  ERRO  {bg.parent} usa {plugin} e NAO declara o processador do Mixin")
        print(f"          {motivo}")
        falhas += 1
    elif not precisa and tem:
        print(f"  ERRO  {bg.parent} usa {plugin} e declara o processador do Mixin")
        print(f"          {motivo}")
        falhas += 1

if falhas:
    sys.exit(1)
print(f"  ok   {conferidos} modulos; processador do Mixin so onde o plugin de build precisa dele")
PYPLUG
echo
echo "=== 21. gancho do JEI 7.8 (1.16.5 Forge) existe no jar de verdade ==="
# Secao propria, e nao um alvo da secao 6, porque a 7.8 e anterior a separacao do JEI em
# "common" e nada bate com as outras oito versoes:
#
#   pacote     mezz.jei.ingredients        (e nao mezz.jei.common.ingredients)
#   elemento   IIngredientListElementInfo  (o ITypedIngredient nem existe)
#   id         getResourceId() -> String   (e nao getResourceLocation() -> ResourceLocation)
#   campo      elementSearch NAO e final   (na 1.18.2 e final, e o mixin de la usa @Shadow @Final)
#
# O ultimo e o mais traicoeiro: @Final num campo que nao e final faz o Mixin recusar a classe
# inteira, e com "required": false isso nao vira crash - vira gancho ausente e silencioso.
BS_JEI78="$JEI_1_16_5" BS_JEI78_ESPERADO="$(grep -oP '(?<=^jei_version_1165=).*' gradle.properties)" \
python3 - <<'PYJEI78'
import os, pathlib, re, subprocess, sys, tempfile, zipfile

jar = os.environ["BS_JEI78"]
if not jar or not pathlib.Path(jar).is_file():
    print("  (pulado: jar do JEI 1.16.5 nao informado)")
    sys.exit(0)

trabalho = tempfile.mkdtemp()
with zipfile.ZipFile(jar) as z:
    z.extractall(trabalho)

versao = ""
toml = pathlib.Path(trabalho) / "META-INF" / "mods.toml"
if toml.exists():
    m = re.search(r'^version\s*=\s*"([^"]+)"', toml.read_text(encoding="utf-8", errors="replace"), re.M)
    versao = m.group(1) if m else ""

def desmontar(classe, com_codigo=False):
    cmd = ["javap", "-p"] + (["-c"] if com_codigo else []) + ["-cp", trabalho, classe]
    return subprocess.run(cmd, capture_output=True, text=True, timeout=180).stdout

falhas = 0
ESPERADO = {
    "mezz.jei.ingredients.IngredientFilter": [
        "getIngredientListUncached(java.lang.String)",
        "mezz.jei.search.IElementSearch elementSearch",
        "public void invalidateCache()",
    ],
    "mezz.jei.ingredients.IIngredientListElementInfo": [
        "public abstract java.lang.String getName()",
        "public abstract java.lang.String getResourceId()",
        "getElement()",
    ],
    "mezz.jei.search.IElementSearch": [
        "getAllIngredients()",
    ],
    "mezz.jei.gui.ingredients.IIngredientListElement": [
        "public abstract boolean isVisible()",
        "getIngredient()",
    ],
}
for classe, assinaturas in ESPERADO.items():
    saida = desmontar(classe)
    if not saida.strip():
        print(f"  ERRO  a classe {classe} nao existe no jar")
        falhas += 1
        continue
    for assinatura in assinaturas:
        if assinatura not in saida:
            print(f"  ERRO  {classe}: nao achei '{assinatura}'")
            falhas += 1

# O campo elementSearch NAO pode ser final: o mixin faz @Shadow sem @Final de proposito.
filtro = desmontar("mezz.jei.ingredients.IngredientFilter")
campo = [l for l in filtro.split("\n") if "IElementSearch elementSearch" in l]
fonte_mixin = pathlib.Path("versions/mc1_16_5/forge/src/main/java/com/rivalzin/bettersearch"
                           "/mixin/jei/IngredientFilterMixin.java")
if campo:
    e_final = "final" in campo[0]
    texto = fonte_mixin.read_text(encoding="utf-8") if fonte_mixin.exists() else ""
    limpo = re.sub(r"/\*.*?\*/", "", texto, flags=re.S)
    usa_final = re.search(r"@Shadow\s*\n\s*@Final\s*\n\s*private\s+IElementSearch", limpo)
    if e_final and not usa_final:
        print("  ERRO  elementSearch E final no jar, mas o mixin nao usa @Final")
        falhas += 1
    elif not e_final and usa_final:
        print("  ERRO  elementSearch NAO e final neste jar, e o mixin usa @Shadow @Final")
        print("          o Mixin recusa a classe inteira - e com required:false vira gancho ausente")
        falhas += 1

# O descritor do method= tem de existir no bytecode, com os parametros exatos.
if not fonte_mixin.exists():
    print(f"  ERRO  nao achei {fonte_mixin}")
    falhas += 1
else:
    m = re.search(r'method\s*=\s*"([\w$]+)\(([^)]*)\)', fonte_mixin.read_text(encoding="utf-8"))
    if not m:
        print("  ERRO  o method= do IngredientFilterMixin precisa trazer o descritor completo")
        falhas += 1
    else:
        BASICOS = {"B": "byte", "C": "char", "D": "double", "F": "float", "I": "int",
                   "J": "long", "S": "short", "Z": "boolean", "V": "void"}
        def tipos(desc):
            fora, i = [], 0
            while i < len(desc):
                dim = 0
                while desc[i] == "[":
                    dim += 1; i += 1
                if desc[i] == "L":
                    fim = desc.index(";", i); nome = desc[i+1:fim].replace("/", "."); i = fim + 1
                else:
                    nome = BASICOS[desc[i]]; i += 1
                fora.append(nome + "[]" * dim)
            return fora
        nome, params = m.group(1), tipos(m.group(2))
        achou = any([p.strip() for p in re.sub(r"<[^<>]*>", "", d.group(1)).split(",") if p.strip()] == params
                    for d in re.finditer(r"^ {2}.*?\b" + re.escape(nome) + r"\(([^)]*)\);\s*$", filtro, re.M))
        if not achou:
            print(f"  ERRO  o mixin mira em {nome}({', '.join(params)}), que nao existe no IngredientFilter")
            falhas += 1

if falhas:
    sys.exit(1)
esperado = os.environ.get("BS_JEI78_ESPERADO", "").strip()
print(f"  ok   getIngredientListUncached, elementSearch (nao-final), IIngredientListElementInfo")
print(f"       e getResourceId conferidos no jar {versao or '(versao nao lida)'}")
if esperado and versao and esperado != versao:
    print(f"  ATENCAO: o build compila contra {esperado} e esta conferencia rodou em {versao}.")
    print(f"           Mesma linha 7.8, mas patch diferente. Para fechar a conta, aponte o")
    print(f"           BS_JEI_1_16_5 para o jar {esperado} do proprio modpack.")
PYJEI78

echo
echo "=== 22. gancho do JEI 4.16 (1.12.2) existe no jar de verdade e os esbocos batem ==="
# A 1.12.2 e a unica versao SEM Mixin no JEI: o gancho troca o campo combinedSearchTrees por
# uma subclasse que soma os nossos resultados. Isso so e legal enquanto quatro fatos do jar
# continuarem verdade - campo nao-final, classe nao-final com construtor publico, indice das
# arvores = posicao na elementList - e enquanto cada membro dos ESBOCOS de compilacao
# (src/jeiApi) existir no jar com o mesmo descritor. Tudo isso e conferido aqui.
BS_JEI1122_JAR="$JEI_1_12_2" python3 - <<'PYJEI1122'
import os, pathlib, subprocess, sys, tempfile, zipfile

jar = os.environ["BS_JEI1122_JAR"]
if not jar or not pathlib.Path(jar).is_file():
    print("  (pulado: jar do JEI 1.12.2 nao informado)")
    sys.exit(0)

trabalho = tempfile.mkdtemp()
with zipfile.ZipFile(jar) as z:
    z.extractall(trabalho)

def desmontar(classe, com_codigo=False):
    cmd = ["javap", "-p"] + (["-c"] if com_codigo else []) + ["-cp", trabalho, classe]
    return subprocess.run(cmd, capture_output=True, text=True, timeout=180).stdout

falhas = []

# 1) Assinaturas que o gancho usa DIRETO (mesmos textos dos esbocos).
ESPERADO = {
    "mezz.jei.Internal": [
        "public static mezz.jei.ingredients.IngredientFilter getIngredientFilter()",
    ],
    "mezz.jei.ingredients.IngredientFilter": [
        "public void invalidateCache()",
        "combinedSearchTrees",
        "elementList",
    ],
    "mezz.jei.suffixtree.CombinedSearchTrees": [
        "public mezz.jei.suffixtree.CombinedSearchTrees()",
        "public it.unimi.dsi.fastutil.ints.IntSet search(java.lang.String)",
        "public void addSearchTree(mezz.jei.suffixtree.ISearchTree)",
    ],
    "mezz.jei.gui.ingredients.IIngredientListElement": [
        "getIngredient()",
        "public abstract java.lang.String getDisplayName()",
        "public abstract java.lang.String getResourceId()",
        "getTooltipStrings()",
    ],
}
saidas = {}
for classe, membros in ESPERADO.items():
    saida = saidas[classe] = desmontar(classe)
    for membro in membros:
        if membro not in saida:
            falhas.append(f"{classe}: nao achei '{membro}'")

# 2) Os dois "nao-final" que sustentam o gancho inteiro.
filtro = saidas["mezz.jei.ingredients.IngredientFilter"]
linha_campo = next((l for l in filtro.splitlines() if "combinedSearchTrees" in l), "")
if " final " in linha_campo:
    falhas.append(f"combinedSearchTrees virou final: '{linha_campo.strip()}' - a troca do campo morre")
arvore = saidas["mezz.jei.suffixtree.CombinedSearchTrees"]
cabecalho = next((l for l in arvore.splitlines() if "class mezz.jei.suffixtree.CombinedSearchTrees" in l), "")
if "final" in cabecalho:
    falhas.append(f"CombinedSearchTrees virou final: '{cabecalho.strip()}' - a subclasse morre")

# 3) O indice das arvores e a posicao na elementList (put recebe size() no addIngredient).
corpo_filtro = desmontar("mezz.jei.ingredients.IngredientFilter", com_codigo=True)
if "GeneralizedSuffixTree.put" not in corpo_filtro or "NonNullList.size" not in corpo_filtro:
    falhas.append("addIngredient nao usa mais size()+put - o mapa indice->elemento mudou")

# 4) fastutil: so usamos membros cuja resolucao o PROPRIO JEI ja exercita neste runtime.
usos = (desmontar("mezz.jei.suffixtree.CombinedSearchTrees", com_codigo=True)
        + desmontar("mezz.jei.suffixtree.GeneralizedSuffixTree", com_codigo=True)
        + desmontar("mezz.jei.suffixtree.Node", com_codigo=True))
for prova in ['IntOpenHashSet."<init>":(I)V', "IntSet.addAll", "IntList.add:(I)Z"]:
    if prova not in usos:
        falhas.append(f"o proprio JEI nao emite mais {prova} - reconferir os esbocos do fastutil")

# 5) Todo esboco mezz/** tem a classe correspondente DENTRO do jar.
raiz = pathlib.Path("versions/mc1_12_2/forge/src/jeiApi/java")
for esboco in sorted(raiz.rglob("mezz/**/*.java")):
    rel = esboco.relative_to(raiz).with_suffix(".class").as_posix()
    if not (pathlib.Path(trabalho) / rel).is_file():
        falhas.append(f"esboco sem classe real no jar: {rel}")

if falhas:
    for f in falhas:
        print(f"  PROBLEMA  {f}")
    sys.exit(1)
print("  ok   Internal/IngredientFilter/CombinedSearchTrees/IIngredientListElement conferidos,")
print("       campo e classe continuam nao-finais, indice = posicao na elementList,")
print("       fastutil so com membros que o proprio JEI emite, esbocos todos com classe real")
PYJEI1122

echo
echo "=== 23. nenhum % solto nos .lang da 1.12.2 ==="
# Na 1.12.2 o I18n.format passa TODA traducao por String.format, com ou sem argumento.
# Um "%tab" literal vira MissingFormatArgument e a tela mostra "Format error: ..." no lugar
# do texto - foi pego ao renomear a opcao do JEI. Aqui, % so pode existir como %s/%d
# (argumento de verdade) ou %% (escapado).
python3 - <<'PYLANG1122'
import pathlib, re, sys

solto = re.compile(r"%(?!%)(?![sd])")
falhas = 0
conferidos = 0
for arq in sorted(pathlib.Path("versions/mc1_12_2/forge/src/main/resources/assets/bettersearch/lang").glob("*.lang")):
    conferidos += 1
    for numero, linha in enumerate(arq.read_text(encoding="utf-8").splitlines(), 1):
        if "=" not in linha or linha.startswith("#"):
            continue
        valor = linha.split("=", 1)[1]
        # Remove os pares legitimos e ve se sobra %.
        resto = valor.replace("%%", "").replace("%s", "").replace("%d", "")
        if "%" in resto:
            print(f"  ERRO  {arq.name}:{numero}: % solto vira 'Format error' na tela: {linha.split('=',1)[0]}")
            falhas += 1
if falhas:
    sys.exit(1)
print(f"  ok   {conferidos} arquivos .lang sem % solto")
PYLANG1122

echo
echo "=== 24. toda flag de trabalho em voo tem o caminho de volta ==="
# O bug real que gerou esta secao: Linguas.carregando (1.12.2) virava true na primeira carga
# e nunca voltava a false. O invalidar() derrubava a tabela, a recarga seguinte via a flag
# levantada e desistia - trocar a lista de idiomas no menu matava a busca entre idiomas ate
# reiniciar o jogo. A regra: se um arquivo poe uma flag volatile de "estou trabalhando" em
# true, o MESMO arquivo tem de devolve-la a false (e dentro de finally, para excecao nao
# deixar a trava fechada).
python3 - <<'PYFLAGS'
import pathlib, re, sys

FLAGS = ("carregando", "montando", "loading", "building")
falhas = 0
conferidos = 0
for arq in sorted(pathlib.Path("versions").rglob("src/main/java/**/*.java")):
    texto = arq.read_text(encoding="utf-8")
    for flag in FLAGS:
        liga = re.search(rf"\b{flag}\s*=\s*true\b", texto)
        if not liga:
            continue
        conferidos += 1
        if not re.search(rf"\b{flag}\s*=\s*false\b", texto):
            print(f"  ERRO  {arq}: '{flag} = true' sem nenhum '{flag} = false' - a flag e uma")
            print(f"        trava de mao unica: depois do primeiro uso, o caminho nunca reabre")
            falhas += 1
            continue
        # O desligamento tem de estar protegido: um throw no meio nao pode deixar a trava
        # fechada. Dois jeitos validos, ambos rodando com sucesso E com excecao: finally
        # (as threads da 1.12.2) ou whenComplete (o CompletableFuture das outras versoes -
        # a primeira redacao desta secao nao o conhecia e acusou 18 arquivos corretos).
        if "finally" not in texto and "whenComplete" not in texto:
            print(f"  ERRO  {arq}: '{flag} = false' existe mas fora de finally/whenComplete -")
            print(f"        uma excecao no caminho feliz deixa a trava fechada")
            falhas += 1
if falhas:
    sys.exit(1)
print(f"  ok   {conferidos} flag(s) de trabalho em voo, todas com volta garantida")
PYFLAGS
}

SAIDA_VERIFY="$(mktemp)"
corpo 2>&1 | tee "$SAIDA_VERIFY"
# "command not found", "unbound variable", "syntax error" sao erros do PROPRIO script.
# Sem eles aqui, um \n literal virando comando passava despercebido: a secao imprimia
# o resultado certo, o shell reclamava no meio, e a saida continuava 0.
if grep -qE "ERRO|PROBLEMA|FALHA|command not found|unbound variable|syntax error|No such file or directory" "$SAIDA_VERIFY"; then
    echo
    echo "############################################################"
    echo "#  NAO PASSOU - ha ERRO/PROBLEMA acima. Nao empacote isto. #"
    echo "############################################################"
    rm -f "$SAIDA_VERIFY"
    exit 1
fi
rm -f "$SAIDA_VERIFY"
echo
echo "todas as secoes passaram."
