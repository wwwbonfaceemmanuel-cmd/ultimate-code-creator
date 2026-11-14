// Monaco is bundled with the app (no CDN) and its workers are built by Vite.
import * as monaco from 'monaco-editor';
import { loader } from '@monaco-editor/react';
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
import jsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker';
import cssWorker from 'monaco-editor/esm/vs/language/css/css.worker?worker';
import htmlWorker from 'monaco-editor/esm/vs/language/html/html.worker?worker';
import tsWorker from 'monaco-editor/esm/vs/language/typescript/ts.worker?worker';

self.MonacoEnvironment = {
  getWorker(_id, label) {
    if (label === 'json') return new jsonWorker();
    if (label === 'css' || label === 'scss' || label === 'less') return new cssWorker();
    if (label === 'html' || label === 'handlebars' || label === 'razor') return new htmlWorker();
    if (label === 'typescript' || label === 'javascript') return new tsWorker();
    return new editorWorker();
  },
};

// Project dependencies are not installed in the browser, so type-level
// diagnostics would be noise. Syntax errors are still underlined.
for (const defaults of [monaco.languages.typescript.typescriptDefaults, monaco.languages.typescript.javascriptDefaults]) {
  defaults.setDiagnosticsOptions({ noSemanticValidation: true, noSyntaxValidation: false });
}
monaco.languages.typescript.typescriptDefaults.setCompilerOptions({
  jsx: monaco.languages.typescript.JsxEmit.Preserve,
  target: monaco.languages.typescript.ScriptTarget.ESNext,
  allowNonTsExtensions: true,
});

monaco.editor.defineTheme('studio-dark', {
  base: 'vs-dark',
  inherit: true,
  rules: [],
  colors: {
    'editor.background': '#0f1218',
    'editor.lineHighlightBackground': '#161a23',
    'editorLineNumber.foreground': '#4a5266',
    'editorGutter.background': '#0f1218',
  },
});

loader.config({ monaco });

export { monaco };
