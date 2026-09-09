---
name: porting-rayneo-android-apps
description: Use when porting or maintaining an Android app for RayNeo glasses, including X3 Pro or Mercury dual-eye UI, Surface video or games, temple gestures, controller navigation, and missing DocumentsUI or storage access. 适用于雷鸟眼镜应用移植与适配分支维护。
---

# 雷鸟 Android 应用移植

保留业务内核和已经实测可用的显示路径，按设备能力补齐双眼 UI、输入与存储访问。这里的 X3 Pro 经验是实现线索；新型号、固件及应用仍需核实。

## 开始工作

1. 读取项目说明、现有适配入口和 Git 状态。确认任务是新移植、适配修复还是同步上游；维护分支时先读 [维护与验收](references/maintenance.md)。
2. 用 `adb devices -l` 重新枚举，再把明确选定的序列号传给只读探查脚本：

   ```powershell
   python scripts/probe_device.py --serial SERIAL --package com.example.app --output probe.json
   ```

   路径相对于本技能目录；`--package` 可省略。脚本按序查询设备、显示、输入设备摘要、当前 Android user、目录/设置 Intent；指定包名时增加权限与 appop 摘要。它不采集文件内容、账号、网络配置或实时输入，也不安装、启动应用、授权或重启。详见 [探查与维护](references/maintenance.md#只读探查脚本)。
3. 核实型号、Android API、实际窗口/显示模式和输入路径。刷机后按全新安装核对应用、权限与目录，旧版成功仅是历史证据。`manufacturer=RayNeo`、`640 px`、某个设备 ID 或事件映射都不代表全部雷鸟产品；区分独立 Android 眼镜与依赖宿主机的外接显示设备。
4. 找到最小故障边界再改代码，按下表只读当前需要的参考。

| 当前工作 | 必读参考 | 关键判断 |
|---|---|---|
| 菜单单眼、Compose、双眼视频/游戏、切后台黑屏 | [显示与生命周期](references/display.md) | 普通 View 复制与独立 Surface 分开；一个内容根只包装一次 |
| 镜腿、返回、菜单、手柄、系统媒体键 | [输入与返回语义](references/input.md) | 先追踪事件来源和一次动作经过的路径，再映射业务行为 |
| 缺 DocumentsUI、授权页不存在、扫描为 0 | [目录与权限](references/storage.md) | 权限和目录列举成功后才保存选择并扫描 |
| 同步上游、构建签名、交付 | [维护与验收](references/maintenance.md) | 对比已工作的适配，验证实际 APK 与设备边界 |

## 保持的边界

- Compose 单眼先查根 View 包装时机、后续 `setContent` 和独立 `Dialog`/`Popup` 窗口；Activity 根容器不覆盖所有窗口，改 `displayMetrics` 也不等于双眼输出。复制第二套页面可能重复 `LaunchedEffect`、导航、扫描和输入副作用。
- 普通 View 的双次绘制覆盖不到 `SurfaceView`/`GLSurfaceView`。保留已工作的游戏/视频 renderer；PixelCopy 是条件性方案，性能、延迟与资源释放要测量。
- 缺少系统选择器不等于已经获得文件权限。失败保留可恢复页面，明确显示“待授权/目录访问失败”，避免报告成成功扫描的空库。
- 安装、授权、启动、重启等设备动作按用户当前授权范围执行；本技能不额外扩张范围，也不对已有授权重复索取确认。报告区分源码判断、构建验证和眼镜实测。
