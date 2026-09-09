# 目录与权限

## 把四个状态分开

| 状态 | 正确行为 |
|---|---|
| SAF 选择器可用，取得有效持久 URI 授权 | 继续原有 SAF provider；成功验证后保存 URI 并扫描 |
| `ACTION_OPEN_DOCUMENT_TREE` 无可用 Activity | 显示本地目录/权限恢复流程，不把捕获异常当扫描成功 |
| 权限不足或目录列举失败 | 显示原因、授权/重试/取消入口，保持旧选择，不入队扫描 |
| 权限满足，目录可列举但确实为空 | 可保存和扫描，明确表示空目录 |

先区分 `content://` 与文件路径。保持项目已有 SAF 与 legacy/path preference 分工；本地路径误写进 URI 字段会造成后续 provider 选错。设置页显示也应观察当前实际使用的字段。

选择器缺失只说明新选择入口受限。先检查现有 URI、持久授权与实际列举；仍有效时可继续使用原 provider，不强制迁移到直接路径。刷机/清数据后应重新验证，这些授权可能已消失。

## 本地目录回退流程

1. 确认该设备与业务确需直接共享存储访问。普通 Android 仍保留原路径；不要全局替换 SAF，也不要为仅需应用私有目录的功能扩大权限。
2. Android 11+ 的全部文件访问状态用应用侧 `Environment.isExternalStorageManager()` 检查。声明 `MANAGE_EXTERNAL_STORAGE` 不等于已授权；`READ_MEDIA_*` 也不覆盖任意 ROM/文档。
3. 未授权时显示恢复页，由用户动作打开应用专属全部文件设置；不可启动时尝试通用全部文件设置。都不存在时保留说明、重试与取消，不循环重开设置。
4. 从设置返回时重新检查权限，未授权继续停留；正确区分设置结果与文件选择 request code，避免共用 `onActivityResult` 尾部无条件 `finish()`。Activity 重建也能恢复页面。
5. 权限满足后，在应用自身 UID 上验证目标为目录、创建所需目录成功、实际列举成功。`listFiles()==null` 是访问/列举失败；`emptyArray()` 才是可访问的空目录。shell 用户能列文件不证明应用能列。
6. 仅验证成功后保存选择并提交一次扫描；终结状态避免按钮、`onResume` 与异步回调重复入队。队列的 append 策略不等于去重。
7. 回退界面纳入双眼显示与手柄焦点，主要操作可见。权限页为透明主题时添加页面背景；用页面文本表达访问状态，避免仅靠系统 Toast。

用户目录及已有文件应完整保留。授权前不预创建目录，失败不覆写已有选择；取消不扫描。路径名称由任务和项目约定决定，`/sdcard/Games` 只是已有模拟器的例子。

Android 10 及更早版本沿用项目兼容分支，不调用 API 30 才有的全部文件访问接口。结合 API、target SDK、legacy/scoped storage 模式和已有运行时读权限流程判断；`requestLegacyExternalStorage` 是版本相关配置，不能单独证明路径可读。应用私有目录按自身访问规则处理，所有分支最后都以应用实际列举为准。

## 设置页缺失时的 ADB appops 路径

只读 probe 使用 `cmd appops get` 查看当前 Android user 的状态。`appops set` 是显式设备授权操作，仅在用户请求/现有授权涵盖该应用全部文件访问时执行；它不属于探查脚本。已有授权无需重复询问。

```powershell
# 先确认 SERIAL、包名以及目标 Android user；示例包名需替换。
$androidUserId = (adb -s SERIAL shell am get-current-user).Trim()
if ($androidUserId -notmatch '^[0-9]+$') { throw 'Android user 查询未得到有效数字' }
adb -s SERIAL shell cmd appops get --user $androidUserId com.example.app MANAGE_EXTERNAL_STORAGE
# 仅在授权范围内执行下面这条写操作。
adb -s SERIAL shell cmd appops set --user $androidUserId com.example.app MANAGE_EXTERNAL_STORAGE allow
adb -s SERIAL shell cmd appops get --user $androidUserId com.example.app MANAGE_EXTERNAL_STORAGE
```

保留变更前模式以便需要时恢复；不要假定前态是 `default`。这个权限不是普通 runtime permission，`pm grant` 不是通用替代。固件若不支持该 appop 或返回错误，记录确切错误并保留恢复页，避免继续一串权限/重启试探。成功后应用内重试，验证 `isExternalStorageManager()` 与真实目录列举；无需默认重启设备。

## 最小验证场景

拒绝授权时不创建、不保存、不扫描；授权后重试成功；有文件与空目录可区分；路径为普通文件、目录创建失败、列举失败均可恢复；设置均缺失时页面留存；返回设置但未授权、Activity 重建、取消以及连续点击都符合预期。最后用已知目录和已知文件验证真实扫描结果。
