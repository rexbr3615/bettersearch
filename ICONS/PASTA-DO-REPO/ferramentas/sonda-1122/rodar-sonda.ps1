# Sonda da 1.12.2 - descobre qual ferramenta de build monta o Minecraft 1.12.2 nesta maquina,
# com os nomes MCP (abaixo da 1.14.4 nao existem mapeamentos da Mojang).
#
#   .\rodar-sonda.ps1
#
# FALHA E RESULTADO VALIDO - mas cuidado ao ler: falha pode ser "a ferramenta recusou" OU
# "a sonda estava mal escrita". Na 1.16.5 a primeira rodada teve duas do segundo tipo.
# O resumo mostra o motivo REAL de cada falha, tirado do "What went wrong" do Gradle.
#
# ATENCAO: a sonda A decompila e RECOMPILA o Minecraft 1.12.2 inteiro na primeira rodada.
# No seu processador isso deve levar poucos minutos, mas nao e travamento - e trabalho.

$ErrorActionPreference = 'Continue'
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path

$gradlew = Join-Path (Split-Path -Parent (Split-Path -Parent $aqui)) 'gradlew.bat'
if (-not (Test-Path $gradlew)) {
    Write-Host "Nao achei o gradlew.bat do mod em: $gradlew" -ForegroundColor Red
    Write-Host "Rode este script de dentro de ...\bettersearch\ferramentas\sonda-1122\" -ForegroundColor Yellow
    exit 1
}

$sondas = @(
    @{ pasta = 'a-rfg';          nome = 'A. RetroFuturaGradle 2.0.2 -> Forge 1.12.2';  tarefa = 'compileJava exportarJar' }
    @{ pasta = 'c-convivencia';  nome = 'C. RFG e Fabric Loom no MESMO build';         tarefa = 'compileJava' }
)

Write-Host ''
Write-Host '=== sonda da 1.12.2 ===' -ForegroundColor Cyan
Write-Host "gradlew: $gradlew"
Write-Host 'A primeira sonda demora: baixa, DECOMPILA e recompila o Minecraft 1.12.2.'
Write-Host ''

Write-Host '--- diagnostico do Java ---' -ForegroundColor Cyan
Write-Host 'java do PATH (o que lanca o gradlew):'
& cmd /c "java -version 2>&1" | ForEach-Object { Write-Host ('  ' + $_) }
$jdks = Join-Path $env:USERPROFILE '.gradle\jdks'
if (Test-Path $jdks) {
    Write-Host 'JDKs que o proprio Gradle ja baixou (o daemon 25 deve sair daqui):'
    Get-ChildItem $jdks -Directory | ForEach-Object { Write-Host ('  ' + $_.Name) }
} else {
    Write-Host "(sem pasta $jdks - nenhum JDK baixado pelo Gradle ainda)" -ForegroundColor Yellow
}
Write-Host ''

$resultados = @()
foreach ($s in $sondas) {
    $dir = Join-Path $aqui $s.pasta
    $log = Join-Path $aqui ($s.pasta + '.log')
    Write-Host ('--- ' + $s.nome + ' ---') -ForegroundColor Cyan

    # Via cmd /c: senao o PowerShell transforma cada linha de stderr do Gradle em
    # "NativeCommandError". Sem --stacktrace: enterra a mensagem util. Licoes da 1.16.5.
    Push-Location $dir
    $linhas = & cmd /c "`"$gradlew`" --no-daemon $($s.tarefa) 2>&1"
    $codigo = $LASTEXITCODE
    Pop-Location

    $linhas | Set-Content -Path $log -Encoding UTF8
    $linhas | ForEach-Object { Write-Host $_ }

    if ($codigo -eq 0) {
        Write-Host ('  PASSOU: ' + $s.nome) -ForegroundColor Green
        $resultados += [pscustomobject]@{ Sonda = $s.nome; Resultado = 'PASSOU'; Motivo = '' }
    } else {
        $motivo = ''
        for ($i = 0; $i -lt $linhas.Count; $i++) {
            if ($linhas[$i] -match '\* What went wrong') {
                $fim = [Math]::Min($i + 3, $linhas.Count - 1)
                if ($fim -ge ($i + 1)) {
                    $motivo = ($linhas[($i + 1)..$fim] -join ' ').Trim()
                }
                break
            }
        }
        if (-not $motivo) { $motivo = "codigo de saida $codigo" }
        Write-Host ('  FALHOU: ' + $s.nome) -ForegroundColor Yellow
        Write-Host ('          ' + $motivo) -ForegroundColor DarkGray
        $resultados += [pscustomobject]@{ Sonda = $s.nome; Resultado = 'FALHOU'; Motivo = $motivo }
    }
    Write-Host ''
}

Write-Host '=== resumo ===' -ForegroundColor Cyan
$resultados | Format-List
Write-Host 'Os .log completos ficaram nesta pasta.'
Write-Host ''
Write-Host 'Ja provado, nao precisa testar de novo:' -ForegroundColor DarkGray
Write-Host '  - Architectury Loom esta FORA para 1.12.2: ele exige o forge-*-userdev.jar, e esse' -ForegroundColor DarkGray
Write-Host '    classifier NUNCA foi publicado para 1.12.2 (404 no .sha1 dos builds 2860 e 2864).' -ForegroundColor DarkGray
Write-Host '    O que existe e o userdev3, o formato que o RFG consome. Nao ha o que insistir.' -ForegroundColor DarkGray
Write-Host '  - O RFG nao esta no portal do Gradle: mora no nexus da GTNH (rodada 1 provou).' -ForegroundColor DarkGray
Write-Host '  - O RFG 2.x e compilado para Java 25 (class file 69, rodada 2). O arquivo' -ForegroundColor DarkGray
Write-Host '    gradle/gradle-daemon-jvm.properties de cada sonda pede o daemon em 25.' -ForegroundColor DarkGray
Write-Host '    Se falhar dizendo que nao achou JVM compativel, a lista de JDKs acima decide' -ForegroundColor DarkGray
Write-Host '    o proximo passo - me mande a saida inteira do mesmo jeito.' -ForegroundColor DarkGray
Write-Host ''
Write-Host 'Me manda: esta saida INTEIRA + os mc1122-*.jar que aparecerem na pasta ModBetterSearch.' -ForegroundColor Cyan
