---
name: porting-rayneo-android-apps
description: Use when porting or maintaining source-based or APK-only Android apps for RayNeo glasses, including all-window dual-eye UI and overlays, Surface video or games, temple gestures, focus navigation, storage access, and upstream/version updates. 适用于雷鸟眼镜应用移植与适配分支维护。
---

# 雷鸟 Android 应用移植

保留业务内核和已经实测可用的显示路径，按设备能力补齐双眼 UI、输入与存储访问。这里的 X3 Pro 经验是实现线索；新型号、固件及应用仍需核实。

**保留原应用名称。** 默认保持原有应用显示名、启动器名称和应用内名称，不自行追加“RayNeo”“X3 Pro”“雷鸟版”“适配版”等前后缀；安装到眼镜本身已表达使用场景。只有用户明确要求改名时才修改。为并存、签名或更新隔离调整内部包名、构建标识，不意味着需要更改用户看到的名称；验收时核对最终 APK 的 application/launcher label。

**完整覆盖、集中接入、最小改动面。** 有源码与 APK-only 都先追踪调用链，优先在应用或依赖中较上游、稳定且实际覆盖目标的公共入口适配。默认覆盖所有应用拥有的窗口和弹层，而不只首页或主 Activity；用创建路径清单证明覆盖。保留原有业务调用与输入语义，将设备逻辑集中在独立适配层。不要用逐页替换制造维护负担，也不要为减少文件数而漏掉独立窗口或 Surface。选择入口及更新方案前读 [最小侵入与版本迁移](references/minimal-adaptation.md)。

**默认使用光标模式（原生焦点导航）。** 未指定操作方式时，上下左右滑动移动界面已有的选中框/焦点，单击确认当前项，双击返回/退出当前层。用户明确说“鼠标模式”时，才使用随滑动自由移动的小白点模拟鼠标，单击点击指针位置、双击返回/退出。两种模式不混用，也不因为“光标”一词而默认生成鼠标指针；已有明确模式沿用，用户后续选择优先。细节见 [输入与返回语义](references/input.md)。

**X3 Pro 电视应用必须补齐双指菜单。** 除滑动映射上下左右、单击映射确认、双击映射返回外，双指轻点映射为菜单键 `KEYCODE_MENU`，复用应用当前页面或弹层的原有菜单行为。长按菜单不能替代双指菜单；不得将所有多指事件一律取消后声称镜腿操作已完整适配。识别、去重与验收细节见 [输入与返回语义](references/input.md#x3-pro-电视应用的双指菜单)。

光标模式应具备“检测移动结果 → 从当前界面重新定位选中项 → 恢复导航”的闭环，不能仅记忆旧 View。区分成功移动、布局等待、正常边界与失焦；界面变化后以当前窗口的有效状态为准，旧记忆仅作经校验的候选。恢复焦点不自动确认条目，也不盲目重放已消费的手势。

## 开始工作

1. 读取项目说明、现有适配入口和 Git 状态。确认任务是新移植、适配修复还是同步上游；维护分支时先读 [维护与验收](references/maintenance.md)。
2. **设备连上后先保障 ADB 通道，再做探查。** 需要设备操作时，先用 `adb devices -l` 枚举并明确选定目标眼镜；立即检查 ADB TCP 5555。**发现未开启，就第一时间执行 `adb -s SERIAL tcpip 5555`，不要等安装、传包、完整探查或掉线后才开启。** 这是用户明确指定的默认操作，不重复询问是否开启。随后连接当前眼镜的 `IP:5555`、核对设备身份，后续优先显式使用无线 serial。具体顺序和验证见 [优先建立 TCP ADB](references/maintenance.md#优先建立-tcp-adb)。纯源码/补丁整理或用户明确暂停设备操作时不触碰设备。
3. TCP ADB 通道处理后，把明确选定、已核验的序列号传给只读探查脚本：

   ```powershell
   python scripts/probe_device.py --serial SERIAL --package com.example.app --output probe.json
   ```

   路径相对于本技能目录；`--package` 可省略。脚本按序查询设备、显示、输入设备摘要、当前 Android user、目录/设置 Intent；指定包名时增加权限与 appop 摘要。它不采集文件内容、账号、网络配置或实时输入，也不安装、启动应用、授权或重启。详见 [探查与维护](references/maintenance.md#只读探查脚本)。
4. 核实型号、Android API、实际窗口/显示模式和输入路径。刷机后按全新安装核对应用、权限与目录，旧版成功仅是历史证据。`manufacturer=RayNeo`、`640 px`、某个设备 ID 或事件映射都不代表全部雷鸟产品；区分独立 Android 眼镜与依赖宿主机的外接显示设备。
5. 找到最小故障边界再改代码，按下表只读当前需要的参考。

| 当前工作 | 必读参考 | 关键判断 |
|---|---|---|
| 选择适配入口、有源码重构、APK-only 补丁或迁移新版本 | [最小侵入与版本迁移](references/minimal-adaptation.md) | 公共接入点优先；重建混淆/窗口清单，检查 DEX 编号与方法引用上限，不按补丁脚本行数判断 |
| 菜单单眼、Compose、双眼视频/游戏、切后台黑屏 | [显示与生命周期](references/display.md) | 普通 View 复制与独立 Surface 分开；一个内容根只包装一次 |
| 视频复制开销、播放发热、清晰度与分辨率上限、跨 App 复用 OES | [高性能视频路径](references/high-performance-video.md) | 接管单解码器 Surface，GPU 一次取帧双眼绘制；区分码流、渲染和 UI，实测负载后判断收益 |
| OES 详细实现、动态提示只显示一眼、Moonlight 经验迁到蓝莓 | [OES 双眼视频实战](references/oes-stereo-field-guide.md) | 视频双眼不代表动态 UI 双眼；记录失败尝试、独立提示层、EGL 退出竞态与最终版本复测边界 |
| 镜腿、鼠标/光标导航、系统左右反向、返回、菜单、手柄 | [输入与返回语义](references/input.md) | 先确认系统是否已转换坐标，避免应用重复反转；再映射业务行为 |
| 缺 DocumentsUI、授权页不存在、扫描为 0 | [目录与权限](references/storage.md) | 权限和目录列举成功后才保存选择并扫描 |
| X3 Pro 页面显示即整机重启、硬件加速双眼绘制、USB ADB 频繁断连 | [设备与固件缺陷](references/device-defects.md) | 优先沿用局部软件绘制规避；区分传输断连与设备重启 |
| 同步上游、构建签名、交付 | [维护与验收](references/maintenance.md) | 对比已工作的适配，验证实际 APK 与设备边界 |

## 保持的边界

- Compose 单眼先查根 View 包装时机、后续 `setContent` 和独立 `Dialog`/`Popup` 窗口；Activity 根容器不覆盖所有窗口，改 `displayMetrics` 也不等于双眼输出。复制第二套页面可能重复 `LaunchedEffect`、导航、扫描和输入副作用。
- 普通 View 的双次绘制覆盖不到 `SurfaceView`/`GLSurfaceView`。保留已工作的游戏/视频 renderer；PixelCopy 是条件性方案，性能、延迟与资源释放要测量。
- 缺少系统选择器不等于已经获得文件权限。失败保留可恢复页面，明确显示“待授权/目录访问失败”，避免报告成成功扫描的空库。
- 安装、授权、启动、重启等设备动作按用户当前授权范围执行；本技能不额外扩张范围，也不对已有授权重复索取确认。报告区分源码判断、构建验证和眼镜实测。
