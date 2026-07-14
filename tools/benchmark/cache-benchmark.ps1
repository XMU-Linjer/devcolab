param(
    [ValidateSet("all", "seed", "no-cache", "redis", "caffeine", "compare")]
    [string] $Mode = "all",

    [string] $BaseUrl = "http://localhost:8080",
    [string] $SeedFile = "tools/benchmark/.benchmark-seed.json",
    [string] $ResultsDir = "tools/benchmark/results",

    [int] $Documents = 500,
    [int] $BlocksPerDocument = 2,
    [int] $ChildPerRoot = 4,
    [int] $Iterations = 300,
    [int] $Warmup = 30,
    [int] $Concurrency = 16,
    [int] $StartupTimeoutSeconds = 90,

    [switch] $SkipSeed,
    [switch] $KeepBackend,
    [switch] $Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$BenchmarkScript = Join-Path $RepoRoot "tools\benchmark\devcollab-benchmark.mjs"
$LogDir = Join-Path $RepoRoot "logs\benchmark"

function Show-Help {
    Write-Host @"
DevCollab cache benchmark runner

Examples:
  powershell -ExecutionPolicy Bypass -File tools\benchmark\cache-benchmark.ps1 -Mode all
  powershell -ExecutionPolicy Bypass -File tools\benchmark\cache-benchmark.ps1 -Mode seed
  powershell -ExecutionPolicy Bypass -File tools\benchmark\cache-benchmark.ps1 -Mode no-cache -SkipSeed
  powershell -ExecutionPolicy Bypass -File tools\benchmark\cache-benchmark.ps1 -Mode redis -SkipSeed
  powershell -ExecutionPolicy Bypass -File tools\benchmark\cache-benchmark.ps1 -Mode caffeine -SkipSeed
  powershell -ExecutionPolicy Bypass -File tools\benchmark\cache-benchmark.ps1 -Mode compare

Modes:
  no-cache  : DEVCOLLAB_CACHE_ENABLED=false
  redis     : DEVCOLLAB_CACHE_ENABLED=true, DEVCOLLAB_LOCAL_CACHE_ENABLED=false
  caffeine  : DEVCOLLAB_CACHE_ENABLED=true, DEVCOLLAB_LOCAL_CACHE_ENABLED=true

Output:
  tools/benchmark/results/no-cache.json
  tools/benchmark/results/redis.json
  tools/benchmark/results/caffeine.json
  tools/benchmark/results/cache-comparison.md
"@
}

function Invoke-Benchmark {
    param([string[]] $Arguments)

    Push-Location $RepoRoot
    try {
        & node $BenchmarkScript @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Benchmark command failed: node $BenchmarkScript $($Arguments -join ' ')"
        }
    } finally {
        Pop-Location
    }
}

function Wait-Backend {
    param([string] $Url, [int] $TimeoutSeconds)

    $uri = [System.Uri] $Url
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    Write-Host "[benchmark] waiting for Knowledge Core at $Url"

    while ((Get-Date) -lt $deadline) {
        $client = $null
        try {
            $client = [System.Net.Sockets.TcpClient]::new()
            $connect = $client.BeginConnect($uri.Host, $uri.Port, $null, $null)
            if ($connect.AsyncWaitHandle.WaitOne(1000)) {
                $client.EndConnect($connect)
                Start-Sleep -Seconds 3
                Write-Host "[benchmark] backend is reachable"
                return
            }
        } catch {
            Start-Sleep -Seconds 1
        } finally {
            if ($null -ne $client) {
                $client.Close()
            }
        }
    }

    throw "Knowledge Core did not start within $TimeoutSeconds seconds. Check logs under logs/benchmark."
}

function Test-PortOpen {
    param([string] $Url)

    $uri = [System.Uri] $Url
    $client = $null
    try {
        $client = [System.Net.Sockets.TcpClient]::new()
        $connect = $client.BeginConnect($uri.Host, $uri.Port, $null, $null)
        if (-not $connect.AsyncWaitHandle.WaitOne(500)) {
            return $false
        }
        $client.EndConnect($connect)
        return $true
    } catch {
        return $false
    } finally {
        if ($null -ne $client) {
            $client.Close()
        }
    }
}

function Start-Backend {
    param([ValidateSet("no-cache", "redis", "caffeine")] [string] $Scenario)

    if (Test-PortOpen -Url $BaseUrl) {
        throw "Port for $BaseUrl is already in use. Stop the existing Knowledge Core process before running this script."
    }

    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
    $stdout = Join-Path $LogDir "$Scenario.out.log"
    $stderr = Join-Path $LogDir "$Scenario.err.log"

    $cacheEnabled = "true"
    $localCacheEnabled = "true"
    if ($Scenario -eq "no-cache") {
        $cacheEnabled = "false"
        $localCacheEnabled = "false"
    } elseif ($Scenario -eq "redis") {
        $cacheEnabled = "true"
        $localCacheEnabled = "false"
    }

    $command = @"
`$env:DEVCOLLAB_CACHE_ENABLED="$cacheEnabled"
`$env:DEVCOLLAB_LOCAL_CACHE_ENABLED="$localCacheEnabled"
`$env:DEVCOLLAB_REDIS_HOST="localhost"
`$env:DEVCOLLAB_REDIS_PORT="6379"
Set-Location "$RepoRoot"
.\mvnw.cmd -pl knowledge-core spring-boot:run
"@

    Write-Host "[benchmark] starting backend scenario=$Scenario cache=$cacheEnabled local=$localCacheEnabled"
    $process = Start-Process `
        -FilePath "powershell.exe" `
        -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $command) `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -WindowStyle Hidden `
        -PassThru

    Wait-Backend -Url $BaseUrl -TimeoutSeconds $StartupTimeoutSeconds
    return $process
}

function Stop-ProcessTree {
    param([int] $ProcessId)

    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue
    foreach ($child in $children) {
        Stop-ProcessTree -ProcessId ([int] $child.ProcessId)
    }

    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($null -ne $process) {
        Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-Seed {
    Write-Host "[benchmark] seeding documents=$Documents blocksPerDocument=$BlocksPerDocument"
    Invoke-Benchmark @(
        "seed",
        "--base-url", $BaseUrl,
        "--documents", "$Documents",
        "--child-per-root", "$ChildPerRoot",
        "--blocks-per-document", "$BlocksPerDocument",
        "--output", $SeedFile
    )
}

function Invoke-Run {
    param([ValidateSet("no-cache", "redis", "caffeine")] [string] $Scenario)

    $output = Join-Path $ResultsDir "$Scenario.json"
    Write-Host "[benchmark] running scenario=$Scenario output=$output"
    Invoke-Benchmark @(
        "run",
        "--label", $Scenario,
        "--base-url", $BaseUrl,
        "--seed", $SeedFile,
        "--iterations", "$Iterations",
        "--warmup", "$Warmup",
        "--concurrency", "$Concurrency",
        "--output", $output
    )
}

function Invoke-Compare {
    $noCache = Join-Path $ResultsDir "no-cache.json"
    $redis = Join-Path $ResultsDir "redis.json"
    $caffeine = Join-Path $ResultsDir "caffeine.json"
    $output = Join-Path $ResultsDir "cache-comparison.md"

    Write-Host "[benchmark] comparing results"
    Invoke-Benchmark @(
        "compare",
        $noCache,
        $redis,
        $caffeine,
        "--output", $output
    )
}

function Invoke-Scenario {
    param(
        [ValidateSet("no-cache", "redis", "caffeine")] [string] $Scenario,
        [bool] $ShouldSeed
    )

    $process = $null
    try {
        $process = Start-Backend -Scenario $Scenario
        if ($ShouldSeed) {
            Invoke-Seed
        }
        Invoke-Run -Scenario $Scenario
    } finally {
        if (-not $KeepBackend -and $null -ne $process) {
            Write-Host "[benchmark] stopping backend scenario=$Scenario"
            Stop-ProcessTree -ProcessId $process.Id
        }
    }
}

if ($Help) {
    Show-Help
    exit 0
}

New-Item -ItemType Directory -Force -Path (Join-Path $RepoRoot $ResultsDir) | Out-Null

switch ($Mode) {
    "seed" {
        $process = $null
        try {
            $process = Start-Backend -Scenario "caffeine"
            Invoke-Seed
        } finally {
            if (-not $KeepBackend -and $null -ne $process) {
                Stop-ProcessTree -ProcessId $process.Id
            }
        }
    }
    "no-cache" {
        Invoke-Scenario -Scenario "no-cache" -ShouldSeed:(-not $SkipSeed)
    }
    "redis" {
        Invoke-Scenario -Scenario "redis" -ShouldSeed:(-not $SkipSeed)
    }
    "caffeine" {
        Invoke-Scenario -Scenario "caffeine" -ShouldSeed:(-not $SkipSeed)
    }
    "compare" {
        Invoke-Compare
    }
    "all" {
        Invoke-Scenario -Scenario "no-cache" -ShouldSeed:(-not $SkipSeed)
        Invoke-Scenario -Scenario "redis" -ShouldSeed:$false
        Invoke-Scenario -Scenario "caffeine" -ShouldSeed:$false
        Invoke-Compare
    }
}
