# 高性能视频路径：单解码器、OES 与双目输出

需要具体 OES 接入/销毁、硬件 Bitmap 与软件 Canvas、动态提示单眼故障、现场日志，或把 Moonlight 经验迁到蓝莓时，继续读 [OES 双眼视频实战与蓝莓迁移](oes-stereo-field-guide.md)。其中明确区分实测成功、失败尝试和最终版本未复测项。

适用于维护 RayNeo 视频/投屏 App 的发热、重复复制、清晰度和分辨率上限问题。myDV、BBLL 等其他 App 可以采用同一架构，但其后端、Surface 所有权、交互和字幕必须在各自工程确认。不要因为都叫“播放器”就复制蓝莓的混淆字段、Activity 名或补丁偏移。

## 先列实际播放入口

从当前前台 Activity 和运行日志倒查播放器实例、输出 Surface、界面镜像器。一个 APK 可同时有 Media3/ExoPlayer、MediaPlayer、IJK、Cicada、RTSP、原生解码和 USB/专用串流。

AirPlay 屏幕镜像、AirPlay 媒体投送、DLNA 媒体、RTSP 不是一个接入点。建立“入口 → 后端 → 解码输出 → 最终渲染 → 手势 → 释放”的表；逐条记录源码依据、已构建、组件测试、真实会话测试。修好一个入口不代表其他入口已覆盖。

重点查已有适配是否把 TextureView 视频当普通 UI：每个视频帧都会让窗口变化，所谓“按 UI 变化复制”仍可能以视频帧率整页 PixelCopy，随后 getPixels/Canvas/upload 再走一遍。硬件解码存在并不保证后续复制便宜。

## 分开四种尺寸

| 尺寸 | 应如何处理 |
|---|---|
| 对外接收能力/媒体轨道 | 保持协议发现和来源兼容；播放器有合适自适应轨道时可限制 viewport/选轨 |
| 码流/解码缓冲区 | 符合实际编码尺寸、crop、旋转和像素比例；不能改一个数字就宣称解码成480p |
| 单眼逻辑输出 | X3 Pro 已确认640x480；GPU对完整源等比fit，黑边居中 |
| 双目物理窗口 | X3 Pro 已确认1280x480；同一帧分别画到两个640x480 viewport |

高分辨率输入可直接由 GPU 采样到小 viewport。不要为眼镜的小输出额外引入 CPU 软件解码、缩放再编码，或先转码成480p再播放；这可能把省下的绘制成本转移到 CPU。

SurfaceTexture 的 defaultBufferSize 是默认值；视频 producer 可按码流覆盖它，不能把它当作硬件解码降分辨率的证明。原始码流较大时仍有解码/带宽成本，零CPU位图复制不等于零解码成本或必然不发热。

回退复制位图也应按实际眼区上限缩放，示例：scale=min(1,640/sourceW,480/sourceH)，再取有效整数尺寸；810x1080 → 360x480，1920x1080 → 640x360。不先放大源位图再缩回眼区，也不把竖屏强拉成640x480。此上限属于已确认X3 Pro，不强制其他设备沿用。

## 优先接到现有解码输出

目标数据流：单个硬件解码器 → Surface(SurfaceTexture/OES) → GL获取一次最新帧及transform matrix → 同一纹理、同一矩阵、同一次提交内绘制双眼。

- Media3/ExoPlayer：使用播放器公开的 Surface 接口。若 PlayerView 仍负责字幕和控件，可让它不持有视频 Surface，再由独立 renderer 接视频；仅改XML的surface_type并不完成接入。处理播放器与 GL Surface 两者先后就绪，以及 PlayerView.setPlayer/重建后重新绑定。
- MediaCodec：在真实 configure 的 Surface 参数处接入，验证返回/赋值确实被消费。Smali 的 invoke-static 返回 Surface 后必须有 move-result-object 并实际传进 configure/构造函数；只搜到函数名不能判定成功。
- MediaPlayer/IJK/Cicada 等：核实该版本的公开 setSurface/setDisplay 和视频尺寸通知，保留原业务 listener，不用一个新的 listener 覆盖原来的结束/错误/尺寸回调。
- 专有 native 后端：先查实际 Surface/JNI/ANativeWindow 接口；不因某个 Java View 名字相同就断言能接管。未确认接口的入口明确留待处理，保留已工作回退。

使用实例/Activity/播放器所有者关联 Surface。避免一个进程级static Surface让不同播放器串用；避免第二套临时OES与旧PixelCopy同时覆盖全屏。准备超时在工作线程有界等待或用就绪回调，别在 Activity.onCreate 睡眠等GL：它依赖主线程完成窗口挂载。接管失败若保留原Surface，后装的空OES层也必须切换到回退或移走，防止黑层盖住原视频。

## 视频和UI分开，交互仍走原业务

视频进OES后，字幕、loading、播放进度、菜单和双击退出不能消失。

一种候选结构是保留surface-less PlayerView负责字幕/控制，用透明640x480 UI纹理只在UI变化时更新，再由同一GL提交覆盖到双眼。去掉不必要的黑色shutter，保证UI层透明。软件捕获普通View只绘制一份；绝不对同一child重复drawChild两次。含硬件专用View/独立Dialog/Popup的UI不能假定软件draw完整。

这不是一项不经测试就可替换所有App的成品：检查截图中的字幕/controls、左眼与右眼相同、UI无变化时无持续复制、动画UI有合理更新上限。GLSurfaceView的Z顺序/透明度必须实测，避免全屏顶层视频遮住菜单，或上层黑shutter遮住视频。

保留用户指定的手势光标/焦点模式；不要趁渲染改造切换成系统鼠标。暂停、seek、双击退出调用当前后端的原业务动作。镜像/直播没有媒体seek语义时不要调用文件播放器接口。左右触摸板精确设备名及各窗口分发原则见input.md，合成输入通过不替代物理触摸板验收。

## 生命周期与调度

- 按新帧requestRender；空闲不自旋。帧率上限是绘制/消费策略，不保证decoder也降低帧率。选择适合目标设备与内容的上限并测量，不能只宣称“从60降到30所以不热”。
- 更新视频尺寸用后端format/video-size事件，并读取实际crop/旋转/像素比例。避免每帧反射查询播放器或只在首帧读一次后永不更新。
- 同一帧updateTexImage一次，变换矩阵应用一致；输出左右viewport而不是左右各采半张源图。
- GL对象属于EGL context。不能让多个renderer共用一组无隔离的native static texture/program ID。可用每实例native handle；一GL线程绑定一renderer且生命周期确认时也可按线程隔离。销毁在所属GL线程执行。
- 退出解绑播放器listener与surface，解除UI观察者，取消延迟任务；旧页面释放不能清掉新页面surface。迟到PixelCopy callback不能复活循环，目标Bitmap在回调结束前不得recycle。
- 回退最多一个请求在途；复用位图和纹理，像素仅在新帧到达时上传。记录尺寸/切换/错误和周期统计，不逐帧刷多个日志。

## 自动验证，逐层报告

1. 从同一页横竖截图在电脑生成短H.264样本，或使用可重复的本地媒体。电脑生成测试片不等于在眼镜上引入转码。播放测试必须走待测的生产组件/实际Activity，标明是否包含真实网络发送端。
2. 确认硬件decoder名称/类型、实际选中的Surface/OES首帧、没有视频CPU Bitmap/int数组及没有并行Window-copy视频路径。固定“installed”日志可过时，优先检查实际decoder绑定日志和对象状态。
3. 截图1280x480拆眼比较，检查同帧像素差、方向、黑边、裁剪与字幕控件。截图差为0不单独证明光学双眼时序或佩戴舒适度。
4. 真正回归同一Activity里的暂停/继续、seek、double-tap返回首页、进后台/恢复、旋转、播放器重建和解码器释放。不要以渲染小样通过替代完整协议、页面交互验收。
5. CPU用同PID的/proc/PID/stat utime+stime差值，确认CLK_TCK，结合/proc/uptime差值计算。单核百分比可超过100%，不是整机总利用率。相同素材、时长、码流、播放/绘制节奏预热后做A/B/A；启动/JIT、静态内容与动态视频不能直接比较。
6. 温度记录来源、两路原始读数、时间趋势和佩戴反馈，不把Thermal Status 0当作镜腿不热。设备光机传感器不自动等同接触皮肤的温度。获准热异常时自动停止的会话，先短日志快照再及时force-stop并复查PID，不长时间继续重型观测。
7. 每条ADB查询有独立超时与原始输出。捕获exec的session_id继续poll；“本次未返回”不能直接写成设备冻结。失败先区分工具/主机传输/设备省电/服务响应，别在多个会话共用设备时全局kill-server或反复启动App。

## 已验证来源与可迁移边界（2026-09-12）

BlueCast 5.2.35/X3 Pro 的AirPlay镜像：原来810x1080被放大复制为1215x1620，后改硬件decoder→OES→1280x480；真实DisplayActivity日志确认OES接管和首帧。短实际会话CPU约7.5%单核，只适用于当时内容。组件预热同源OES约24%–32%，有界PixelCopy约47%，各约2.2秒；不能外推其他App或长时热收益。

同APK的DLNA Media3当时仍走TextureView+Window复制，短CPU样本约103%单核且设备温度读数上升，说明入口必须分别适配。Media3透明UI拆分在该记录时仍在测试，不能标成已验收模板。myDV/BBLL在本次没有实际修改或性能验收；接手会话应在各自当前代码、设备状态、渲染UI与输入上验证。

本地蓝莓工程中EyeRenderSize、AirplayOutput、DualEyeRenderer和渲染Instrumentation可作为实现线索；它们含App专用字段与类名，不能整套不审查搬入其他工程。本参考已包含独立执行所需决策要点，无需蓝莓目录存在。

官方接口参考：[MediaCodec](https://developer.android.com/reference/android/media/MediaCodec)、[SurfaceTexture](https://developer.android.com/reference/android/graphics/SurfaceTexture)、[Media3 ExoPlayer](https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer)、[PlayerView](https://developer.android.com/reference/androidx/media3/ui/PlayerView)。采用前核对目标App实际依赖版本和可用方法。
