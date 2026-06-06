@echo off
setlocal

set "MAVEN_CMD=%~dp0tools\apache-maven-3.9.9\bin\mvn.cmd"

if not exist "%MAVEN_CMD%" (
    where mvn.cmd >nul 2>nul
    if errorlevel 1 (
        where mvn >nul 2>nul
        if errorlevel 1 (
            echo Maven was not found. Install Maven globally or restore tools\apache-maven-3.9.9.
            exit /b 1
        )
        set "MAVEN_CMD=mvn"
    ) else (
        set "MAVEN_CMD=mvn.cmd"
    )
)

if "%GROQ_API_KEY%"=="" (
    for /f "usebackq delims=" %%A in (`powershell.exe -NoProfile -Command "[Environment]::GetEnvironmentVariable('GROQ_API_KEY', 'User')"`) do set "GROQ_API_KEY=%%A"
)

if "%DATABASE_URL%"=="" (
    for /f "usebackq delims=" %%A in (`powershell.exe -NoProfile -Command "[Environment]::GetEnvironmentVariable('DATABASE_URL', 'User')"`) do set "DATABASE_URL=%%A"
)

if "%DATABASE_USERNAME%"=="" (
    for /f "usebackq delims=" %%A in (`powershell.exe -NoProfile -Command "[Environment]::GetEnvironmentVariable('DATABASE_USERNAME', 'User')"`) do set "DATABASE_USERNAME=%%A"
)

if "%DATABASE_PASSWORD%"=="" (
    for /f "usebackq delims=" %%A in (`powershell.exe -NoProfile -Command "[Environment]::GetEnvironmentVariable('DATABASE_PASSWORD', 'User')"`) do set "DATABASE_PASSWORD=%%A"
)

if "%DATABASE_PASSWORD%"=="" (
    echo DATABASE_PASSWORD is not set.
    echo Set it to your local PostgreSQL password before starting the backend.
    echo.
    echo Current PowerShell terminal:
    echo   $env:DATABASE_PASSWORD="your_actual_postgres_password"
    echo   .\run-backend.bat
    echo.
    echo Future PowerShell terminals:
    echo   setx DATABASE_PASSWORD "your_actual_postgres_password"
    echo   Open a new terminal, then run .\run-backend.bat
    exit /b 1
)

if "%GROQ_API_KEY%"=="" (
    echo GROQ_API_KEY is not loaded. AI reports will use the fallback message.
) else (
    echo GROQ_API_KEY is loaded.
)

if "%DATABASE_URL%"=="" set "DATABASE_URL=jdbc:postgresql://localhost:5433/nutritrust"
if "%DATABASE_USERNAME%"=="" set "DATABASE_USERNAME=postgres"

echo Database URL is %DATABASE_URL%
echo Database user is %DATABASE_USERNAME%

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$psql=(Get-Command psql.exe -ErrorAction SilentlyContinue).Source; if(-not $psql){$candidates=@('C:\Program Files\PostgreSQL\18\bin\psql.exe','C:\Program Files\PostgreSQL\17\bin\psql.exe','C:\Program Files\PostgreSQL\16\bin\psql.exe','C:\Program Files\PostgreSQL\15\bin\psql.exe'); foreach($candidate in $candidates){if(Test-Path $candidate){$psql=$candidate; break}}}; if(-not $psql){exit 0}; try{$uri=[Uri](($env:DATABASE_URL -replace '^jdbc:','')); $hostName=$uri.Host; $port=if($uri.Port -gt 0){$uri.Port}else{5432}; $database=$uri.AbsolutePath.TrimStart('/')} catch {Write-Host 'Skipping database preflight because DATABASE_URL could not be parsed.'; exit 0}; $env:PGPASSWORD=$env:DATABASE_PASSWORD; & $psql -h $hostName -p $port -U $env:DATABASE_USERNAME -d $database -c 'select 1' *> $null; if($LASTEXITCODE -ne 0){Write-Host ''; Write-Host 'DATABASE_PASSWORD is set, but PostgreSQL rejected it.'; Write-Host 'Fix it with your actual local PostgreSQL password, then run the backend again.'; Write-Host ''; Write-Host 'Current PowerShell terminal:'; Write-Host '  $env:DATABASE_PASSWORD=\"your_actual_postgres_password\"'; Write-Host '  .\run-backend.bat'; Write-Host ''; Write-Host 'Future PowerShell terminals:'; Write-Host '  setx DATABASE_PASSWORD \"your_actual_postgres_password\"'; Write-Host '  Open a new terminal, then run .\run-backend.bat'; exit 2}; exit 0"
if errorlevel 1 exit /b 1

"%MAVEN_CMD%" spring-boot:run
