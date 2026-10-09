# 探查、分支维护与验收

## 优先建立 TCP ADB

用户指定的默认流程：目标眼镜一旦通过 ADB 可达，优先检查并开启 TCP 5555，再运行完整探查、传包、安装或启动。此开启操作已由用户明确要求，不再单独索取确认；用户当前明确暂停设备操作时除外。

1. `adb devices -l` 枚举，按目标型号和序列号明确选择眼镜，不能选“第一台设备”。所有设备命令串行并指定 `-s SERIAL`。
2. 立即用 `adb -s SERIAL shell getprop service.adb.tcp.port` 检查当前运行端口。空值、`-1`、`0` 或非 `5555` 时，第一时间执行 `adb -s SERIAL tcpip 5555`。不要先跑完整探查、安装或长时间抓日志，也不要仅凭 `persist.adb.tcp.port` 推断当前已开启。若当前已核验同一眼镜的 `IP:5555` 可用，则直接复用，避免重复重启 adbd。
3. 在当前传输可用时读取并暂存该眼镜当前 IP、`ro.serialno`、型号和 device；若尚未取得 IP，可在开启后传输恢复时查询。`tcpip` 会重启设备侧 adbd，短暂掉线不代表整机重启；设置命令成功只证明请求被接受。
4. 用刚核实的当前地址执行 `adb connect IP:5555`，再以 `adb -s IP:5555 shell getprop ro.serialno` 等信息核对同一设备。核验成功后，探查、安装、日志和运行操作优先使用这个无线 serial。不得从 mDNS 发现结果直接挑一台其他设备，也不沿用过期 IP。
5. 端口已设为 5555 但无线连接失败时，检查当前地址、网络可达性和身份，不反复执行 `tcpip`。眼镜没有可用网络时明确记录“TCP 5555 已请求/已开启、无线尚未连通”，不把设置端口等同无线通道可用，不自行改 Wi-Fi 网络。
6. ADB 已不可达时不能发送 `tcpip`；先区分 USB 接口、设备模式与网络传输。Windows 若将同一序列号枚举为 Qualcomm Diagnostics 900E，应报告该实际模式，不只写“ADB 不稳定”；不自行操作诊断串口、重启设备或全局 `adb kill-server`。

`probe_device.py` 仍是只读脚本，不承担上述模式切换；调用者在运行脚本前完成本节流程。TCP ADB 降低对不稳定 USB 传输的依赖，不承诺防止固件故障或设备进入诊断模式。

## 只读探查脚本

`scripts/probe_device.py` 仅使用 Python 标准库。先运行 `adb devices -l` 选择当前设备，再显式指定序列号；脚本不连接新地址、切换 ADB 模式或选择“第一台设备”。USB、Wi-Fi 与 IPv6 形式均可作为 serial，禁止空白和 shell 元字符。

```powershell
python scripts/probe_device.py --help
python scripts/probe_device.py --serial SERIAL
python scripts/probe_device.py --serial SERIAL --package com.example.app --adb 'C:\Android\platform-tools\adb.exe' --output probe.json
python -B scripts/test_probe_device.py
```

路径相对于技能目录。`--output` 只创建新文件且不自动创建父目录；省略时打印 JSON。输出含设备 serial，交接或发布前按需要去标识化；不要把现场探查 JSON 一律提交仓库或写入技能。

| 输出 | 用途与边界 |
|---|---|
| `manufacturer`/`model`/`device`/`brand`、Android 版本/API | 确认当前设备；不单靠品牌开启固定尺寸的适配 |
| `wm_size`/`wm_density`、`display_summary` | 区分默认显示与实际 display；窗口区域仍需应用侧验证 |
| `input_summary` | 输入设备名、source 等筛选摘要；不包含实时事件与事件队列 |
| `android_user`、`document_tree_picker`、设置 Intent 查询 | 保留 Android user；输出 `No activity found` 表示没有匹配处理者，不是脚本失败 |
| 可选 `package_presence`、`package_summary`、`all_files_appop` | 当前 user 下精确包名是否已安装、版本、存储声明/授予摘要与 appop；实际访问由应用验证 |

每条命令都是参数数组、`shell=False`、固定只读子命令，按序执行且有独立超时（默认 8 秒，最大 30 秒）。传输预检失败立即结束；其他命令失败保留部分结果。查询当前 Android user 失败则跳过所有 user 相关查询，不假定 user 0。没有 `su`、权限授予、安装、应用启动、截图或网络/账号配置采集。

退出码：`0` 表示所有已安排命令返回成功，`1` 表示探查不完整，`2` 表示参数/输出文件错误。`complete=true` 只证明命令执行完毕，不证明每项能力存在。命令结果含 `status`/`returncode`；`truncated` 标记摘要上限，不能把被筛掉或截断的内容理解为不存在。需要更多细节时针对特定服务做一次只读查询。

## 维护已有适配分支

先确认仓库/分支、remote、工作区变更、当前适配相对上游的提交边界。用户指定维护自己的 fork 时保留适配提交和发布目标；有并行工作时使用隔离 worktree，避免 reset/覆盖他人改动。

源码与 APK-only 更新都遵循 [最小侵入与版本迁移](minimal-adaptation.md)：公共入口优先，适配实现集中，业务调用尽量保持原样。比较修改前后的原有文件/方法触点，并说明独立窗口或 Surface 为什么需要例外入口；不能用减少补丁数量掩盖漏适配。源码子模块必须发布可拉取的提交；APK 补丁必须校验新版入口、匹配数量与修改后结构。合并无冲突或补丁执行成功不代表新版本的全部窗口与弹层仍受覆盖。

设备刷机或清数据后，把现场当作全新安装：重新核对固件、显示模式、目标包是否存在、当前 Android user、全部文件访问/普通权限、实际目录及持久 URI 授权。设备品牌相同不证明旧版环境已恢复；旧截图、历史安装或授权成功都不算本次验证。逐个恢复应用时，在每个包的首次启动与首次扫描边界分别验收，避免沿用上一款 App 的权限结果。

部分 X3 Pro 的 USB adbd 在负载升高时会掉线。断连与重启的证据判别、本地主机日志分段，以及硬件加速相关重启的规避见 [设备与固件缺陷](device-defects.md)。中断后重新枚举、核对同一设备并从实际状态继续，不把掉线解释成用户拔线或应用崩溃。按本文开头的“优先建立 TCP ADB”流程，在连接尚可用时立即检查并开启 TCP 5555，核验同一设备后优先使用 `IP:5555`，不等 USB 掉线才切换；不要对其他已连接设备执行全局 ADB 重启。网络名、地址和口令均从当前会话获取，口令不写入技能、探查结果或仓库。设备操作串行执行，减少连续重型抓取；先检查上一次变更是否已生效再重试。

梳理适配触点而非逐页重写：Application 注册、设备识别、Manifest 元数据/进程/Activity、双眼普通 UI 容器、专用游戏/视频 renderer、输入 overlay、存储 provider 与扫描入口、构建 flavor/依赖版本。

进行手势对照时，先确认设备清醒、目标窗口有焦点，并核对摘下眼镜后的自动省电设置。应用窗口的 `FLAG_KEEP_SCREEN_ON` 不能作为厂商省电策略或 ADB 持续在线的验证依据；黑屏、断线与应用崩溃分别查证。不因连接改善就把此前所有失败归为省电。需要临时调整系统设置时沿用当前授权、记录原值；用户主动修改的设置按其意图保留。方向开关的运行值与持久化值可能异步同步，恢复边界见 [输入与返回语义](input.md#临时切换方向开关与恢复)。

同步后特别核对：

- 新增或异步 Activity/Fragment/Compose 内容、Dialog、Popup、Tooltip、消息提示和 DecorView 延迟浮层是否落在适配边界之外；对照 [窗口与弹层清单](display.md)。游戏进程自己的 Application 是否仍注册必要逻辑。
- 上游初始化/`setContent` 时机变化是否影响包装器；返回和手柄映射是否仍使用原入口。
- 原有双眼 Surface 输出是否被普通 View 包装二次处理。
- target SDK、SAF API、provider 选择、preference 字段及任务调度变化是否影响权限回退。
- variant、applicationId、版本号与签名是否符合这次交付；不要用 debug 构建结果描述 release 验证。

针对变更运行项目规定的检查。权限/目录状态机适合 JVM 测试；布局、手势和 Surface 恢复须设备验证。构建完成后核对实际 APK 的包名、版本、ABI、debuggable 与签名；安装/替换包前确认用户已授权相应设备操作及签名兼容性，不以卸载清数据消除升级失败。

## 验收与报告

| 边界 | 证据 |
|---|---|
| 构建/打包 | 本次命令成功及生成 APK 身份、签名信息 |
| 普通 UI | 首页、设置、菜单、权限恢复页两眼可读；左右位置/裁剪/滚动正常 |
| Surface 内容 | 已知游戏/视频两眼输出、恢复与切换正常；性能结论有实际测量 |
| 输入 | 物理左右镜腿、手柄及返回，每次操作只触发一次预期动作 |
| 存储 | 授权/拒绝/取消/恢复路径；以已知文件证明扫描，不仅检查 appop |

截图可以帮助检查逻辑画面，不能单独证明佩戴舒适度、真实双眼同步或输入路径。报告明确区分“源码已检查”“构建通过”“已安装”和“硬件实际操作通过”。保留有用的命令、结果与尚未验证项，避免把历史验证写成新构建的实测结论。

## 经验来源与适用边界

本技能提炼自本地 `RAYNEO_APP_PORTING_PLAYBOOK_2026-03-18.md`（尤其显示/输入/生命周期、旧应用 SOP 和双目容器章节），以及 Lemuroid 适配中的 `DualEyeLayout`、`DualEyeActivityWrapper`、`RightEyeGestureOverlay`、`RayNeoGameScreen`、`RayNeoMirrorView` 和目录选择路径。引用名称用于在原项目追踪依据；执行技能无需安装这些项目。

保留了机制与故障边界，没有把历史硬编码尺寸、厂商匹配、输入枚举顺序或注释中的性能断言当作跨设备保证。新工程优先查看自身实际代码与设备输出；此技能不要求引入 Lemuroid 的业务类或依赖。

## Java 控件构造期的 Kotlin 重写方法

Legado 的图片动作弹窗曾在阅读退出时触发已确认的崩溃：延迟创建 `PopupWindow` 子类时，Java 父类构造过程经 `setContentView` 调用了重写的 `isShowing`，而 Kotlin 子类的 Context 字段尚未赋值。重写查询应在构造期也成立，不能依赖子类已完成初始化；加入“构造后未显示就 dismiss、显示后 dismiss、重复 show”的设备回归检查。这个案例验证了具体初始化顺序，不能只因 Activity 在退出时崩溃就把原因归到 onDestroy 中的业务清理。
