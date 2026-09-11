# 显示与生命周期

## 先确认输出路径

对每个可见页面列出：Activity/窗口、业务内容根、普通 View/Compose 或独立 Surface、是否已经输出双眼。首页、设置、对话框、游戏菜单和权限恢复页都属于验收范围。

在源代码中搜索 `AlertDialog`、`Dialog`、`DropdownMenu`、`Popup`、`PopupWindow` 和窗口创建入口；单一 Activity 看起来双眼正常不能代表模态层已覆盖。可复用的应用内 Dialog 入口应保持原有确认、取消、外部点击与返回语义，再处理左右显示。只修改 `Resources.displayMetrics` 或 composable 的宽度并不改变实际 Window/Surface 输出目标。

有源码时，一个可复用实现是单独的 `DualEyeDialog`：Dialog 使用全宽窗口，内部仅一个单眼 ComposeView，并通过双绘制容器输出两眼。继承父 CompositionContext 以保留主题与状态；宿主 View 随窗口移除时释放 composition。Lemuroid 的共用 AlertDialog 入口和游戏长按菜单使用此模式；普通设备仍走原 Material 窗口。背景/面板拦截使用仅指针手势，不给手柄导航额外增加透明的 `clickable` 焦点；镜腿返回回调指向当前 Dialog 的 dismiss，而非误退后面的 Activity。此方式覆盖接入该入口的窗口，不自动接管第三方组件或系统权限窗口。

Legado 的一次已确认反例：X3 Pro API 32 上运行 target SDK 36 的旧 APK 时，反射 `Dialog.mWindowManager` 被 hidden-API 检查拒绝，日志同时出现 `reflection, denied` 和 `NoSuchFieldException`；原版本 lint 也报 `SoonBlockedPrivateApi`。因此不能把私有字段反射的异常简单解释为字段从固件中消失，也不能把包装函数被调用视为窗口已双眼化。检查当前 target SDK、实际运行日志和窗口输出；优先选择公开的 Dialog/Window 内容接口。这个记录只验证了旧反射路径失败，不代表替代实现已经跨 App 验收。

| 内容 | 可复用方向 | 前提及限制 |
|---|---|---|
| 普通 View；位于同一 View 窗口的 Compose | 一个逻辑内容树，限制到单眼区域，左右绘制 | 验证裁剪、硬件图层、动画、焦点、无障碍；不要把性能最高写成无条件结论 |
| Compose `Dialog`、`Popup` 或其他 Window | 对各自窗口适配，或按产品设计放进已双眼化的页面层 | Activity 根包装不自动覆盖其他窗口 |
| `SurfaceView`、`GLSurfaceView`、原生游戏 Surface | 保留现有输出；可控制 renderer 时同一帧/纹理绘制左右 viewport | 父 View 再绘制一次不会复制独立 Surface 的像素 |
| renderer 封闭但 Surface 可捕获 | 在平台支持时评估 PixelCopy 到右眼 Surface | 复制、Bitmap 和额外呈现带来成本；保护内容和 Surface 未就绪可能失败 |
| 输入已经是 SBS/立体帧 | 先判明左右半帧及目标显示模式 | 不要把完整 SBS 帧复制到每只眼睛造成二次分割 |

历史 X3 Pro 项目记录为 `1280×480`、density `160`，每眼 `640×480`。这只能作为该设备配置；以当前显示、窗口可用区域、insets、方向和设备模式决定实际左右区域。也不要把投屏项目里的 180° 旋转、水平翻转机械加入新 App：用带文字、方向标记和边框的图案核对显示，再检查纹理矩阵。

普通 View Dialog 的另一条已验证路径是保留系统 `DecorView` 与 `WindowManager` 的关系，把其公开子内容放进一个双绘制容器。外窗口覆盖两个眼区，每眼面板宽度单独保存；后续 `setLayout` 不应改变两眼间距。Legado 在 X3 Pro/API 32 上已验证 90% 宽度调整、重复显示、外部取消、不可取消窗口、右眼单次确认及菜单窗口关闭。它不覆盖平台输入法、任意 PopupWindow 或 Surface。

## 普通 View/Compose：一个内容根只包装一次

复用已有效的双绘制容器时保持一个逻辑内容树，左右眼使用同一状态和同一输入入口。绘制第二份视觉并不要求创建第二个 ViewModel、NavHost、播放器或扫描任务。

1. 在实际内容已安装后包装根 View，保留原 layout params 与 owner 链。用类型/专用 tag 标识包装器和 overlay，重复调用应无副作用。
2. 核实 `onActivityCreated` 的 `post` 是否发生在内容创建前；初次无 child 后直接返回会遗漏晚创建的页面。后续 `setContent`/`setContentView` 也可能替换包装器。优先使用项目明确的内容安装点，或有限且可解除的监听，避免无限轮询。
3. 恢复 Activity 时也创建内容。设置页返回仅更新状态，避免重装内容导致包装丢失；检查旋转/进程恢复、多次打开菜单和 `singleTask` 新 Intent。
4. 专门输出双眼的 Surface 页面排除普通包装；需要手势 overlay 时独立、幂等安装。不要双重包装已双眼化的内容。
5. 显示坐标、单眼逻辑坐标和输入坐标分开。右眼坐标重映射使用事件副本并在分发后回收；避免原地修改共享 `MotionEvent` 影响其他监听或重复偏移。
6. 透明 Activity 的恢复界面需要可读的不透明背景；给短高度页面安排滚动、手柄焦点和两眼同时可见的错误提示。

普通 View 容器的 `dispatchDraw` 复制是一种已在本地项目采用的技术，不保证所有 Compose 图层、Popup 或第三方 View 都一致。先验证现有容器与失败窗口的关系；只在确认视觉复制不适用后才考虑双 Compose 渲染，并把业务副作用、导航和输入保留在共同上层。

## Surface、视频与游戏

有渲染控制权时，单播放器/解码器输出同一纹理，再使用左右 viewport；保留 Fit/Zoom/Stretch、实际帧宽高、纹理变换矩阵与字幕/控件所在层。不要引入双解码或第二个模拟器实例。

现有游戏采用 `GLRetroView + PixelCopy + 右眼 Surface` 时，升级上游或修复权限通常不要求换掉它。需要优化前先记录帧率、帧时间、复制耗时和双眼相位差；“约一帧且感知不到”只能是经测量的特定结果。

PixelCopy 实现重点核对：

- API 与源 Surface 有效性；请求入口异常与异步失败都能恢复状态，避免每帧刷屏。
- 同时最多一个在途请求；位图尺寸与源/目的 Surface 对应，尺寸变化时旧请求仍持有旧位图。
- 异步回调完成前不 recycle 其目标 Bitmap；用实例/代次或所有权判断丢弃旧结果。
- 暂停、解绑、detach 时撤下帧回调；迟到回调不向旧 Surface 绘制，也不复活采样循环。恢复只创建一个循环。
- `lockCanvas` 与 `unlockCanvasAndPost` 的失败及 Surface 销毁竞态；持有 Canvas 后按所属 Surface 生命周期清理。

已有代码具备这些结构的部分片段不等于生命周期已经验证。切后台、进入菜单、返回游戏、Surface 重建、尺寸变化和连续启动不同内容需要实测。

## 共享播放器或服务的所有权

旧 Activity 的 `onStop` 仅释放仍归自己持有的 Surface/listener。新 Activity 已接管时，以引用相等或 generation/token 判断，避免旧页面清掉新画面。任务是否应在后台继续由应用功能决定；移植无需把所有业务一律升级成前台服务。

## 可单独研究的系统级方向

用户明确要求跨 App 通用方案时，可在当前应用适配完成后评估“单眼逻辑显示 + GPU 整帧复制”：例如让应用与其 Dialog/Popup 在真正的单眼逻辑 display 中运行，再在物理左右眼区域呈现该 display 的完整合成结果。`640×480` 仅在目标设备确认后作为该单眼尺寸。

这是条件性研究方向，尚无本技能提供的通用实现或真机结论。先查应用/子窗口的 display 归属、VirtualDisplay/系统合成接口的权限和系统版本、Surface/受保护内容是否纳入源画面、输入与焦点坐标映射、复制延迟、刷新同步与失败恢复。区分“应用只感知单眼逻辑显示”和“资源报告变窄但实际仍在原 display”。不因研究方向而直接改 `wm size`、替换 compositor、获取 root 或重启；这些是另一个需按用户范围实施与验证的任务。
