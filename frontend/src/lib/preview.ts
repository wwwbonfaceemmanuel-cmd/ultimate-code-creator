import type { ProjectFile } from '../types';

export interface PreviewDoc {
  entry: string;
  html: string;
}

function resolve(base: string, ref: string): string {
  const clean = ref.split('?')[0].split('#')[0];
  const parts = clean.startsWith('/') ? [] : base.split('/').slice(0, -1);
  for (const seg of clean.split('/')) {
    if (seg === '..') parts.pop();
    else if (seg && seg !== '.') parts.push(seg);
  }
  return parts.join('/');
}

/** Pick the page to preview: index.html if present, else the shallowest HTML file. */
export function findEntry(files: ProjectFile[]): string | null {
  const html = files.filter((f) => /\.html?$/i.test(f.path));
  if (!html.length) return null;
  html.sort((a, b) => {
    const score = (p: string) => (/(^|\/)index\.html?$/i.test(p) ? 0 : 1) * 100 + p.split('/').length;
    return score(a.path) - score(b.path);
  });
  return html[0].path;
}

/**
 * Build a self-contained document for a sandboxed iframe by inlining the
 * project's own stylesheets and scripts. Nothing is fetched from the backend.
 */
export function buildPreview(files: ProjectFile[], entry: string): PreviewDoc | null {
  const entryFile = files.find((f) => f.path === entry);
  if (!entryFile) return null;
  const byPath = new Map(files.map((f) => [f.path, f.content]));
  const doc = new DOMParser().parseFromString(entryFile.content, 'text/html');

  doc.querySelectorAll('link[rel~="stylesheet"][href]').forEach((link) => {
    const css = byPath.get(resolve(entry, link.getAttribute('href')!));
    if (css === undefined) return;
    const style = doc.createElement('style');
    style.textContent = css;
    link.replaceWith(style);
  });

  doc.querySelectorAll('script[src]').forEach((script) => {
    const js = byPath.get(resolve(entry, script.getAttribute('src')!));
    if (js === undefined) return;
    const inline = doc.createElement('script');
    const type = script.getAttribute('type');
    if (type) inline.setAttribute('type', type);
    inline.textContent = js.replace(/<\/script/gi, '<\\/script');
    script.replaceWith(inline);
  });

  return { entry, html: '<!doctype html>\n' + doc.documentElement.outerHTML };
}
