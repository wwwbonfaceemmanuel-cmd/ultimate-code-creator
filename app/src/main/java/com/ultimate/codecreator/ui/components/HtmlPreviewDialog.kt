package com.ultimate.codecreator.ui.components

import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ultimate.codecreator.R
import com.ultimate.codecreator.engine.PreviewSandbox
import java.io.ByteArrayInputStream

@Composable
fun HtmlPreviewDialog(
    htmlContent: String,
    javaScriptEnabled: Boolean,
    onDismiss: () -> Unit
) {
    var stopped by remember { mutableStateOf(false) }
    var rendererGone by remember { mutableStateOf(false) }
    var previewKey by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.preview_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.preview_network_blocked), style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { stopped = true }) {
                        Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.stop_preview))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                if (!javaScriptEnabled) {
                    Text(
                        stringResource(R.string.preview_js_disabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                when {
                    rendererGone -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.renderer_crashed), modifier = Modifier.padding(24.dp))
                    }
                    stopped -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.preview_stopped), modifier = Modifier.padding(24.dp))
                    }
                    else -> key(previewKey) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = javaScriptEnabled
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.allowFileAccessFromFileURLs = false
                                    settings.allowUniversalAccessFromFileURLs = false
                                    settings.domStorageEnabled = false
                                    settings.setGeolocationEnabled(false)
                                    settings.javaScriptCanOpenWindowsAutomatically = false
                                    settings.setSupportMultipleWindows(false)
                                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    isSaveEnabled = false
                                    setDownloadListener { _, _, _, _, _ -> /* blocked */ }
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                            return if (request.url.scheme?.lowercase() in setOf("data", "blob", "about")) null
                                            else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                        }
                                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                            rendererGone = true
                                            return true
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                                    }
                                }
                            },
                            update = { webView ->
                                webView.loadDataWithBaseURL("about:blank", PreviewSandbox.wrap(htmlContent), "text/html", "UTF-8", null)
                            },
                            onRelease = { webView ->
                                webView.stopLoading()
                                webView.destroy()
                            }
                        )
                    }
                }
            }
        }
    }
}
