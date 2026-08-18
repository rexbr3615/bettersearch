# Publica o Better Search da 1.16.5 FORGE no modpack e confere tudo antes.
#
#   .\publicar-1165-forge.ps1                 -> usa o modpack padrao abaixo
#   .\publicar-1165-forge.ps1 "Nome do Pack"  -> usa outro perfil do ModrinthApp
#
# Mesma ideia do publicar-1165.ps1 (que publica o Fabric): atualiza, limpa, compila, confere o
# que saiu DENTRO do jar e so entao copia. Para em vermelho em qualquer passo.
param([string]$Perfil = "Kimetsu no Yaiba (Demon-Slayer)")

$ErrorActionPreference = 'Stop'
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "[1] atualizando a pasta a partir do zip mais novo" -ForegroundColor Cyan
& (Join-Path $aqui 'atualizar.ps1')

Write-Host "[2] conferindo os arquivos de mixin da fonte" -ForegroundColor Cyan
foreach ($nome in @('bettersearch-forge.mixins.json', 'bettersearch-rei.mixins.json', 'bettersearch-jei.mixins.json')) {
    $arq = Join-Path $aqui "versions\mc1_16_5\forge\src\main\resources\$nome"
    if (-not (Test-Path $arq)) { Write-Host "    FALTA $nome" -ForegroundColor Red; exit 1 }
    $texto = Get-Content $arq -Raw
    # O Forge 1.16.5 carrega Mixin 0.8.4. Pedir 0.8.5 e crash antes do menu - ja aconteceu.
    if ($texto -notmatch '"minVersion"\s*:\s*"0\.8\.4"') {
        Write-Host "    $nome nao pede minVersion 0.8.4" -ForegroundColor Red; exit 1
    }
    if ($texto -notmatch '"compatibilityLevel"\s*:\s*"JAVA_8"') {
        Write-Host "    $nome nao esta em JAVA_8" -ForegroundColor Red; exit 1
    }
}
Write-Host "    ok - minVersion 0.8.4 e JAVA_8 nos dois" -ForegroundColor Green

Write-Host "[3] apagando a saida antiga (senao o Gradle diz 'up-to-date')" -ForegroundColor Cyan
foreach ($pasta in @('versions\mc1_16_5\forge\build\resources',
                     'versions\mc1_16_5\forge\build\libs',
                     'versions\mc1_16_5\forge\build\devlibs')) {
    $alvo = Join-Path $aqui $pasta
    if (Test-Path $alvo) { Remove-Item $alvo -Recurse -Force }
}

Write-Host "[4] compilando" -ForegroundColor Cyan
& cmd /c "`"$aqui\gradlew.bat`" :versions:mc1_16_5:forge:build 2>&1" | ForEach-Object { Write-Host $_ }
if ($LASTEXITCODE -ne 0) { Write-Host "    o build falhou - me mande a saida inteira" -ForegroundColor Red; exit 1 }

Write-Host "[5] conferindo o que saiu DENTRO do jar" -ForegroundColor Cyan
$libs = Join-Path $aqui 'versions\mc1_16_5\forge\build\libs'
$jar = Get-ChildItem $libs -Filter '*.jar' | Where-Object { $_.Name -notmatch 'sources|javadoc' } |
       Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { Write-Host "    nenhum jar em $libs" -ForegroundColor Red; exit 1 }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
try {
    $nomes = $zip.Entries | ForEach-Object { $_.FullName }
    # Anunciar no manifesto um arquivo de mixin que nao esta dentro do jar faz o Mixin reclamar
    # na inicializacao. Conferir os dois lados aqui evita descobrir isso em jogo.
    foreach ($obrigatorio in @('bettersearch-forge.mixins.json',
                               'bettersearch-rei.mixins.json',
                               'bettersearch-jei.mixins.json',
                               'com/rivalzin/bettersearch/mixin/jei/IngredientFilterMixin.class',
                               'com/rivalzin/bettersearch/client/JeiSearch.class',
                               'com/rivalzin/bettersearch/mixin/rei/SearchProviderImplMixin.class',
                               'com/rivalzin/bettersearch/mixin/rei/AsyncSearchManagerMixin.class',
                               'com/rivalzin/bettersearch/client/ReiSearch.class')) {
        if ($nomes -notcontains $obrigatorio) {
            Write-Host "    FALTA no jar: $obrigatorio" -ForegroundColor Red; exit 1
        }
    }
    $entrada = $zip.Entries | Where-Object { $_.FullName -eq 'META-INF/MANIFEST.MF' }
    $leitor = New-Object System.IO.StreamReader($entrada.Open())
    $manifesto = $leitor.ReadToEnd(); $leitor.Close()
    # O formato JAR quebra o manifesto em 72 BYTES por linha e continua a proxima com um espaco.
    # A nossa linha tem 73, entao "...mixins.jso" + quebra + " n". O Java remonta isso sozinho
    # ao ler (e do padrao), mas quem le o texto cru precisa desfazer a quebra antes de procurar -
    # sem isto a conferencia acusa falta de um arquivo que esta la, errando por um byte.
    $manifesto = $manifesto -replace "\r?\n ", ""
    if (($manifesto -notmatch 'bettersearch-rei\.mixins\.json') -or
        ($manifesto -notmatch 'bettersearch-jei\.mixins\.json')) {
        Write-Host "    o MixinConfigs do manifesto nao anuncia o do REI" -ForegroundColor Red
        $linha = ($manifesto -split "\r?\n" | Where-Object { $_ -match '^MixinConfigs:' })
        Write-Host "    o que esta escrito la: $linha" -ForegroundColor Yellow
        exit 1
    }
} finally { $zip.Dispose() }
Write-Host "    ok - os dois mixins, o gancho do REI e o manifesto estao la" -ForegroundColor Green

Write-Host "[6] copiando para o modpack" -ForegroundColor Cyan
$destino = Join-Path $env:APPDATA "ModrinthApp\profiles\$Perfil\mods"
if (-not (Test-Path $destino)) { Write-Host "    nao achei $destino" -ForegroundColor Red; exit 1 }
Get-ChildItem $destino -Filter 'bettersearch*.jar' | Remove-Item -Force
Copy-Item $jar.FullName -Destination $destino -Force

# Conferir a copia, e nao supor que ela aconteceu.
#
# Existe porque ja perdemos uma rodada exatamente assim: o passo [5] falhou por um bug MEU de
# conferencia, o script saiu antes do [6], e o jar antigo continuou no mods. O teste em jogo
# entao mediu o build velho - e pareceu que o gancho do REI nao funcionava.
$copiado = Join-Path $destino $jar.Name
if (-not (Test-Path $copiado)) {
    Write-Host "    a copia NAO chegou em $destino" -ForegroundColor Red; exit 1
}
$h1 = (Get-FileHash $jar.FullName -Algorithm SHA256).Hash
$h2 = (Get-FileHash $copiado    -Algorithm SHA256).Hash
if ($h1 -ne $h2) {
    Write-Host "    o jar no mods NAO e igual ao que acabou de ser compilado" -ForegroundColor Red; exit 1
}
$outros = Get-ChildItem $destino -Filter 'bettersearch*.jar' | Where-Object { $_.Name -ne $jar.Name }
if ($outros) {
    Write-Host "    sobrou outro Better Search no mods: $($outros.Name -join ', ')" -ForegroundColor Red; exit 1
}
Write-Host "    $($jar.Name) -> $destino" -ForegroundColor Green
Write-Host "    conferido: e o unico Better Search la, e o SHA256 bate" -ForegroundColor Green
Write-Host ""
Write-Host "Pronto. Abra o jogo e teste." -ForegroundColor Green
Write-Host "Se o REI nao responder, rode isto e me mande a saida:" -ForegroundColor Cyan
Write-Host '  Select-String -Path "$env:APPDATA\ModrinthApp\profiles\Kimetsu no Yaiba (Demon-Slayer)\logs\latest.log" -Pattern "Better Search|bettersearch-rei|Mixin"' -ForegroundColor DarkGray
