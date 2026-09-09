# RayNeo X3 Pro 眼镜 App 开发 / 移植总指南

> 目标：这份文档不是宣传材料，也不是泛泛而谈的 AR 设计规范。
> 它是给 **AI Agent / 工程师直接开工** 用的执行指南。
>
> 适用场景：
> 1. 从零开发一个 RayNeo X3 Pro 应用
> 2. 把现有 Android 应用改造成 RayNeo X3 Pro 版本
> 3. 尤其适合：播放器、投屏接收器、状态面板、轻工具、AI 助手

---

## 0. 速查导引

如果你是未来接手这个项目的 Agent / 工程师，建议按下面顺序读：

1. **先看 `## 1` 和 `## 2`**：先建立 RayNeo X3 Pro 适配的总原则和这次项目已经验证过的结论
2. **如果你在做输入适配，看 `## 8`、`## 23`、`## 24`**：分别对应输入分层说明、输入映射查表、页面/状态/权限对照表
3. **如果你在做播放器 / 接收器 / 镜像，看 `## 7`、`## 12`、`## 13`、`## 14`**：分别对应显示链路、协议兼容、会话抢占、线程边界
4. **如果你在做旧应用移植，看 `## 17`**：这是最接近实际落地顺序的 SOP
5. **如果你要把工作继续交给下一个 Agent，看 `## 21`、`## 22`**：一个是开工 prompt，一个是交接输入模板
6. **如果你在评估 BBLL / BBLL_Mod 经验怎么吸收，看 `## 25`**：只学输入层和交互组织，不替换当前项目已验证的双目主链路

如果只想快速抓当前文档里最容易直接复用的部分：

- 输入方案：`## 8` + `## 23`
- 播放器 / 接收器方案：`## 7` + `## 12` + `## 13`
- 旧应用移植 SOP：`## 17`
- AI Agent 可直接复用的 prompt：`## 21` + `## 22`
- BBLL 取舍判断：`## 25`
- **黑盒 APK Smali 改造（无源码适配）**：`## 2.5`
- **AirPlay Mirror 特有坑（流分辨率、libairplay.so 约束）**：`## 12.5`

---

## 1. 先给结论：RayNeo X3 Pro 适配的正确思路

如果没有额外要求，默认按下面这条工程指令执行：

> **保留业务内核，重做显示层和交互层；优先做 Android 原生、0DoF、低密度、低功耗、少交互、双目稳定同显的版本。**

不要默认做这些事：

1. 不要把手机 UI 直接缩小搬上去
2. 不要把 TV 式复杂入口矩阵搬上去
3. 不要一上来就做 3DoF / Unity / ARDK
4. 不要把关键业务绑死在某个物理手势上
5. 不要让 Activity 生命周期决定后台核心能力是否存活
6. 不要为了“看起来像眼镜 App”而重写整套业务核心

RayNeo X3 Pro 上真正应该优先解决的是：

1. **双目显示是否舒服、稳定、方向正确**
2. **输入路径是否足够少、足够稳**
3. **前台页面退出后，后台任务是否还活着**
4. **播放器 / 镜像 / 网络发现 / 前后台切换是否稳定**
5. **提示信息是否能被用户真正看到**

---

## 2. 本项目已经验证过的核心经验

这部分不是理论，而是这次实际适配 RayNeo X3 Pro 的结果。

### 2.1 显示层的核心结论

1. 单眼逻辑画布可以按 **640x480** 理解
2. 双目视频不要做双解码；应该是：
   - **单播放器**
   - **单 Surface / 单 SurfaceTexture**
   - **左右眼两个 viewport 复用同一纹理**
3. RayNeo X3 Pro 的实际显示方向在本项目里需要：
   - **旋转 180°**
   - **再做水平镜像**
4. 视频显示模式至少准备三种：
   - `Fit`：按比例完整显示，允许一组黑边
   - `Zoom`：按比例填满，允许裁剪
   - `Stretch`：强行拉伸占满
5. 不能只相信静态分辨率；真实画面方向还要结合 `SurfaceTexture` 的 `textureMatrix` 判断是否发生了 90° 旋转
6. **AirPlay Mirror 的实际流分辨率是动态的**，横竖屏切换时会变化：
   - libairplay.so 通过 `FakeSurfaceHolder.setFixedSize()` 通知初始尺寸（通常是 640×480）
   - 真实内容分辨率通过 `air.video.size.change` LocalBroadcast 获取（DMRCenter 发送，携带 "WxH" 字符串）
   - 渲染器必须监听该广播并动态更新视口计算，否则横竖屏切换后画面比例会出错
7. **libairplay.so 的 surfaceChanged 尺寸必须和实际 Surface 匹配**：
   - 向 libairplay.so 报告错误的 Surface 尺寸（例如 1280×720 而实际是 640×480）会导致严重卡顿
   - 原因：libairplay.so 内部尝试按更高分辨率编解码，超出实际 Surface 能力

对应实现可参考：

- `android-studio-project/ExoPlayer-AirPlay-Receiver/src/main/java/com/github/warren_bank/exoplayer_airplay_receiver/ui/rayneo/RayNeoStereoVideoView.java`
- `android-studio-project/ExoPlayer-AirPlay-Receiver/src/main/res/layout/activity_video.xml`
- BlueBerry 适配：`base_decompiled/smali/com/rayneo/helper/DualEyeRenderer.smali`（`onVideoSizeChanged` + `notifyAirPlayVideoSize`）

### 2.2 输入层的核心结论

1. RayNeo 镜腿触控不要通过悬浮层硬接管
2. 更稳的做法是：
   - 在 Activity 中接 `dispatchTouchEvent()`
   - 同时接 `dispatchGenericMotionEvent()`
   - 只分析来自镜腿触控板设备的 `MotionEvent`
3. 触控板设备名可通过 `cyttsp` / `capsense` 识别
4. 左右镜腿事件不要一开始都吃掉
5. 在这个项目里，**右镜腿用于应用自定义手势**，**左镜腿尽量留给系统媒体 / 音量语义**
6. 如果当前场景不安全，不要强行响应手势；要有 gating 逻辑
7. **Mercury 系统会拦截所有镜腿触控 (cyttsp5_mt) 事件**，app 层面在默认情况下收不到任何触控事件：
   - 这是 RayNeo X3 Pro 上"触控板不响应"的根本原因，不是 app 代码问题
   - Mercury 将这些事件优先用于系统手势（音量、返回等）
   - 已知规避方向（未完全确认）：在 Activity 内添加特定 view（`leftMargin=640` 的右眼区域 View），可能触发 Mercury 将事件转发给 app
   - 如果功能可以降级：优先把退出/返回等关键操作绑定到双击手势（在 `dispatchTouchEvent` 内基于时间差判断），而不要依赖单次触控精确坐标

对应实现可参考：

- `android-studio-project/ExoPlayer-AirPlay-Receiver/src/main/java/com/github/warren_bank/exoplayer_airplay_receiver/ui/rayneo/RayNeoTempleInput.java`
- `android-studio-project/ExoPlayer-AirPlay-Receiver/src/main/java/com/github/warren_bank/exoplayer_airplay_receiver/ui/rayneo/RayNeoMediaControlCoordinator.java`
- `android-studio-project/ExoPlayer-AirPlay-Receiver/src/main/java/com/github/warren_bank/exoplayer_airplay_receiver/ui/rayneo/RayNeoMediaControlMapper.java`
- `android-studio-project/ExoPlayer-AirPlay-Receiver/src/main/java/com/github/warren_bank/exoplayer_airplay_receiver/ui/rayneo/RayNeoMediaControlGate.java`

### 2.3 生命周期的核心结论

这类眼镜应用最容易被忽略的问题，不是“能不能播”，而是“**切换页面 / 切下一条视频 / 抢占后回来时会不会死**”。

本项目里出现过的关键问题：

- `VideoPlayerActivity` 是 `singleTask`
- 切新视频时，新 Activity 实例先创建了新的 Surface
- 旧 Activity 很快进入 `onStop()`
- 旧 Activity 又把 **新的 Surface / listener 清掉**
- 最终现象是：
  - 播放器状态 READY
  - 但没有画面
  - 或画面比例异常
  - 或直接“像卡死了一样”

最终正确结论：

> 在 `onStop()` 里，只能清理“仍然属于自己”的 Surface / listener，不能无脑清空全局播放器当前引用。

对应实现可参考：

- `android-studio-project/ExoPlayer-AirPlay-Receiver/src/main/java/com/github/warren_bank/exoplayer_airplay_receiver/exoplayer2/VideoActivity.java`
- `android-studio-project/ExoPlayer-AirPlay-Receiver/src/main/java/com/github/warren_bank/exoplayer_airplay_receiver/exoplayer2/PlayerManager.java`

### 2.4 提示层的核心结论

RayNeo 上系统 Toast 不可靠，真实问题包括：

1. Toast 可能被系统杀掉
2. Toast 可能显示在双眼中间，不在单眼可读区
3. 即使代码调用成功，用户也可能实际上看不到

所以这类设备上的提示，不要依赖系统 Toast。

更可靠的做法：

1. 在当前 Activity 布局里准备 **左右眼各一个 TextView**
2. 注册给一个统一的 `ToastUtils`
3. 提示时同时更新左右两个 TextView
4. 一段时间后自动隐藏

对应实现可参考：

- `android-studio-project/ExoPlayer-AirPlay-Receiver/src/main/java/com/github/warren_bank/exoplayer_airplay_receiver/utils/ToastUtils.java`
- `android-studio-project/ExoPlayer-AirPlay-Receiver/src/main/res/layout/activity_video.xml`
- `android-studio-project/ExoPlayer-AirPlay-Receiver/src/main/java/com/github/warren_bank/exoplayer_airplay_receiver/ui/RayNeoHomeActivity.java`

### 2.5 黑盒 APK Smali 改造的核心结论

当源码不可得、只能通过 apktool 反编译改 smali 时，这个项目（BlueBerry 投屏适配）积累了以下结论：

**关于渲染模式：**

1. 投屏接收器类 app 通常有多条渲染路径，必须分别处理：
   - **Mode A (SurfaceTexture/OES)**：AirPlay Mirror → libairplay.so 直接渲染到 SurfaceTexture，由 GLSurfaceView 读取纹理并绘制双目
   - **Mode B (PixelCopy/Bitmap)**：Android 投屏 / DLNA → PixelCopy 抓取 SurfaceView 帧，以 Bitmap 形式交给 GL shader 绘制双目
2. 颜色问题（如蓝红互换）通常是 shader 里 swizzle 顺序错误（`.rgba` → `.bgra`）
3. 坐标轴问题（上下翻转）通常是纹理坐标系和 OpenGL 坐标系方向不一致

**关于 APK 重打包风险：**

1. `apktool b` 全量重建会重新打包 resources，可能触发 native .so 崩溃（本项目：libairplay.so SIGABRT）
2. 更安全的做法：
   - 用 `apktool b` 只获取重新编译的 `classes.dex`
   - 保留原 APK 的其他内容（resources、classes2.dex 如未改动）
   - 用 zip 工具替换 dex 和 .so 后重签名
3. 如果改动了 smali 中的字段数量、方法签名，注意同步修改所有引用该类的 smali 文件

**关于构建流程（本项目）：**

```
# 1. 编译 smali → dex（不动 resources）
java -jar ~/tools/apktool.jar b base_decompiled -o rebuilt_for_dex.apk

# 2. 签名
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
  --key-pass pass:android --v1-signing-enabled true --v2-signing-enabled true \
  --out 蓝莓投屏_3.8.51_X3Pro.apk rebuilt_for_dex.apk

# 3. 覆盖安装
adb install -r 蓝莓投屏_3.8.51_X3Pro.apk
```

**关于静态引用的注意事项：**

1. 如果需要在不同类之间传递渲染器引用，可以用静态字段（如 `sAirPlayInstance`）——但要注意生命周期，Activity 销毁时应清空
2. 新增的静态方法可以作为跨类调用的桥接点（避免修改调用链上每一个 smali 文件）

---

## 3. 什么时候适合做 RayNeo X3 Pro 版本

优先适合的应用类型：

1. 播放器
2. 投屏接收器 / 镜像接收器
3. 状态页 / 等待页 / 配对页
4. 轻量工具
5. AI 助手 / 翻译 / 提词 / 导航辅助
6. 图片查看 / 简单阅读辅助

不适合直接搬运的类型：

1. 强依赖复杂触摸交互的手机应用
2. 高密度列表浏览型应用
3. 大量文本输入型应用
4. 多层菜单深钻型应用
5. 复杂表单型应用
6. 社交流 / 电商流 / 信息流原样搬运

如果原应用不适合直接搬运，正确策略是：

> 做“眼镜版子集”，不是做“手机版缩放版”。

---

## 4. 默认工程架构

RayNeo X3 Pro 项目更适合下面这种结构：

1. `HomeActivity`
   - 只负责首页状态显示
   - 权限申请兜底
   - 自动拉起后台能力
2. `Foreground Service`
   - 长时间驻留
   - 网络监听
   - 协议注册
   - 持续任务
3. `Manager / Controller`
   - 真正的业务状态机
   - 播放控制
   - 镜像控制
   - 会话仲裁
4. `UiState`
   - 把后台状态投影成前台可显示状态
5. `Renderer / StereoView`
   - 双目渲染
   - 状态 HUD
   - in-app toast

### 4.1 不要把业务核心写死在 Activity

如果以下情况同时出现，先重构再谈眼镜适配：

1. Activity 里同时做 UI、网络、状态机、播放器控制
2. Fragment / View 直接驱动核心协议逻辑
3. 页面退出后业务就停止
4. 生命周期回调里充满关键业务分支

### 4.2 首页的职责应该非常少

一个合格的 RayNeo 首页，通常只做：

1. 显示设备名 / 服务名
2. 显示当前网络状态
3. 显示是否 ready
4. 显示投屏地址 / 配对码 / 错误信息
5. 自动启动后台服务

不要在首页堆这些东西：

1. 多层菜单
2. 设置大集合
3. 信息流
4. 花哨控制面板
5. 大段说明文字

本项目首页可参考：

- `android-studio-project/ExoPlayer-AirPlay-Receiver/src/main/java/com/github/warren_bank/exoplayer_airplay_receiver/ui/RayNeoHomeActivity.java`

---

## 5. Android Manifest 基线

RayNeo 项目建议至少具备以下基线：

### 5.1 添加 Mercury 应用标记

```xml
<meta-data
    android:name="com.rayneo.mercury.app"
    android:value="true" />
```

本项目对应位置：

- `android-studio-project/ExoPlayer-AirPlay-Receiver/src/main/AndroidManifest.xml:53`

### 5.2 首页 Activity

建议：

1. `MAIN + LAUNCHER`
2. `singleTask`
3. 全屏主题
4. 尽量只做状态壳

本项目参考：

- `AndroidManifest.xml:123`

### 5.3 播放器 / 镜像 Activity

建议：

1. `singleTask`
2. 全屏
3. 对于视频页可固定 `landscape`
4. 允许 Service 主导拉起

本项目参考：

- `AndroidManifest.xml:107`
- `AndroidManifest.xml:143`

### 5.4 长驻能力放前台服务

对于投屏接收、网络监听、镜像接收这类应用，应使用前台服务承载核心能力。

本项目参考：

- `AndroidManifest.xml:65`

---

## 6. 双目显示设计规则

### 6.1 单眼逻辑画布

默认按单眼 `640x480` 思考布局。

这不是说必须每个布局都写死 px，而是说：

1. 你面对的是非常有限的可读区域
2. 不要用手机“空间很大”的心智模型设计 UI
3. 大部分重要信息应该集中在单眼中心区

### 6.2 安全边距

推荐默认安全边距：

1. 左右：20~30dp
2. 上下：16~24dp

原因：

1. 贴边内容双目可读性差
2. 边缘区域更容易不舒服
3. 文字和轻提示不适合贴到极限边界

### 6.3 颜色与亮度

默认策略：

1. 背景优先纯黑或近黑
2. 少用大面积纯白
3. 红色只用于强告警
4. 不要做持续高亮背景
5. 少动画，少闪烁

### 6.4 文本密度

默认要求：

1. 不要做手机式高密度排版
2. 重要文本要大
3. 长句拆短句
4. 一屏只讲一件事
5. 状态优先于解释

---

## 7. 视频 / 镜像显示链路的正确做法

### 7.1 正确方案：单解码，双 viewport

正确结构：

1. 一个播放器 / 一个解码器
2. 一个视频输出 Surface
3. 一个 `SurfaceTexture`
4. 左右眼只是同一纹理的两次绘制

本项目的实现思路：

1. `RayNeoStereoVideoView` 内部维护 `SurfaceTexture`
2. 对外暴露 `Surface`
3. `PlayerManager` 把视频输出打到这个 Surface
4. OpenGL 每帧把同一纹理画到左眼和右眼两个 viewport

参考：

- `RayNeoStereoVideoView.java`
- `VideoActivity.java`

### 7.2 必备显示模式

建议至少支持三种：

1. `Fit`
   - 完整显示
   - 允许黑边
2. `Zoom`
   - 尽量铺满
   - 允许裁剪
3. `Stretch`
   - 完全占满
   - 允许变形

本项目中：

- `RayNeoStereoVideoView.cycleScaleMode()` 实现三种模式轮转
- `VideoActivity` 中通过手势触发模式切换

### 7.3 不要只信 SPS 宽高比

有些视频流或镜像流在旋转后：

1. SPS 分辨率变了
2. `textureMatrix` 也可能反映出旋转
3. 只看一边容易判断错

本项目最终经验：

> 宽高比要结合 `textureMatrix` 和视频尺寸一起判断，不能只靠静态分辨率。

### 7.4 切流 / 切视频时一定要防 Surface 竞态

这是播放器类最容易踩的坑之一：

1. 新页面已准备好新 Surface
2. 旧页面在 `onStop()` 里把全局 Surface 清掉
3. 看起来像播放器没问题，但画面没了

必须做到：

1. 只清理“当前实例仍拥有”的资源
2. 不要在旧实例里盲目 `setVideoOutputSurface(null)`
3. listener 也一样，不能无脑置空

---

## 8. 触摸板、按键、手势映射：必须写清楚的部分

这部分是这份文档最重要的补充之一。

很多 Agent 做 RayNeo 适配时容易犯两个错误：

1. 把“物理镜腿动作”直接当成“固定业务语义”
2. 左右镜腿全部接管，结果和系统输入打架

正确做法是把输入分成三层理解：

1. **原始输入层**：真实的 `MotionEvent` / `KeyEvent`
2. **抽象语义层**：单击、双击、前滑、后滑、双指等
3. **业务动作层**：播放暂停、音量、seek、退出、模式切换

### 8.1 本项目里，触摸板原始输入是怎么识别的

当前实现不是靠悬浮层，而是靠 Activity 分发：

1. `dispatchTouchEvent()`
2. `dispatchGenericMotionEvent()`

然后只处理这些设备：

- 设备名包含 `cyttsp`
- 或设备名包含 `capsense`

参考：

- `RayNeoTempleInput.java:62`
- `RayNeoTempleInput.java:70`
- `RayNeoTempleInput.java:77`

### 8.2 左右镜腿在当前项目里的判断方式

当前工程里的经验性策略：

1. 第一次见到的 temple touch device，先假设为 **右镜腿**
2. 第二个不同 deviceId，假设为 **左镜腿**
3. 运行时通过日志校准

代码参考：

- `RayNeoTempleInput.java:84`
- `RayNeoTempleInput.java:96`
- `RayNeoTempleInput.java:102`

这不是“宇宙真理”，但在真实设备适配初期是一个**低侵入、能工作、便于记录日志修正**的方法。

### 8.3 当前项目里，为什么默认不消费左镜腿 MotionEvent

当前实现明确写了：

> 左镜腿事件默认不消费，让系统处理。

原因：

1. 左镜腿在 RayNeo 生态里天然更接近系统媒体 / 音量 / 语音语义
2. 全部拦截左镜腿，很容易把系统级体验拦坏
3. 对媒体类应用来说，左镜腿更适合作为“系统保底输入”，右镜腿才适合作为“应用增强输入”

对应代码：

- `RayNeoTempleInput.java:64`
- `RayNeoTempleInput.java:65`
- `RayNeoTempleInput.java:72`

### 8.4 当前项目里，右镜腿 MotionEvent 手势语义

当前 `RayNeoTempleInput` 支持的原始手势包括：

1. 单击
2. 双击
3. 单指滑动
4. 双指点按

参考：

- `RayNeoTempleInput.java:138`
- `RayNeoTempleInput.java:141`
- `RayNeoTempleInput.java:164`

### 8.5 当前项目里，视频播放页的右镜腿映射

在 `VideoActivity` 里，右镜腿直接对应的业务语义是：

1. **单击** → 播放 / 暂停
2. **双击** → 返回 / 退出播放器
3. **右滑** → 快进 `+15s`
4. **左滑** → 后退 `-15s`
5. **上滑** → 音量加
6. **下滑** → 音量减
7. **双指点按** → 切换显示模式（Fit / Zoom / Stretch）

对应代码位置：

- `VideoActivity.java:112`
- `VideoActivity.java:116`
- `VideoActivity.java:119`
- `VideoActivity.java:123`
- `VideoActivity.java:126`
- `VideoActivity.java:129`
- `VideoActivity.java:132`

### 8.6 一个非常重要的例外：DLNA 会话下禁用 seek 手势

这不是“随便定的交互规则”，而是**真实兼容性结论**。

在本项目中，某些 DLNA 发送端（例如抖音类场景）会在接收端突然修改进度后认为状态异常，然后主动断开会话。

因此当前项目采取的策略是：

1. DLNA 会话下，右滑 / 左滑的 seek 手势禁用
2. 仍然保留暂停 / 音量这类较安全控制

对应代码：

- `VideoActivity.java:105`
- `VideoActivity.java:120`
- `VideoActivity.java:123`

这条经验很重要：

> **眼镜端“能控制”不等于“应该控制”。协议兼容性比手势丰富度更重要。**

### 8.7 首页的手势应该比播放器更少

本项目首页 `RayNeoHomeActivity` 的策略非常保守：

1. 单击：无动作
2. 滑动：无动作
3. 双指：无动作
4. **仅保留右镜腿双击退出**

参考：

- `RayNeoHomeActivity.java:58`
- `RayNeoHomeActivity.java:60`

这是正确方向：

> 首页是状态壳，不是控制中心。不要为了“支持更多操作”把首页变复杂。

### 8.8 当前项目里还有一条“按键映射链路”

除了 `MotionEvent` 手势链路，本项目还保留了一条 **KeyEvent 语义链路**。

这条链路的价值是：

1. 有些设备 / 系统层会把镜腿动作翻译成标准媒体键
2. 用标准 `KeyEvent` 适配时，和现有播放器控制系统更容易复用
3. 更适合做“系统媒体语义”对接

本项目中，`RayNeoMediaControlCoordinator` 里做的映射是：

#### 左镜腿抽象语义

1. `KEYCODE_MEDIA_PLAY_PAUSE` → `LEFT_DOUBLE_TAP`
2. `KEYCODE_VOLUME_UP` → `LEFT_SWIPE_FORWARD`
3. `KEYCODE_VOLUME_DOWN` → `LEFT_SWIPE_BACKWARD`

#### 右镜腿抽象语义

1. `KEYCODE_MEDIA_FAST_FORWARD` → `RIGHT_SWIPE_FORWARD`
2. `KEYCODE_MEDIA_REWIND` → `RIGHT_SWIPE_BACKWARD`

参考：

- `RayNeoMediaControlCoordinator.java:105`

### 8.9 从抽象输入到业务动作的映射

当前 `RayNeoMediaControlMapper` 的业务映射如下：

1. `LEFT_DOUBLE_TAP` → `PLAY_PAUSE`
2. `LEFT_SWIPE_FORWARD` → `VOLUME_UP`
3. `LEFT_SWIPE_BACKWARD` → `VOLUME_DOWN`
4. `RIGHT_SWIPE_FORWARD` → `SEEK_FORWARD_15S`
5. `RIGHT_SWIPE_BACKWARD` → `SEEK_BACK_15S`

当前未启用或保留占位的包括：

1. `RIGHT_SINGLE_TAP`
2. `RIGHT_DOUBLE_TAP`
3. `TWO_FINGER_GESTURE`
4. `APPLE_WATCH_INPUT`
5. `UNPROVEN_CAPABILITY`

参考：

- `RayNeoMediaControlMapper.java:12`

### 8.10 KeyEvent 路径还有一个“临时 2x 倍速”语义

当前项目在 `RayNeoMediaControlCoordinator` 中有一条增强控制：

1. 当收到 `KEYCODE_MEDIA_FAST_FORWARD`
2. 且是 **重复按下**（`ACTION_DOWN` 且 `repeatCount > 0`）
3. 会触发 `TEMP_SPEED_2X_PRESS`
4. 对应业务动作是：临时把播放速度切到 `2.0x`
5. 当 `ACTION_UP` 时再恢复到 `1.0x`

参考：

- `RayNeoMediaControlCoordinator.java:77`
- `VideoActivity.java:165`
- `VideoActivity.java:169`

### 8.11 为什么要做输入 gating

不是所有时刻都应该响应媒体控制。

本项目里的 gating 逻辑分两层：

1. **基础可控条件**：前台确实是播放器，且播放器可见
2. **增强可控条件**：当前内容可 seek，且不处于危险瞬态（如 buffering）

具体规则：

- 左镜腿基础控制：只在前台播放器场景允许
- 右镜腿增强控制：要求
  - 前台播放器
  - 当前内容可 seek
  - 不处于不安全瞬态

参考：

- `RayNeoMediaControlGate.java:7`
- `RayNeoMediaControlGate.java:11`
- `VideoActivity.java:261`

这非常关键，因为眼镜上的输入如果不做 gating，用户会遇到：

1. 刚进页面就误触
2. 缓冲瞬间操作导致状态错乱
3. 镜像页 / 设置页被播放器手势抢走输入
4. 发送端协议和本地控制语义冲突

### 8.12 关于“左右镜腿到底该怎么分工”的建议

如果你在做一个新项目，推荐先按下面的分层思路：

#### 方案 A：保守稳定版（推荐起步）

1. 左镜腿：尽量交给系统
2. 右镜腿：应用自定义增强手势
3. 首页：只留退出 / 返回
4. 播放页：才启用 seek / 模式切换 / 额外控制

#### 方案 B：标准媒体键融合版

1. 接收系统翻译后的 `KeyEvent`
2. 用 `RayNeoMediaControlCoordinator` 这种 mapper/gate/coordinator 三段式做抽象
3. 业务层只关心“播放 / 暂停 / 音量 / seek”语义

#### 不推荐的方案

1. 左右镜腿一上来全部自己接管
2. 没有日志就硬猜左右设备对应关系
3. 把每个物理方向直接写死成关键业务逻辑
4. 不区分首页、设置页、播放页、镜像页的输入权限

### 8.13 这次项目里的最终输入经验总结

可以直接记成一句话：

> **右镜腿更适合作为应用增强输入；左镜腿优先让系统保底；播放器页可以扩展，首页必须克制；手势再丰富，也要服从协议兼容性和状态 gating。**

---

## 9. PlayerManager 与媒体按键链路

RayNeo 适配时，很多输入最终还是会落到播放器控制。

本项目 `PlayerManager` 已经支持的标准媒体键语义包括：

1. `KEYCODE_MEDIA_PLAY` → 播放
2. `KEYCODE_MEDIA_PAUSE` → 暂停
3. `KEYCODE_MEDIA_PLAY_PAUSE` → 播放 / 暂停切换
4. `KEYCODE_MEDIA_STOP` → 停止
5. `KEYCODE_MEDIA_PREVIOUS` → 上一项
6. `KEYCODE_MEDIA_NEXT` → 下一项
7. `KEYCODE_MEDIA_REWIND` → 相对后退 `-5s`
8. `KEYCODE_MEDIA_FAST_FORWARD` → 相对前进 `+15s`
9. `KEYCODE_VOLUME_UP / DOWN` → 音量及放大量控制

参考：

- `PlayerManager.java:1390`
- `PlayerManager.java:1428`
- `PlayerManager.java:1434`
- `PlayerManager.java:1478`
- `PlayerManager.java:1490`

这说明一个很有用的工程经验：

> 如果你把 RayNeo 手势最终转成标准媒体键或统一播放命令，后续移植成本会低很多。

---

## 10. 如果你的应用是播放器：推荐的页面结构

### 10.1 播放页

建议包含：

1. 全屏双目视频区域
2. 极少量顶部按钮宿主（可隐藏）
3. 底部轻量 toast / 状态反馈
4. 不依赖系统 Toast

本项目布局参考：

- `activity_video.xml`

### 10.2 不要让 HUD 太重

如果用户是在眼镜里看视频，HUD 应该是“辅助”，不是“主角”。

默认策略：

1. 正常播放时 HUD 尽量轻
2. 操作时给出短反馈
3. 不要一直显示复杂按钮组
4. 不要像手机播放器那样长期盖一层完整控制面板

### 10.3 模式切换要可见但不扰人

显示模式切换这类操作：

1. 一定要给反馈
2. 反馈要短
3. 不要中断视频
4. 最好在双目各自显示同样文本

---

## 11. 如果你的应用是首页 / 等待页 / 状态壳

首页最重要的不是“能点很多东西”，而是：

1. 用户一眼知道应用活着
2. 一眼知道当前在等什么
3. 一眼知道接下来怎么做

因此首页建议展示：

1. 设备名
2. Ready / Waiting 状态
3. Wi‑Fi 状态
4. IP / endpoint
5. 配对码 / 配对提示
6. DLNA / AirPlay / Mirror 状态摘要

本项目首页正是这种结构：

- `RayNeoHomeActivity.java`

---

## 12. 如果你的应用涉及投屏 / AirPlay / DLNA / Mirror

这部分是本项目很重要的协议经验。

### 12.1 AirPlay 与 DLNA 共存时，原则上要同时考虑两件事

1. **发现协议是否都对外可见**
2. **控制语义是否互相污染**

### 12.2 AirPlay Bonjour 的一个关键经验

本项目中，AirPlay mDNS 最终采用的是：

- 使用 `MyJmDNSImpl(null, "localhost", 0l)`
- 即：**绑定所有接口**，而不是只绑特定 `localAddress`

原因：

1. 热点环境
2. 多网卡环境
3. 某些设备上特定接口绑定会导致发现异常

参考：

- `AirPlayBonjour.java`
- `MyJmDNSImpl.java`

这是当前项目里一个**不要轻易回退**的结论。

### 12.3 DLNA 发现的一个关键经验

对于 SSDP，实际环境里“理论上会被发现”不代表“真的容易被发现”。

本项目里为增强热点 / 弱环境发现，做过这些经验性策略：

1. NOTIFY 周期缩短
2. 每轮发多次 alive
3. 加更多 M-SEARCH 响应日志

### 12.4 Mirror-core 与公共 AirPlay 身份要解耦

如果项目既支持媒体投送又支持镜像，必须注意：

> **Mirror-core 内部能力和 TXT 生成，不能污染公共 AirPlay 可见身份。**

这是本项目里的硬约束之一。

### 12.5 如果你做 AirPlay Mirror，这几个坑已经被验证过

当前项目里已验证的关键点：

1. `/info` 必须始终返回完整设备信息，而不是只回 qualifier 对应的 txt 片段
2. 某些发送端会发 **单步 SETUP**，即带 `ekey + isScreenMirroringSession=true` 但没有 `streams`
3. 空字符串不能被当成合法 `ekey`
4. 重复 SETUP 时旧线程 stop / close / join 的锁顺序很容易死锁
5. 旋转后 codec config 变化时，需要重新配置解码器
6. **libairplay.so（预编译库）的 Surface 尺寸约束**：
   - libairplay.so 通过 `SurfaceHolder.Callback.surfaceChanged(w, h)` 感知渲染目标尺寸
   - 必须传递实际 Surface 的真实尺寸；传更大的尺寸（如 1280×720 而实际是 640×480）会导致 libairplay.so 内部超额分配资源、画面卡顿甚至冻结
   - **结论：surfaceChanged 报的尺寸必须和实际 Surface 吻合，不能用"期望输出分辨率"替代**
7. **AirPlay Mirror 内容分辨率是动态的**：
   - 发送端横竖屏切换时，流的编码分辨率会改变（例如 1920×1080 → 1080×1920）
   - libairplay.so 或底层 DMRCenter 在分辨率变化时会发送 `air.video.size.change` LocalBroadcast，携带 "WxH" 字符串
   - 渲染器必须监听该广播并动态更新内容尺寸，否则旋转后宽高比仍然用旧值，导致画面错误缩放
   - 典型实现：维护 `mVideoWidth`/`mVideoHeight`，通过 `onVideoSizeChanged(w, h)` 更新，letterbox 计算实时使用这两个值

参考：

- `airplay-mirror-core/src/main/java/com/rayneo/airplay/mirror/core/AirPlayProtocolHelper.java`
- `airplay-mirror-core/src/main/cpp/legacy_mirror_session.cpp`
- `AirPlayMirrorDecoder.java`

### 12.6 当前项目里镜像显示能力的公开能力声明

在 `AirPlayProtocolHelper` 里，当前公开给发送端的显示能力是：

1. `1280x720`
2. `maxFPS = 30`
3. 返回完整 display / audioFormats / features 信息

参考：

- `AirPlayProtocolHelper.java:64`
- `AirPlayProtocolHelper.java:78`

如果你做的是新的接收器项目，可以把这部分当成：

> **如何让发送端“相信你是一个能工作的接收器”** 的经验样本。

---

## 13. 会话抢占与页面残留

如果你的应用支持多种前台内容（视频、图片、镜像、Miracast、WebRTC），一定要设计 **会话抢占**。

本项目真实踩过的坑：

1. 先播放了视频
2. 再开始 Mirror
3. Mirror 结束后，之前暂停住的视频画面还残留在前台

最终经验：

> 抢占旧会话时，不能只发 stop，还要显式隐藏旧播放器页面。

本项目参考：

- `PlaybackPreemption.java:77`

当前处理方式：

1. `Msg_Stop`
2. `Msg_Hide_Player`

这类设计对眼镜设备尤其重要，因为：

1. 前台 UI 很容易“看起来还在那儿”
2. 用户会直接认为应用坏了
3. 残留画面在近眼显示里非常明显

---

## 14. 页面、协议、播放器之间的线程边界

这类应用经常有：

1. HTTP 线程
2. SSDP / mDNS 线程
3. 播放器线程
4. UI 主线程
5. GL 渲染线程

必须明确一条规则：

> **ExoPlayer / Media3 的核心访问要回主线程，不要在协议工作线程直接打播放器。**

本项目里，AirPlay / DLNA 控制曾经因为工作线程直接访问播放器导致崩溃。

最终修正方向：

1. 协议线程接收命令
2. `Handler.post()` 回主线程
3. 再走统一播放核心

这条原则对所有 RayNeo 播放类应用都适用。

---

## 15. UI 反馈：为什么 in-app toast 比系统 toast 更重要

在普通 Android 手机上，Toast 是很自然的方案；
但在 RayNeo X3 Pro 上，很多时候它不是一个可靠交互组件。

所以建议：

1. 所有关键提示都优先考虑 in-app 方案
2. 在双目界面中，每只眼各自放一个提示宿主
3. 统一注册 / 注销
4. 提示文本尽量短：1~3 个词或一个短句

尤其适合用于：

1. 显示模式切换
2. 配对状态变化
3. 注册成功 / 失败
4. 当前动作反馈
5. 小错误提示

---

## 16. 调试 SOP：RayNeo 项目不要靠猜

这是这次项目非常重要的一条方法论。

### 16.1 每次改动都要能产生日志证据

如果你在处理下面这些问题：

1. 视频没显示
2. 画面倒转 / 镜像错误
3. 切视频卡死
4. Mirror 连上但无画面
5. 手势似乎无效
6. 某个 App 搜不到服务

不要直接开始凭感觉连改几轮。

正确做法是：

1. 在关键入口埋日志
2. 每次尝试都确认“是否收到了事件”
3. 确认链路断在：
   - 发现
   - 连接
   - 协议
   - 解码
   - Surface
   - 渲染
   - 输入
   其中哪一层

### 16.2 这类项目建议重点打日志的位置

#### 输入层

1. 第一个 / 第二个 temple deviceId
2. 左右镜腿判定结果
3. `ACTION_DOWN / MOVE / UP`
4. pointer count
5. 手势最终识别结果

#### 生命周期层

1. `onCreate`
2. `onStart`
3. `onStop`
4. `onDestroy`
5. Surface ready / release
6. listener set / clear

#### 渲染层

1. `onSurfaceCreated`
2. `onSurfaceChanged`
3. `onFrameAvailable`
4. `onDrawFrame`
5. 当前视频宽高比
6. texture matrix 是否旋转

#### 协议层

1. 收到的 URL / 请求体
2. `/info`
3. `SETUP`
4. `RECORD`
5. DLNA 的 M-SEARCH / SOAP Action
6. Bonjour / SSDP 注册信息

### 16.3 ADB 连接不稳定时的策略

RayNeo X3 Pro 真机调试里，一个现实问题是：

> **ADB 连接可能比较脆弱。**

工程上正确策略是：

1. 自动重试
2. 不要把一次断线当成逻辑结论
3. 调试脚本和流程要容忍频繁重连

---

## 17. 适配现有 Android 应用的实际 SOP

如果你手里已经有一个普通 Android 应用，要移植到 RayNeo X3 Pro，建议按这个顺序做。

### 第 1 步：先判断是否值得移植

如果它属于：

1. 播放器
2. 接收器
3. 状态工具
4. 轻量 AI 助手
5. 图片查看

就值得继续。

如果它属于：

1. 高密度信息流
2. 重表单输入
3. 复杂多页面浏览
4. 强依赖多点触控

先做“眼镜版子集”，不要硬搬完整版。

### 第 2 步：抽离业务内核

把这些内容从原页面里剥离：

1. 网络逻辑
2. 播放逻辑
3. 协议逻辑
4. 状态机
5. 长时任务

### 第 3 步：先搭状态壳，不先还原原 UI

先做：

1. 首页状态页
2. 主任务页
3. 错误提示
4. 最小输入路径

不要先做：

1. 原版所有菜单
2. 原版所有设置
3. 原版所有弹窗
4. 原版所有工具条

### 第 4 步：重做输入层

把原来的：

1. 多点触摸
2. 长列表滚动
3. 大量拖动
4. 复杂手势

改成：

1. 自动完成
2. 单步确认
3. 少量模式切换
4. 物理键 / 镜腿手势 / 系统媒体键融合

### 第 5 步：最后再打磨视觉

顺序一定要对：

1. 先稳定
2. 再可控
3. 再舒服
4. 最后才是更像“正式产品”

---

## 18. 如果要新开发一个 RayNeo X3 Pro 应用，推荐的最小模板

可以直接按这个模板起项目：

### 18.1 模块结构

1. `HomeActivity`
2. `CoreService`
3. `UiState`
4. `InputCoordinator`
5. `StereoHudView` 或 `StereoVideoView`
6. `SessionArbiter`（如果涉及多个前台能力）

### 18.2 页面结构

1. 首页：状态
2. 主页面：主任务
3. 设置页：只有必要时才做

### 18.3 输入结构

1. 原始输入采集器
2. 输入抽象 mapper
3. gate / policy
4. 业务动作 executor

也就是：

> `raw input -> semantic input -> gate -> business action`

这比把手势直接写进 Activity 里更容易维护。

---

## 19. 这次项目里明确不要做的事

这是经验非常强的一组结论。

1. **不要切回旧 AirplayServer 路线**
2. **不要让 mirror-core 内部身份污染公共 AirPlay 发现身份**
3. **不要给 `/scrub`、`/playback-info` 一类状态接口乱补推断默认值**
4. **不要重新打开已经修好的 authoritative snapshot 架构**
5. **不要把 BBLL_Mod 那套双目视图直接搬进来**
   - 可以借鉴输入和控制思路
   - 但不要整套复用其双目 UI / overlay 方案
6. **不要为了“更完整交互”去抢系统左镜腿的全部语义**
7. **不要因为某个协议理论上支持就默认去开放所有控制动作**

---

## 20. 测试清单

每次给 RayNeo 版本出包前，至少测下面这些点。

### 20.1 安装与启动

1. 能安装
2. 首页能拉起
3. 服务能启动
4. 切前后台不会直接死

### 20.2 双目显示

1. 左右眼都能看见
2. 方向正确
3. 镜像正确
4. 文本不贴边
5. 提示位置可读

### 20.3 输入

1. 右镜腿单击
2. 右镜腿双击
3. 右镜腿上下左右滑
4. 双指点按
5. 左镜腿是否被系统正常接管
6. 在首页和播放页是否按预期分流

### 20.4 播放器

1. 播放
2. 暂停 / 恢复
3. seek
4. 切下一条视频
5. 切后台回来
6. 多次切流
7. 不同分辨率 / 横竖屏素材

### 20.5 镜像 / 投屏

1. 服务能被发现
2. 能连上
3. 连上后真有画面
4. 旋转后比例正常
5. 结束后旧会话不会残留

### 20.6 协议兼容

1. AirPlay
2. DLNA
3. Mirroring
4. 不同发送端至少抽样测几个

---

## 21. 给 AI Agent 的开工模板

如果你要把这份文档喂给一个新的 AI Agent，建议不要只把文档丢给它然后说“你开始吧”。

更有效的做法是：

1. 先给 Agent 一段**任务指令版 prompt**
2. 再把这份文档作为背景资料
3. 再补充当前项目路径、目标应用类型、当前优先级

下面给出可直接复制的版本。

### 21.1 通用总指令版（适用于大多数 RayNeo X3 Pro 项目）

把下面这段直接发给 Agent：

```text
你现在在处理一个 RayNeo X3 Pro 眼镜应用项目。

你的目标不是把手机 Android UI 直接搬到眼镜上，而是：
1. 保留原有业务核心
2. 重做显示层和交互层
3. 优先做 Android 原生、0DoF、低密度、低功耗、少交互、双目稳定同显的版本

工作时必须默认遵守这些原则：
1. 不要默认做 3DoF / Unity / ARDK，除非需求明确要求
2. 不要把关键能力绑死在手机触摸交互上
3. 不要把复杂逻辑继续堆在 Activity / Fragment 里
4. 前台页面只负责展示，后台持续能力应放到 Service / Manager
5. 如果涉及视频或镜像，必须优先保证单解码、单 Surface、双 viewport 的双目稳定显示
6. 不要依赖系统 Toast，优先做 in-app 双目提示
7. 输入设计要分层：raw input -> semantic input -> gate -> business action
8. 默认右镜腿做应用增强输入，左镜腿尽量让系统保底
9. 首页必须克制，优先展示状态、连接信息、配对信息，不要做复杂控制中心
10. 遇到问题不要靠猜，必须通过日志确认问题断在哪一层：输入、生命周期、Surface、渲染、协议、网络发现、线程切换

你的执行顺序应当是：
1. 先判断这是新开发还是旧应用移植
2. 识别应用类型：播放器 / 接收器 / 工具 / 状态页 / AI 助手
3. 抽离业务内核
4. 搭建 RayNeo 风格首页和主任务页
5. 接入双目显示
6. 接入输入分层
7. 处理生命周期、Surface、会话抢占、前后台切换
8. 最后再做显示模式、增强控制和视觉优化

输出结果时，优先给：
1. 架构判断
2. 文件改动点
3. 风险点
4. 验证步骤
```

### 21.2 旧 Android 应用移植版 prompt

```text
你现在要把一个已有 Android 应用改造成 RayNeo X3 Pro 版本。

你的任务不是保留原 UI，而是保留原业务内核并重做眼镜版外壳。

请按下面顺序工作：
1. 判断原应用属于哪一类：播放器、接收器、工具、状态页、AI 助手、其他
2. 列出必须保留的业务能力
3. 列出必须丢弃或重做的手机交互和手机 UI
4. 找出 Activity / Fragment 中耦合过深的业务逻辑，迁移到 Service / Manager
5. 新建一个 RayNeo 首页，只显示状态、网络、连接、配对或错误信息
6. 如果涉及视频：改造成单解码、单 Surface、双 viewport
7. 建立输入系统：
   - 采集原始 MotionEvent / KeyEvent
   - 转成抽象语义
   - 通过 gate 判断当前是否允许处理
   - 再落到业务动作
8. 默认右镜腿负责应用增强输入，左镜腿尽量保留给系统语义
9. 所有关键反馈改成 in-app 双目提示，不依赖系统 Toast
10. 对生命周期、Surface、线程切换、会话抢占、网络发现分别补日志
11. 给出可执行的验证步骤，覆盖首页、输入、显示、前后台、协议兼容

你每次输出时都要明确：
1. 哪些是复用原逻辑
2. 哪些是重做的眼镜壳
3. 哪些问题已经通过日志确认
4. 哪些地方仍然只是推测
```

### 21.3 新开发 RayNeo 应用版 prompt

```text
你现在要新开发一个 RayNeo X3 Pro 应用。

请默认采用以下基线：
1. Android 原生
2. 0DoF
3. 首页状态壳 + 前台服务
4. 低密度、低功耗、少交互
5. 双目稳定同显

请按下面顺序工作：
1. 先判断这个应用是否真的适合眼镜
2. 定义最小主任务，要求用户能在 1~3 步内完成核心闭环
3. 设计首页，只展示 ready 状态、连接状态、配对信息或主任务摘要
4. 设计主页面，不要做手机式复杂操作流
5. 如果是媒体类，优先搭建单解码、单 Surface、双 viewport 显示链路
6. 输入先做最少集合：确认、返回、模式切换
7. 使用 in-app 双目提示替代系统 Toast
8. 用日志验证输入、生命周期、Surface、渲染、协议线程切换
9. 等基础稳定后，再添加 seek、音量、显示模式、增强控制
10. 输出阶段性结果时附带测试清单

默认不要：
1. 直接做高密度 UI
2. 直接做 3DoF
3. 直接做复杂菜单树
4. 直接引入重动画和重渲染背景
```

### 21.4 播放器 / 接收器专项 prompt

```text
你现在处理的是 RayNeo X3 Pro 上的播放器 / 投屏接收器 / 镜像接收器。

你必须优先保证以下事项：
1. 单解码、单 Surface、双 viewport
2. 画面方向正确，必要时做 180° 旋转和水平镜像校正
3. 视频宽高比不能只信静态尺寸，必要时结合 textureMatrix 判断旋转
4. 切视频、切流、切前后台时不能出现 Surface 被旧 Activity 清掉的竞态
5. 协议线程不能直接访问播放器，要切回主线程
6. 抢占旧会话时，不仅要 stop，还要显式 hide 旧播放器页面
7. 系统 Toast 不可靠，所有关键提示改成 in-app 双目提示
8. 手势能力服从协议兼容性，不能因为能控制就盲目开放 seek 等动作

你的输出必须包含：
1. 显示链路图
2. 生命周期风险点
3. 输入映射表
4. 协议兼容风险
5. 真实验证步骤
```

### 21.5 AI 助手 / 轻工具专项 prompt

```text
你现在处理的是 RayNeo X3 Pro 上的 AI 助手 / 轻工具类应用。

默认目标不是做一个手机聊天窗口，而是做一个近眼 HUD 工具。

请遵守：
1. 一屏只呈现少量信息
2. 优先展示当前状态、当前结论、下一步动作
3. 交互尽量少，能自动完成就自动完成
4. 首页是状态壳，不是信息流
5. 语音可以优先，但必须保留非语音兜底路径
6. 不要依赖长列表滚动和复杂菜单
7. 所有提示优先用 in-app 双目提示
```

---

## 22. AI Agent 接手项目时的输入模板

如果你要把项目交给下一个 Agent，建议不要只说“看文档继续做”。

建议附上这份输入模板：

```text
项目类型：<播放器 / 接收器 / 工具 / AI 助手 / 其他>
当前目标：<一句话描述>
当前优先级：
1. <最高优先级>
2. <次优先级>
3. <延后项>

当前已知硬约束：
1. <约束 1>
2. <约束 2>
3. <约束 3>

当前已知已修复事项：
1. <已修复点 1>
2. <已修复点 2>

当前未解决风险：
1. <风险 1>
2. <风险 2>

要求 Agent 的工作方式：
1. 不要靠猜，改动必须配日志证据
2. 不要扩大范围，只做当前目标要求的事
3. 如果涉及输入，先写清 raw/semantic/gate/action 四层
4. 如果涉及视频，先保证 Surface / 生命周期正确
5. 输出时给出验证步骤
```

---

## 23. 附录 A：RayNeo X3 Pro 输入映射查表

这一节做成查表形式，方便后续 Agent 直接用。

### 23.1 原始输入采集层

| 层级 | 当前项目做法 | 备注 |
|---|---|---|
| Motion 输入入口 | `dispatchTouchEvent()` | 在 Activity 里接，不加悬浮层 |
| Generic Motion 入口 | `dispatchGenericMotionEvent()` | 补充部分设备事件路径 |
| 设备过滤 | `device.getName()` 包含 `cyttsp` / `capsense` | 用于识别镜腿触控板 |
| 左右镜腿判断 | 首个 deviceId 先假定为右，第二个为左 | 通过日志校准 |
| 左镜腿 MotionEvent | 默认不消费 | 尽量留给系统 |
| 右镜腿 MotionEvent | 可消费 | 用于应用增强输入 |

### 23.2 当前项目里的 MotionEvent 手势抽象

| 原始手势 | 抽象语义 | 当前实现位置 |
|---|---|---|
| 单击 | `onTap()` | `RayNeoTempleInput` |
| 双击 | `onDoubleTap()` | `RayNeoTempleInput` |
| 左右滑 | `onSwipeLeft()` / `onSwipeRight()` | `RayNeoTempleInput` |
| 上下滑 | `onSwipeUp()` / `onSwipeDown()` | `RayNeoTempleInput` |
| 双指点按 | `onTwoFingerTap()` | `RayNeoTempleInput` |

### 23.3 当前项目里的 KeyEvent 抽象映射

| KeyEvent | 抽象输入 | 语义归属 |
|---|---|---|
| `KEYCODE_MEDIA_PLAY_PAUSE` | `LEFT_DOUBLE_TAP` | 左镜腿双击 |
| `KEYCODE_VOLUME_UP` | `LEFT_SWIPE_FORWARD` | 左镜腿前滑 |
| `KEYCODE_VOLUME_DOWN` | `LEFT_SWIPE_BACKWARD` | 左镜腿后滑 |
| `KEYCODE_MEDIA_FAST_FORWARD` | `RIGHT_SWIPE_FORWARD` | 右镜腿前滑 |
| `KEYCODE_MEDIA_REWIND` | `RIGHT_SWIPE_BACKWARD` | 右镜腿后滑 |

### 23.4 当前项目里的业务动作映射表

| 抽象输入 | 业务动作 | 说明 |
|---|---|---|
| `LEFT_DOUBLE_TAP` | `PLAY_PAUSE` | 通过 mapper 转换 |
| `LEFT_SWIPE_FORWARD` | `VOLUME_UP` | 左镜腿偏系统语义 |
| `LEFT_SWIPE_BACKWARD` | `VOLUME_DOWN` | 左镜腿偏系统语义 |
| `RIGHT_SWIPE_FORWARD` | `SEEK_FORWARD_15S` | 仅在允许时开放 |
| `RIGHT_SWIPE_BACKWARD` | `SEEK_BACK_15S` | 仅在允许时开放 |
| 重复按住 `FAST_FORWARD` | `TEMP_SPEED_2X_PRESS` | 临时 2x 倍速 |
| `FAST_FORWARD` 抬起 | `TEMP_SPEED_RELEASE_RESTORE` | 恢复 1x |

### 23.5 视频播放页的当前手势表

| 页面 | 物理输入 | 当前动作 | 备注 |
|---|---|---|---|
| 播放页 | 右单击 | 播放 / 暂停 | 安全动作 |
| 播放页 | 右双击 | 返回 / 退出播放器 | 当前固定 |
| 播放页 | 右滑 | `+15s` | DLNA 会话下禁用 |
| 播放页 | 左滑 | `-15s` | DLNA 会话下禁用 |
| 播放页 | 上滑 | 音量加 | 走系统音量键语义 |
| 播放页 | 下滑 | 音量减 | 走系统音量键语义 |
| 播放页 | 右双指点按 | 切换 `Fit/Zoom/Stretch` | 同时显示提示 |

### 23.6 首页的当前手势表

| 页面 | 物理输入 | 当前动作 | 备注 |
|---|---|---|---|
| 首页 | 右双击 | 退出首页 | 当前保留 |
| 首页 | 右单击 | 无动作 | 保持克制 |
| 首页 | 滑动 | 无动作 | 保持状态壳 |
| 首页 | 双指 | 无动作 | 保持状态壳 |

### 23.7 输入 gating 决策表

| 条件 | 左镜腿基础控制 | 右镜腿增强控制 |
|---|---|---|
| 当前前台不是播放器 | 不允许 | 不允许 |
| 前台是播放器但不可见 | 不允许 | 不允许 |
| 前台播放器可见、内容不可 seek | 允许基础控制 | 不允许增强控制 |
| 前台播放器可见、内容可 seek、非危险瞬态 | 允许 | 允许 |
| 正在 buffering / 不安全瞬态 | 基础控制可视情况保留 | 增强控制建议禁用 |

### 23.8 推荐的页面-输入权限表

| 页面类型 | 左镜腿策略 | 右镜腿策略 | 推荐说明 |
|---|---|---|---|
| 首页 / 等待页 | 尽量交给系统 | 仅保留退出 / 返回 | 首页不是控制中心 |
| 播放页 | 系统保底 | 应用增强控制 | 最适合放自定义控制 |
| 镜像页 | 尽量少接管 | 很克制 | 镜像模式通常不需要复杂交互 |
| 设置页 | 不建议抢系统语义 | 仅保留少量确认 / 返回 | 以焦点导航为主 |
| AI 助手页 | 可保留系统语义 | 只做确认 / 展开 / 返回 | 不能做复杂菜单树 |

### 23.9 不推荐映射表

| 不推荐做法 | 原因 |
|---|---|
| 左右镜腿全部拦截 | 容易和系统媒体语义冲突 |
| 不做日志就硬编码左右镜腿 | 真机差异大，难定位 |
| 首页支持太多操作 | 首页应是状态壳 |
| 所有协议场景都开放 seek | 某些发送端会断连 |
| 把物理方向永久等同于业务语义 | 用户配置和系统层翻译可能改变 |

---

## 24. 附录 B：页面状态 / 会话状态 / 输入权限对照表

### 24.1 页面状态与显示重点

| 页面状态 | 主要目标 | 显示重点 | 不该做什么 |
|---|---|---|---|
| 首页等待态 | 告诉用户系统已就绪或正在等待 | 设备名、IP、状态、配对信息 | 不做复杂控制面板 |
| 播放态 | 让内容成为主角 | 视频、少量反馈、短提示 | 不长期盖重 HUD |
| 镜像态 | 稳定显示外部画面 | 画面本身、极少连接提示 | 不加复杂控制 UI |
| 设置态 | 做少量配置 | 焦点明确、项目少 | 不做深层菜单树 |
| 错误态 | 让用户知道发生了什么 | 一句话错误 + 下一步动作 | 不展示长篇错误说明 |

### 24.2 会话抢占表

| 新会话类型 | 旧会话类型 | 必须动作 |
|---|---|---|
| Mirror | 视频播放 | stop + hide old player |
| DLNA | AirPlay 媒体 | 释放旧会话所有权 |
| AirPlay 媒体 | DLNA | 切到新会话并更新前台页面 |
| Miracast / WebRTC | 视频播放 | 停旧播放并清理残留 UI |

### 24.3 生命周期风险表

| 风险点 | 典型现象 | 正确处理 |
|---|---|---|
| 旧 Activity `onStop()` 清掉新 Surface | READY 但无画面 | 只清理属于自己的 Surface |
| 工作线程直接访问播放器 | wrong thread 崩溃 | 切回主线程后再调播放器 |
| 系统 Toast 不可见 | 用户看不到反馈 | 用 in-app 双目提示 |
| 抢占只 stop 不 hide | 旧画面残留 | stop + hide player |
| seek 动作和发送端语义冲突 | 发送端主动断开 | 协议敏感场景禁用 seek |

---

## 25. 附录 C：BBLL / BBLL_Mod 可借鉴点与不建议照搬点

这一节的目标不是评价 BBLL / BBLL_Mod “好不好”，而是明确：

> **如果你在做 RayNeo X3 Pro 项目，BBLL 那条路线里哪些经验值得学，哪些只能参考，哪些不应该直接搬进当前项目。**

### 25.1 先给结论

可以直接记成一句话：

> **学 BBLL 的输入层、页面分工和交互组织；不要拿 BBLL 的双目壳去替换当前项目已经验证有效的单解码、单 Surface、单纹理、双 viewport 主链路。**

原因很简单：

1. BBLL / BBLL_Mod 对“眼镜上怎么组织输入和页面”很有参考价值
2. 但当前项目是播放器 / 接收器 / 镜像类应用，核心矛盾首先是：
   - 视频稳定显示
   - Surface 生命周期
   - 协议兼容
   - 会话抢占
3. 这些问题上，当前项目自己的实现已经更贴近真实需求

### 25.2 可以直接吸收的部分

下面这些做法，适合从 BBLL 思路里吸收进来：

#### 1. 输入层不要直接绑业务逻辑

可以借鉴的不是某个具体手势，而是这种组织方法：

1. 原始输入采集
2. 抽象成语义输入
3. 再做页面级 / 状态级 gating
4. 最后再落到业务动作

也就是：

> `raw input -> semantic input -> gate -> business action`

这一点和本项目当前 `RayNeoTempleInput` + `RayNeoMediaControlCoordinator` + `RayNeoMediaControlMapper` + `RayNeoMediaControlGate` 的方向是一致的。

#### 2. 左右镜腿分工要明确

BBLL 类项目值得借鉴的一点是：

1. 不要把所有输入都堆成同一层逻辑
2. 要把“确认 / 返回 / 系统语义”和“应用增强语义”分开
3. 不同页面里允许的动作集合应该不同

这与当前项目结论一致：

1. 左镜腿优先留给系统保底语义
2. 右镜腿更适合做应用增强输入
3. 首页和播放页不能共用一套完整手势表

#### 3. 页面级输入 gating

BBLL 路线真正值得学的不是“手势更多”，而是“不同页面允许不同控制”。

这对 RayNeo 项目很重要，因为：

1. 首页是状态壳，不是控制中心
2. 播放页才适合放 seek、模式切换这类增强控制
3. 镜像页和等待页应该非常克制
4. 设置页更适合焦点导航，而不是堆手势

#### 4. 分眼提示和轻 HUD 的思路

BBLL 类项目在“近眼界面不能像手机那样做重 UI”这点上有参考价值。

可以吸收的原则包括：

1. 提示尽量短
2. 反馈尽量近内容区域
3. 一次只表达一件事
4. 需要时做分眼一致反馈

这与当前项目的 in-app 双目 toast 方向兼容。

### 25.3 只建议参考、不要直接照搬的部分

下面这些可以看思路，但不要直接复制实现：

#### 1. 双目 UI 壳层结构

可以参考它如何把信息拆成“近眼可读的小块”；
但不要直接把其现有双目页面结构整套挪过来。

原因：

1. 当前项目主要是媒体内容为主，UI 只是辅助
2. BBLL 类 UI 更偏应用壳 / 页面壳
3. 媒体接收器场景下，主画面必须让位给视频 / 镜像内容，而不是 UI 框架

#### 2. 分眼提示形式

可以参考“分眼显示同样的短反馈”这类原则；
但不要直接照搬具体 overlay 组织方式。

更好的做法是：

1. 根据当前页面布局决定提示宿主
2. 保持提示短、轻、可消失
3. 不让提示系统反过来主导页面结构

#### 3. 焦点交互与菜单组织

可以参考其“少层级、强主路径”的想法；
但不能因为某个 BBLL 页面结构可用，就默认适合播放器 / 接收器项目。

判断标准应该是：

1. 这个页面是不是主任务页
2. 这个动作是不是高频动作
3. 这个操作是否真的需要常驻入口

### 25.4 不建议直接引入的部分

下面这些是当前项目里明确不建议直接搬的：

#### 1. 用 BBLL 双目主链路替换当前项目视频主链路

这是最不建议做的事。

当前项目已经验证有效的显示主线是：

1. 单解码
2. 单 Surface
3. 单 `SurfaceTexture`
4. 单纹理复用到左右眼两个 viewport

这条链路直接服务于：

1. 视频播放
2. AirPlay 媒体接收
3. AirPlay Mirror / 其他镜像模式

如果强行换成另一套更偏 UI 壳的双目方案，风险很高：

1. Surface 生命周期复杂度上升
2. 渲染链路变重
3. 更容易干扰当前播放器 / 镜像稳定性
4. 解决不了本项目最核心的问题，反而会增加变量

#### 2. 用 overlay 思路反客为主

如果把双目 overlay 作为页面结构中心，常见后果是：

1. 视频不是主角了
2. 输入焦点变复杂
3. 提示、控制、主画面彼此抢层级
4. 页面越来越像手机 UI 缩放版

RayNeo 媒体类项目里，更好的顺序应该始终是：

1. 先让内容稳定显示
2. 再加少量 HUD
3. 最后再补必要提示

#### 3. 为了“整合 BBLL 经验”而重写当前已验证代码路径

只要当前路径已经在真机上验证稳定，就不要为了风格统一去重写：

1. `RayNeoStereoVideoView` 的视频主链路
2. `VideoActivity` 的 Surface ownership 清理方式
3. 当前项目已经验证过的输入 gating 分层
4. 当前项目已经验证过的 in-app toast 宿主方式

### 25.5 如果你真的要“把 BBLL 经验整合进来”，正确做法是什么

推荐按下面顺序吸收，而不是大替换：

#### 第一层：先学方法，不搬代码

先吸收这些方法论：

1. 输入分层
2. 左右镜腿分工
3. 页面级 gating
4. 少层级主路径设计
5. 近眼提示要短、轻、稳

#### 第二层：只在输入层和页面策略层借鉴

更适合整合的地方：

1. `InputCoordinator` 的分层方式
2. 页面状态和权限表
3. 手势 / 按键映射表的组织方式
4. 首页、播放页、设置页的输入策略差异

#### 第三层：保持当前双目视频主链路不变

这一点是硬要求：

1. 媒体显示仍然以当前 `RayNeoStereoVideoView` 为主
2. 仍然保持单解码 + 单 Surface + 双 viewport
3. BBLL 经验最多只影响 HUD / 输入 / 页面组织，不影响视频主渲染路径

### 25.6 给未来 Agent 的一句话执行指令

如果你以后让另一个 Agent 继续做 RayNeo X3 Pro 适配，可以直接给它这句：

> **吸收 BBLL / BBLL_Mod 的输入层、左右镜腿分工、页面级 gating 和轻交互组织方式；不要拿它的双目 UI 壳或渲染路径替换当前项目已经验证稳定的媒体显示主链路。**

---

## 26. 有源码 Android 应用移植的双目显示标准方案（2026-03-30 验证）

本节记录的是在 Lemuroid（开源模拟器）适配过程中总结的、**有源码可以修改**时的最优双目显示实现思路。

### 26.1 核心思路（一句话）

> **让程序以为自己是 640×480；在每个 Activity 最外层包一层 `DualEyeLayout`，用硬件 Canvas 把内容复制到右侧；GLSurfaceView 类内容单独用 PixelCopy 处理；其他全不用动。**

不要自己想歪，按这个思路来。

### 26.2 两个关键组件

**`DualEyeLayout`（`FrameLayout` 子类）**

核心实现：

```kotlin
class DualEyeLayout(context: Context) : FrameLayout(context) {
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)           // 左眼：x=0 正常渲染
        canvas.save()
        canvas.translate(640f, 0f)
        super.dispatchDraw(canvas)           // 右眼：x=640 replay 同一份 RenderNode
        canvas.restore()
    }
}
```

性能说明：硬件加速模式下，子 View 的 draw 操作被录制进 GPU RenderNode（display list）。第二次 `super.dispatchDraw` 只是在 GPU 上 replay 已录制好的指令，没有额外 CPU 开销、没有内存拷贝。这是 View 层内容复制的最高性能方式。

**`DualEyeActivityWrapper`（`Application.ActivityLifecycleCallbacks`）**

在 `Application.onCreate()` 里注册，对每个 Activity 的 `onActivityCreated` 用 `decorView.post {}` 把 `android.R.id.content` 里的根 View（ComposeView 或传统 View）：
- 移出 content frame
- 套进 `DualEyeLayout`（宽度设为 640px）
- 重新加入 content frame（宽度 MATCH_PARENT = 1280px）

### 26.3 GLSurfaceView 的特殊处理

`GLSurfaceView`（含 LibretroDroid 的 `GLRetroView`）**不走 View Canvas**，`DualEyeLayout` 的 canvas 复制对它无效（右侧会是黑色）。

解法：
- 将含 `GLRetroView` 的 Activity（`RayNeoGameActivity`）**排除在 `DualEyeActivityWrapper` 之外**
- 该 Activity 单独维护一个 `Row(GLRetroView + RayNeoMirrorView)` 全宽布局
- `RayNeoMirrorView` 通过 `PixelCopy.request(SurfaceView, ...)` 每帧从 `GLRetroView` 抓取并绘制到右侧

### 26.4 设备信息（RayNeo X3 Pro 实测）

```
ro.product.manufacturer = RayNeo
ro.product.model        = ARGF20
ro.product.brand        = RayNeo
wm size                 = 1280x480
wm density              = 160   （1dp = 1px）
ro.build.version.sdk    = 32    （Android 12）
```

density 160 意味着：1px = 1dp，640px = 640dp，布局可以用 dp 或 px，数值完全一致。

### 26.5 注意事项

1. `DynamicColors.applyToActivitiesIfAvailable()` 也注册了生命周期回调，在它之后注册 `DualEyeActivityWrapper`，不冲突
2. `DualEyeLayout` 的 `dispatchDraw` 方案对普通 View 和 Compose（`ComposeView`）都有效
3. 镜腿输入不要过早拦截 `ACTION_DOWN`/`ACTION_MOVE`，会破坏系统手势（Mercury 的退出双击）
4. 游戏进程（`:game` 进程）是独立进程，`Application.onCreate()` 在两个进程里都会执行，`isMainProcess()` 可用来区分

---

## 27. 最终一句话原则

如果只允许保留一句话给未来的 Agent，那就是：

> **RayNeo X3 Pro 适配的关键，不是做得多，而是做得稳：保留业务核心，重做双目显示和输入分层；默认右镜腿做应用增强、左镜腿让系统保底；先解决 Surface、生命周期、发现协议和可见提示，再谈更复杂的交互。**
