$ErrorActionPreference = 'Stop'
# Ejecutar solo después de revisar cambios intencionales en los SQL.
# El manifiesto del servidor autoriza exactamente estos archivos, no cualquier SQL subido.
$demoStages = [ordered]@{
    INICIAL = '00_configuracion_inicial.sql'
    HABILITAR_MATRICULAS = '09_habilitar_matriculas.sql'
    ESCENARIOS = '12_escenarios.sql'
}
$demoLines = @('paquete=EXPO-2026-sql-v1')
foreach ($stage in $demoStages.Keys) {
    $demoFile = Join-Path $PSScriptRoot $demoStages[$stage]
    $demoLines += "$stage.archivo=$($demoStages[$stage])"
    $demoLines += "$stage.sha256=$((Get-FileHash -LiteralPath $demoFile -Algorithm SHA256).Hash.ToLowerInvariant())"
}
[IO.File]::WriteAllLines((Join-Path $PSScriptRoot 'manifest.properties'), $demoLines, [Text.UTF8Encoding]::new($false))
