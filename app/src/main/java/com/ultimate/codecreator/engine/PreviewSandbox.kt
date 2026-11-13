package com.ultimate.codecreator.engine

/** Defense in depth alongside the manifest's missing INTERNET permission and
 * the WebView's network interception. No JavaScript bridge is exposed.
 */
object PreviewSandbox {
    private const val POLICY = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; media-src data: blob:; connect-src 'none'; frame-src 'none'; object-src 'none'; worker-src 'none'; base-uri 'none'; form-action 'none'"
    private const val META = "<meta http-equiv=\"Content-Security-Policy\" content=\"$POLICY\">"

    fun wrap(html: String): String {
        val head = Regex("<head(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE).find(html)
        val insertion = head?.let { it.range.last + 1 }
            ?: Regex("^\\s*<!doctype[^>]*>", RegexOption.IGNORE_CASE).find(html)?.let { it.range.last + 1 }
            ?: 0
        return html.substring(0, insertion) + META + html.substring(insertion)
    }
}
