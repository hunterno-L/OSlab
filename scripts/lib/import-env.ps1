# Read data only: never evaluate an environment file as PowerShell code.
function Read-ProjectEnvironment {
    param([Parameter(Mandatory)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Environment file does not exist: $Path"
    }
    $values = @{}
    $lineNumber = 0
    foreach ($rawLine in [System.IO.File]::ReadAllLines((Resolve-Path -LiteralPath $Path).Path)) {
        $lineNumber++
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith('#')) { continue }
        if ($line -notmatch '^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            throw "Invalid environment assignment at line $lineNumber in $Path"
        }
        $key = $Matches[1]
        $value = $Matches[2].Trim()
        if ($values.ContainsKey($key)) { throw "Duplicate environment key at line $lineNumber in $Path" }
        if ($value.StartsWith('"') -or $value.StartsWith("'")) {
            if ($value.Length -lt 2 -or $value[$value.Length - 1] -ne $value[0]) {
                throw "Unclosed environment quote at line $lineNumber in $Path"
            }
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$key] = $value
    }
    return $values
}

function Import-ProjectEnvironment {
    param([Parameter(Mandatory)][string]$Path)

    $values = Read-ProjectEnvironment -Path $Path
    foreach ($key in $values.Keys) {
        if ($null -eq [Environment]::GetEnvironmentVariable($key, 'Process')) {
            [Environment]::SetEnvironmentVariable($key, $values[$key], 'Process')
        }
    }
}
