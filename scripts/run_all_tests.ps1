#Requires -Version 5.1
<#
.SYNOPSIS
    RunSight 全自动测试脚本
    运行 app + companion 模块的 JVM 单元测试，如有连接设备则同时运行仪器测试
.DESCRIPTION
    1. 运行 :app:testDebugUnitTest
    2. 运行 :companion:testDebugUnitTest
    3. 检测 Android 设备，如有则运行仪器测试
    4. 汇总测试结果并输出报告
#>

$ErrorActionPreference = "Stop"
$projectDir = Split-Path -Parent $PSScriptRoot
$reportsDir = "$projectDir\build\reports\auto-test"
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$reportFile = "$reportsDir\test-report-$timestamp.txt"

function Write-Report {
    param([string]$message)
    Write-Host $message
    Add-Content -Path $reportFile -Value $message
}

# 创建报告目录
New-Item -ItemType Directory -Force -Path $reportsDir | Out-Null
"RunSight 全自动测试报告 - $timestamp" | Set-Content -Path $reportFile
"========================================" | Add-Content -Path $reportFile
"" | Add-Content -Path $reportFile

# 设置 Java 环境
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

Push-Location $projectDir

try {
    # ====== JVM 单元测试 ======
    Write-Report ""
    Write-Report "[1/4] 运行 app 模块 JVM 单元测试..."
    $appUnitTestOutput = & .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain 2>&1
    $appUnitExit = $LASTEXITCODE
    $appUnitTestOutput | Add-Content -Path $reportFile

    if ($appUnitExit -eq 0) {
        Write-Report "[PASS] app 模块 JVM 单元测试通过"
    } else {
        Write-Report "[FAIL] app 模块 JVM 单元测试失败 (exit=$appUnitExit)"
    }

    Write-Report ""
    Write-Report "[2/4] 运行 companion 模块 JVM 单元测试..."
    $compUnitTestOutput = & .\gradlew.bat :companion:testDebugUnitTest --no-daemon --console=plain 2>&1
    $compUnitExit = $LASTEXITCODE
    $compUnitTestOutput | Add-Content -Path $reportFile

    if ($compUnitExit -eq 0) {
        Write-Report "[PASS] companion 模块 JVM 单元测试通过"
    } else {
        Write-Report "[FAIL] companion 模块 JVM 单元测试失败 (exit=$compUnitExit)"
    }

    # ====== 检测 Android 设备 ======
    Write-Report ""
    Write-Report "[3/4] 检测 Android 设备..."
    $adbDevices = & adb devices 2>$null
    $connectedDevices = $adbDevices | Select-String "device$" | Where-Object { $_ -notmatch "List" }

    if ($connectedDevices) {
        Write-Report "检测到设备:"
        $connectedDevices | ForEach-Object { Write-Report "  $_" }

        Write-Report ""
        Write-Report "[4/4] 运行 app 模块仪器测试 (connectedAndroidTest)..."
        $instrTestOutput = & .\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain 2>&1
        $instrExit = $LASTEXITCODE
        $instrTestOutput | Add-Content -Path $reportFile

        if ($instrExit -eq 0) {
            Write-Report "[PASS] app 模块仪器测试通过"
        } else {
            Write-Report "[FAIL] app 模块仪器测试失败 (exit=$instrExit)"
        }
    } else {
        Write-Report "未检测到 Android 设备，跳过仪器测试"
        Write-Report "[SKIP] 仪器测试 (无设备)"
    }

    # ====== 汇总 ======
    Write-Report ""
    Write-Report "========================================"
    Write-Report "测试汇总"
    Write-Report "========================================"
    Write-Report "app 单元测试:    $(if($appUnitExit -eq 0){'PASS'}else{'FAIL'})"
    Write-Report "companion 单元测试: $(if($compUnitExit -eq 0){'PASS'}else{'FAIL'})"
    if ($connectedDevices) {
        Write-Report "app 仪器测试:    $(if($instrExit -eq 0){'PASS'}else{'FAIL'})"
    } else {
        Write-Report "app 仪器测试:    SKIP (无设备)"
    }
    Write-Report ""
    Write-Report "详细报告: $reportFile"

    # 打开 HTML 报告（如果存在）
    $appHtmlReport = "$projectDir\app\build\reports\tests\testDebugUnitTest\index.html"
    if (Test-Path $appHtmlReport) {
        Write-Report "app 单元测试 HTML 报告: $appHtmlReport"
    }

} finally {
    Pop-Location
}
