/** Present only when the site runs inside the Android app (see android/). */
interface AndroidBridge {
  saveFile(token: string, name: string, base64: string, mimeType: string): void;
}

function toBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('The file could not be read for saving.'));
    reader.onload = () => resolve(String(reader.result).split(',', 2)[1] ?? '');
    reader.readAsDataURL(blob);
  });
}

/**
 * Save a file to the user's device. Browsers get a normal download; the
 * Android app cannot download in-memory files itself, so the file is handed
 * to the app, which writes it to the Downloads folder.
 */
export async function saveBlob(blob: Blob, name: string): Promise<void> {
  // The app gives its token only to this page, never to the sandboxed project preview.
  const host = window as unknown as { AndroidApp?: AndroidBridge; __androidSaveToken?: string };
  if (host.AndroidApp && typeof host.AndroidApp.saveFile === 'function' && host.__androidSaveToken) {
    host.AndroidApp.saveFile(host.__androidSaveToken, name, await toBase64(blob), blob.type || 'application/zip');
    return;
  }
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
