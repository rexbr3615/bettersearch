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

EXTRA_CP="${BS_EXTRA_CP:-}"

# jar do Minecraft por linha de versao (BS_JAR_<versao> sobrescreve)
JAR_1_21_1="${BS_JAR_1_21_1:-$(ls versions/mc1_21_1/neoforge/build/moddev/artifacts/neoforge-*-merged.jar 2>/dev/null | head -1)}"
JAR_1_21_9="${BS_JAR_1_21_9:-$(ls versions/mc1_21_9/neoforge/build/moddev/artifacts/neoforge-*-merged.jar 2>/dev/null | head -1)}"
JAR_1_21_11="${BS_JAR_1_21_11:-$(ls versions/mc1_21_11/neoforge/build/moddev/artifacts/neoforge-*-merged.jar 2>/dev/null | head -1)}"
JAR_1_20_1="${BS_JAR_1_20_1:-$(ls versions/mc1_20_1/fabric/build/mcjar/*.jar 2>/dev/null | head -1)}"
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
STUBS_NEO="${BS_STUBS_NEO:-}"             # FML e barramento de eventos do NeoForge

# Pasta vazia usada como -sourcepath. Sem isto o javac encontra os .java que vem DENTRO do jar
# do NeoForge e tenta compilar o Minecraft inteiro junto.
EMPTY=build/verify/.empty
mkdir -p "$EMPTY"

compile() {  # $1 nome  $2 jar  $3 comum  $4 loader  $5 release  $6 stubs  $7 classpath extra
  local name="$1" jar="$2" common="$3" loader="$4" release="$5" stubs="$6" extracp="${7:-}"
  [ "$release" -gt "$RELEASE_CAP" ] && release="$RELEASE_CAP"
  printf '  %-22s ' "$name"
  if [ -z "$jar" ] || [ ! -f "$jar" ]; then
    echo "(pulado: jar do Minecraft nao encontrado)"
    return
  fi
  local CP="$jar"
  [ -n "$extracp" ] && CP="$CP:$extracp"
  [ -n "$EXTRA_CP" ] && CP="$CP:$EXTRA_CP"
  local SRC
  SRC="$(find core/src/main/java "$common" "$loader" -name '*.java')"
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
    echo "ok (zero erros, zero avisos)"
  else
    echo "PROBLEMA"
    echo "$out" | head -20 | sed 's/^/      /'
  fi
}

echo "=== 1. compilacao contra o Minecraft real ==="
compile "1.21.1 neoforge" "$JAR_1_21_1" versions/mc1_21_1/common/src/main/java versions/mc1_21_1/neoforge/src/main/java 21 "$STUBS"
compile "1.21.1 fabric"   "$JAR_1_21_1" versions/mc1_21_1/common/src/main/java versions/mc1_21_1/fabric/src/main/java   21 "$STUBS $STUBS_FABRIC"
compile "1.21.9 neoforge" "$JAR_1_21_9" versions/mc1_21_9/common/src/main/java versions/mc1_21_9/neoforge/src/main/java 21 "$STUBS_2119 $STUBS_NEO"
compile "1.21.9 fabric"   "$JAR_1_21_9" versions/mc1_21_9/common/src/main/java versions/mc1_21_9/fabric/src/main/java   21 "$STUBS_2119 $STUBS_FABRIC_2119"
compile "1.21.11 neoforge" "$JAR_1_21_11" versions/mc1_21_11/common/src/main/java versions/mc1_21_11/neoforge/src/main/java 21 "$STUBS_2119 $STUBS_NEO"
compile "1.21.11 fabric"  "$JAR_1_21_11" versions/mc1_21_11/common/src/main/java versions/mc1_21_11/fabric/src/main/java  21 "$STUBS_2119 $STUBS_FABRIC_21111"
compile "26.1 neoforge"   "$JAR_26_1"  versions/mc26_1/common/src/main/java   versions/mc26_1/neoforge/src/main/java   25 "$STUBS_2119 $STUBS_NEO"    "$CP_26_1_NEO"
compile "26.1 fabric"     "$JAR_26_1"  versions/mc26_1/common/src/main/java   versions/mc26_1/fabric/src/main/java     25 "$STUBS_2119 $STUBS_MODMENU" "$CP_26_1_FABRIC"
compile "26.2 neoforge"   "$JAR_26_2"  versions/mc26_2/common/src/main/java   versions/mc26_2/neoforge/src/main/java   25 "$STUBS_2119 $STUBS_NEO"    "$CP_26_2_NEO"
compile "26.2 fabric"     "$JAR_26_2"  versions/mc26_2/common/src/main/java   versions/mc26_2/fabric/src/main/java     25 "$STUBS_2119 $STUBS_MODMENU" "$CP_26_2_FABRIC"
compile "1.20.1 fabric"   "$JAR_1_20_1" versions/mc1_20_1/common/src/main/java versions/mc1_20_1/fabric/src/main/java   17 "$STUBS $STUBS_FABRIC"
compile "1.20.1 forge"    "$JAR_1_20_1" versions/mc1_20_1/common/src/main/java versions/mc1_20_1/forge/src/main/java    17 "$STUBS $STUBS_FORGE"

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
echo "=== 4. os dois arquivos de mixin da 1.20.1 continuam iguais ==="
# O Forge precisa da linha "refmap" (roda com nomes SRG) e o Fabric nao pode te-la (quem
# escreve la e o Loom). Por isso sao dois arquivos - e por isso eles podem sair de sincronia
# sem ninguem perceber. Tirando o refmap, tem de ser byte a byte o mesmo arquivo.
COMMON_MIXINS=versions/mc1_20_1/common/src/main/resources/bettersearch.mixins.json
FORGE_MIXINS=versions/mc1_20_1/forge/src/main/resources/bettersearch-forge.mixins.json
if diff <(grep -v '"refmap"' "$FORGE_MIXINS") "$COMMON_MIXINS" > /tmp/bs-mixin-diff 2>&1; then
  echo "  ok   $FORGE_MIXINS == $COMMON_MIXINS (+ refmap)"
else
  echo "  ERRO os dois sairam de sincronia:"
  sed 's/^/      /' /tmp/bs-mixin-diff
fi
