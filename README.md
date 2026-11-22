# 云跳（YunTiao）

一个**纯原生、零第三方依赖**的安卓开屏广告跳过工具。基于系统无障碍服务模拟点击，支持自定义规则、微信小程序开屏广告，主打省电省内存。

---

## 功能

- 自动点击开屏广告的「跳过 / Skip / 关闭广告」按钮
- **微信小程序广告**：节点找不到时自动用屏幕坐标手势兜底（可自定义坐标、次数、延时）
- 按 App 包名定制规则，支持通配 `*`、Activity 名过滤
- 匹配方式：包含 / 完全相等 / 正则
- 每条规则独立开关，全局总开关
- 导入 / 导出规则（剪贴板 JSON，方便备份和分享）
- 累计与当日跳过次数、内存环形日志

## 省电省内存设计

| 措施 | 说明 |
| --- | --- |
| 事件驱动 | 只监听窗口状态变化与内容变化，**不轮询、不常驻前台通知、不申请网络权限** |
| 系统级节流 | `notificationTimeout=100ms`；应用层再按 250ms 节流 |
| 有界扫描 | 节点扫描深度 ≤10、节点数 ≤150，命中即停 |
| 省电模式 | 只响应窗口切换，扫描深度降到 6、节点数降到 60、坐标重试限 1 次 |
| 轻量存储 | 仅 SharedPreferences + org.json，无数据库、无文件日志 |
| 极小体积 | 纯 Android Framework + Java，release 开启代码/资源压缩，APK 约百 KB 级 |
| 无后台唤醒 | 无 WakeLock、无 JobScheduler、无 Alarm |

## 编译

环境：Android Studio（Hedgehog 及以上）、JDK 17、Android SDK Platform 34、Gradle 8.4（AGP 8.2.2）。

1. 用 Android Studio 打开本目录 `AdSkip/`
2. 同步 Gradle（首次需联网下载 Gradle 与 AGP）
3. `Build > Generate Signed Bundle / APK` 生成安装包，或直接运行到手机

> 若提示缺少 `gradle-wrapper.jar`：用 Android Studio 自带 Gradle 执行 `gradle wrapper`，或从任意现有 Gradle 项目复制 `gradle-wrapper.jar` 到 `gradle/wrapper/` 后重新同步。

## GitHub 云编译（推荐，无需本机环境）

项目已内置 `.github/workflows/build-apk.yml`，推到 GitHub 后自动编译并签名：

1. 在 GitHub 新建一个仓库（例如 `AdSkip`）
2. 把 `AdSkip/` 目录下的**全部内容**（含隐藏的 `.github/` 和 `.gitignore`）上传到仓库
   - 网页上传：`Add file > Upload files`，把文件拖进去；注意隐藏文件夹也要一起传
   - 或用命令行：
     ```bash
     cd AdSkip
     git init
     git add .
     git commit -m "init"
     git branch -M main
     git remote add origin https://github.com/你的用户名/AdSkip.git
     git push -u origin main
     ```
3. 进入仓库的 `Actions` 标签页，等待自动运行；也可点 `Run workflow` 手动触发
4. 运行成功后，在本次运行页面底部的 `Artifacts` 下载 **`YunTiao-apk`**
5. 解压得到 `app-release.apk`，传到手机安装

触发条件：推送到 `main`/`master` 分支、推送 `v*` 标签、或在 Actions 页手动点击。

> 排错：如果构建报 `Directory ... does not contain a Gradle build`，说明项目源码没有上传完整。仓库根目录必须能看到 `settings.gradle`、`build.gradle`、`app/` 和 `gradle/`。网页上传时隐藏文件夹容易被漏掉，务必检查。

> 云编译每次会生成**临时签名密钥**，所以不同构建版本的签名不一致。手机上安装新版本前请先卸载旧版。
> 密钥只在本次 CI 任务中存在，任务结束后自动销毁，不会上传或留存。

## 使用

1. 安装后打开 App，点「去开启无障碍服务」
2. 在系统设置中找到「云跳」并开启无障碍权限
   - 部分国产 ROM 还需在系统设置中把本 App 加入「自启动 / 后台高耗电白名单」，否则服务会被杀
3. 回到 App，确认状态显示「已开启」
4. 默认内置两条规则：
   - **通用开屏广告**：适用于大多数原生按钮
   - **微信 / 小程序广告**：`com.tencent.mm`，开启坐标兜底，默认点屏幕 (88%, 10%)

## 规则字段说明

| 字段 | 含义 |
| --- | --- |
| 目标包名 | `*` 表示所有应用；精确包名优先级高于 `*` |
| 活动名过滤 | 只在该 Activity 名包含指定文字时生效，留空不过滤 |
| 关键字 | 节点文本 / 内容描述中出现的跳过词，逗号分隔 |
| 匹配方式 | 包含 / 完全相等 / 正则（正则不区分大小写） |
| 只点可点击节点 | WebView 渲染的伪按钮往往不可点击，建议关闭 |
| 坐标手势兜底 | 找不到节点时按百分比坐标点击，API 24+ 生效 |
| 延时 ms | 广告出现后等待多久再点 |
| 重试次数 | 坐标兜底最多尝试几次 |
| 冷却 ms | 同一应用两次成功跳过的最小间隔，防止误触 |
| 窗口 ms | 一次开屏的有效期，超时不再尝试 |
| X/Y (%) | 坐标兜底位置，相对屏幕宽高的百分比 |

## 微信小程序广告特别说明

微信小程序广告很多是直接在 WebView / Canvas 中绘制的，**无障碍树里不一定有「跳过」节点**。这种情况只能靠坐标手势兜底：

- 默认坐标 (88%, 10%) 适配大多数右上角跳过按钮
- 如果点不中，在规则编辑里微调 X/Y（例如 90/12）
- 不同手机状态栏高度不同，Y 值可能需要 ±2 的调整
- 若广告是完全自绘且位置随机，任何自动化工具都无法 100% 命中

## 隐私与限制

- 不申请网络、存储、定位等权限，不上传任何数据；规则只保存在本机
- 本工具只是模拟用户点击，**不破解、不修改其他 App**，但可能违反部分 App 或平台的用户协议，请自行判断是否使用
- 无障碍服务一旦被系统回收需要重新开启； Android 13+ 对后台无障碍服务限制更严格
- 部分 App 使用自定义渲染或反自动化检测，跳过成功率无法保证
- 本工具仅供个人学习与便利使用，请勿用于商业分发或恶意目的

## 目录结构

```
AdSkip/
├── .github/workflows/build-apk.yml       GitHub 云编译配置
├── .gitignore
├── README.md
├── app/src/main/java/com/chyxu/adskip/
│   ├── SkipAccessibilityService.java   核心服务与规则引擎
│   ├── Rule.java / RuleStore.java      规则模型与持久化
│   ├── MainActivity.java               主界面
│   ├── RuleEditActivity.java           规则编辑
│   ├── LogActivity.java / AppLog.java  日志
│   └── res/...                         布局与资源
├── app/src/main/AndroidManifest.xml
├── app/src/main/res/xml/accessibility_service_config.xml
└── build.gradle / settings.gradle / gradle.properties
```
