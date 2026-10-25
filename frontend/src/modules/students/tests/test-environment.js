import { vi } from "vitest";
import * as session from "@/core/auth/session-storage";
import * as client from "@/core/api/client";
import * as rules from "../student-rules";
import * as api from "../student-api";
import * as form from "../student-form";
import * as detail from "../student-detail";
import * as students from "../students";

const modules = {
  "core/auth/session-storage": session,
  "core/api/client": client,
  "modules/students/student-rules": rules,
  "modules/students/student-api": api,
  "modules/students/student-form": form,
  "modules/students/student-detail": detail,
  "modules/students/students": students,
};
/** Importa código real para medir cobertura; solo simula la red. */
export default function harness(fetcher = async () => {
  throw new Error("HTTP no esperado");
}) {
  session.clearSession();
  vi.stubGlobal("fetch", vi.fn(fetcher));
  return (path) => {
    if (!(path in modules)) throw new Error("Módulo no registrado: " + path);
    return modules[path];
  };
}
