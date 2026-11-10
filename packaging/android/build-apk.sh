#!/bin/bash
# 大乐透安卓 APK 构建脚本（不依赖 Gradle / Android Studio）
# 依赖：JDK 8 + sdk/android-11 下的 platform(android.jar) 与 build-tools
# 用法：bash build-apk.sh
set -e

cd "$(dirname "$0")"
PROJ="$(pwd)"
SDK="$PROJ/sdk/android-11"
AJAR="$SDK/android.jar"
OUT="$PROJ/../DaLeTou.apk"
KS="$PROJ/release.keystore"
KS_ALIAS="lottery"

# 签名密钥密码不写进脚本：从环境变量 LOTTERY_KS_PASS 读取。
# 用法：export LOTTERY_KS_PASS='你的密码'  再运行本脚本。
KS_PASS="${LOTTERY_KS_PASS:-}"
if [ -z "$KS_PASS" ]; then
  echo "错误：未设置环境变量 LOTTERY_KS_PASS（安卓签名密钥 release.keystore 的密码）。"
  echo "      请先执行： export LOTTERY_KS_PASS='你的密码'"
  echo "      注意：release.keystore 是覆盖升级已安装 App 的唯一凭证，务必在仓库外备份。"
  exit 1
fi

echo "=== 1. 准备 assets 与图标 ==="
mkdir -p assets build/tools
# 手机端文件全部平铺到 assets 根目录（避免 Windows 下子目录路径分隔符问题）
rm -rf assets && mkdir -p assets
cp "$PROJ/../../mobile/"* assets/
ls assets/
javac -encoding UTF-8 -d build/tools "$PROJ/../tools/IconGen.java"
java -cp build/tools IconGen build/app.ico \
  "png:48:res/mipmap-mdpi/ic_launcher.png" \
  "png:72:res/mipmap-hdpi/ic_launcher.png" \
  "png:96:res/mipmap-xhdpi/ic_launcher.png" \
  "png:144:res/mipmap-xxhdpi/ic_launcher.png" \
  "png:192:res/mipmap-xxxhdpi/ic_launcher.png"

echo "=== 2. 编译资源（aapt2） ==="
rm -rf build/res.zip build/gen build/classes build/dex build/base.apk
"$SDK/aapt2.exe" compile --dir res -o build/res.zip
"$SDK/aapt2.exe" link -o build/base.apk \
  -I "$AJAR" \
  --manifest AndroidManifest.xml \
  -A assets \
  --java build/gen \
  --min-sdk-version 21 --target-sdk-version 30 \
  --version-code 1 --version-name 2.1 \
  build/res.zip

echo "=== 3. 编译 Java 源码 ==="
mkdir -p build/classes
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJAR" \
  -d build/classes \
  build/gen/com/lottery/app/R.java src/com/lottery/app/MainActivity.java \
  > build/javac.log 2>&1 || { cat build/javac.log; exit 1; }
grep -v "bootstrap class path" build/javac.log || true

echo "=== 4. dex（d8） ==="
mkdir -p build/dex
java -cp "$SDK/lib/d8.jar" com.android.tools.r8.D8 \
  --lib "$AJAR" --min-api 21 --release \
  --output build/dex \
  $(find build/classes -name "*.class")

echo "=== 5. 打包并 zipalign ==="
cp build/base.apk build/unsigned.apk
jar uf build/unsigned.apk -C build/dex classes.dex
"$SDK/zipalign.exe" -f 4 build/unsigned.apk build/aligned.apk

echo "=== 6. 签名 ==="
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias "$KS_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -dname "CN=DaLeTou, OU=Personal, O=Personal, L=CN, C=CN" >/dev/null 2>&1
  echo "已生成签名密钥：$KS"
fi
# apksigner：新版（build-tools 30）要 Java 9+，本机只有 JDK 8，
# 所以用 build-tools 29.0.3 里的 apksigner 0.9（支持 v1/v2/v3，Java 8 可跑）
SIGNER="$PROJ/sdk/bt29/android-10/lib/apksigner.jar"
if [ ! -f "$SIGNER" ]; then
  echo "缺少 apksigner.jar（$SIGNER）"; exit 1
fi
rm -f "$OUT"
java -cp "$SIGNER" com.android.apksigner.ApkSignerTool sign \
  --ks "$KS" --ks-key-alias "$KS_ALIAS" \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$OUT" build/aligned.apk

echo "=== 7. 校验 ==="
java -cp "$SIGNER" com.android.apksigner.ApkSignerTool verify --print-certs "$OUT" | head -6
echo "--- badging ---"
"$SDK/aapt2.exe" dump badging "$OUT" | head -6
ls -la "$OUT"
echo "=== 构建完成 ==="
