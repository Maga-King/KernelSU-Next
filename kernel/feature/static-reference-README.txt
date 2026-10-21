这是固定查询参考策略的实验分支，不是修改手机实际执行策略的工具。

2026-10-21：修复参考策略干预真实应用启动的问题。
移除继承自上游的 setprocattr 拦截，进程域切换全部交回原生 SELinux。
查询上下文只在参考策略的私有 SID 表内解析，不再向活动策略同步 SID。
查询入口还要确认调用者已进入真实 appdomain，并排除 zygote、app_zygote、webview_zygote 及带 zygote 的厂商域。
不能只判断 UID：Android 先降 UID，再做 security_check_context 和 setcon；启动前校验必须仍使用真实策略。
读取活动策略仅用于判定调用者身份，不向活动策略或 SID 表写入。
本修复同时适用于动态备份和三套静态参考，参考 policy 文件本身没有修改。

2026-10-18：LKM workflow 新增开关及 OriginOS / ColorOS17 / HyperOS 三选一。
使用方法、当前文件摘要与来源以 references/README.md、references/manifest.json 为准。
reference.policy 仍保留为原来的 OriginOS 默认参考，没有移动或被其他系统覆盖。
以下是初始基线的制作记录；OriginOS 二进制后来经过 2026-10-15 审阅更新，旧摘要不是当前文件摘要。

reference.policy 是 2026-10-04 运行中的 OriginOS DSU 分区 CIL 重编译结果。
策略格式 30，大小 2278330 字节，SHA256 如下：
954080162a620cb6273a5f583d36db9bb3ada65a3ea0abfd2eba5bc8d5dc5849
原始内核活动策略也已离线留档，但没有把 root 模块注入后的活动策略直接当作参考。
参考策略去掉了 system_server execmem、全部 permissive 标记和源码编译器指出的 neverallow 冲突授权。
普通应用 JIT 所需的 execmem 保留。清理时删除整条冲突 allow，可能连带减少合法查询授权。
它只适合当查询参考，不适合刷入、加载到实际 SELinux 或当 ROM 编译策略使用。
neverallow 不存在于编译后的二进制里；检查范围是本次提取 CIL 里实际包含的断言。

构建时需要 CONFIG_KSU_STATIC_SELINUX_REFERENCE=y，默认关闭。
运行时仍需开启 KSUN 原有 selinux_hide。固定参考不改变 feature 开关及原来的作用范围。
固定参考只替换 backup_sepolicy 的构建来源；查询消费者与实际进程切换已隔离。
selinux_state.policy 仍使用真实策略，由上游原有逻辑注入真实 KSU 权限。
不会在开机时重新清洗策略；开机只解析编译进内核的固定二进制。

启动时会核对策略格式版本及全部 class/权限编号。
不匹配、内存不足或解析失败时，回退为上游动态备份，不强行使用固定策略。
参考策略的 policydb 和 sidtab 独立分配，不共用实际策略的指针。

注意：查询结果变了不代表实际安全风险消失。
有些软件会先查询再决定是否执行，查询视图变化也可能影响它们。
当前已移除上游用备份验证 setcurrent 目标的逻辑，参考缺少 ROM 域不会拦截真实域切换。
这里保证的是不替换内核执行策略和域切换；应用若根据查询结果自行决定行为，仍会受到返回值影响。
该实验不保证所有 ROM 兼容，也不承诺隐藏所有 root、hook、内核或完整性检测信号。
后续 ROM 升级可能需要重新制作固定参考，即使 class 编号相同也不能保证业务语义相同。

scripts/embed_static_reference.py 负责生成编译用头文件。
scripts/tests/test_static_reference.py 校验生成过程和接入位置。
tools/static_reference_probe.c 可用 libsepol 做只读离线探针检查，不会加载内核策略。
CI 生成的 ko 仅用于验证源码能编译，不是本手机可直接加载的安装包。
手机已经内置 KSUN 时尤其不要另行加载这个 ko。

本地验证：未跳过 neverallow 的完整 CIL 编译通过，二进制回读和 roundtrip 通过。
libsepol 比较了当前策略与参考策略的 108 个 class 和全部权限编号，匹配。
13 个 root 产品上下文探针全部无效，13 个实际访问计算里只有两项正向控制允许。
class=0 的第 14 项按 Linux 查询源码建模，不等于内核实测。
原始活动策略 SHA256 在制作前后相同，手机实际策略没有变化。
这些结果不代表完整 Hunter 已在改版 KSUN 内核上通过；还需要后续实机测试。

查询接口使用编号的原理可查 Linux 6.6 源码：
https://github.com/torvalds/linux/blob/v6.6/security/selinux/ss/services.c
