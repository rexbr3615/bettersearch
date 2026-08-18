# Baixa o JEI da 1.12.2 - o unico visualizador do plano desta versao por enquanto.
#
#   .\ferramentas\baixar-1122.ps1
#
# Conferido na fonte antes de escrever isto:
#   - o JEI da 1.12.2 e a linha 4.x; o maven do BlameJared lista ate a 4.16.5.1027;
#   - EMI e REI nao existem para 1.12.2;
#   - o NEI da 1.12.2 existe mas roda EM CIMA do JEI - se um dia entrar, e depois dele.

$ErrorActionPreference = 'Continue'
$dest = 'C:\Users\gemad\Downloads\ModBetterSearch'
New-Item -ItemType Directory -Force -Path $dest | Out-Null
$cabecalho = @{ 'User-Agent' = 'rivalzin/bettersearch (contato via modrinth)' }

Write-Host ''
Write-Host '=== JEI da 1.12.2 ===' -ForegroundColor Cyan

# 1) tenta o Modrinth (traz sempre o mais novo publicado la)
$ok = $false
try {
    $gv = [uri]::EscapeDataString('["1.12.2"]')
    $ld = [uri]::EscapeDataString('["forge"]')
    $versoes = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/jei/version?game_versions=$gv&loaders=$ld" -Headers $cabecalho -TimeoutSec 60
    if ($versoes -and $versoes.Count -gt 0) {
        $v = $versoes[0]
        $arq = $v.files | Where-Object { $_.primary } | Select-Object -First 1
        if (-not $arq) { $arq = $v.files[0] }
        $saida = Join-Path $dest 'jei-1.12.2.jar'
        Write-Host ("  Modrinth: " + $v.version_number) -ForegroundColor Green
        Invoke-WebRequest -Uri $arq.url -OutFile $saida -Headers $cabecalho -TimeoutSec 300
        Write-Host ("  -> " + $saida)
        $ok = $true
    }
} catch { Write-Host ("  Modrinth falhou: " + $_.Exception.Message) -ForegroundColor DarkGray }

# 2) se o Modrinth nao tiver, vai direto no maven do BlameJared, na versao conferida hoje
if (-not $ok) {
    $url = 'https://maven.blamejared.com/mezz/jei/jei_1.12.2/4.16.5.1027/jei_1.12.2-4.16.5.1027.jar'
    $saida = Join-Path $dest 'jei-1.12.2.jar'
    Write-Host '  Modrinth nao tem 1.12.2; baixando 4.16.5.1027 do maven do BlameJared' -ForegroundColor Yellow
    try {
        Invoke-WebRequest -Uri $url -OutFile $saida -Headers $cabecalho -TimeoutSec 300
        Write-Host ("  -> " + $saida) -ForegroundColor Green
        $ok = $true
    } catch { Write-Host ("  FALHOU: " + $_.Exception.Message) -ForegroundColor Red }
}

Write-Host ''
if ($ok) { Write-Host 'Pronto. Anexa o jei-1.12.2.jar junto com a saida da sonda.' -ForegroundColor Cyan }
else     { Write-Host 'Nada baixado - me manda este erro.' -ForegroundColor Red }
