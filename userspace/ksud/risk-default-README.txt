本分支的模块安装风险扫描默认关闭。

没有持久配置，或者配置读不到时，ksud module risk status 会返回 Disabled。
模块安装不进入 contains_risk_in_module，因此不会联网取 GitHub 风险规则，也不会执行内置风险规则扫描。
ZIP 读取、module.prop 解析、模块 ID 检查和其他正常安装流程不变。
这只解决风险扫描环节的等待，不保证所有模块都能安装，也没有关闭其他联网功能。

需要时仍能手动开启或关闭，管理器里原来的开关也保留。
ksud module risk enable
ksud module risk disable
ksud module risk status

已经保存 enabled=true 的设备会继续开启，这是尊重用户明确设置，并不是新默认值失效。
这类设备若要马上关闭，用 root 执行 /data/adb/ksud module risk disable。
只更新内核不会替换管理器内置的 ksud，需要使用本分支编译的新 ksud 或管理器。

scripts/tests/test_risk_default.py 在临时目录编译真实 getter 和布尔解析函数进行回归测试。
覆盖无配置、读取失败、显式开启/关闭和安装入口短路，不改手机配置。
