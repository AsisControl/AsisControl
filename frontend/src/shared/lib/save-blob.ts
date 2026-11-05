/** Descarga un archivo recibido del backend con el nombre indicado. */
export function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  link.click();
  // Se libera después: algunos navegadores aún no empezaron la descarga.
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
