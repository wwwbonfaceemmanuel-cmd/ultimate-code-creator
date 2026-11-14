/** Mirrors the backend's path rules so mistakes are caught before a request. */
export function cleanPath(raw: string): { path?: string; error?: string } {
  const parts = raw.trim().replace(/\\/g, '/').split('/').filter((p) => p && p !== '.');
  if (!parts.length) return { error: 'Enter a file name.' };
  if (parts.includes('..')) return { error: "Paths may not contain '..'." };
  const path = parts.join('/');
  if (path.length > 240 || parts[0].includes(':')) return { error: 'That path is not valid.' };
  return { path };
}
