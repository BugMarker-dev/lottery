package com.lottery;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 从体彩官方 API 拉取大乐透历史开奖数据（gameNo=85）。
 *
 * <p>关于 TLS：本类使用 JDK 默认的证书校验与主机名校验，不做任何绕过。
 * 若运行时报 SSLHandshakeException，正确做法是把服务端证书导入 JDK truststore：
 * <pre>
 *   keytool -import -alias sporttery -file sporttery.crt ^
 *           -keystore "%JAVA_HOME%\jre\lib\security\cacerts"
 * </pre>
 * 不要使用空的 X509TrustManager 或恒真的 HostnameVerifier —— 它们是 JVM 级全局副作用
 * （HttpsURLConnection.setDefaultXXX），会使本进程后续所有 HTTPS 请求都失去防中间人
 * 能力，而不只是这一个接口。
 */
public class HistoryFetcher {

    private static final String API_BASE =
            "https://webapi.sporttery.cn/gateway/lottery/getHistoryPageListV1.qry"
                    + "?gameNo=85&provinceId=0&isVerify=1&pageNo=1&pageSize=";

    /** 模拟 Chrome 请求头，避免 403 反爬 */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 15000;

    /** 前区号码个数：开奖结果里前 5 位是前区 */
    private static final int FRONT_COUNT = 5;
    /** 前区 5 个 + 后区 2 个 */
    private static final int EXPECTED_NUMBERS = FRONT_COUNT + 2;

    public List<HistoryRecord> fetch(int pageSize) throws Exception {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize 必须大于 0，当前值：" + pageSize);
        }
        return parseRecords(httpGet(API_BASE + pageSize));
    }

    public List<HistoryRecord> fetch() throws Exception {
        return fetch(100);
    }

    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            conn.setRequestProperty("Referer", "https://www.sporttery.cn/");
            conn.setRequestProperty("Origin", "https://www.sporttery.cn");
            conn.setRequestProperty("Sec-Fetch-Site", "same-site");
            conn.setRequestProperty("Sec-Fetch-Mode", "cors");
            conn.setRequestProperty("Sec-Fetch-Dest", "empty");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code != 200) {
                throw new RuntimeException("服务器返回 HTTP " + code + "，请稍后重试");
            }
            return readBody(conn);
        } finally {
            conn.disconnect();
        }
    }

    private String readBody(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * 解析接口返回的 JSON。格式异常的期次会被跳过并打印提示，而不是静默产出错误号码。
     *
     * @throws IllegalStateException 整体结构异常或没有任何可用记录时抛出
     */
    private List<HistoryRecord> parseRecords(String json) {
        JSONObject value = new JSONObject(json).optJSONObject("value");
        if (value == null) {
            throw new IllegalStateException("接口返回结构异常：缺少 value 字段");
        }
        JSONArray list = value.optJSONArray("list");
        if (list == null) {
            throw new IllegalStateException("接口返回结构异常：缺少 value.list 字段");
        }

        List<HistoryRecord> records = new ArrayList<>(list.length());
        int skipped = 0;

        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) {
                skipped++;
                continue;
            }
            String issue = item.optString("lotteryDrawNum", "").trim();
            String date = item.optString("lotteryDrawTime", "").trim();
            // 格式: "03 08 14 20 27 09 11"（前 5 个前区，后 2 个后区）
            String raw = item.optString("lotteryDrawResult", "").trim();

            if (issue.isEmpty() || raw.isEmpty()) {
                skipped++;
                continue;
            }

            String[] parts = raw.split("\\s+");
            if (parts.length != EXPECTED_NUMBERS) {
                System.err.println("跳过第 " + issue + " 期：号码个数为 " + parts.length
                        + "，期望 " + EXPECTED_NUMBERS + "（原始值：" + raw + "）");
                skipped++;
                continue;
            }

            HistoryRecord record = buildRecord(issue, date, parts);
            if (record == null) {
                System.err.println("跳过第 " + issue + " 期：号码解析失败（原始值：" + raw + "）");
                skipped++;
                continue;
            }
            records.add(record);
        }

        if (records.isEmpty()) {
            throw new IllegalStateException("未解析到任何有效的开奖记录，原始响应：" + json);
        }
        if (skipped > 0) {
            System.err.println("共跳过 " + skipped + " 期格式异常的数据。");
        }
        return records;
    }

    /** 号码非法时返回 null，由调用方决定如何处理 */
    private HistoryRecord buildRecord(String issue, String date, String[] parts) {
        List<Integer> front = new ArrayList<>(FRONT_COUNT);
        List<Integer> back = new ArrayList<>(EXPECTED_NUMBERS - FRONT_COUNT);

        for (int j = 0; j < parts.length; j++) {
            int n;
            try {
                n = Integer.parseInt(parts[j]);
            } catch (NumberFormatException e) {
                return null;
            }
            if (n < 1) {
                return null;
            }
            if (j < FRONT_COUNT) {
                front.add(n);
            } else {
                back.add(n);
            }
        }
        return new HistoryRecord(issue, date, front, back);
    }
}
