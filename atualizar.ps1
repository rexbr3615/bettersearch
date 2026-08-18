# Atualiza ESTA pasta com o conteudo do bettersearch-1.3.0.zip mais recente.
#
#   .\atualizar.ps1
#
# Existe porque ja perdemos tres rodadas com o mesmo mal-entendido: o zip novo era gravado em
# ModBetterSearch\bettersearch-1.3.0.zip, mas o build e a sonda rodavam de uma pasta extraida
# antes. Tudo "falhava" de novo, com o codigo antigo. Este script tira a extracao manual do
# caminho: ele acha o zip, espelha as pastas de fonte por cima daqui e AVISA o que mudou.
#
# Ele preserva as pastas build/ e .gradle/ - seu cache de compilacao continua valendo.

$ErrorActionPreference = 'Stop'
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path

# Procura o zip subindo ate 4 niveis, para funcionar independente do nome que voce deu a pasta.
#
# Junta TODOS os candidatos e fica com o MAIS NOVO - nao com o mais proximo. A versao anterior
# parava no primeiro que achasse subindo, e com varias copias espalhadas pelo Downloads isso
# uma hora ia pegar uma antiga e "atualizar" a pasta para tras, sem ninguem perceber.
$candidatos = @()
$dir = $aqui
for ($i = 0; $i -lt 4 -and $dir; $i++) {
    $tentativa = Join-Path $dir 'bettersearch-1.3.0.zip'
    if (Test-Path $tentativa) { $candidatos += Get-Item $tentativa }
    $dir = Split-Path -Parent $dir
}
if ($candidatos.Count -eq 0) {
    Write-Host 'Nao achei bettersearch-1.3.0.zip subindo a partir desta pasta.' -ForegroundColor Red
    exit 1
}

$escolhido = $candidatos | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$zip = $escolhido.FullName

Write-Host ''
Write-Host "aqui: $aqui" -ForegroundColor Cyan
# Mostra todos, com idade. Se aparecer mais de um, da para ver na hora qual foi usado e por que.
foreach ($c in ($candidatos | Sort-Object LastWriteTime -Descending)) {
    $idade = [math]::Round(((Get-Date) - $c.LastWriteTime).TotalMinutes)
    $marca = if ($c.FullName -eq $zip) { '  ->' } else { '    ' }
    $cor   = if ($c.FullName -eq $zip) { 'Cyan' } else { 'DarkGray' }
    Write-Host ("{0} {1}  (ha {2} min)" -f $marca, $c.FullName, $idade) -ForegroundColor $cor
}
if ($candidatos.Count -gt 1) {
    Write-Host '      (havia mais de um; usei o mais novo)' -ForegroundColor Yellow
}
Write-Host ''

$tmp = Join-Path $env:TEMP ('bs-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
Expand-Archive -Path $zip -DestinationPath $tmp -Force
$origem = Join-Path $tmp 'bettersearch'
if (-not (Test-Path $origem)) {
    Write-Host "O zip nao tem a pasta 'bettersearch' dentro." -ForegroundColor Red
    exit 1
}

# /MIR espelha: alem de copiar o que mudou, APAGA o que nao existe mais no zip. E isso que faz
# diferenca aqui - foi assim que sobrou uma pasta c-forgegradle5 e ficaram arquivos de EMI num
# lugar de onde eles ja tinham saido. Copiar por cima sem apagar deixa lixo que quebra o build.
# /XD build .gradle: nao mexe no cache de compilacao.
# 'gradle' entrou na lista por causa do gradle-daemon-jvm.properties (o daemon em Java 25
# que a 1.12.2 exige). Sem espelhar a pasta, o arquivo nunca chegaria na sua maquina.
foreach ($pasta in @('core', 'tools', 'ferramentas', 'versions', 'gradle')) {
    $de  = Join-Path $origem $pasta
    $pra = Join-Path $aqui   $pasta
    if (-not (Test-Path $de)) { continue }
    robocopy $de $pra /MIR /XD build .gradle /NFL /NDL /NJH /NJS /NP | Out-Null
    # robocopy usa 0-7 para sucesso; 8 ou mais e erro de verdade.
    if ($LASTEXITCODE -ge 8) {
        Write-Host "  ERRO ao espelhar $pasta (robocopy $LASTEXITCODE)" -ForegroundColor Red
        exit 1
    }
    Write-Host "  espelhado: $pasta" -ForegroundColor Green
}

Get-ChildItem -Path $origem -File | ForEach-Object {
    Copy-Item $_.FullName -Destination (Join-Path $aqui $_.Name) -Force
}
Write-Host '  copiados:  arquivos da raiz (build.gradle, settings.gradle, gradle.properties, .md)' -ForegroundColor Green

Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ''
Write-Host 'Pronto. Esta pasta agora e igual ao zip.' -ForegroundColor Cyan
Write-Host ''
