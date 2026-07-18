param(
    [string] $CoreHttp = "http://localhost:8080",
    [string] $CoreGrpcHost = "127.0.0.1",
    [int] $CoreGrpcPort = 9090,
    [int] $Warmup = 30,
    [int] $Iterations = 200,
    [int] $Rounds = 3,
    [string] $Output = "tools/benchmark/results/core-transport-comparison.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$OutputPath = if ([System.IO.Path]::IsPathRooted($Output)) {
    $Output
} else {
    Join-Path $RepoRoot $Output
}

function Test-TcpPort {
    param([string] $HostName, [int] $Port)
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connect = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $connect.AsyncWaitHandle.WaitOne(1000)) {
            return $false
        }
        $client.EndConnect($connect)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

$httpUri = [System.Uri] $CoreHttp
if (-not (Test-TcpPort -HostName $httpUri.Host -Port $httpUri.Port)) {
    throw "Knowledge Core HTTP is not reachable at $CoreHttp"
}
if (-not (Test-TcpPort -HostName $CoreGrpcHost -Port $CoreGrpcPort)) {
    throw "Knowledge Core gRPC is not reachable at ${CoreGrpcHost}:$CoreGrpcPort"
}

Push-Location $RepoRoot
try {
    Write-Host "[core-transport-benchmark] HTTP=$CoreHttp gRPC=${CoreGrpcHost}:$CoreGrpcPort"
    $mavenArguments = @(
        "-pl", "collaboration-gateway",
        "-am",
        "-Dtest=CoreTransportBenchmarkIT",
        "-Dsurefire.failIfNoSpecifiedTests=false",
        "-Dcore.http=$CoreHttp",
        "-Dcore.grpc.host=$CoreGrpcHost",
        "-Dcore.grpc.port=$CoreGrpcPort",
        "-Dbenchmark.warmup=$Warmup",
        "-Dbenchmark.iterations=$Iterations",
        "-Dbenchmark.rounds=$Rounds",
        "-Dbenchmark.output=$OutputPath",
        "test"
    )
    & .\mvnw.cmd $mavenArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Core transport benchmark failed"
    }
} finally {
    Pop-Location
}
