# Sonda da 1.16.5 - descobre qual ferramenta de build monta o Minecraft 1.16.5 com os nomes
# oficiais da Mojang, na mesma versao de Gradle que o Better Search ja usa.
#
#   powershell -ExecutionPolicy Bypass -File rodar-sonda.ps1
#
# FALHA E RESULTADO VALIDO - mas cuidado ao ler: falha pode ser "a ferramenta recusou" OU
# "a sonda estava mal escrita". A primeira rodada teve duas do segundo tipo, e o resumo
# dizia que tinha dado tudo errado. Agora o script mostra o motivo REAL de cada falha em vez
# de enterrar tudo em stacktrace.

$ErrorActionPreference = 'Continue'
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path

$gradlew = Join-Path (Split-Path -Parent (Split-Path -Parent $aqui)) 'gradlew.bat'
if (-not (Test-Path $gradlew)) {
    Write-Host "Nao achei o gradlew.bat do mod em: $gradlew" -ForegroundColor Red
    Write-Host "Rode este script de dentro de ...\bettersearch\ferramentas\sonda-1165\" -ForegroundColor Yellow
    exit 1
}

$sondas = @(
    @{ pasta = 'a-archloom-forge'; nome = 'A. Architectury Loom -> Forge 1.16.5';  tarefa = 'compileJava' }
    @{ pasta = 'b-loom-fabric';    nome = 'B. Fabric Loom 1.17  -> Fabric 1.16.5'; tarefa = 'compileJava' }
    @{ pasta = 'c-convivencia';    nome = 'C. os dois juntos no mesmo build';      tarefa = 'compileJava' }
)

Write-Host ''
Write-Host '=== sonda da 1.16.5 (segunda rodada) ===' -ForegroundColor Cyan
Write-Host "gradlew: $gradlew"
Write-Host 'A primeira pode demorar: baixa o Minecraft 1.16.5 e um JDK 8.'
Write-Host ''

$resultados = @()
foreach ($s in $sondas) {
    $dir = Join-Path $aqui $s.pasta
    $log = Join-Path $aqui ($s.pasta + '.log')
    Write-Host ('--- ' + $s.nome + ' ---') -ForegroundColor Cyan

    # Via cmd /c: assim o PowerShell nao transforma cada linha de stderr do Gradle em um
    # registro de erro (era de onde vinha aquele "NativeCommandError" no meio da saida).
    # Sem --stacktrace: na primeira rodada ele gerou 200 linhas que esconderam a mensagem util.
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
        # O motivo de verdade vem logo depois de "* What went wrong:". Pegar dali, e nao a
        # primeira linha que parecer erro, e o que faz o resumo dizer algo util.
        $motivo = ''
        for ($i = 0; $i -lt $linhas.Count; $i++) {
            if ($linhas[$i] -match '\* What went wrong') {
                # Guarda o intervalo: se "What went wrong" for a ultima linha, um range
                # invertido em PowerShell le o array de tras para frente e mostra lixo.
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
Write-Host 'Ja provado na primeira rodada, nao precisa testar de novo:' -ForegroundColor DarkGray
Write-Host '  ForgeGradle 5.1.77 esta FORA. Ele mesmo diz: "Found Gradle version Gradle 9.6.1.' -ForegroundColor DarkGray
Write-Host '  Versions Gradle 8.0 and newer are not supported."' -ForegroundColor DarkGray
Write-Host ''
Write-Host 'Me manda a saida. Se alguma falhar, o motivo no resumo acima ja diz se foi a' -ForegroundColor Cyan
Write-Host 'ferramenta que recusou ou se fui eu que escrevi a sonda errado de novo.' -ForegroundColor Cyan
