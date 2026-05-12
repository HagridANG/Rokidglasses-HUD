# Rokid Glasses ADB 自动监控安装脚本
# 当眼镜 USB 重新连接时自动安装最新 APK

$adb = "C:\Users\Panasonic\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apkPath = "C:\Users\Panasonic\StudioProjects\Runsight\app\build\outputs\apk\release\app-release.apk"
$glassesSerial = "1901092534017058"

Write-Host "=== Rokid Glasses 自动安装监控 ===" -ForegroundColor Cyan
Write-Host "目标设备: $glassesSerial" -ForegroundColor Gray
Write-Host "APK路径: $apkPath" -ForegroundColor Gray
Write-Host "按 Ctrl+C 停止监控" -ForegroundColor DarkGray
Write-Host ""

$installed = $false

while (-not $installed) {
    $devices = & $adb devices 2>$null
    $connected = $devices | Select-String -Pattern $glassesSerial
    
    if ($connected -and $connected -match "device$") {
        Write-Host "$(Get-Date -Format 'HH:mm:ss') 眼镜已连接！正在安装..." -ForegroundColor Green
        
        $result = & $adb -s $glassesSerial install -r $apkPath 2>&1
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "$(Get-Date -Format 'HH:mm:ss') 安装成功！" -ForegroundColor Green
            $installed = $true
            
            # 启动服务验证
            Write-Host "正在启动 TiltDetectionService..." -ForegroundColor Cyan
            & $adb -s $glassesSerial shell am startservice -n "com.mouzhi.runsight/.service.TiltDetectionService" 2>$null
            
            # 检查 LRing 蓝牙
            Write-Host "检查蓝牙状态..." -ForegroundColor Cyan
            $btDevices = & $adb -s $glassesSerial shell dumpsys bluetooth_manager | Select-String -Pattern "r08_c503|Glasses_7058" -Context 0,2
            if ($btDevices) {
                Write-Host "发现蓝牙设备:" -ForegroundColor Green
                $btDevices | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
            } else {
                Write-Host "未检测到 r08_c503 或 Glasses_7058 蓝牙设备" -ForegroundColor Yellow
            }
            
            # 检查浮窗权限
            $overlay = & $adb -s $glassesSerial shell settings get secure enabled_accessibility_services 2>$null
            Write-Host "无障碍服务: $overlay" -ForegroundColor Gray
            
            # 日志抓取
            Write-Host "`n抓取最近日志..." -ForegroundColor Cyan
            & $adb -s $glassesSerial logcat -d -t 30 -s "TiltDetectionService:I" "BluetoothLocationReceiver:I" "HeadUpActivity:I" 2>$null | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
            
        } else {
            Write-Host "$(Get-Date -Format 'HH:mm:ss') 安装失败: $result" -ForegroundColor Red
        }
    } else {
        $currentDevices = $devices | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] }
        Write-Host "$(Get-Date -Format 'HH:mm:ss') 等待眼镜连接... (当前设备: $($currentDevices -join ', '))" -ForegroundColor DarkGray
    }
    
    if (-not $installed) {
        Start-Sleep -Seconds 3
    }
}

Write-Host "`n监控结束。请测试仰头触发和LRing控制。" -ForegroundColor Cyan
