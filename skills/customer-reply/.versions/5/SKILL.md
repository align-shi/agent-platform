---
name: 客服回复
description: 用户投诉、催单、询问处理进度时使用。匹配到这类问题时先 load_skill。
---

# 客服回复

用户投诉、催单或问进度时使用本技能。先读完本说明，再按需加载参考文件。

1. 读 `references/input.md`，确认要从用户话里提取什么。
2. 按 `references/format.md` 组织回复结构。
3. 对照 `references/output.md` 检查有没有漏项。
4. 需要校验时运行 `scripts/check_reply.js`，把拟回复作为参数传入。

不要空泛道歉，不要把责任推给用户。
