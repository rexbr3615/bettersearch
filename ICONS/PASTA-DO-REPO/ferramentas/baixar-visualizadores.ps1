# Baixa JEI, EMI e REI da 1.19.2 e da 1.18.2, e imprime as versoes de Fabric API,
# Mod Menu e Parchment que eu preciso para escrever os build.gradle.
#
# Roda direto: botao direito no arquivo -> "Executar com o PowerShell".
# Ou: powershell -ExecutionPolicy Bypass -File baixar-visualizadores.ps1

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
Write-Host '=== 1. visualizadores ===' -ForegroundColor Cyan
Write-Host "destino: $dest"
Write-Host ''

$achados = 0
$faltando = 0
foreach ($mc in @('1.19.2', '1.18.2')) {
    foreach ($projeto in @('jei', 'emi', 'rei')) {
        foreach ($loader in @('forge', 'fabric')) {
            $versoes = Get-Versoes $projeto $mc $loader
            if (-not $versoes -or $versoes.Count -eq 0) {
                Write-Host ("  {0,-7} {1,-4} {2,-7} nao existe" -f $mc, $projeto, $loader) -ForegroundColor DarkGray
                $faltando++
                continue
            }
            $versao = $versoes[0]
            $arquivo = $versao.files | Where-Object { $_.primary } | Select-Object -First 1
            if (-not $arquivo) { $arquivo = $versao.files[0] }

            $saida = Join-Path $dest ("{0}-{1}-{2}.jar" -f $projeto, $mc, $loader)
            try {
                Invoke-WebRequest -Uri $arquivo.url -OutFile $saida -Headers $cabecalho -TimeoutSec 300
                $mb = [math]::Round((Get-Item $saida).Length / 1MB, 1)
                Write-Host ("  {0,-7} {1,-4} {2,-7} ok    {3}  ({4} MB)" -f `
                    $mc, $projeto, $loader, $versao.version_number, $mb) -ForegroundColor Green
                $achados++
            } catch {
                Write-Host ("  {0,-7} {1,-4} {2,-7} falhou o download" -f $mc, $projeto, $loader) -ForegroundColor Red
            }
        }
    }
    Write-Host ''
}

Write-Host '=== 2. versoes que eu preciso (nao baixa nada, so imprime) ===' -ForegroundColor Cyan
Write-Host ''
foreach ($mc in @('1.19.2', '1.18.2')) {
    Write-Host ("  -- Minecraft {0}" -f $mc) -ForegroundColor White
    foreach ($par in @(@('fabric-api', 'fabric'), @('modmenu', 'fabric'))) {
        $versoes = Get-Versoes $par[0] $mc $par[1]
        if ($versoes -and $versoes.Count -gt 0) {
            Write-Host ("     {0,-12} {1}" -f $par[0], $versoes[0].version_number)
        } else {
            Write-Host ("     {0,-12} nao achei" -f $par[0]) -ForegroundColor DarkGray
        }
    }
    # O Parchment nao esta no Modrinth: os mapeamentos ficam no maven proprio deles.
    try {
        $url = "https://maven.parchmentmc.org/org/parchmentmc/data/parchment-$mc/maven-metadata.xml"
        [xml]$meta = Invoke-RestMethod -Uri $url -Headers $cabecalho -TimeoutSec 60
        $ultima = $meta.metadata.versioning.versions.version | Select-Object -Last 1
        Write-Host ("     {0,-12} {1}" -f 'parchment', $ultima)
    } catch {
        Write-Host ("     {0,-12} nao achei (segue sem, nao e obrigatorio)" -f 'parchment') -ForegroundColor DarkGray
    }
    Write-Host ''
}

Write-Host "$achados jars baixados, $faltando sem versao publicada."
Write-Host 'Me manda essa tela inteira, incluindo a parte 2.'
Write-Host ''
