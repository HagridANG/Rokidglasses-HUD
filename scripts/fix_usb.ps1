# RunSight USB 修复脚本
# 用途：当眼镜显示为 RG-glasses-IDP 或无法识别时，自动修复 ADB 连接

$deviceId = "USB\VID_18D1&PID_4EE7\1901092534017058"
$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

Write-Host "=== RunSight USB 修复工具 ===" -ForegroundColor Cyan

# 1. 停止 ADB
Write-Host "[1/4] 停止 ADB 服务器..."
& $adbPath kill-server 2>$null
Start-Sleep -Seconds 2

# 2. 卸载设备驱动缓存
Write-Host "[2/4] 卸载错误的驱动缓存..."
pnputil /remove-device $deviceId 2>$null | Out-Null
Start-Sleep -Seconds 2

# 3. 重启 ADB
Write-Host "[3/4] 重启 ADB 服务器..."
& $adbPath start-server 2>$null | Out-Null
Start-Sleep -Seconds 3

# 4. 检查设备
Write-Host "[4/4] 检查设备状态..."
$devices = & $adbPath devices 2>$null

if ($devices -match "1901092534017058.*device") {
    Write-Host "✅ 设备已正常连接！" -ForegroundColor Green
    & $adbPath -s 1901092534017058 shell echo "OK"
} else {
    Write-Host "⚠️ 设备仍未识别。请执行以下操作：" -ForegroundColor Yellow
    Write-Host "   1. 拔掉眼镜 USB 线"
    Write-Host "   2. 等待 10 秒"
    Write-Host "   3. 重新插入 USB 线（确保插紧）"
    Write-Host "   4. 如果仍不行，换一根 USB 线或换 USB 2.0 端口"
    Write-Host ""
    Write-Host "   按回车键重新检测..."
    Read-Host | Out-Null
    
    # 再次检测
    $devices = & $adbPath devices 2>$null
    if ($devices -match "1901092534017058.*device") {
        Write-Host "✅ 设备已连接！" -ForegroundColor Green
    } else {
        Write-Host "❌ 仍无法识别。请检查眼镜是否开机、USB 线是否正常。" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "按回车键退出..."
Read-Host | Out-Null
