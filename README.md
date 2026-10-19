# Rider EReader

一个 JetBrains Rider 插件，可以读取 EPUB，并把内容分页显示在底部状态栏。

## 功能

- 打开 `.epub` 文件
- 在下方状态栏显示当前阅读片段
- 自动保存当前书籍和阅读位置，重启 Rider 后从上次位置继续阅读
- 点击状态栏阅读文本进入下一页
- 鼠标悬停时高亮阅读区域，右键打开阅读菜单
- 可在右键菜单或 `Tools | EReader` 中控制阅读和显示选项
- 支持鼠标悬停高亮、完整文本提示和滚轮翻页
- 通过快捷键进入下一页 / 上一页
- 从目录中选择章节
- 从右键菜单或 `Tools | EReader` 复制当前页正文
- 一键隐藏 / 恢复显示

## 默认快捷键

- 打开书籍：`Ctrl+Alt+O`
- 下一页：`Ctrl+Alt+Right`
- 上一页：`Ctrl+Alt+Left`
- 选择目录：`Ctrl+Alt+T`
- 隐藏/显示：`Ctrl+Alt+H`

也可以在 Rider 的 `Settings | Keymap` 中搜索 `EReader` 修改快捷键。

## 菜单功能

在状态栏阅读文本上点击鼠标右键，或打开 `Tools | EReader`，均可使用以下功能：

| 菜单项                              | 功能                                                         |
| ----------------------------------- | ------------------------------------------------------------ |
| `EReader: Open EPUB`                | 选择并打开一本 EPUB；打开成功后从第一页开始阅读并恢复显示阅读条 |
| `EReader: Previous Page`            | 进入上一页；当前章节已到开头时进入上一章节的最后一页         |
| `EReader: Next Page`                | 进入下一页；当前章节已到末尾时进入下一章节的第一页           |
| `EReader: Choose Chapter`           | 打开目录选择窗口并跳转到指定章节的第一页，同时恢复显示阅读条 |
| `EReader: Copy Current Page`        | 将当前页正文复制到剪贴板，不包含书名、章节名和阅读进度       |
| `EReader: Hover Highlight`          | 启用或关闭鼠标悬停在阅读条上时的高亮效果；设置会随当前项目保存 |
| `EReader: Show Text on Hover`       | 启用或关闭悬停时显示完整阅读文本的提示；设置会随当前项目保存 |
| `EReader: Mouse Wheel Page Turning` | 启用或关闭鼠标滚轮翻页；默认关闭，设置会随当前项目保存       |
| `EReader: Hide or Show`             | 隐藏或恢复状态栏阅读条；未打开 EPUB 时也可隐藏 `EReader: no book`，隐藏时翻页操作不会改变当前页 |

### 鼠标滚轮翻页

启用 `EReader: Mouse Wheel Page Turning` 后，只有同时满足以下条件时才会响应鼠标滚轮：

- 已打开 EPUB
- 状态栏阅读条处于显示状态
- 鼠标指针悬停在阅读条上

向上滚动进入上一页，向下滚动进入下一页。鼠标位于编辑器或其他界面区域时，滚轮保持原有功能。

## 开发约定

- 每次新增用户可见功能，都必须同时注册到 `plugin.xml` 的 `EReader.Actions`。
- `EReader.Actions` 同时用于 `Tools | EReader` 和状态栏右键菜单，不单独维护两套菜单功能。
- 普通命令使用 `AnAction`，开关选项使用 `ToggleAction`；用户设置保存在 `EReaderService.State`。
- 新功能完成后同步更新 README，并执行 `compileKotlin` 和 `buildPlugin` 验证。

## 构建

需要本机安装 JDK 21 和 Gradle。

```powershell
gradle buildPlugin
```

构建产物在：

```text
build/distributions/
```

在 Rider 中通过 `Settings | Plugins | Install Plugin from Disk...` 安装 zip 包。
