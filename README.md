# HeadUpDisplay

专为 Rokid Glasses 设计的息屏仰头亮屏应用。

**#使用方法**
安装软件，打开后，点击唯一一个按钮，然后把这个软件的辅助功能打开，然后返回软件，之后试试看是不是实现功能了。

## 功能

- **息屏仰头 1 秒** → 自动点亮屏幕（无浮窗）
- **亮屏仰头 1 秒** → 左下角弹出透明小浮窗（显示时间 / 日期 / 星期）
- **持续仰头不重复触发** → 避免平躺时反复亮屏
- **低头后再次仰头** → 正常触发

## 工作原理

通过 `AccessibilityService` + `TYPE_GAME_ROTATION_VECTOR` / `TYPE_ACCELEROMETER` WAKEUP 传感器检测头部姿态：

| 姿态 | Roll | Pitch | Accel Z |
|------|------|-------|---------|
| 平视 | -4° ~ -9° | -71° ~ -75° | 2.2 ~ 3.2 |
| 低头 | -2° ~ -5° | -34° ~ -37° | 7.4 ~ 7.9 |
| 抬头 | -168° ~ -173° | -65° ~ -68° | -3.5 ~ -3.9 |

仰头判定：`abs(roll) > 150° && accelZ < -2.0`，连续 2 个样本确认，保持 1 秒后触发。

## 安装

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

## 构建

```bash
./gradlew :app:assembleRelease
```

## 权限

- `BIND_ACCESSIBILITY_SERVICE` — 核心服务，用于息屏保活和传感器监听
- `WAKE_LOCK` — 点亮屏幕
- `SYSTEM_ALERT_WINDOW` — 亮屏浮窗
- `HIGH_SAMPLING_RATE_SENSORS` — 50Hz 传感器采样

## 兼容性

- **设备**: Rokid Glasses (Android 12)

- **LRing 兼容**: 不占用蓝牙，与 BLE 控制无冲突

## License

MIT
