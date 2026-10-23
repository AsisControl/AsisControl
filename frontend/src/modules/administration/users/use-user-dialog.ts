"use client";
import { useEffect, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { message } from "@/core/api/client";
import type { Account } from "../types";
import { obtenerUsuario } from "./services/usuarioService";
export type UserDialogKind = "detail" | "edit" | "reset" | "deactivate";
/** Parámetro de la URL de cada ventana: ?ver=12, ?editar=12… */
const DIALOG_PARAMS: Record<UserDialogKind, string> = {
  detail: "ver",
  edit: "editar",
  reset: "restablecer",
  deactivate: "desactivar",
};
function dialogFromUrl(params: Pick<URLSearchParams, "get">) {
  for (const [kind, param] of Object.entries(DIALOG_PARAMS)) {
    const id = Number(params.get(param));
    if (Number.isInteger(id) && id > 0)
      return { kind: kind as UserDialogKind, id };
  }
  return null;
}
/**
 * Ventana abierta según la URL: el botón "atrás" la cierra y el enlace se
 * puede compartir. La cuenta se pide al backend al abrir la ventana de un usuario.
 */
export function useUserDialog(onError: (error: string) => void) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const requested = dialogFromUrl(searchParams);
  const requestKey = requested ? String(requested.id) : "";
  const [loaded, setLoaded] = useState<{ key: string; account: Account }>();
  const openedInApp = useRef(false);
  const onErrorRef = useRef(onError);
  useEffect(() => {
    onErrorRef.current = onError;
  });
  useEffect(() => {
    if (!requestKey) return;
    let active = true;
    obtenerUsuario(Number(requestKey))
      .then((account) => {
        if (active) setLoaded({ key: requestKey, account });
      })
      .catch((e) => {
        if (!active) return;
        onErrorRef.current(message(e));
        router.replace(pathname, { scroll: false });
      });
    return () => {
      active = false;
    };
  }, [requestKey, pathname, router]);
  const urlFor = (kind: UserDialogKind, id: number) =>
    `${pathname}?${DIALOG_PARAMS[kind]}=${id}`;
  return {
    kind: requested?.kind ?? null,
    account: loaded?.key === requestKey ? loaded.account : null,
    open(kind: UserDialogKind, id: number) {
      openedInApp.current = true;
      router.push(urlFor(kind, id), { scroll: false });
    },
    /** Cambia de ventana sin sumar otra entrada al historial. */
    switchTo(kind: UserDialogKind, id: number) {
      router.replace(urlFor(kind, id), { scroll: false });
    },
    close() {
      if (openedInApp.current) router.back();
      else router.replace(pathname, { scroll: false });
      openedInApp.current = false;
    },
  };
}
