# 同步上游与维护分支改动

本分支面向一加 8e 的 OriginOS 移植。上游是
`https://github.com/KernelSU-Next/KernelSU-Next.git` 的 `dev` 分支。
同步使用普通 merge，保留双方历史；推送目标是 `Maga-King/KernelSU-Next`。

## 分支改动的位置

| 功能 | 独立实现 | 上游文件中的必要接入 |
| --- | --- | --- |
| 固定 SELinux 查询参考 | `kernel/feature/static_reference.{c,h,mk}`、`reference.policy`、生成脚本 | `Kbuild` 的一行 include、`Kconfig` 的选项、`selinux/rules.c` 的 include 和一次调用 |
| SELinux 查询隔离 | `kernel/feature/selinux_hide.c`、`scripts/tests/test_query_only.py` | 参考策略仅用于查询；真实权限检查、进程转换和系统 zygote 预检沿用活动策略；保留 app zygote 探测和活动 app 上下文校验 |
| Aster UI | `ui/home/`、`ui/shell/`、主题、动画及组件；详见 `ASTER_UI.md` | 保留全景/标准布局、壁纸与已有设置、刷新行为；上游安装/模块后端变更接入现有 UI，不恢复旧启动动画 |
| 分支 Manager 证书 | `kernel/manager/fork_manager_certificate.h`、`manager/signing-certificate.json`、签名准备与校验脚本 | `manager/apk_sign.c` 的 include 和两次证书检查；两个 Manager workflow 的签名准备与校验步骤 |
| 风险扫描默认关闭 | `userspace/ksud/src/risk.rs` 中缺失/不可读配置返回 false | 使用上游配置键 `risk.enabled`；显式开关仍由上游处理 |
| 分支说明 | 本文、`fork-manager-signing.md`、各功能 README | `docs/README.md` 的分支标题 |

上游已经包含 ZIP 风险扫描增强和 services 阶段防重复执行逻辑，直接沿用其实现。
不要再次在这些路径上叠加旧补丁。

保留上游 C/H 文件原有格式，只格式化三个分支自有 C/H 文件。
`scripts/check_fork_format.py` 是该范围的清单，CI 使用 clang-format 18.1.8。
不要对整个 `kernel/` 执行格式化后提交，否则会重新产生大量无关差异。
新增分支自有 C/H 文件时，把它们加入这个清单。

签名密码和私钥继续使用现有 `FORK_MANAGER_*` Actions secrets。
不要通过复制整份上游 workflow 或改写其构建任务来添加分支逻辑；签名细节维护在独立 Python 脚本中。

## 后续同步

在干净的工作区执行：

```sh
git switch dev
git pull --ff-only origin dev
git fetch upstream dev
git merge --no-commit --no-ff upstream/dev
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check_fork_format.py
git diff --cached upstream/dev -- kernel/manager/apk_sign.c kernel/selinux/rules.c userspace/ksud/src/risk.rs
git commit
git push origin dev
```

首次使用该克隆时添加 `upstream` remote；格式检查需要安装
`python3 -m pip install clang-format==18.1.8`。测试运行环境需要 C 编译器和 Rust 编译器。
合并出现冲突时，先理解上游接口变化，再保留表中列出的行为。
不能用全局 `ours`/`theirs` 策略掩盖冲突。

SELinux 开关开启和关闭的内核编译由 `static-reference-test.yml` 验证。
Manager 完整构建继续使用现有 workflow。
这种布局减少冲突范围；上游修改同一接口时仍可能需要适配。

## 2026-10-29 同步

从分支 `059a87a5` 合并上游 `b427a3f9`，纳入 8 个提交：Linux 6.19
preinit 接口适配、mount namespace 系统调用适配、bind mount 下的 `init.rc`
识别，以及 SUSFS 控制节点的授权和标签修复。

管理器随上游移除安装页的内核模块屏蔽选项和对应参数传递；开发者仍可使用
ksud 的 `--block-module`。Aster 主界面、全景/标准布局、壁纸、主题、刷新和
分支更新源均保留。`Flash.kt` 的资源读取适配与上游参数变化同时保留。

本次合并不改写三套参考策略、查询隔离修复、Manager 签名配置或风险扫描默认值。
按仓库主人本次要求，验收重点为关闭静态参考策略和开启 ColorOS17 参考策略。
两种配置的 Android 15 / 6.6 内核均已通过 Actions 实际编译：
https://github.com/Maga-King/KernelSU-Next/actions/runs/40817261327
这里的关闭仅指静态查询参考功能，不是关闭系统 SELinux enforcing。

23 项回归测试（含 C/Rust 运行测试）和分支 clang-format 18.1.8 检查通过。
arm64 Aster `assemblePreview` 成功。上游遗漏删除的 22 种语言、110 条
`install_block_modules*` 文案导致 lint ExtraTranslation 错误，已随同步清理；
清理后 `lintPreview` 通过，零错误。其余翻译与 Aster 资源保留。
完整多架构构建按主人要求取消；未进行真机刷入验证。
