# lottery · 大乐透号码生成器

基于体彩历史开奖数据的号码生成与预测工具，提供 Web 界面与命令行两种用法。

- **后端**：Java 8+，使用 JDK 内置 `com.sun.net.httpserver`，无第三方 Web 框架
- **构建**：Maven，`maven-shade-plugin` 打成单个可执行 jar
- **前端**：原生 HTML + Vue（本地引入，无构建步骤），含 PWA 移动端页面
- **数据源**：中国体彩网公开接口 `webapi.sporttery.cn`

## 目录结构

```
lottery/
├── src/main/java/com/lottery/   后端源码
│   ├── LotteryApp.java          程序入口（交互模式 / CLI 模式）
│   ├── LotteryServer.java       内置 HTTP 服务与接口路由
│   ├── LotteryGenerator.java    号码生成逻辑
│   ├── LotteryAnalyzer.java     历史数据分析与预测
│   ├── HistoryFetcher.java      历史开奖数据抓取
│   ├── HistoryRecord.java       开奖记录模型
│   └── LotteryTicket.java       单注号码模型
├── static/                      桌面端 Web 页面
├── mobile/                      移动端 PWA 页面
├── pom.xml                      Maven 配置
├── start.bat                    Windows 一键启动脚本
└── .gitignore
```

## 构建与运行

需要 **JDK 8 或更高版本**（`java -version` 可验证）和 **Maven**。

```bash
mvn clean package
java -jar target/lottery-1.0.0.jar
```

Windows 下也可以先构建一次，之后直接双击 `start.bat` 启动 —— 它执行的就是上面那条 `java -jar`，并会在端口被占用时自动顺延到下一个可用端口。

### 命令行用法

```bash
java -jar lottery-1.0.0.jar          # 交互模式
java -jar lottery-1.0.0.jar 5        # 直接随机生成 5 注
java -jar lottery-1.0.0.jar p 5      # 拉取历史数据并预测生成 5 注
```

## 关于仓库内容

以下目录属于构建产物，已通过 `.gitignore` 排除，不纳入版本控制：

| 目录 | 内容 | 体积 |
|---|---|---|
| `target/` | Maven 编译输出与 shade 后的 jar | ~16 MB |
| `packaging/` | 打包成品：内嵌 JRE、Android SDK、安装包、APK | ~512 MB |

需要分发安装包时，在本地执行构建后从 `packaging/` 取用，或走 Releases 单独上传。

## 免责声明

本项目仅用于编程学习与技术演示。彩票开奖结果是独立随机事件，任何基于历史数据的"预测"都不具备可靠性，请勿据此投注。
