package com.lottery;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 大乐透 Web 服务：JDK 内置 HttpServer，零第三方依赖。
 *
 * <p>监听 0.0.0.0，同一网段内的设备（手机、其他电脑）可直接通过局域网 IP 访问。
 *
 * <p>接口：
 * <pre>
 *   GET /api/generate?count=5   随机生成
 *   GET /api/predict?count=3    基于历史数据预测（首次调用会拉取 100 期并缓存）
 *   GET /api/stats              热号统计
 *   GET /api/recent?count=10    最近开奖记录
 *   GET /                       前端页面（static/ 目录）
 * </pre>
 *
 * <p>启动：{@code java ... com.lottery.LotteryServer [端口]}，默认 8080。
 */
public class LotteryServer {

    private static final int DEFAULT_PORT = 8080;
    private static final Path STATIC_DIR = Paths.get("static");
    private static final int MAX_TICKETS = LotteryGenerator.MAX_BATCH_COUNT;
    private static final int HISTORY_PAGE_SIZE = 100;

    private final LotteryGenerator generator = new LotteryGenerator();
    /** 懒加载缓存；加载失败置回 null，下次请求自动重试 */
    private volatile LotteryAnalyzer analyzer;

    /** 端口被占时向后顺延尝试的最大次数（8080 → 8089） */
    private static final int MAX_PORT_TRIES = 10;

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        boolean openBrowser = true;
        for (String arg : args) {
            if ("silent".equalsIgnoreCase(arg)) {
                openBrowser = false;          // 不自动开浏览器（后台/脚本运行用）
            } else {
                try {
                    port = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    System.out.println("参数无效：" + arg + "（应为端口号或 silent），端口用默认 " + DEFAULT_PORT);
                }
            }
        }
        new LotteryServer().start(port, openBrowser);
    }

    private void start(int preferredPort, boolean openBrowser) throws IOException {
        // 端口被占自动顺延，不再直接 BindException 闪退
        HttpServer server = null;
        int port = preferredPort;
        for (int i = 0; i < MAX_PORT_TRIES; i++) {
            try {
                server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
                break;
            } catch (IOException e) {
                System.out.println("端口 " + port + " 已被占用，尝试 " + (port + 1) + " ...");
                port++;
            }
        }
        if (server == null) {
            throw new IOException("端口 " + preferredPort + " ~ " + (port - 1) + " 全部被占用，无法启动");
        }
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/api/generate", ex -> guard(ex, this::handleGenerate));
        server.createContext("/api/predict", ex -> guard(ex, this::handlePredict));
        server.createContext("/api/stats", ex -> guard(ex, this::handleStats));
        server.createContext("/api/recent", ex -> guard(ex, this::handleRecent));
        server.createContext("/", ex -> guard(ex, this::handleStatic));
        server.start();

        System.out.println("============================================");
        System.out.println("   大乐透号码生成器 v2.1  Web 服务已启动");
        System.out.println("============================================");
        System.out.println("  实际端口:    " + port + (port != preferredPort ? "  (首选 " + preferredPort + " 被占，已自动顺延)" : ""));
        System.out.println("  本机访问:    http://localhost:" + port);
        for (String ip : localIps()) {
            System.out.println("  局域网访问:  http://" + ip + ":" + port);
        }
        System.out.println("  静态资源目录: " + STATIC_DIR.toAbsolutePath());
        System.out.println("  按 Ctrl+C 停止服务");
        System.out.println("============================================");

        if (openBrowser) {
            final String url = "http://localhost:" + port;
            // 延迟 2 秒等浏览器就绪，打开失败不影响服务
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                    }
                } catch (Exception e) {
                    System.out.println("自动打开浏览器失败（不影响服务），请手动访问: " + url);
                }
            }, "browser-opener").start();
        }
    }

    // ---------------------------------------------------------------
    // API 处理
    // ---------------------------------------------------------------

    private void handleGenerate(HttpExchange ex) throws IOException {
        int count = intParam(ex, "count", 5, 1, MAX_TICKETS);
        List<LotteryTicket> tickets = generator.generate(count);
        sendJson(ex, 200, new JSONObject()
                .put("mode", "random")
                .put("count", tickets.size())
                .put("tickets", ticketsJson(tickets)));
    }

    private void handlePredict(HttpExchange ex) throws IOException {
        int count = intParam(ex, "count", 5, 1, MAX_TICKETS);
        LotteryAnalyzer ready = ensureAnalyzer();
        if (ready == null) {
            sendJson(ex, 503, new JSONObject().put("error", "历史数据加载失败，请稍后重试"));
            return;
        }
        List<LotteryTicket> tickets = ready.predict(count);
        sendJson(ex, 200, new JSONObject()
                .put("mode", "predict")
                .put("count", tickets.size())
                .put("total", ready.getRecordCount())
                .put("tickets", ticketsJson(tickets)));
    }

    private void handleStats(HttpExchange ex) throws IOException {
        LotteryAnalyzer ready = ensureAnalyzer();
        if (ready == null) {
            sendJson(ex, 503, new JSONObject().put("error", "历史数据加载失败，请稍后重试"));
            return;
        }
        sendJson(ex, 200, new JSONObject()
                .put("total", ready.getRecordCount())
                .put("recentN", ready.getRecentN())
                .put("front", freqJson(ready.getFrontGlobalFreq(), ready.getFrontRecentFreq(),
                        35, ready.getRecordCount(), ready.getRecentN()))
                .put("back", freqJson(ready.getBackGlobalFreq(), ready.getBackRecentFreq(),
                        12, ready.getRecordCount(), ready.getRecentN())));
    }

    private void handleRecent(HttpExchange ex) throws IOException {
        int count = intParam(ex, "count", 10, 1, 100);
        LotteryAnalyzer ready = ensureAnalyzer();
        if (ready == null) {
            sendJson(ex, 503, new JSONObject().put("error", "历史数据加载失败，请稍后重试"));
            return;
        }
        JSONArray arr = new JSONArray();
        for (HistoryRecord r : ready.getLatest(count)) {
            arr.put(new JSONObject()
                    .put("issue", r.getIssue())
                    .put("date", r.getDate())
                    .put("front", new JSONArray(r.getFront()))
                    .put("back", new JSONArray(r.getBack())));
        }
        sendJson(ex, 200, new JSONObject().put("count", arr.length()).put("records", arr));
    }

    private JSONArray ticketsJson(List<LotteryTicket> tickets) {
        JSONArray arr = new JSONArray();
        for (LotteryTicket t : tickets) {
            arr.put(new JSONObject()
                    .put("front", new JSONArray(t.getFront()))
                    .put("back", new JSONArray(t.getBack())));
        }
        return arr;
    }

    /** 组装某区（前区/后区）的全局 + 近期频率，按全局次数降序、号码升序 */
    private JSONObject freqJson(int[] global, int[] recent, int max, int total, int recentN) {
        List<int[]> list = new ArrayList<>();   // [num, globalCount, recentCount]
        for (int i = 1; i <= max; i++) {
            list.add(new int[]{i, global[i], recent[i]});
        }
        list.sort((a, b) -> b[1] != a[1] ? Integer.compare(b[1], a[1]) : Integer.compare(a[0], b[0]));

        JSONArray gArr = new JSONArray();
        JSONArray rArr = new JSONArray();
        for (int[] e : list) {
            gArr.put(new JSONObject()
                    .put("num", e[0]).put("count", e[1])
                    .put("pct", round1(e[1] * 100.0 / Math.max(total, 1))));
            rArr.put(new JSONObject()
                    .put("num", e[0]).put("count", e[2])
                    .put("pct", round1(e[2] * 100.0 / Math.max(recentN, 1))));
        }
        return new JSONObject().put("global", gArr).put("recent", rArr);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** 双重检查锁的懒加载；失败返回 null 且清空缓存，下次请求自动重试 */
    private LotteryAnalyzer ensureAnalyzer() {
        LotteryAnalyzer ready = analyzer;
        if (ready != null) {
            return ready;
        }
        synchronized (this) {
            if (analyzer == null) {
                try {
                    List<HistoryRecord> records = new HistoryFetcher().fetch(HISTORY_PAGE_SIZE);
                    analyzer = new LotteryAnalyzer(records);
                    System.out.println("已加载 " + records.size() + " 期历史数据。");
                } catch (Exception e) {
                    System.out.println("加载历史数据失败：" + e.getMessage());
                    return null;
                }
            }
            return analyzer;
        }
    }

    // ---------------------------------------------------------------
    // 静态文件
    // ---------------------------------------------------------------

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path == null || path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }
        Path file = STATIC_DIR.resolve(path.substring(1)).normalize();
        if (!file.startsWith(STATIC_DIR) || !Files.isRegularFile(file)) {
            sendJson(ex, 404, new JSONObject().put("error", "Not Found: " + path));
            return;
        }
        byte[] body = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", mime(path) + "; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private static String mime(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    // ---------------------------------------------------------------
    // 工具
    // ---------------------------------------------------------------

    private interface Handler {
        void handle(HttpExchange ex) throws IOException;
    }

    /** 统一异常兜底：任何未捕获异常都转成 500 JSON，而不是裸断连接 */
    private void guard(HttpExchange ex, Handler handler) throws IOException {
        try {
            handler.handle(ex);
        } catch (IllegalArgumentException e) {
            sendJson(ex, 400, new JSONObject().put("error", e.getMessage()));
        } catch (Exception e) {
            System.out.println("处理请求出错 " + ex.getRequestURI() + "：" + e);
            sendJson(ex, 500, new JSONObject().put("error", "服务器内部错误：" + e.getMessage()));
        }
    }

    private int intParam(HttpExchange ex, String name, int def, int min, int max) {
        Map<String, String> q = query(ex);
        if (!q.containsKey(name) || q.get(name).trim().isEmpty()) {
            return def;
        }
        int v;
        try {
            v = Integer.parseInt(q.get(name).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 " + name + " 必须是整数，当前值：" + q.get(name));
        }
        if (v < min || v > max) {
            throw new IllegalArgumentException("参数 " + name + " 取值范围 " + min + "-" + max + "，当前值：" + v);
        }
        return v;
    }

    private Map<String, String> query(HttpExchange ex) {
        Map<String, String> map = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                map.put(decode(pair), "");
            } else {
                map.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
            }
        }
        return map;
    }

    /** Java 8 的 URLDecoder.decode(String, String) 会抛受检异常，包一层 */
    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;   // UTF-8 永远可用，不会走到这里
        }
    }

    private void sendJson(HttpExchange ex, int status, JSONObject body) throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** 枚举非回环的 IPv4 地址，用于打印局域网访问入口 */
    private List<String> localIps() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                Enumeration<InetAddress> addrs = nics.nextElement().getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr.isSiteLocalAddress()
                            && addr.getHostAddress().indexOf(':') < 0) {
                        ips.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return ips;
    }
}
