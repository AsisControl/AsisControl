type NavigationTarget = { nav: string; startCreate: boolean };

const pendingRoutes: Record<string, string> = {
  asistencia: "Asistencia",
  academico: "Académico",
  horarios: "Horarios",
  finanzas: "Finanzas",
  "mis-cursos": "Mis cursos",
  evaluaciones: "Evaluaciones",
  tareas: "Tareas",
  horario: "Horario",
  progreso: "Progreso",
  pagos: "Pagos",
};

const pageRoutes: Record<string, NavigationTarget> = {
  "/": { nav: "Inicio", startCreate: false },
  "/login": { nav: "Inicio", startCreate: false },
  "/administracion": { nav: "Administración", startCreate: false },
  "/administracion/accesos/nuevo": { nav: "Administración", startCreate: true },
  "/matricula": { nav: "Matrícula", startCreate: false },
  "/estudiantes": { nav: "Matrícula", startCreate: false },
};

/** Resolve only known destinations; an invalid URL never falls back to Inicio. */
export function resolveNavigation(
  pathname: string,
  search: string,
): NavigationTarget | null {
  const params = new URLSearchParams(search);
  if ([...params.keys()].some((key) => key !== "modulo")) return null;
  const modules = params.getAll("modulo");
  if (modules.length) {
    if (pathname !== "/" || modules.length !== 1) return null;
    const slug = modules[0];
    if (!Object.hasOwn(pendingRoutes, slug)) return null;
    return { nav: pendingRoutes[slug], startCreate: false };
  }
  return Object.hasOwn(pageRoutes, pathname) ? pageRoutes[pathname] : null;
}

/** All returned URLs are internal constants, never user-supplied router targets. */
export function navigationHref(nav: string): string | null {
  if (nav === "Inicio") return "/";
  if (nav === "Administración") return "/administracion";
  if (nav === "Matrícula") return "/matricula";
  const entry = Object.entries(pendingRoutes).find(([, label]) => label === nav);
  return entry ? `/?modulo=${entry[0]}` : null;
}
