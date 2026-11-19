$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
if (!(Test-Path '.env')) { throw 'Copy .env.example to .env and configure it first.' }
Get-Content '.env' | ForEach-Object {
  $line = $_.Trim()
  if ($line -and !$line.StartsWith('#')) {
    $parts = $line.Split('=', 2)
    if ($parts.Count -eq 2) { [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process') }
  }
}
Set-Location 'backend'
mvn spring-boot:run
