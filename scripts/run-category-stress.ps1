<#
Stress launcher with separate pilot profile. Default/PrepareOnly performs read-only preflight and exits.
Execution requires BOTH -Run and -ConfirmIsolation; no load is started on import/parse.
Official: five cohorts10VU,400s workload +60s quiet recovery. Pilot: five cohorts1VU,30s +60s recovery.
No transcript is used. Password/JWT values are never written to command metadata.
#>
[CmdletBinding()]
param(
    [switch]$PrepareOnly,
    [switch]$Run,
    [switch]$Pilot,
    [switch]$ConfirmIsolation,
    [string]$JMeterHome = 'E:\Tools\apache-jmeter-5.6.3\apache-jmeter-5.6.3'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$credentialFile = Join-Path $projectRoot 'data/credentials/pilot-admin.clixml'
$checker = Join-Path $PSScriptRoot 'check-category-stress-run.py'
$jmeter = Join-Path $JMeterHome 'bin/jmeter.bat'
$plan = Join-Path $projectRoot 'test-plans/23120274_Stress_20261119.jmx'
$utf8 = [System.Text.UTF8Encoding]::new($false)
$monitorJob = $null
$stopFile = $null
$nativeExit = $null
$scriptExit = 2
$runDirectory = $null
$execution = $null
$previousEmail = $env:ESHOP_ADMIN_EMAIL
$previousPassword = $env:ESHOP_ADMIN_PASSWORD
function Set-LocalCredentials {
    try {
        $loadedCredential = Import-Clixml -LiteralPath $credentialFile
        if ($loadedCredential -isnot [System.Management.Automation.PSCredential]) { throw 'Wrong credential type' }
        $env:ESHOP_ADMIN_EMAIL = $loadedCredential.UserName
        $env:ESHOP_ADMIN_PASSWORD = $loadedCredential.GetNetworkCredential().Password
    } catch { throw 'Cannot decrypt local credentials. No values were printed.' }
}
function Clear-LocalCredentials {
    $env:ESHOP_ADMIN_EMAIL = $previousEmail
    $env:ESHOP_ADMIN_PASSWORD = $previousPassword
}
function Save-NewJson([string]$Path, $Value) {
    if (Test-Path -LiteralPath $Path) { throw 'Refusing to overwrite an evidence file' }
    [System.IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 20) + "`n", $utf8)
}
try {
    if ($PrepareOnly -and $Run) { throw 'Choose PrepareOnly or Run, not both' }
    if ($Run -and -not $ConfirmIsolation) { throw 'Run requires explicit ConfirmIsolation' }
    if (-not (Test-Path -LiteralPath $jmeter)) { throw 'JMeter executable missing' }
    if (-not (Test-Path -LiteralPath $credentialFile)) { throw 'Local credential file missing' }
    & git -C $projectRoot check-ignore --quiet -- $credentialFile
    if ($LASTEXITCODE -ne 0) { throw 'Credential path is not ignored by Git' }
    $operatingSystem = Get-CimInstance Win32_OperatingSystem
    $freeMiB = [math]::Floor($operatingSystem.FreePhysicalMemory / 1024)
    if ($freeMiB -lt 2048) { Write-Host ('BLOCKED: free RAM=' + $freeMiB + ' MiB below2048'); throw 'Insufficient RAM' }
    $backendListeners = @(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object { $_.LocalPort -eq 3000 })
    $backendIds = @($backendListeners | ForEach-Object { $_.OwningProcess } | Sort-Object -Unique)
    if ($backendIds.Count -ne 1) { Write-Host 'BLOCKED: backend localhost:3000 not ready'; throw 'Backend not ready' }
    $backendProcessId = [int]$backendIds[0]
    $backend = Get-Process -Id $backendProcessId
    $backendStart = $backend.StartTime.ToUniversalTime().ToString('o')
    $logicalProcessors = [int](Get-CimInstance Win32_ComputerSystem).NumberOfLogicalProcessors
    if ((Get-PSDrive -Name E).Free -lt 1GB) { throw 'Insufficient result disk space' }
    Set-LocalCredentials
    $preflightText = & python $checker preflight
    $preflightCode = $LASTEXITCODE
    Clear-LocalCredentials
    if ($preflightCode -ne 0) { throw 'Read-only credential/baseline/hash preflight blocked; no load started' }
    $preflight = $preflightText | ConvertFrom-Json
    if ($preflight.status -ne 'READY') { throw 'Preflight not READY' }
    $preflight | Add-Member NoteProperty freeRAMMiB $freeMiB
    $preflight | Add-Member NoteProperty backendPid $backendProcessId
    $preflight | Add-Member NoteProperty backendStartUtc $backendStart
    $preflight | Add-Member NoteProperty logicalProcessors $logicalProcessors
    Write-Host ('PREFLIGHT READY | backend PID={0} | free RAM={1} MiB | categories={2} | products={3}' -f
        $backendProcessId, $freeMiB, @($preflight.baseline.categories).Count, @($preflight.baseline.products).Count)
    $mode = if ($Pilot) { 'pilot' } else { 'official' }
    Write-Host ('Stress profile=' + $mode + ' | official10/20/30/40/50VU400s | pilot1/2/3/4/5VU30s | quiet recovery60s | think1000-2000ms')
    if (-not $Run) {
        Write-Host 'PREPARE ONLY: no JMeter engine, mutation, official JTL or run directory created. Await confirmation.'
        $scriptExit = 0
        return
    }
    if (Get-NetUDPEndpoint -LocalPort 4445 -ErrorAction SilentlyContinue) { throw 'JMeter shutdown port 4445 is already in use' }
    $runId = ('stress_' + $mode + '_') + (Get-Date -Format 'yyyyMMddTHHmmss') + '_' + [guid]::NewGuid().ToString('N').Substring(0, 8)
    $runDirectory = Join-Path $projectRoot ('results/stress/' + $mode + '/' + $runId)
    if (Test-Path -LiteralPath $runDirectory) { throw 'Run directory already exists' }
    New-Item -ItemType Directory -Path $runDirectory | Out-Null
    $preflight | Add-Member NoteProperty runId $runId
    $preflight | Add-Member NoteProperty isolationConfirmed ([bool]$ConfirmIsolation)
    Save-NewJson (Join-Path $runDirectory 'preflight.json') $preflight
    $jtl = Join-Path $runDirectory 'samples.jtl'
    $jmeterLog = Join-Path $runDirectory 'jmeter.log'
    $dashboard = Join-Path $runDirectory 'html'
    $stopFile = Join-Path $runDirectory '.monitor-stop'
    $resourceFile = Join-Path $runDirectory 'backend-resources.csv'
    $monitorJob = Start-Job -ArgumentList $backendProcessId, $backendStart, $logicalProcessors, $resourceFile, $stopFile, $runDirectory -ScriptBlock {
        param($TargetBackendId, $ExpectedStart, $LogicalCount, $CsvPath, $StopPath, $EvidencePath)
        $lastTime = $null; $lastCpu = $null; $shutdownSent = $false
        while (-not (Test-Path -LiteralPath $StopPath)) {
            $instant = [DateTimeOffset]::UtcNow
            $process = Get-Process -Id $TargetBackendId -ErrorAction SilentlyContinue
            $sameProcess = $false
            if ($process) { try { $sameProcess = $process.StartTime.ToUniversalTime().ToString('o') -eq $ExpectedStart } catch {} }
            $cpuSeconds = $null; $cpuOneCore = $null; $cpuMachine = $null; $workingMiB = $null; $privateMiB = $null
            if ($sameProcess) {
                $cpuSeconds = [double]$process.CPU
                $workingMiB = [math]::Round($process.WorkingSet64 / 1MB, 3)
                $privateMiB = [math]::Round($process.PrivateMemorySize64 / 1MB, 3)
                if ($null -ne $lastTime -and $null -ne $lastCpu) {
                    $seconds = ($instant - $lastTime).TotalSeconds
                    if ($seconds -gt 0) {
                        $cpuOneCore = [math]::Round(100 * ($cpuSeconds - $lastCpu) / $seconds, 3)
                        $cpuMachine = [math]::Round($cpuOneCore / $LogicalCount, 3)
                    }
                }
                $lastCpu = $cpuSeconds; $lastTime = $instant
            }
            $freeRam = $null
            try { $freeRam = [math]::Floor((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1024) } catch {}
            [pscustomobject]@{
                timestampUtc=$instant.ToString('o'); backendPid=$TargetBackendId; sameBackendProcess=$sameProcess
                backendCpuSecondsTotal=$cpuSeconds; backendCpuPercentOneCore=$cpuOneCore; backendCpuPercentMachine=$cpuMachine
                backendWorkingSetMiB=$workingMiB; backendPrivateBytesMiB=$privateMiB; hostFreeRAMMiB=$freeRam
            } | Export-Csv -LiteralPath $CsvPath -Append -NoTypeInformation -Encoding UTF8
            if ((-not $sameProcess -or $null -eq $freeRam -or $freeRam -lt 2048) -and -not $shutdownSent) {
                # Graceful JMeter-only shutdown; never kill/restart/reset the SUT.
                [System.IO.File]::WriteAllText((Join-Path $EvidencePath 'monitor-alert.json'),
                    ('{"reason":"BACKEND_CHANGED_OR_RAM_GUARD","timestampUtc":"' + $instant.ToString('o') + '"}'))
                try {
                    $udp = [System.Net.Sockets.UdpClient]::new()
                    $bytes = [System.Text.Encoding]::ASCII.GetBytes('Shutdown')
                    [void]$udp.Send($bytes, $bytes.Length, '127.0.0.1', 4445)
                    $udp.Dispose()
                } catch {}
                $shutdownSent = $true
            }
            Start-Sleep -Seconds 1
        }
    }
    $jmeterArgs = @(
        '-n', '-t', $plan, '-l', $jtl, '-j', $jmeterLog, '-e', '-o', $dashboard,
        ('-Jstress_mode=' + $mode), '-Jhw05.stress_reviewed=true',
        '-Jthink_min_ms=1000', '-Jthink_range_ms=1000',
        '-Jhw05.allow_mutation=true', '-Jhw05.isolated_sut=true',
        ('-Jrun_id=' + $runId), '-Jengine_id=stress_local',
        ('-Jproject_root=' + $projectRoot), ('-Jcsv_file=' + (Join-Path $projectRoot 'data/categories.csv')),
        '-Jjmeterengine.nongui.port=4445',
        '-Jjmeter.save.saveservice.output_format=csv', '-Jjmeter.save.saveservice.timestamp_format=ms',
        '-Jjmeter.save.saveservice.print_field_names=true', '-Jsampleresult.timestamp.start=true',
        '-Jjmeter.save.saveservice.thread_counts=true', '-Jjmeter.save.saveservice.time=true',
        '-Jjmeter.save.saveservice.label=true', '-Jjmeter.save.saveservice.response_code=true',
        '-Jjmeter.save.saveservice.successful=true', '-Jjmeter.save.saveservice.url=false',
        '-Jjmeter.save.saveservice.response_data=false', '-Jjmeter.save.saveservice.response_data.on_error=false',
        '-Jjmeter.save.saveservice.samplerData=false', '-Jjmeter.save.saveservice.requestHeaders=false',
        '-Jjmeter.save.saveservice.responseHeaders=false', '-Jjmeter.save.saveservice.response_message=true',
        '-Jjmeter.save.saveservice.assertion_results_failure_message=false', '-Jjmeter.save.saveservice.assertion_results=none',
        '-Jsample_variables=', '-Lorg.apache.http=WARN', '-Lorg.apache.http.wire=OFF', '-Lorg.apache.jmeter.protocol.http=WARN'
    )
    $cohortThreads = if ($Pilot) { 1 } else { 10 }
    $ramp = if ($Pilot) { 1 } else { 20 }
    $delays = if ($Pilot) { @(0,3,6,9,12) } else { @(0,80,160,240,320) }
    $durations = if ($Pilot) { @(30,27,24,21,18) } else { @(400,320,240,160,80) }
    $jmeterArgs += @(('-Jstress_cohort_threads=' + $cohortThreads), ('-Jstress_ramp_seconds=' + $ramp))
    for ($cohort = 0; $cohort -lt 5; $cohort++) {
        $jmeterArgs += ('-Jstress_delay_' + $cohort + '=' + $delays[$cohort])
        $jmeterArgs += ('-Jstress_duration_' + $cohort + '=' + $durations[$cohort])
    }
    $execution = [ordered]@{
        kind='stress'; mode=$mode; engineId='stress_local'; runId=$runId; startedAt=[DateTimeOffset]::UtcNow.ToString('o'); executable=$jmeter
        arguments=$jmeterArgs; nativeLauncherExitCode=$null; finishedAt=$null; backendProcessUnchanged=$false
        credentials='Local DPAPI CLIXML -> process environment; no values retained'
        cpuDefinition='OneCore=100*deltaCpuSeconds/deltaWallSeconds; Machine=OneCore/logicalProcessors; first sample is null'
        ramDefinition='Process working set/private bytes, not JavaScript heap'; resourceIntervalSeconds=1
        workload=@{cohortThreads=$cohortThreads;rampSeconds=$ramp;delays=$delays;durations=$durations;quietRecoverySeconds=60;thinkMinMs=1000;thinkRangeMs=1000}
    }
    Save-NewJson (Join-Path $runDirectory 'execution-start.json') $execution
    Write-Host ('RESULT DIRECTORY: ' + $runDirectory)
    Write-Host ('ACTUAL COMMAND (no credentials): & "' + $jmeter + '" ' + (($jmeterArgs | ForEach-Object {'"' + $_ + '"'}) -join ' '))
    Set-LocalCredentials
    # Synchronous native call: read LASTEXITCODE immediately, no Start-Process wrapper or pipeline.
    & $jmeter @jmeterArgs
    $nativeExit = $LASTEXITCODE
    $execution.nativeLauncherExitCode = $nativeExit
    $execution.finishedAt = [DateTimeOffset]::UtcNow.ToString('o')
    if ($null -eq $nativeExit) { throw 'Native launcher exit code was not returned; do not assume zero' }
    $currentBackend = Get-Process -Id $backendProcessId -ErrorAction SilentlyContinue
    if ($currentBackend) {
        $execution.backendProcessUnchanged = $currentBackend.StartTime.ToUniversalTime().ToString('o') -eq $backendStart
    }
    [System.IO.File]::WriteAllText($stopFile, 'stop', $utf8)
    Wait-Job $monitorJob -Timeout 10 | Out-Null
    if ($monitorJob.State -ne 'Completed') { Stop-Job $monitorJob }
    $execution['monitorJobState'] = [string]$monitorJob.State
    $jobErrors = @($monitorJob.ChildJobs | ForEach-Object { $_.Error })
    $execution['monitorErrorCount'] = $jobErrors.Count
    Save-NewJson (Join-Path $runDirectory 'execution.json') $execution
    $postText = & python $checker postcheck --run-dir $runDirectory
    $postExit = $LASTEXITCODE
    $postResult = $postText | ConvertFrom-Json
    Write-Host ('NATIVE JMETER LAUNCHER EXIT CODE: ' + $nativeExit)
    Write-Host ('POSTCHECK STATUS: ' + $postResult.status)
    Write-Host ('RAW JTL: ' + $jtl)
    Write-Host ('DASHBOARD: ' + (Join-Path $dashboard 'index.html'))
    Write-Host 'No Task 2 analysis or SLA/capacity verdict has been performed.'
    if ($nativeExit -ne 0) { $scriptExit = [int]$nativeExit }
    elseif ($postExit -ne 0 -or $execution.monitorJobState -ne 'Completed' -or $execution.monitorErrorCount -ne 0) { $scriptExit = 2 }
    else { $scriptExit = 0 }
} catch {
    # Do not print exception context that might contain raw responses or credentials.
    Write-Error 'Stress launcher stopped. Preserve files; inspect preflight/metadata/JMeter log. Do not reset SUT or rerun blindly.' -ErrorAction Continue
    $scriptExit = 2
} finally {
    Clear-LocalCredentials
    if ($stopFile -and -not (Test-Path -LiteralPath $stopFile)) { [System.IO.File]::WriteAllText($stopFile, 'stop', $utf8) }
    if ($monitorJob) {
        Wait-Job $monitorJob -Timeout 5 | Out-Null
        if ($monitorJob.State -ne 'Completed') { Stop-Job $monitorJob }
        Remove-Job $monitorJob
    }
}
exit $scriptExit
