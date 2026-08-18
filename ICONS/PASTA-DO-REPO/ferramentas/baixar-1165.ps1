# Baixa o JEI e o REI da 1.16.5 e imprime as versoes de Fabric API e Mod Menu.
#
#   powershell -ExecutionPolicy Bypass -File baixar-1165.ps1
#
# Tres coisas que ja sei da 1.16.5, conferidas na API do Modrinth e nao de memoria:
#
#   * o EMI NAO EXISTE nesta versao (o primeiro lancamento dele foi na 1.18.2). A opcao dele
#     continua aparecendo no menu, pela sua regra dos ports de terceiros - so nao ha gancho.
#   * o JEI so tem versao de FORGE aqui. Nada de JEI no Fabric da 1.16.5.
#   * o REI tem numeros de versao DIFERENTES por loader: 6.5.436 no Forge e 5.12.385 no
#     Fabric. Nao e detalhe de numeracao - sao bases de codigo diferentes, e o gancho que a
#     gente usa hoje (SearchFilter, createFilter, copyAndOrder) provavelmente nao existe em
#     nenhuma das duas. Por isso o script baixa AS DUAS: eu preciso abrir com javap para
#     descobrir a forma real antes de escrever qualquer linha.

$ErrorActionPreference = 'Continue'
$dest = 'C:\Users\gemad\Downloads\ModBetterSearch'
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$cabecalho = @{ 'User-Agent' = 'rivalzin/bettersearch (contato via modrinth)' }

function Get-Versoes($projeto, $mc, $loader) {
    $gv = [uri]::EscapeDataString('["' + $mc + '"]')
    $ld = [uri]::EscapeDataString('["' + $loader + '"]')
    $url = "https://api.modrinth.com/v2/project/$projeto/version?game_versions=$gv&loaders=$ld"
    try { return Invoke-RestMethod -Uri $url -Headers $cabecalho -TimeoutSec 60 } catch { return $null }
}

Write-Host ''
Write-Host '=== 1. visualizadores da 1.16.5 ===' -ForegroundColor Cyan
Write-Host "destino: $dest"
Write-Host ''

$baixados = 0
foreach ($projeto in @('jei', 'emi', 'rei')) {
    foreach ($loader in @('forge', 'fabric')) {
        $versoes = Get-Versoes $projeto '1.16.5' $loader
        if (-not $versoes -or $versoes.Count -eq 0) {
            Write-Host ("  {0,-4} {1,-7} nao existe para esta versao" -f $projeto, $loader) -ForegroundColor DarkGray
            continue
        }
        $v = $versoes[0]
        $arq = $v.files | Where-Object { $_.primary } | Select-Object -First 1
        if (-not $arq) { $arq = $v.files[0] }
        $saida = Join-Path $dest ("{0}-1.16.5-{1}.jar" -f $projeto, $loader)
        Write-Host ("  {0,-4} {1,-7} {2}" -f $projeto, $loader, $v.version_number) -ForegroundColor Green
        try {
            Invoke-WebRequest -Uri $arq.url -OutFile $saida -Headers $cabecalho -TimeoutSec 300
            Write-Host ("         -> " + (Split-Path -Leaf $saida))
            $baixados++
        } catch {
            Write-Host ("         FALHOU: " + $_.Exception.Message) -ForegroundColor Red
        }
    }
}

Write-Host ''
Write-Host '=== 2. versoes que eu preciso para escrever os build.gradle ===' -ForegroundColor Cyan
Write-Host '(nao baixa nada: so me diz o numero exato, para eu nao chutar de novo)'
Write-Host ''
foreach ($p in @(@('fabric-api','Fabric API'), @('modmenu','Mod Menu'))) {
    $versoes = Get-Versoes $p[0] '1.16.5' 'fabric'
    if ($versoes -and $versoes.Count -gt 0) {
        Write-Host ("  {0,-12} {1}" -f $p[1], $versoes[0].version_number) -ForegroundColor Green
    } else {
        Write-Host ("  {0,-12} nao achei" -f $p[1]) -ForegroundColor Yellow
    }
}

Write-Host ''
Write-Host ("Pronto: {0} jar(s) em {1}" -f $baixados, $dest) -ForegroundColor Cyan
Write-Host 'Agora roda a sonda: ferramentas\sonda-1165\rodar-sonda.ps1'
Write-Host ''
