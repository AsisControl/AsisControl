param(
    [ValidateSet('mysql', 'local')]
    [string]$Profile,
    [switch]$Build
)

$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    # Carga valores literales: nunca ejecuta el contenido del archivo .env.
    if (Test-Path -LiteralPath '.env') {
        Get-Content -LiteralPath '.env' | ForEach-Object {
            if ($_ -match '^\s*([A-Za-z_][A-Za-z_0-9]*)\s*=(.*)$') {
                $key = $matches[1]
                $value = $matches[2].Trim()
                if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                    ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                    $value = $value.Substring(1, $value.Length - 2)
                }
                [Environment]::SetEnvironmentVariable($key, $value, 'Process')
            }
        }
    }
    if ($Profile) { $env:SPRING_PROFILES_ACTIVE = $Profile }
    if (-not $env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE = 'local' }

    # Evita el acceso indirecto de Oracle javapath; busca un JDK 21 real.
    $jdk = Get-ChildItem -LiteralPath "$env:ProgramFiles\Java" -Directory -ErrorAction SilentlyContinue |
        Where-Object Name -Like 'jdk-21*' | Sort-Object Name -Descending | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
    if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath "$env:JAVA_HOME\bin\java.exe")) {
        throw 'Configura JAVA_HOME con la ruta de tu JDK 21.'
    }
    $jar = 'controller\target\asiscontrol-controller-1.0.0-SNAPSHOT.jar'
    if ($Build -or -not (Test-Path -LiteralPath $jar)) {
        & .\mvnw.cmd -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar el backend.' }
    }
    Write-Host "Iniciando backend con perfil $env:SPRING_PROFILES_ACTIVE. Detener con Ctrl+C."
    if ($env:SPRING_PROFILES_ACTIVE -eq 'local') {
        Write-Host 'H2 temporal: no contiene los datos de MySQL y se borra al detener el proceso.'
    }
    & "$env:JAVA_HOME\bin\java.exe" -jar $jar
    if ($LASTEXITCODE -ne 0) { throw 'El backend no pudo arrancar. Revisa logs/asiscontrol.log.' }
} finally {
    Pop-Location
}
