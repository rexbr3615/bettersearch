# Publica o Better Search da 1.12.2 e confere DENTRO do jar antes de copiar.
#
#   .\publicar-1122.ps1                    -> so builda e confere
#   .\publicar-1122.ps1 "Nome do Perfil"   -> tambem copia para o mods do perfil do ModrinthApp
#
# Nasceu do bug dos .lang: a fonte estava certa e o jar saiu sem os arquivos. Conferir o que
# SAIU - e nao o que entrou - e a unica prova que vale.
param([string]$Perfil = "")

$ErrorActionPreference = 'Stop'
$aqui = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "[1] atualizando a pasta a partir do zip mais novo" -ForegroundColor Cyan
& (Join-Path $aqui 'atualizar.ps1')

Write-Host "[2] compilando" -ForegroundColor Cyan
& cmd /c "`"$aqui\gradlew.bat`" :versions:mc1_12_2:forge:build 2>&1" | ForEach-Object { Write-Host $_ }
if ($LASTEXITCODE -ne 0) { Write-Host "    build falhou - me mande a saida inteira" -ForegroundColor Red; exit 1 }

Write-Host "[3] conferindo o que saiu DENTRO do jar" -ForegroundColor Cyan
$libs = Join-Path $aqui 'versions\mc1_12_2\forge\build\libs'
$jar = Get-ChildItem $libs -Filter '*.jar' | Where-Object { $_.Name -notmatch 'dev|sources' } |
       Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { Write-Host "    nenhum jar em $libs" -ForegroundColor Red; exit 1 }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
try {
    $nomes = $zip.Entries | ForEach-Object { $_.FullName }
    $faltas = @()
    foreach ($obrigatorio in @('mcmod.info',
                               'pack.mcmeta',
                               'assets/bettersearch/icon.png',
                               'assets/bettersearch/lang/en_us.lang',
                               'assets/bettersearch/lang/pt_br.lang',
                               'com/rivalzin/bettersearch/client/gui/BetterSearchConfigScreen.class',
                               'com/rivalzin/bettersearch/client/gui/LanguageSelectScreen.class',
                               'com/rivalzin/bettersearch/client/gui/ToggleSwitch.class',
                               'com/rivalzin/bettersearch/client/BuscaCriativa.class',
                               'com/rivalzin/bettersearch/forge/GanchoDeBusca.class',
                               'com/rivalzin/bettersearch/forge/jei/IntegracaoJei.class',
                               'com/rivalzin/bettersearch/forge/jei/ArvoreJei.class',
                               'com/rivalzin/bettersearch/forge/jei/BuscaJei.class',
                               'com/rivalzin/bettersearch/forge/Teclas.class')) {
        if ($nomes -notcontains $obrigatorio) { $faltas += $obrigatorio }
    }
    # Os ESBOCOS de compilacao (mezz/, it/) jamais podem entrar no jar: dentro do jogo eles
    # substituiriam as classes reais do JEI/fastutil e derrubariam tudo. Trava dura.
    $vazados = @($nomes | Where-Object { $_ -match '^(mezz/|it/unimi)' })
    if ($vazados.Count -gt 0) {
        Write-Host "    ESBOCO VAZOU PARA O JAR (nao instale este jar):" -ForegroundColor Red
        $vazados | Select-Object -First 8 | ForEach-Object { Write-Host ("      " + $_) -ForegroundColor Red }
        exit 1
    }
    # As 16 fotos de previa do menu (uma por opcao). Contar e mais robusto que listar as 16.
    $previas = @($nomes | Where-Object { $_ -match '^assets/bettersearch/textures/gui/options/.+\.png$' })
    if ($previas.Count -lt 16) {
        $faltas += "assets/bettersearch/textures/gui/options/*.png ($($previas.Count) de 16 previas)"
    }
    if ($faltas.Count -gt 0) {
        Write-Host "    FALTAM no jar:" -ForegroundColor Red
        $faltas | ForEach-Object { Write-Host ("      " + $_) -ForegroundColor Red }
        Write-Host "    O jar tem $($nomes.Count) entradas; me mande esta saida." -ForegroundColor Yellow
        exit 1
    }
    Write-Host "    conteudo de assets/ do jar (a prova, nao a promessa):" -ForegroundColor DarkGray
    $nomes | Where-Object { $_ -match '^(assets/|pack.mcmeta|mcmod.info)' } |
        ForEach-Object { Write-Host ("      " + $_) -ForegroundColor DarkGray }
} finally { $zip.Dispose() }
Write-Host "    ok - mcmod.info, pack.mcmeta, icone, .lang, o menu novo e as 16 previas estao la ($($jar.Name))" -ForegroundColor Green

if ($Perfil) {
    Write-Host "[4] copiando para o perfil '$Perfil'" -ForegroundColor Cyan
    $destino = Join-Path $env:APPDATA "ModrinthApp\profiles\$Perfil\mods"
    if (-not (Test-Path $destino)) { Write-Host "    nao achei $destino" -ForegroundColor Red; exit 1 }
    Get-ChildItem $destino -Filter 'bettersearch*.jar' | Remove-Item -Force
    Copy-Item $jar.FullName -Destination $destino -Force
    $h1 = (Get-FileHash $jar.FullName -Algorithm SHA256).Hash
    $h2 = (Get-FileHash (Join-Path $destino $jar.Name) -Algorithm SHA256).Hash
    if ($h1 -ne $h2) { Write-Host "    a copia nao confere" -ForegroundColor Red; exit 1 }
    Write-Host "    $($jar.Name) -> mods (SHA256 confere)" -ForegroundColor Green
} else {
    Write-Host "[4] (sem perfil informado - o jar esta em $libs)" -ForegroundColor DarkGray
}
Write-Host ""
Write-Host "Pronto." -ForegroundColor Green
