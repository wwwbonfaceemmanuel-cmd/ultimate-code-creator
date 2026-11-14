export const LANGUAGES = [
  { id: 'html', label: 'HTML' },
  { id: 'css', label: 'CSS' },
  { id: 'javascript', label: 'JavaScript' },
  { id: 'typescript', label: 'TypeScript' },
  { id: 'python', label: 'Python' },
  { id: 'java', label: 'Java' },
  { id: 'kotlin', label: 'Kotlin' },
  { id: 'php', label: 'PHP' },
  { id: 'cpp', label: 'C++' },
  { id: 'csharp', label: 'C#' },
  { id: 'dart', label: 'Dart' },
] as const;

export function languageLabel(id: string): string {
  return LANGUAGES.find((l) => l.id === id)?.label ?? id;
}

const EXT_TO_MONACO: Record<string, string> = {
  html: 'html', htm: 'html', css: 'css', scss: 'scss', js: 'javascript', mjs: 'javascript', cjs: 'javascript',
  jsx: 'javascript', ts: 'typescript', tsx: 'typescript', py: 'python', java: 'java', kt: 'kotlin', kts: 'kotlin',
  php: 'php', cpp: 'cpp', cc: 'cpp', cxx: 'cpp', c: 'c', h: 'cpp', hpp: 'cpp', cs: 'csharp', dart: 'dart',
  json: 'json', md: 'markdown', xml: 'xml', yml: 'yaml', yaml: 'yaml', sql: 'sql', sh: 'shell', gradle: 'java',
  toml: 'ini', ini: 'ini', dockerfile: 'dockerfile',
};

export function monacoLanguage(path: string): string {
  const name = path.split('/').pop() ?? '';
  if (name.toLowerCase() === 'dockerfile') return 'dockerfile';
  const ext = name.includes('.') ? name.split('.').pop()!.toLowerCase() : '';
  return EXT_TO_MONACO[ext] ?? 'plaintext';
}

export function timeAgo(iso: string): string {
  const seconds = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 60) return 'just now';
  const units: [number, string][] = [[86400, 'day'], [3600, 'hour'], [60, 'minute']];
  for (const [size, label] of units) {
    if (seconds >= size) {
      const n = Math.floor(seconds / size);
      return `${n} ${label}${n === 1 ? '' : 's'} ago`;
    }
  }
  return 'just now';
}
