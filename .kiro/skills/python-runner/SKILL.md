---
description: 执行 Python 脚本并返回结果
---

# Python Script Runner

这个 Skill 用于执行 Python 脚本。

## 使用方法

当用户需要运行 Python 代码时，使用以下步骤：

1. 将 Python 代码保存到临时文件（如 `script.py`）
2. 使用 `python3 script.py` 命令执行
3. 返回执行结果

## 示例

```python
# 简单的 Hello World
print("Hello from Python!")

# 计算示例
result = sum(range(1, 101))
print(f"1到100的和是: {result}")
```

## 注意事项

- 确保系统已安装 Python 3
- 脚本执行在当前工作目录下
- 可以使用 `pip` 安装的第三方库
