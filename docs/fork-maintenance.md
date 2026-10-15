# 同步上游与维护分支改动

本分支面向一加 8e 的 OriginOS 移植。上游是
`https://github.com/KernelSU-Next/KernelSU-Next.git` 的 `dev` 分支。
同步使用普通 merge，保留双方历史；推送目标是 `Maga-King/KernelSU-Next`。

## 分支改动的位置

| 功能 | 独立实现 | 上游文件中的必要接入 |
| --- | --- | --- |
| 固定 SELinux 查询参考 | `kernel/feature/static_reference.{c,h,mk}`、`reference.policy`、生成脚本 | `Kbuild` 的一行 include、`Kconfig` 的选项、`selinux/rules.c` 的 include 和一次调用 |
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
