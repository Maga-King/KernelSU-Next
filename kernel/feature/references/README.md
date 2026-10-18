# LKM 参考策略

在 Actions 的 **Build LKM for KernelSU** 点 Run workflow。勾选“启用静态 SELinux 参考策略”，再从 OriginOS、ColorOS17、HyperOS 里选一个。默认不勾选，仍用原来的动态备份。运行时也要开启管理器的 SELinux Hide。

OriginOS 沿用 `kernel/feature/reference.policy`，没有改动，也没有挪走；ColorOS17 和 HyperOS 放在本目录。脚本通过 `KSU_REFERENCE_POLICY` 指向所选文件，不会把它覆盖回 OriginOS 的文件。ARM64 和 x86_64 都收到相同选择，artifact 里附 `selinux-reference.json`，可查看来源和 SHA-256。

ColorOS17 来自 PJZ110_17.0.0.103(CN01) 的五分区静态 CIL。HyperOS 来自提供的 DNA_HYPER 解包，使用真正的 `system/system/etc/selinux`，并按 vendor 的 202504 映射及 genfs 版本组合。其旧 precompiled 与三个平台分区的摘要不匹配，因此重新编译，没有直接拿缓存。

这两套新参考保留原厂权限，没有套用 OriginOS 的清理结果；编译使用 Android init 的 `-N` 参数跳过构建期 neverallow 检查。均做过二进制回读、roundtrip，未发现 KSU/Magisk 动态注入类型。来源和编译记录在各自 audit.json。

它们只用于查询参考，不修改系统实际执行权限。内核仍会检查 class 和权限编号的 ABI，不匹配会回退动态备份；选中一种系统并不代表适配该系统的每个版本。

如果由其他 workflow 调用 `build-lkm.yml`，传入：

```yaml
with:
  static_selinux_reference: true
  selinux_reference_source: ColorOS17 # OriginOS / ColorOS17 / HyperOS
```

手动编译模块时也可以直接传 `CONFIG_KSU_STATIC_SELINUX_REFERENCE=y` 和所选文件的绝对路径 `KSU_REFERENCE_POLICY=...`。没有传路径时继续使用原来的 OriginOS 参考。
