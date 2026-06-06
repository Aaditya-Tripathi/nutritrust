param(
    [string]$Model = "llama-3.1-8b-instant"
)

$ErrorActionPreference = "Stop"

function Read-ErrorBody {
    param($ErrorRecord)

    if (-not $ErrorRecord.Exception.Response) {
        return $ErrorRecord.Exception.Message
    }

    try {
        $reader = New-Object System.IO.StreamReader($ErrorRecord.Exception.Response.GetResponseStream())
        $reader.BaseStream.Position = 0
        return $reader.ReadToEnd()
    } catch {
        return $ErrorRecord.Exception.Message
    }
}

$apiKey = $env:GROQ_API_KEY
$keySource = "current terminal"

if ([string]::IsNullOrWhiteSpace($apiKey)) {
    $apiKey = [Environment]::GetEnvironmentVariable("GROQ_API_KEY", "User")
    $keySource = "Windows user environment"
}

Write-Host "Groq API Test"
Write-Host "============="

if ([string]::IsNullOrWhiteSpace($apiKey)) {
    Write-Host "Status : FAILED"
    Write-Host "Reason : GROQ_API_KEY is not set."
    Write-Host ""
    Write-Host "Set it for future terminals:"
    Write-Host 'setx GROQ_API_KEY "your_api_key_here"'
    exit 1
}

Write-Host "Key    : found in $keySource"
Write-Host "Model  : $Model"

$headers = @{
    Authorization = "Bearer $apiKey"
}

try {
    $models = Invoke-RestMethod `
        -Method Get `
        -Uri "https://api.groq.com/openai/v1/models" `
        -Headers $headers

    Write-Host "Models : OK ($($models.data.Count) available)"
} catch {
    Write-Host "Status : FAILED"
    Write-Host "Step   : model list"
    Write-Host "Reason : $(Read-ErrorBody $_)"
    exit 1
}

$body = @{
    model = $Model
    messages = @(
        @{
            role = "user"
            content = "Reply exactly: GROQ_OK"
        }
    )
    temperature = 0
    max_tokens = 20
} | ConvertTo-Json -Depth 8

try {
    $response = Invoke-RestMethod `
        -Method Post `
        -Uri "https://api.groq.com/openai/v1/chat/completions" `
        -Headers $headers `
        -ContentType "application/json" `
        -Body $body

    $content = $response.choices[0].message.content.Trim()
    if ($content -ne "GROQ_OK") {
        Write-Host "Status : FAILED"
        Write-Host "Step   : chat completion"
        Write-Host "Reason : Groq responded, but the model did not return the expected test text."
        Write-Host "Reply  : $content"
        exit 1
    }

    Write-Host "Chat   : OK"
    Write-Host "Status : PASSED"
    exit 0
} catch {
    Write-Host "Status : FAILED"
    Write-Host "Step   : chat completion"
    Write-Host "Reason : $(Read-ErrorBody $_)"
    exit 1
}
