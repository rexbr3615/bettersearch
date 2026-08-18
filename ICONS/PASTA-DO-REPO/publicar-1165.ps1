# Atualiza, compila, CONFERE e so entao copia o jar da 1.16.5 Fabric para o modpack.
#
#   powershell -ExecutionPolicy Bypass -File publicar-1165.ps1
#
# Existe porque a sequencia "atualizar -> compilar -> copiar" tem tres pontos onde da para
# achar que deu certo sem ter dado: o zip pode estar velho, o Gradle pode dizer "up-to-date"
# e nao regerar o jar, e o jar velho pode ir para o modpack do mesmo jeito. Aqui cada etapa
# confere a anterior e PARA se nao bater.

$ErrorActionPreference = 'Stop'
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $aqui

function Passo($n, $texto) { Write-Host ""; Write-Host "[$n] $texto" -ForegroundColor Cyan }
function Morre($texto) { Write-Host ""; Write-Host "PAROU: $texto" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------- 1. atualizar do zip
Passo 1 "atualizando a pasta a partir do zip mais novo"
& powershell -ExecutionPolicy Bypass -File (Join-Path $aqui 'atualizar.ps1')
if ($LASTEXITCODE -ne 0) { Morre "o atualizar.ps1 falhou" }

# ---------------------------------------------------------------- 2. conferir a FONTE
Passo 2 "conferindo o fabric.mod.json da fonte"
$fonte = Join-Path $aqui 'versions\mc1_16_5\fabric\src\main\resources\fabric.mod.json'
if (-not (Test-Path $fonte)) { Morre "nao achei $fonte" }
$txtFonte = Get-Content $fonte -Raw
if ($txtFonte -match '"fabric-api"') {
    Morre "a FONTE ainda pede 'fabric-api'. O zip que voce tem e antigo - me avisa."
}
if ($txtFonte -notmatch '"fabric"\s*:') { Morre "a fonte nao pede nem fabric nem fabric-api" }
Write-Host "    ok - a fonte pede 'fabric'" -ForegroundColor Green

# ---------------------------------------------------------------- 3. forcar a regeracao
Passo 3 "apagando a saida antiga do modulo (senao o Gradle diz 'up-to-date')"
foreach ($p in @('versions\mc1_16_5\fabric\build\resources',
                 'versions\mc1_16_5\fabric\build\libs',
                 'versions\mc1_16_5\fabric\build\devlibs')) {
    $alvo = Join-Path $aqui $p
    if (Test-Path $alvo) { Remove-Item $alvo -Recurse -Force; Write-Host "    apagado: $p" }
}

Passo 4 "compilando"
& cmd /c "`"$aqui\gradlew.bat`" :versions:mc1_16_5:fabric:build 2>&1" | ForEach-Object { Write-Host $_ }
if ($LASTEXITCODE -ne 0) { Morre "o build falhou - me manda a saida acima" }

# ---------------------------------------------------------------- 5. conferir o JAR
Passo 5 "conferindo o que saiu DENTRO do jar"
$jar = Get-ChildItem (Join-Path $aqui 'versions\mc1_16_5\fabric\build\libs') -Filter '*.jar' |
       Where-Object { $_.Name -notlike '*-sources*' } | Sort-Object LastWriteTime -Descending |
       Select-Object -First 1
if (-not $jar) { Morre "o build passou mas nao gerou jar nenhum" }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
$entrada = $zip.Entries | Where-Object { $_.FullName -eq 'fabric.mod.json' }
if (-not $entrada) { $zip.Dispose(); Morre "o jar nao tem fabric.mod.json dentro" }
$txtJar = (New-Object IO.StreamReader($entrada.Open())).ReadToEnd()
$zip.Dispose()

Write-Host ("    jar: " + $jar.Name)
Write-Host ("    de : " + $jar.LastWriteTime)
if ($txtJar -match '"fabric-api"') {
    Morre "o jar AINDA pede 'fabric-api' mesmo depois de recompilar. Nao copiei nada."
}
Write-Host "    ok - o jar pede 'fabric'" -ForegroundColor Green

# ---------------------------------------------------------------- 6. copiar
Passo 6 "copiando para o modpack"
$destino = Join-Path $env:APPDATA 'ModrinthApp\profiles\EchoShift Ultimate\mods'
if (-not (Test-Path $destino)) { Morre "nao achei a pasta do modpack: $destino" }
Get-ChildItem $destino -Filter 'bettersearch*.jar' | ForEach-Object {
    Write-Host ("    removendo antigo: " + $_.Name)
    Remove-Item $_.FullName -Force
}
Copy-Item $jar.FullName -Destination $destino -Force
Write-Host ("    copiado: " + $jar.Name + " -> " + $destino) -ForegroundColor Green

Write-Host ""
Write-Host "Pronto. Pode abrir o jogo." -ForegroundColor Cyan
Write-Host ""
