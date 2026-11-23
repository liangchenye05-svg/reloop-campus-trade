$ErrorActionPreference = 'Stop'
$path = Join-Path $PSScriptRoot '.env'
$settings = foreach ($name in @('MYSQL_ROOT_PASSWORD', 'CT_IDENTITY_DB_PASSWORD', 'CT_COMMERCE_DB_PASSWORD', 'JWT_SECRET', 'INTERNAL_SERVICE_KEY', 'NACOS_AUTH_TOKEN', 'NACOS_AUTH_IDENTITY_VALUE')) {
    $bytes = [byte[]]::new(32)
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $secret = ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
    "$name=$secret"
}
$settings += 'CAMPUS_WEB_PORT=8088', 'ORDER_TIMEOUT=PT15M'
$stream = [IO.File]::Open($path, [IO.FileMode]::CreateNew)
try {
    $writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false))
    try { $writer.WriteLine(($settings -join "`n")) } finally { $writer.Dispose() }
} finally { $stream.Dispose() }
Write-Output 'Generated deploy/.env'
