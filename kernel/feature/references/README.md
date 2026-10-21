# LKM 参考策略

在 Actions 的 **Build LKM for KernelSU** 点 Run workflow。勾选“启用静态 SELinux 参考策略”，再从 OriginOS、ColorOS17、HyperOS 里选一个。默认不勾选，仍用原来的动态备份。运行时也要开启管理器的 SELinux Hide。

需要可以直接安装的管理器 APK 时，运行 **Build Manager**，同样选开关和策略。`manager_variant` 选 `spoofed` 就只编伪装版；只要 APK 时不用勾额外桌面 ksud。策略选择会传到所有 LKM，再经 ksud 嵌入 APK，不是仅给管理器改个标记。APK 的 `assets/selinux-reference.json` 也记录本次选择。

OriginOS 沿用 `kernel/feature/reference.policy`，没有改动，也没有挪走；ColorOS17 和 HyperOS 放在本目录。脚本通过 `KSU_REFERENCE_POLICY` 指向所选文件，不会把它覆盖回 OriginOS 的文件。ARM64 和 x86_64 都收到相同选择，artifact 里附 `selinux-reference.json`，可查看来源和 SHA-256。

ColorOS17 来自 PJZ110_17.0.0.103(CN01) 的五分区静态 CIL。HyperOS 来自提供的 DNA_HYPER 解包，使用真正的 `system/system/etc/selinux`，并按 vendor 的 202504 映射及 genfs 版本组合。其旧 precompiled 与三个平台分区的摘要不匹配，因此重新编译，没有直接拿缓存。

这两套新参考保留原厂权限，没有套用 OriginOS 的清理结果；编译使用 Android init 的 `-N` 参数跳过构建期 neverallow 检查。均做过二进制回读、roundtrip，未发现 KSU/Magisk 动态注入类型。来源和编译记录在各自 audit.json。

它们只用于查询参考，不修改系统实际执行权限。内核仍会检查 class 和权限编号的 ABI，不匹配会回退动态备份；选中一种系统并不代表适配该系统的每个版本。

2026-10-21 已移除上游的 `setprocattr` 拦截，并取消查询时向活动 SID 表同步。真实应用域切换由原生 SELinux 和当前 ROM 策略处理，即使参考里缺少该 ROM 的域也不再拦截启动。旧内核或旧 LKM 需要重新编译并更新，单换管理器不会修复内核中的旧代码。应用若根据查询结果自行决定行为，仍可能受到返回值影响。

查询入口读取调用者的真实域，覆盖 `appdomain` 和 `app_zygote` 预加载；系统 zygote、其他系统域及多用户的系统 UID 走原生查询。Hunter、Duck、春秋都会在 `app_zygote` 里做检查，因此不能整个跳过该域。

Android 在真正 `setcon()` 前会先调用 `security_check_context()`。为保护启动，`/context` 请求若指定当前 ROM 已知的应用域，交给原始处理器完整验证用户、角色和 MLS；不会只凭类型存在就返回成功，也不会在原生拒绝后改用参考放行。其他上下文、`/access` 权限矩阵和状态查询保留参考视图。模块若把自己的类型加入真实 `appdomain`，该类型也会落入原生上下文校验范围。

如果由其他 workflow 调用 `build-lkm.yml`，传入：

```yaml
with:
  static_selinux_reference: true
  selinux_reference_source: ColorOS17 # OriginOS / ColorOS17 / HyperOS
```

手动编译模块时也可以直接传 `CONFIG_KSU_STATIC_SELINUX_REFERENCE=y` 和所选文件的绝对路径 `KSU_REFERENCE_POLICY=...`。没有传路径时继续使用原来的 OriginOS 参考。
