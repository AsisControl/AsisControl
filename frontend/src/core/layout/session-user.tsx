"use client";
import { createContext, useContext } from "react";
import type { AuthenticatedUser } from "@/core/auth/types";
const SessionUserContext = createContext<AuthenticatedUser | null>(null);
export const SessionUserProvider = SessionUserContext.Provider;
/** Usuario autenticado para las pantallas que se muestran dentro del shell. */
export function useSessionUser(): AuthenticatedUser {
  const user = useContext(SessionUserContext);
  if (!user) throw new Error("useSessionUser se usa fuera de AppShell");
  return user;
}
