package com.lottery.app;

import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * 单 Activity + WebView 外壳：页面与业务逻辑全部在 assets/index.html 里（纯前端），
 * 这里只负责把它全屏显示出来，并代理官方接口请求。
 *
 * 为什么代理 webapi.sporttery.cn：
 * 页面从 file:// 加载，fetch 出去的请求 Origin 为 null 且没有 Referer，
 * 体彩官方 WAF 会直接拒绝（手机上表现为 HTTP 502）。这里在原生层拦截
 * 该域名的请求，用 HttpURLConnection 补上 Origin/Referer/UA 后转发，
 * 再把响应流式回给 WebView。浏览器/添加到主屏幕场景不受影响。
 */
public class MainActivity extends Activity {

    private static final String API_HOST = "webapi.sporttery.cn";
    /** 标准 Chrome 移动端 UA（去掉 WebView 的 "; wv)" 标记，减少被 WAF 拦截的概率） */
    private static final String CHROME_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/124.0.0.0 Mobile Safari/537.36";

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webView.setBackgroundColor(0xFFF4F5F7);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);              // localStorage 缓存历史数据
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        // 页面从 file:// 加载，需要放开同源限制才能访问 https 接口
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);
        s.setUserAgentString(CHROME_UA);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (!API_HOST.equals(u.getHost())) {
                    return super.shouldInterceptRequest(view, request);
                }
                return proxyGet(u);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(false);
        }

        webView.loadUrl("file:///android_asset/index.html");
    }

    /** 补头转发官方接口请求；失败时返回 502，让页面走内置离线数据兜底 */
    private WebResourceResponse proxyGet(Uri uri) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(uri.toString()).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", CHROME_UA);
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Origin", "https://www.sporttery.cn");
            conn.setRequestProperty("Referer", "https://www.sporttery.cn/");
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String ct = conn.getContentType();
            String enc = conn.getContentEncoding();
            return new WebResourceResponse(
                    ct != null ? ct : "application/json", enc, code, "OK", null, in);
        } catch (IOException e) {
            if (conn != null) conn.disconnect();
            return new WebResourceResponse("text/plain", "UTF-8", 502, "Bad Gateway",
                    null, new ByteArrayInputStream(new byte[0]));
        }
    }

    /** 返回键：页面内可回退就回退，否则退出应用 */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
