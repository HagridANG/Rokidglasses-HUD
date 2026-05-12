# RunSight 眼镜自动安装脚本
# 眼镜重新连接后自动安装最新 APK

$adb = "C:\Users\Panasonic\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apk = "C:\Users\Panasonic\StudioProjects\Runsight\app\build\outputs\apk\release\app-release.apk"
$glassesId = "1901092534017058"

Write-Host "=== RunSight 眼镜自动安装监控 ===" -ForegroundColor Cyan
Write-Host "APK: $apk" -ForegroundColor Gray
Write-Host "目标设备: $glassesId" -ForegroundColor Gray
Write-Host "请重新插拔眼镜，或更换USB线/端口..." -ForegroundColor Yellow
Write-Host "按 Ctrl+C 停止监控" -ForegroundColor DarkGray
Write-Host ""

$installed = $false
$attemptCount = 0

while (-not $installed) {
    $attemptCount++
    $devices = & $adb devices 2>$null
    $glassesOnline = $devices | Select-String -Pattern "$glassesId\s+device"
    
    if ($glassesOnline) {
        Write-Host "$(Get-Date -Format 'HH:mm:ss') 眼镜已连接！正在安装 APK..." -ForegroundColor Green
        
        $result = & $adb -s $glassesId install -r $apk 2>&1
        Write-Host $result -ForegroundColor Gray
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "$(Get-Date -Format 'HH:mm:ss') ✅ 安装成功！" -ForegroundColor Green
            $installed = $true
            
            # 启动主界面
            Write-Host "启动应用..." -ForegroundColor Cyan
            & $adb -s $glassesId shell am start -n "com.mouzhi.runsight/.SimpleMainActivity" 2>$null
            
            Write-Host "`n=== 安装完成 ===" -ForegroundColor Green
            Write-Host "请测试仰头触发和LRing控制。" -ForegroundColor White
            
        } else {
            Write-Host "$(Get-Date -Format 'HH:mm:ss') ❌ 安装失败，继续监控..." -ForegroundColor Red
        }
    } else {
        if ($attemptCount % 10 -eq 0) {
            Write-Host "$(Get-Date -Format 'HH:mm:ss') 等待眼镜连接... ($($attemptCount*3)秒)" -ForegroundColor DarkGray
        }
    }
    
    if (-not $installed) {
        Start-Sleep -Seconds 3
    }
}

Read-Host "`n按回车键退出"
