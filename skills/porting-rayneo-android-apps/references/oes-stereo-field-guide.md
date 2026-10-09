# OES 双眼视频实战：渲染、动态提示与蓝莓迁移

适用于可接管解码 Surface 的视频/投屏 App。先读 [高性能视频路径](high-performance-video.md)；这里补充 Moonlight 12.2 的实机经验和可迁移实现细节。不要把普通 View 软件绘制、视频硬解、GPU 合成统称为一个“硬件加速开关”。

## 证据等级与已知限制

2026-09-17，X3 Pro ARGF20 / MercuryLiteXR、API 32、2BAU、Adreno 621：Moonlight 与 Sunshine 2026.914.233613 真实桌面串流使用 `c2.qti.hevc.decoder.low_latency`，日志确认 hardware/vendor=true、1280×720/30 fps/5 Mbps SDR，OES 首帧和双眼画面成功。测试期间 boot ID 不变，未观察到整机重启。

这支持“此构建、此固件、此会话的 OES 路径可运行”，不证明全部 GPU/HWUI 路径安全，也不支持将历史重启归因于 CPU 阉割、缺库或特定驱动。物理镜腿、长期温升、所有独立窗口并未全验收。

关键反例：视频双眼正常、首页/设置/配对截图左右相同，但后来动态退出提示只更新左眼。代码包含两次绘制也不构成双眼显示证据。最终用视频上方两个独立 TextView 显示同一退出提示，两眼实测成功；普通动态 Toast/字幕/控件的通用镜像仍未解决。后文保留失败尝试，不把它们写成修复配方。

## 一条视频链路，不复制解码工作

```text
协议/播放器业务 -> 一个硬件 decoder
                      |
                Surface(producer)
                      |
           SurfaceTexture / external OES
                      |
        GL线程 updateTexImage 一次 + producer矩阵
                      |
          同一帧两个viewport -> 一次EGL swap

普通UI/输入/提示 -> 独立于视频的宿主与适配入口
```

1. 在 GL 线程创建 EGL context、external OES texture 和 SurfaceTexture，设置 LINEAR 与 CLAMP_TO_EDGE；片元着色器使用 `samplerExternalOES`。先查扩展支持和 shader compile/link 状态。现有 SDR 示例使用 GLES2、RGBA8，无深度/模板缓冲；不是 HDR 模板。
2. `SurfaceTexture.setDefaultBufferSize` 按协商码流尺寸设置，创建 `Surface` 后将它实际传入原播放器 `setSurface` 或 MediaCodec `configure`。producer 仍可覆盖默认尺寸；要读真实 output format/crop，不能把 defaultBufferSize 当作解码限幅证据。
3. GL 准备完成后通知业务线程；保留原回调线程约定。Moonlight 用主线程桥接 `SurfaceHolder.Callback`，既有 `surfaceChanged` 才触发连接。其他播放器需追踪自己的 ready/start 条件，不能照搬回调名字。
4. 使用 `RENDERMODE_WHEN_DIRTY`。帧通知只置新帧标记并 `requestRender()`，不在通知线程调用 GL。不使用按 UI 每帧 PixelCopy、Bitmap.getPixels、CPU 视频缩放/重编码，也不并行保留旧复制循环。
5. GL 消费一次最新帧并读取 `getTransformMatrix()`，对两个眼区使用同一纹理和矩阵；完整源图分别显示在两眼，不是把源图左右切半。`updateTexImage` 可跳过中间排队帧，绘制帧计数不等于接收包数或全部解码帧数。
6. 清屏为黑色，再等比 fit。1280×720 → 左眼 `(0,60,640,360)`、右眼 `(640,60,640,360)`；在上述已确认640×480眼区上，4:3则每眼填满。处理输出尺寸、crop、旋转、SAR；Moonlight 本次实际为 720p、rotation=0、SAR=1:1，不把其他情况记作已验证。
7. 缓存 shader locations、顶点缓冲和 viewport 参数。尺寸变化时更新，避免每帧分配数组、查 uniform 或刷盘。每 10 秒帧统计即可；诊断错误检查的频率应显式控制。

最小帧循环（结构示例，不是可直接复制的完整 renderer）：

```java
// onFrameAvailable: framePending.set(true); requestRender();
if (framePending.getAndSet(false)) {
    surfaceTexture.updateTexImage();
    surfaceTexture.getTransformMatrix(producerMatrix);
    hasFrame = true;
}
clearBlack();
if (hasFrame) {
    bindExternalTextureAndMatrix(producerMatrix);
    drawFullSourceIn(leftEyeFitRect);
    drawFullSourceIn(rightEyeFitRect);
}
// GLSurfaceView performs one swap after onDrawFrame returns.
```

“绘制命令已记录”不等于 swap 成功、SurfaceFlinger 已呈现或光学同步。若需要定位 swap 边界，应在实际 EGL owner 中增加证据，而不是把首帧日志重命名为“显示成功”。

## 保留业务状态、输入目标和 Surface 所有权

- 原 SurfaceView 同时承担焦点、触摸、捕获、pre-IME 或坐标归一化时，不要简单隐藏后继续依赖它。Moonlight 改为可见单眼输入代理，保留 ID、布局、焦点、原 listener；视频在独立全宽 GLSurfaceView。先安装输入/UI 宿主，再创建依赖父容器的虚拟手柄；完成 decoder 能力检查和业务初始化后，才创建并接通视频输出；不要倒置 Surface 就绪与 decoder.configure 的依赖。
- 输入归一化以有效单眼视频区域为准，16:9 时为 640×360，含 letterbox 偏移。左右眼事件副本只转换一次。显示分辨率和请求远端码流分辨率分别管理。
- 格式变化经 decoder output-size callback 更新 viewport 和输入代理，不覆盖原后端通知；解绑时移除监听。界面鼠标模式不自动代表 DLNA/视频接收协议具有回传远端鼠标的能力。
- 画面帧率提示应设到真正呈现的输出 Surface，decoder 输入 Surface 的 producer 节流是另一件事。不要把面向屏幕的刷新设置都机械发给 OES producer。
- 资源按实例/代次绑定。迟到的 GL-ready 回调遇到 close、Activity finishing/destroyed 或代次变化就丢弃，避免旧 Surface 绑定新页面。

### 销毁与重建的两个实际坑

**旧播放器不一定支持换 Surface 后续播。** Moonlight 的 `attemptedConnection` 只允许一次启动，销毁后先 prepareForStop。示例选择 EGL/Surface 丢失后结束当前串流，下次进入重新连接，而不是对已停止 decoder 调新 `surfaceCreated` 假装恢复。迁到蓝莓时按各后端选择公开换 Surface、完整重建或明确退出；成功回调不能替代暂停/后台/重进测试。

**queueEvent 不保证退出前执行。** GLSurfaceView detach 会请求 GL 线程退出并等待；退出标志可先于事件队列被处理。只有 `queueEvent(release)` 随即 `super.onDetachedFromWindow()` 可能漏释放。可采用：

1. UI 线程标记 closed，拒绝新帧和迟到回调；通知原 decoder/播放器 surface 将消失，停止输入/定时任务。
2. 在所属 GL 线程解绑 listener、释放 Surface/SurfaceTexture、删除 texture/program，清理幂等。
3. 调用 superclass detach 等待 GL 线程结束后，再幂等释放仍存在的 Java Surface/SurfaceTexture 句柄。此时与 `updateTexImage` 不并发；剩余 GL 对象由 context 销毁回收。不要在任意线程补做 GL delete。
4. 区分“已通知 decoder 停止”与“异步 codec teardown 完毕”，检查后端真实 stop/release 顺序；不要在主线程 sleep 或等待依赖主线程回调的 GL 初始化。

## UI 单独处理：软件画布不是双眼动态更新保证

普通 UI 的候选路线是单棵单眼逻辑树绘制一次到透明 640×480 Bitmap，再左右复用；不把 SurfaceView 视频画入它，不做两套业务页面。Bitmap 只在尺寸改变时分配，清透明再绘制，旧 Bitmap 仍被 HWUI 引用时不立即 recycle。最终 Moonlight 还使用全宽宿主的软件 layer；这会增加完整物理层缓存，不能说 UI 只有一张 640×480 缓冲或零额外上传。

**硬件 Bitmap 会使软件 Canvas 崩溃。** 上游封面 `ImageDecoder` 默认 allocator 可能返回 `Bitmap.Config.HARDWARE`。在受影响的适配设备路径指定 `ALLOCATOR_SOFTWARE`，检查旧 BitmapFactory HARDWARE 分支与内存缓存；其他设备保留原路径。不能用“已启用软件 layer”推断所有资源都可软件绘制。

### 动态提示单眼：什么有效，什么没有证实

| 检查/尝试 | Moonlight 本次结果及含义 |
|---|---|
| 页面与视频左右截图 | 首页、设置、配对及视频可双眼显示，不代表后加提示也正确 |
| 动态 Toast 加到共享 UI 宿主 | 提示只出现在左眼，局部差异集中在提示矩形 |
| 扩大 `invalidateChildInParent` damage、`onDescendantInvalidated` 全量 invalidate | 没有单独修好动态提示；不能把 damage clipping 写成已定位根因 |
| 直接指定当前宿主并 invalidate 根窗口 | 仍单眼；不能简单归因于选错 Toast 窗口或系统 Toast 回退 |
| 软件层从半宽子容器移到全宽宿主 | 仍未单独修复；HWUI/cache/裁剪的具体原因没有闭环 |
| 在同一宿主 dispatchDraw 内直接按两眼坐标画文字 | 实机仍只左眼；“有两次 draw”不是验收 |
| 独立全宽 overlay，左右各一个无交互 TextView | 最终两眼提示实测成功；3 秒内第二组三击返回 AppView，Surface/EGL 释放路径日志完整，不等于长期无泄漏证明 |

小提示适合最后一条：两个纯显示控件共享一个文本/超时/所有者，放在同一物理全宽 root 上、位于视频之上。复制的是展示，业务副作用和返回动作仍只有一个；不拦触摸、不抢焦点，超时/销毁同时移除两眼，尺寸使用当前已核实眼区。不要复制两套播放器/Compose 副作用，不把提示级成功扩大成复杂菜单、字幕或交互控件全可用。

蓝莓可按信息量选择：静态 toast/loading 文案采用独立双眼展示；高频字幕可评估一次生成透明 UI 纹理并由同一 GL swap 合成两眼，但这是候选方案，本次未实测。复杂 Dialog/Popup 保留独立窗口适配及输入所有权，不能用视频成功掩盖它们。

## 日志必须覆盖“之前”和“之后”

| 阶段 | 需要记录的定位信息 |
|---|---|
| 进程与设备 | 构建/版本、型号、固件、API、ABI、boot ID、elapsed time、PID/线程、低内存状态 |
| EGL | context/window surface create 和 destroy 的 begin/end、返回值、eglGetError；GL vendor/renderer/version/extensions |
| shader/OES | compile/link 状态与错误、texture/Surface 代次、协商缓冲尺寸、真实输出眼区 |
| decoder | 名称、hardware/vendor 标志、configure/start begin/end、格式/crop/rotation/SAR、Surface validity、CodecException diagnosticInfo |
| UI | 首次 software draw begin/end、合成命令记录、宿主尺寸/窗口类型；不逐帧持久化 |
| 首帧/运行 | producer timestamp、首次纹理消费、首个双眼绘制提交、低频帧计数/时间 |
| 退出 | 用户退出/Surface 丢失/context 重建原因，停止通知、资源释放、post-join fallback 完成 |

应用私有日志轮换限额；初始化/销毁关键边界可同步刷盘，周期性能统计不要每帧 fsync。不要记录媒体内容、PIN、密码或账号。帧率/CPU 数据注明诊断开销。

启动测试前主机分段采集 logcat，短超时查询 boot ID/uptime；断连重现后比较同一设备，不自动重启/反复拉起 App。若 boot ID 改变，停止该复现链，保存 boot reason、可读取的 pstore/崩溃记录。应用日志尾部可能因内核重启丢失，最后到达阶段只收窄范围，不证明 GPU 或缺库根因。

## 性能怎么报告

Moonlight 初始桌面串流的观测：28–30 fps；CPU 37.4–39.0% **单核**；PSS 78,242 KiB（约76.4 MiB）、RSS 201,020 KiB；设备整体 `gpubusy` busy/total 约7.4–8.2%，GPU clock 权限拒绝；USB 供电时电池传感器35.5→37.5→38.0°C，Thermal Status=0但没有可读温度通道。这不是 A/B 性能提升证明，GPU 数字不是应用专属，电池温度不是镜腿表面温度。

这些采样发生在最终输入/提示层和全宽软件缓存调整**之前**，最终版未重新基准测试。迁移不能承诺蓝莓同样数值，也不能把早期测量套到后续渲染改变。

CPU 公式：`100 * Δ(utime+stime) / CLK_TCK / Δuptime`，取同 PID 的 `/proc/PID/stat`，确认 CLK_TCK；PID 改变则重开区间。标明单核计量，不直接当整机负载。gpubusy 先确认节点单位与读取重置语义，保留原始对，避免将每次区间值误作累加差值。帧率由同会话计数差/时间差算，不用 GL request 次数代替实际呈现帧数。

蓝莓迁移后用同协议、同发送端、同素材/动态程度、同码流、同帧率先预热再 A/B/A；同时覆盖无视频闲置、静态画面、动态视频、提示出现/消失。记录 CPU、内存、复制次数、GPU 可读计数及温度趋势。桌面静态画面和游戏/高动态镜像不可直接对比。

## 蓝莓投屏的迁移清单

1. 重新固定用户当前指定 APK/源码、包名/版本/哈希/签名与已接受旧包；先读现有适配和故障记录，旧版只作回滚。更新技能不是授权重装蓝莓或触发已知重启路径。
2. 分列 AirPlay 镜像、AirPlay 媒体、DLNA、RTSP 等实际入口及 decoder/player。已有5.2.35记录中 AirPlay OES 与 DLNA TextureView 属于不同路线，必须重新核实当前版本；不因 AirPlay 成功就删除 DLNA 回退。
3. 每入口找到业务真正使用的 output Surface 和创建/释放 owner。APK-only 使用原始指令精确匹配与调用结果验证，重新定位混淆符号，不搬 Moonlight 类名/偏移。
4. 优先先迁一个已查明入口：一个 decoder、一个 OES renderer、一个 frame callback。撤掉该入口的旧视频复制循环；失败时明确切回原显示并移除遮挡层，不允许两条高成本路径长期同时运行。
5. 先验证带边框/文字/方向标记的横屏、竖屏视频及实际协议会话，再验证字幕、加载、错误、退出提示。首帧、提示出现、更新、消失、第二次显示分别拆眼截图，不能只对静态首页做一次 diff。
6. 小提示先尝试已实测的独立双眼纯展示层；复杂控件保留原业务和单次输入。一般软件镜像动态刷新缺陷没有通用修复，不复制 Moonlight 宿主后宣称全覆盖。
7. 验证后台/重进、连接中取消、断开、尺寸变化、旧回调延迟到达、GL detach。AirPlay/DLNA 的暂停/seek/结束语义分别接回原播放器。
8. 默认保留技能的焦点模式与该项目既定手势。Moonlight 的远程鼠标、单指左键/双指右键和两轮三击是用户对串流主机控制的选择；蓝莓接收视频是否支持反向控制要查协议，不自动迁移这些交互。
9. 完成最终渲染版性能复测后再交付，列已验证入口与待验入口。发生重启按设备缺陷流程取证并停止重复触发，不为了“CPU/缺库猜想”循环试危险路径。

## 可追踪实现与现场材料

参考仓库：`WayneShao/moonlight-android`，`rayneo/x3-pro`，提交 `d01a5a8b`，版本12.2-rayneo.2。本地根 `D:/Backup/Desktop/moonlight-android`；这些是源码线索，不是执行技能的必需依赖。

- `app/src/main/java/com/limelight/ui/rayneo/StereoVideoView.java`：OES、两眼 viewport、EGL 日志、退出与 post-join 释放。
- `DecoderSurface.java`、`Game.java`：单输出桥接、可见输入代理、原业务生命周期。
- `binding/video/MediaCodecDecoderRenderer.java`：真实 configure/start、output crop 尺寸与诊断。
- `grid/assets/DiskAssetLoader.java`：设备限定的软件 Bitmap allocator。
- `StereoHost.java`、`StereoToast.java`：候选软件 UI 与独立退出提示；普通动态 Toast 缺陷仍需处理，禁止作为完整通用成品照搬。
- `RayNeoTrace.java`、`scripts/capture_rayneo.py`：轮换日志、boot ID/uptime、有限主机捕获。
- `docs/DEVICE-VALIDATION-20260917.md` 与本地忽略目录 `out/device-20260917/`：性能原始数据、失败尝试截图、最终 `exit-overlay-final.png`。原始截图包含桌面内容，不随技能复制/公开。

本参考包含关键机制与边界；BlueCast 迁移须以其当前调用链和最终构建证据为准。
