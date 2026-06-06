$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$FrontendDir = Join-Path $Root "frontend"
$BackendScript = Join-Path $Root "run-backend.bat"

function Get-LanAddress {
    $addresses = Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object {
            $_.IPAddress -notlike "127.*" -and
            $_.IPAddress -notlike "169.254.*" -and
            $_.PrefixOrigin -ne "WellKnown"
        } |
        Sort-Object InterfaceMetric |
        Select-Object -ExpandProperty IPAddress

    if ($addresses) {
        return $addresses[0]
    }

    return "YOUR-LAPTOP-IP"
}

function Stop-FrontendOnPort {
    param([int] $Port)

    $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $connections) {
        return
    }

    $processIds = $connections | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($processId in $processIds) {
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($process -and $process.ProcessName -eq "node") {
            Write-Host "Stopping existing frontend dev server on port $Port (PID $processId)..." -ForegroundColor Yellow
            Stop-Process -Id $processId -Force
        } elseif ($process) {
            Write-Host "Port $Port is already used by $($process.ProcessName) (PID $processId). Stop it manually if Vite cannot start." -ForegroundColor Yellow
        }
    }
}

function Test-PortListening {
    param([int] $Port)
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

if (-not (Test-Path $BackendScript)) {
    throw "Backend launcher was not found at $BackendScript"
}

if (-not (Test-Path $FrontendDir)) {
    throw "Frontend folder was not found at $FrontendDir"
}

$LanAddress = Get-LanAddress

Write-Host ""
Write-Host "NutriTrust AI local HTTPS launcher" -ForegroundColor Cyan
Write-Host "Project: $Root"
Write-Host ""

Stop-FrontendOnPort -Port 5173

if (Test-PortListening -Port 8080) {
    Write-Host "Backend already appears to be running on http://localhost:8080" -ForegroundColor Green
} else {
    Write-Host "Starting Spring Boot backend..." -ForegroundColor Cyan
    Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/k", "cd /d `"$Root`" && `"$BackendScript`"" `
        -WorkingDirectory $Root `
        -WindowStyle Normal
}

Write-Host "Starting React HTTPS frontend..." -ForegroundColor Cyan
Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/k", "cd /d `"$FrontendDir`" && set VITE_API_BASE_URL= && npm run dev:https" `
    -WorkingDirectory $FrontendDir `
    -WindowStyle Normal

Write-Host ""
Write-Host "Open on this laptop:" -ForegroundColor Green
Write-Host "  https://localhost:5173/"
Write-Host ""
Write-Host "Open on your phone on the same Wi-Fi:" -ForegroundColor Green
Write-Host "  https://$LanAddress`:5173/"
Write-Host ""
Write-Host "Phone notes:" -ForegroundColor Yellow
Write-Host "  1. Accept the browser certificate warning if shown."
Write-Host "  2. Use the https:// URL, not http://, or camera scanning will be blocked."
Write-Host "  3. Keep the backend and frontend terminal windows open while testing."
Write-Host ""
Write-Host "Press any key to close this launcher window. The backend/frontend windows will keep running."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
