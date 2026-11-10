# lottery · 财富密码（大乐透号码生成器）

基于体彩历史开奖数据的号码生成与预测工具，提供 Web 界面、命令行和安卓 App 三种形态。
安装到手机/桌面后显示名为「财富密码」，包名 `com.lottery.app` 保持不变以保证覆盖升级。

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

只需要 **JDK 8**（`java -version` 验证）。不装 Maven 也能构建。

> ⚠️ 仓库里**不含任何 jar 文件**（`*.jar` 已被 `.gitignore` 排除），`clone` 下来必须先构建一次才能运行。

### 方式一：手工构建（项目最初采用的方式，无需 Maven）

```bash
# 1. 编译，classpath 指向本地仓库里的 org.json
JSON_JAR="$HOME/.m2/repository/org/json/json/20231013/json-20231013.jar"
javac -encoding UTF-8 -cp "$JSON_JAR" -d target/classes $(find src/main/java -name "*.java")

# 2. 打 fat jar（把 org.json 的类一起解进去，否则运行时报 NoClassDefFoundError）
mkdir -p target/fat && cd target/fat
unzip -oq "$JSON_JAR" && cp -r ../classes/* .
jar cfe ../../lottery-web.jar com.lottery.LotteryServer .
cd ../..
```

Windows 的 cmd / Git Bash 里 `;` 分隔的 classpath 会被 MSYS 改写，需要加前缀
`MSYS2_ARG_CONV_EXCL='*'`。

### 方式二：有 Maven 的话

```bash
mvn clean package
java -jar target/lottery-1.0.0.jar
```

`pom.xml` 里的 `mainClass` 已设为 `com.lottery.LotteryServer`，构建出的 jar 直接启动 Web 服务。

### 启动

```bash
java -jar lottery-web.jar          # 默认 8080，端口被占自动顺延到 8081~8089
java -jar lottery-web.jar 9000     # 指定端口
java -jar lottery-web.jar silent   # 不自动打开浏览器（后台运行用）
```

Windows 下也可以先构建一次，之后直接双击 `start.bat`。服务监听 `0.0.0.0`，启动后终端会打印本机与局域网访问地址，同网段手机可直接访问。

### 命令行模式（CLI）

jar 的默认入口是 Web 服务，命令行版要显式指定类名：

```bash
java -cp lottery-web.jar com.lottery.LotteryApp       # 交互模式
java -cp lottery-web.jar com.lottery.LotteryApp 5     # 随机生成 5 注
java -cp lottery-web.jar com.lottery.LotteryApp p 5   # 拉取历史数据并预测 5 注
```

### 安卓 APK

构建脚本不依赖 Gradle / Android Studio，走 aapt2 + javac + d8 + zipalign + apksigner：

```bash
export LOTTERY_KS_PASS='签名密钥密码'    # 密码不写在脚本里，必须通过环境变量传入
bash packaging/android/build-apk.sh
```

依赖 `packaging/android/sdk/` 下解压好的 platform 与 build-tools 目录（**勿删**）。
`release.keystore` 是覆盖升级已安装 App 的唯一凭证，请在仓库外另行备份。

## 关于仓库内容

以下目录属于构建产物，已通过 `.gitignore` 排除，不纳入版本控制：

| 目录 | 内容 | 体积 |
|---|---|---|
| `target/` | Maven 编译输出与 shade 后的 jar | ~16 MB |
| `packaging/` | 打包成品：内嵌 JRE、Android SDK、安装包、APK | ~512 MB |

需要分发安装包时，在本地执行构建后从 `packaging/` 取用，或走 Releases 单独上传。

## 免责声明

本项目仅用于编程学习与技术演示。彩票开奖结果是独立随机事件，任何基于历史数据的"预测"都不具备可靠性，请勿据此投注。
