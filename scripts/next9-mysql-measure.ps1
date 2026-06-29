param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$SeedCount = 800,
    [int]$LoopCount = 60,
    [int]$PageSize = 20
)

$ErrorActionPreference = 'Stop'

Write-Output "[NEXT-9] seed start: count=$SeedCount"
1..$SeedCount | ForEach-Object {
    $i = $_
    $body = @{
        orderId = (400000 + $i)
        idempotencyKey = ("next9-mysql-" + $i + "-" + [guid]::NewGuid().ToString('N').Substring(0,8))
        amount = 1000.00
        method = 'CARD'
    } | ConvertTo-Json

    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/payments" -ContentType 'application/json' -Body $body | Out-Null
}
Write-Output "[NEXT-9] seed done"

function Measure-Latencies {
    param(
        [string]$Url,
        [int]$Count
    )

    $vals = @()
    1..$Count | ForEach-Object {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        Invoke-WebRequest -UseBasicParsing -Uri $Url | Out-Null
        $sw.Stop()
        $vals += $sw.Elapsed.TotalMilliseconds
    }

    $sorted = $vals | Sort-Object
    $idx = [Math]::Ceiling(0.95 * $Count) - 1

    return [PSCustomObject]@{
        avg = [Math]::Round((($vals | Measure-Object -Average).Average), 2)
        p95 = [Math]::Round($sorted[$idx], 2)
        min = [Math]::Round($sorted[0], 2)
        max = [Math]::Round($sorted[$sorted.Count - 1], 2)
    }
}

$offsetUrl = "$BaseUrl/api/v1/payments?page=0&size=$PageSize"
$cursorUrl = "$BaseUrl/api/v1/payments/cursor?size=$PageSize"

Write-Output "[NEXT-9] measure offset: $offsetUrl"
$offset = Measure-Latencies -Url $offsetUrl -Count $LoopCount

Write-Output "[NEXT-9] measure cursor: $cursorUrl"
$cursor = Measure-Latencies -Url $cursorUrl -Count $LoopCount

Write-Output "offset avg=$($offset.avg)ms p95=$($offset.p95)ms min=$($offset.min)ms max=$($offset.max)ms"
Write-Output "cursor avg=$($cursor.avg)ms p95=$($cursor.p95)ms min=$($cursor.min)ms max=$($cursor.max)ms"
