package com.aicodestudio.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Opens the hosted AI Code Studio website full-screen.
 *
 * The app holds no secrets and no logic of its own: sign-in, projects and AI
 * generation all happen on the website. It adds a loading screen, an offline
 * screen with a retry button, the phone's Back button, and saving ZIP
 * downloads to the Downloads folder.
 */
public class MainActivity extends Activity {

    private static final String APP_URL = BuildConfig.APP_URL;
    private static final long SLOW_NOTICE_MS = 8000;
    private static final int MAX_SAVE_BYTES = 50 * 1024 * 1024;

    /**
     * The bridge is visible to every frame, including the sandboxed preview of
     * AI-generated pages. Only the app's own page is given this token, so code
     * running in a preview cannot write files to the phone.
     */
    private final String saveToken = java.util.UUID.randomUUID().toString();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private LinearLayout overlay;
    private ProgressBar spinner;
    private TextView title;
    private TextView body;
    private Button retry;
    private boolean failed;
    private boolean shownOnce;

    private final Runnable slowNotice = () -> {
        if (!failed && overlay.getVisibility() == View.VISIBLE) {
            body.setText(R.string.loading_slow);
            body.setVisibility(View.VISIBLE);
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.background));

        web = new WebView(this);
        web.setBackgroundColor(getColor(R.color.background));
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildOverlay();
        root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true); // keeps the user signed in
        settings.setDatabaseEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportZoom(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setUserAgentString(settings.getUserAgentString() + " AICodeStudioAndroid/" + BuildConfig.VERSION_NAME);

        web.addJavascriptInterface(new Bridge(), "AndroidApp");
        web.setWebViewClient(new Client());

        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            load();
        }
    }

    private void buildOverlay() {
        int pad = dp(32);
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(pad, pad, pad, pad);
        overlay.setBackgroundColor(getColor(R.color.background));
        overlay.setClickable(true); // do not let taps fall through to the page behind

        spinner = new ProgressBar(this);
        overlay.addView(spinner, wrap(0));

        title = new TextView(this);
        title.setTextColor(getColor(R.color.text));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setGravity(Gravity.CENTER);
        overlay.addView(title, wrap(dp(20)));

        body = new TextView(this);
        body.setTextColor(getColor(R.color.text_muted));
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        body.setGravity(Gravity.CENTER);
        overlay.addView(body, wrap(dp(8)));

        retry = new Button(this);
        retry.setText(R.string.retry);
        retry.setOnClickListener(v -> load());
        overlay.addView(retry, wrap(dp(20)));
    }

    private LinearLayout.LayoutParams wrap(int topMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = topMargin;
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void load() {
        failed = false;
        showLoading();
        web.loadUrl(APP_URL + "/");
    }

    private void showLoading() {
        spinner.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
        body.setVisibility(View.GONE);
        title.setText(R.string.loading);
        overlay.setVisibility(View.VISIBLE);
        handler.removeCallbacks(slowNotice);
        handler.postDelayed(slowNotice, SLOW_NOTICE_MS);
    }

    private void showOffline() {
        failed = true;
        handler.removeCallbacks(slowNotice);
        spinner.setVisibility(View.GONE);
        title.setText(R.string.offline_title);
        body.setText(R.string.offline_body);
        body.setVisibility(View.VISIBLE);
        retry.setVisibility(View.VISIBLE);
        overlay.setVisibility(View.VISIBLE);
    }

    private static boolean isAppUrl(Uri uri) {
        Uri app = Uri.parse(APP_URL);
        return "https".equals(uri.getScheme()) && app.getHost() != null && app.getHost().equalsIgnoreCase(uri.getHost());
    }

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (isAppUrl(uri)) {
                return false;
            }
            // Anything that is not the app itself opens in the phone's browser.
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(MainActivity.this, R.string.no_browser, Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            // Only the very first load hides the page; later navigations stay visible.
            if (!shownOnce && !failed) {
                showLoading();
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (failed) {
                return;
            }
            if (url != null && isAppUrl(Uri.parse(url))) {
                view.evaluateJavascript("window.__androidSaveToken='" + saveToken + "';", null);
            }
            shownOnce = true;
            handler.removeCallbacks(slowNotice);
            overlay.setVisibility(View.GONE);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                shownOnce = false;
                showOffline();
                view.loadUrl("about:blank"); // hide the browser's own error page
            }
        }
    }

    /** Called by the website (window.AndroidApp) when it runs inside this app. */
    private class Bridge {
        @JavascriptInterface
        public void saveFile(String token, String name, String base64, String mimeType) {
            if (!saveToken.equals(token)) {
                return;
            }
            String safeName = name == null ? "" : name.replaceAll("[^A-Za-z0-9._-]+", "-");
            if (safeName.isEmpty() || safeName.startsWith(".")) {
                safeName = "project.zip";
            }
            final String fileName = safeName;
            try {
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                if (data.length > MAX_SAVE_BYTES) {
                    throw new IllegalStateException("the file is too large");
                }
                final String message;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentResolver resolver = getContentResolver();
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType == null ? "application/octet-stream" : mimeType);
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (target == null) {
                        throw new IllegalStateException("Downloads folder is not available");
                    }
                    try (OutputStream out = resolver.openOutputStream(target)) {
                        if (out == null) {
                            throw new IllegalStateException("Downloads folder is not writable");
                        }
                        out.write(data);
                    }
                    message = getString(R.string.saved, fileName);
                } else {
                    // Android 9 and older: the app's own folder needs no storage permission.
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) {
                        throw new IllegalStateException("Storage is not available");
                    }
                    File file = new File(dir, fileName);
                    try (OutputStream out = new FileOutputStream(file)) {
                        out.write(data);
                    }
                    message = getString(R.string.saved_app_folder, file.getAbsolutePath());
                }
                toast(message);
            } catch (Exception e) {
                toast(getString(R.string.save_failed, String.valueOf(e.getMessage())));
            }
        }
    }

    private void toast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onBackPressed() {
        if (!failed && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        web.destroy();
        super.onDestroy();
    }
}
