import { api, apiBlob } from "@/core/api/client";
import type { ArchivoResponse } from "../types";

export function subirArchivo(file: File) {
  const body = new FormData();
  body.append("archivo", file);
  return api<ArchivoResponse>("/api/archivos", { method: "POST", body, timeoutMs: 120000 });
}

export async function descargarArchivo(file: Pick<ArchivoResponse, "id" | "nombreOriginal">) {
  const blob = await apiBlob(`/api/archivos/${file.id}/descarga`);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  try {
    link.href = url;
    link.download = file.nombreOriginal;
    document.body.appendChild(link);
    link.click();
  } finally {
    link.remove();
    URL.revokeObjectURL(url);
  }
}
