param(
    [Parameter(Position = 0)]
    [ValidateSet("start", "stop", "status", "verify")]
    [string] $Action = "start",

    [int] $StartupTimeoutSeconds = 180,
    [switch] $SkipFrontendBuild,
    [switch] $VerifyAfterStart,
    [switch] $WithObservability,
    [switch] $KeepInfrastructure
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
Add-Type -AssemblyName System.Net.Http

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$RuntimeDir = Join-Path $RepoRoot "logs\local-demo"
$StateFile = Join-Path $RuntimeDir "processes.json"
$MavenWrapper = Join-Path $RepoRoot "mvnw.cmd"
$EnvFile = Join-Path $RepoRoot ".env"

function Write-Step {
    param([string] $Message)
    Write-Host "[local-demo] $Message"
}

function Assert-Command {
    param([string] $Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command is unavailable: $Name"
    }
}

function Import-DotEnv {
    if (-not (Test-Path -LiteralPath $EnvFile)) {
        return
    }

    foreach ($line in Get-Content -LiteralPath $EnvFile -Encoding UTF8) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#") -or -not $trimmed.Contains("=")) {
            continue
        }

        $parts = $trimmed.Split("=", 2)
        $name = $parts[0].Trim()
        $value = $parts[1].Trim()
        if (-not [Environment]::GetEnvironmentVariable($name, "Process")) {
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

function Get-EnvOrDefault {
    param(
        [string] $Name,
        [string] $DefaultValue
    )
    $value = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ($value) {
        return $value
    }
    return $DefaultValue
}

function Test-ContainerRunning {
    param([string] $ContainerName)
    $state = & docker inspect --format "{{.State.Running}}" $ContainerName 2>$null
    return $LASTEXITCODE -eq 0 -and $state -eq "true"
}

function Resolve-RedisHostPort {
    $configuredPort = [int](Get-EnvOrDefault "REDIS_HOST_PORT" "6379")
    if (Test-ContainerRunning -ContainerName "devcollab-redis") {
        $previousErrorPreference = $ErrorActionPreference
        try {
            # An old container may exist without a published port. docker then
            # writes a diagnostic to stderr; treat that as a normal miss so the
            # compose recreation/fallback logic below can continue.
            $ErrorActionPreference = "SilentlyContinue"
            $bindings = @(& docker port devcollab-redis "6379/tcp" 2>$null)
            $dockerPortExitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $previousErrorPreference
        }
        $binding = $bindings | Select-Object -First 1
        if ($dockerPortExitCode -eq 0 -and $binding -match ":(?<port>\d+)$") {
            $runningPort = [int] $Matches.port
            [Environment]::SetEnvironmentVariable("REDIS_HOST_PORT", "$runningPort", "Process")
            if ($runningPort -ne $configuredPort) {
                Write-Step "using running DevCollab Redis host port $runningPort"
            }
            return
        }
    }
    if (-not (Test-TcpPort -HostName "localhost" -Port $configuredPort)) {
        return
    }

    foreach ($candidate in 16379..16479) {
        if (-not (Test-TcpPort -HostName "localhost" -Port $candidate)) {
            [Environment]::SetEnvironmentVariable("REDIS_HOST_PORT", "$candidate", "Process")
            Write-Step "Redis host port $configuredPort is occupied by another process; using $candidate for DevCollab"
            return
        }
    }
    throw "Redis port $configuredPort is occupied and no fallback port is available in 16379-16479"
}

function Test-TcpPort {
    param(
        [string] $HostName,
        [int] $Port,
        [int] $TimeoutMilliseconds = 500
    )

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $result = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $result.AsyncWaitHandle.WaitOne($TimeoutMilliseconds)) {
            return $false
        }
        $client.EndConnect($result)
        return $true
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

function Test-HttpEndpoint {
    param(
        [int] $Port,
        [int] $TimeoutMilliseconds = 3000
    )

    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.UseProxy = $false
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromMilliseconds($TimeoutMilliseconds)
    try {
        $response = $client.GetAsync("http://127.0.0.1:$Port/").GetAwaiter().GetResult()
        return [int] $response.StatusCode -ge 200 -and [int] $response.StatusCode -lt 400
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
        $handler.Dispose()
    }
}

function Wait-HttpEndpoint {
    param(
        [string] $Name,
        [int] $Port
    )

    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-HttpEndpoint -Port $Port) {
            Write-Step "$Name HTTP endpoint is healthy on port $Port"
            return
        }
        Start-Sleep -Milliseconds 1000
    }
    throw "$Name did not return a successful HTTP response on port $Port within $StartupTimeoutSeconds seconds"
}

function Wait-TcpPort {
    param(
        [string] $Name,
        [int] $Port
    )

    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort -HostName "localhost" -Port $Port) {
            Write-Step "$Name is reachable on port $Port"
            return
        }
        Start-Sleep -Milliseconds 1000
    }
    throw "$Name did not become reachable on port $Port within $StartupTimeoutSeconds seconds"
}

function Wait-ManagedService {
    param([object] $Entry)

    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort -HostName "localhost" -Port ([int] $Entry.port)) {
            Write-Step "$($Entry.name) is reachable on port $($Entry.port)"
            return
        }
        if (-not (Get-Process -Id ([int] $Entry.processId) -ErrorAction SilentlyContinue)) {
            $stdoutTail = if (Test-Path -LiteralPath $Entry.stdout) {
                (Get-Content -LiteralPath $Entry.stdout -Tail 30 -Encoding UTF8) -join [Environment]::NewLine
            } else { "<no stdout log>" }
            $stderrTail = if (Test-Path -LiteralPath $Entry.stderr) {
                (Get-Content -LiteralPath $Entry.stderr -Tail 30 -Encoding UTF8) -join [Environment]::NewLine
            } else { "<no stderr log>" }
            throw "$($Entry.name) exited before opening port $($Entry.port).`nSTDOUT:`n$stdoutTail`nSTDERR:`n$stderrTail"
        }
        Start-Sleep -Milliseconds 1000
    }
    throw "$($Entry.name) did not become reachable on port $($Entry.port) within $StartupTimeoutSeconds seconds. Logs: $($Entry.stdout), $($Entry.stderr)"
}

function Assert-PortAvailable {
    param(
        [string] $Name,
        [int] $Port
    )
    if (Test-TcpPort -HostName "localhost" -Port $Port) {
        throw "$Name cannot start because port $Port is already in use. Stop the existing process or use the existing manual environment."
    }
}

function Invoke-Checked {
    param(
        [string] $FilePath,
        [string[]] $Arguments
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
    }
}

function Write-State {
    param([object[]] $Processes)
    New-Item -ItemType Directory -Path $RuntimeDir -Force | Out-Null
    $Processes | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $StateFile -Encoding UTF8
}

function Read-State {
    if (-not (Test-Path -LiteralPath $StateFile)) {
        return @()
    }
    $raw = Get-Content -Raw -LiteralPath $StateFile -Encoding UTF8
    if (-not $raw.Trim()) {
        return @()
    }
    return @(ConvertFrom-Json $raw)
}

function Test-ManagedStateHealthy {
    $processes = Read-State
    if ($processes.Count -ne 3) {
        return $false
    }
    foreach ($entry in $processes) {
        if (-not (Get-Process -Id ([int] $entry.processId) -ErrorAction SilentlyContinue)) {
            return $false
        }
        if (-not (Test-TcpPort -HostName "localhost" -Port ([int] $entry.port))) {
            return $false
        }
    }
    $nginxPort = [int](Get-EnvOrDefault "NGINX_HOST_PORT" "8088")
    if (-not (Test-HttpEndpoint -Port $nginxPort)) {
        return $false
    }
    return $true
}

function Start-MavenService {
    param(
        [string] $Name,
        [string] $Module,
        [int] $Port
    )

    Assert-PortAvailable -Name $Name -Port $Port
    New-Item -ItemType Directory -Path $RuntimeDir -Force | Out-Null

    $stdout = Join-Path $RuntimeDir "$Name.stdout.log"
    $stderr = Join-Path $RuntimeDir "$Name.stderr.log"
    # -am builds the shared gRPC contract in the same Reactor. The parent POM
    # skips spring-boot:run while executable child modules explicitly enable it.
    $command = "`"$MavenWrapper`" -pl $Module -am spring-boot:run"

    Write-Step "starting $Name"
    $process = Start-Process `
        -FilePath "cmd.exe" `
        -ArgumentList @("/d", "/s", "/c", $command) `
        -WorkingDirectory $RepoRoot `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -WindowStyle Hidden `
        -PassThru

    return [pscustomobject]@{
        name = $Name
        module = $Module
        port = $Port
        processId = $process.Id
        stdout = $stdout
        stderr = $stderr
    }
}

function Stop-ManagedProcesses {
    $processes = Read-State
    foreach ($entry in @($processes | Sort-Object processId -Descending)) {
        $pidValue = [int] $entry.processId
        if (Get-Process -Id $pidValue -ErrorAction SilentlyContinue) {
            Write-Step "stopping $($entry.name) process tree pid=$pidValue"
            & taskkill.exe /PID $pidValue /T /F | Out-Null
        }
    }
    Remove-Item -LiteralPath $StateFile -Force -ErrorAction SilentlyContinue
}

function Ensure-KafkaTopics {
    $topics = @(
        "devcollab.document.events",
        "devcollab.cache.events",
        "devcollab.review.events",
        "devcollab.notification.events",
        "devcollab.git.events",
        "devcollab.dead-letter"
    )
    foreach ($topic in $topics) {
        Write-Step "ensuring Kafka topic $topic"
        Invoke-Checked -FilePath "docker" -Arguments @(
            "exec", "devcollab-kafka",
            "/opt/kafka/bin/kafka-topics.sh",
            "--bootstrap-server", "localhost:9092",
            "--create", "--if-not-exists",
            "--topic", $topic,
            "--partitions", "3",
            "--replication-factor", "1"
        )
    }
}

function Ensure-ObservabilityImages {
    $services = @("tempo", "loki", "otel-collector", "alloy", "prometheus", "grafana")
    foreach ($service in $services) {
        $pulled = $false
        foreach ($attempt in 1..3) {
            Write-Step "pulling observability image service=$service attempt=$attempt/3"
            & docker compose --profile observability pull $service
            if ($LASTEXITCODE -eq 0) {
                $pulled = $true
                break
            }
            Start-Sleep -Seconds 2
        }
        if (-not $pulled) {
            throw "Failed to pull observability image for $service after 3 attempts"
        }
    }
}

function Set-SharedServiceEnvironment {
    $nginxPort = Get-EnvOrDefault -Name "NGINX_HOST_PORT" -DefaultValue "8088"
    [Environment]::SetEnvironmentVariable("DEVCOLLAB_DB_URL", (Get-EnvOrDefault "DEVCOLLAB_DB_URL" "jdbc:postgresql://localhost:5432/devcollab"), "Process")
    [Environment]::SetEnvironmentVariable("DEVCOLLAB_DB_USERNAME", (Get-EnvOrDefault "DEVCOLLAB_DB_USERNAME" "devcollab"), "Process")
    [Environment]::SetEnvironmentVariable("DEVCOLLAB_DB_PASSWORD", (Get-EnvOrDefault "DEVCOLLAB_DB_PASSWORD" "devcollab"), "Process")
    [Environment]::SetEnvironmentVariable("DEVCOLLAB_REDIS_HOST", "localhost", "Process")
    [Environment]::SetEnvironmentVariable("DEVCOLLAB_REDIS_PORT", (Get-EnvOrDefault "REDIS_HOST_PORT" "6379"), "Process")
    [Environment]::SetEnvironmentVariable("DEVCOLLAB_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092", "Process")
    [Environment]::SetEnvironmentVariable("DEVCOLLAB_ELASTICSEARCH_URL", "http://localhost:9200", "Process")
    [Environment]::SetEnvironmentVariable("DEVCOLLAB_ELASTICSEARCH_ENABLED", "true", "Process")
    [Environment]::SetEnvironmentVariable(
        "DEVCOLLAB_GIT_DATA_ROOT",
        (Join-Path $RepoRoot ".data\git-repositories"),
        "Process"
    )
    [Environment]::SetEnvironmentVariable("DEVCOLLAB_WEB_ORIGIN", "http://localhost:$nginxPort", "Process")
}

function Start-LocalDemo {
    Assert-Command "docker"
    Assert-Command "node"
    Assert-Command "npm.cmd"
    if (-not (Test-Path -LiteralPath $MavenWrapper)) {
        throw "Maven Wrapper not found: $MavenWrapper"
    }
    Import-DotEnv
    if (Test-Path -LiteralPath $StateFile) {
        if (Test-ManagedStateHealthy) {
            $entry = "http://localhost:$(Get-EnvOrDefault 'NGINX_HOST_PORT' '8088')"
            Write-Step "managed services are already running: $entry"
            if ($VerifyAfterStart) {
                Invoke-LocalDemoVerification
            }
            return
        }
        Write-Step "removing stale managed process state from an interrupted startup"
        Stop-ManagedProcesses
    }

    Resolve-RedisHostPort
    Set-SharedServiceEnvironment

    if (-not $SkipFrontendBuild) {
        Write-Step "building Vue frontend"
        Push-Location (Join-Path $RepoRoot "web")
        try {
            Invoke-Checked -FilePath "npm.cmd" -Arguments @("run", "build")
        }
        finally {
            Pop-Location
        }
    }
    elseif (-not (Test-Path -LiteralPath (Join-Path $RepoRoot "web\dist\index.html"))) {
        throw "-SkipFrontendBuild was used but web\dist\index.html does not exist"
    }

    Write-Step "starting PostgreSQL, Redis, Kafka and Elasticsearch"
    Push-Location $RepoRoot
    try {
        Invoke-Checked -FilePath "docker" -Arguments @("compose", "up", "-d", "postgres", "redis", "kafka", "elasticsearch")
    }
    finally {
        Pop-Location
    }

    Wait-TcpPort -Name "PostgreSQL" -Port ([int](Get-EnvOrDefault "POSTGRES_HOST_PORT" "5432"))
    Wait-TcpPort -Name "Redis" -Port ([int](Get-EnvOrDefault "REDIS_HOST_PORT" "6379"))
    Wait-TcpPort -Name "Kafka" -Port 9092
    Wait-TcpPort -Name "Elasticsearch" -Port 9200
    Ensure-KafkaTopics

    if ($WithObservability) {
        [Environment]::SetEnvironmentVariable("DEVCOLLAB_TRACING_ENABLED", "true", "Process")
        [Environment]::SetEnvironmentVariable("DEVCOLLAB_OTLP_TRACES_ENDPOINT", "http://localhost:4318/v1/traces", "Process")
        Write-Step "starting Prometheus, Loki, Tempo, OpenTelemetry Collector, Alloy and Grafana"
        Push-Location $RepoRoot
        try {
            Ensure-ObservabilityImages
            Invoke-Checked -FilePath "docker" -Arguments @(
                "compose", "--profile", "observability", "up", "-d", "--pull", "never",
                "tempo", "loki", "otel-collector", "alloy", "prometheus", "grafana"
            )
        }
        finally {
            Pop-Location
        }
        Wait-TcpPort -Name "OpenTelemetry Collector" -Port 4318
        Wait-TcpPort -Name "Prometheus" -Port 9091
        Wait-TcpPort -Name "Loki" -Port 3100
        Wait-TcpPort -Name "Tempo" -Port 3200
        Wait-TcpPort -Name "Grafana" -Port 3000
    }

    $managed = @()
    try {
        [Environment]::SetEnvironmentVariable("DEVCOLLAB_OUTBOX_WORKER_ENABLED", "true", "Process")
        [Environment]::SetEnvironmentVariable("DEVCOLLAB_SEARCH_ENGINE", "elasticsearch", "Process")
        $managed += Start-MavenService -Name "knowledge-core" -Module "knowledge-core" -Port 8080
        Write-State -Processes $managed
        Wait-ManagedService -Entry $managed[-1]

        [Environment]::SetEnvironmentVariable("DEVCOLLAB_WORKER_SERVER_PORT", "8082", "Process")
        [Environment]::SetEnvironmentVariable("DEVCOLLAB_WORKER_NOTIFICATION_ENABLED", "true", "Process")
        $managed += Start-MavenService -Name "devcollab-worker" -Module "devcollab-worker" -Port 8082
        Write-State -Processes $managed
        Wait-ManagedService -Entry $managed[-1]

        [Environment]::SetEnvironmentVariable("DEVCOLLAB_GATEWAY_PORT", "8090", "Process")
        [Environment]::SetEnvironmentVariable("DEVCOLLAB_CORE_BASE_URL", "http://localhost:8080", "Process")
        $managed += Start-MavenService -Name "collaboration-gateway" -Module "collaboration-gateway" -Port 8090
        Write-State -Processes $managed
        Wait-ManagedService -Entry $managed[-1]

        Write-Step "starting Nginx unified entry"
        Push-Location $RepoRoot
        try {
            Invoke-Checked -FilePath "docker" -Arguments @("compose", "up", "-d", "--force-recreate", "nginx")
        }
        finally {
            Pop-Location
        }
        Wait-TcpPort -Name "Nginx" -Port ([int](Get-EnvOrDefault "NGINX_HOST_PORT" "8088"))
        Wait-HttpEndpoint -Name "Nginx" -Port ([int](Get-EnvOrDefault "NGINX_HOST_PORT" "8088"))
    }
    catch {
        Write-Step "startup failed; managed process logs are in logs\local-demo"
        Stop-ManagedProcesses
        throw
    }

    $entry = "http://localhost:$(Get-EnvOrDefault 'NGINX_HOST_PORT' '8088')"
    Write-Step "local demo is ready: $entry"
    if ($VerifyAfterStart) {
        Invoke-LocalDemoVerification
    }
}

function Stop-LocalDemo {
    Stop-ManagedProcesses
    Push-Location $RepoRoot
    try {
        Invoke-Checked -FilePath "docker" -Arguments @("compose", "stop", "nginx")
        if (-not $KeepInfrastructure) {
            Invoke-Checked -FilePath "docker" -Arguments @("compose", "stop", "postgres", "redis", "kafka", "elasticsearch")
            Invoke-Checked -FilePath "docker" -Arguments @(
                "compose", "--profile", "observability", "stop",
                "prometheus", "loki", "tempo", "otel-collector", "alloy", "grafana"
            )
        }
    }
    finally {
        Pop-Location
    }
    Write-Step "local demo stopped"
}

function Show-LocalDemoStatus {
    Import-DotEnv
    $checks = @(
        @{ name = "Knowledge Core"; port = 8080 },
        @{ name = "Worker"; port = 8082 },
        @{ name = "Gateway"; port = 8090 },
        @{ name = "Nginx"; port = [int](Get-EnvOrDefault "NGINX_HOST_PORT" "8088") },
        @{ name = "Prometheus"; port = [int](Get-EnvOrDefault "PROMETHEUS_HOST_PORT" "9091") },
        @{ name = "Loki"; port = [int](Get-EnvOrDefault "LOKI_HOST_PORT" "3100") },
        @{ name = "Tempo"; port = [int](Get-EnvOrDefault "TEMPO_HOST_PORT" "3200") },
        @{ name = "OTel Collector"; port = [int](Get-EnvOrDefault "OTEL_HTTP_HOST_PORT" "4318") },
        @{ name = "Grafana"; port = [int](Get-EnvOrDefault "GRAFANA_HOST_PORT" "3000") }
    )
    foreach ($check in $checks) {
        $state = if (Test-TcpPort -HostName "localhost" -Port $check.port) { "UP" } else { "DOWN" }
        Write-Step "$($check.name) port=$($check.port) state=$state"
    }
}

function Invoke-LocalDemoVerification {
    Assert-Command "node"
    Import-DotEnv
    $nginxPort = Get-EnvOrDefault "NGINX_HOST_PORT" "8088"
    $baseUrl = "http://localhost:$nginxPort"
    $wsUrl = "ws://localhost:$nginxPort"

    [Environment]::SetEnvironmentVariable("DEVCOLLAB_NGINX_BASE_URL", $baseUrl, "Process")
    Invoke-Checked -FilePath "node" -Arguments @((Join-Path $RepoRoot "tools\e2e-nginx-check.mjs"))

    [Environment]::SetEnvironmentVariable("DEVCOLLAB_CORE_BASE_URL", $baseUrl, "Process")
    [Environment]::SetEnvironmentVariable("DEVCOLLAB_GATEWAY_WS_URL", $wsUrl, "Process")
    Invoke-Checked -FilePath "node" -Arguments @((Join-Path $RepoRoot "tools\e2e-gateway-check.mjs"))

    if ($WithObservability) {
        Invoke-Checked -FilePath "node" -Arguments @((Join-Path $RepoRoot "tools\e2e-observability-check.mjs"))
    }
}

switch ($Action) {
    "start" { Start-LocalDemo }
    "stop" { Stop-LocalDemo }
    "status" { Show-LocalDemoStatus }
    "verify" { Invoke-LocalDemoVerification }
}
