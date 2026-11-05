"use client";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { readSession } from "@/core/auth/session-storage";
import { Students } from "@/modules/students";
import { Feedback } from "@/shared/ui";
import { GuardiansPanel, RelationshipsPanel } from "./family-panels";
import { TeachersPanel } from "./teachers-panel";
import styles from "./academic-records.module.css";

type RecordView = { id: string; label: string };
/** Estudiantes y familias por un lado y docentes por otro, en un solo selector. */
const VIEW_GROUPS: { label: string; views: RecordView[] }[] = [
  {
    label: "Estudiantes y familias",
    views: [
      { id: "estudiantes", label: "Estudiantes" },
      { id: "apoderados", label: "Apoderados" },
      { id: "parentescos", label: "Parentescos" },
    ],
  },
  { label: "Personal", views: [{ id: "docentes", label: "Docentes" }] },
];
const DEFAULT_VIEW = "estudiantes";

/** Fichas académicas; la vista sale de la URL: {basePath}/{vista}. */
export function AcademicRecords({
  basePath,
  view,
}: {
  basePath: string;
  view?: string;
}) {
  const router = useRouter();
  const current = view ?? DEFAULT_VIEW;
  const valid = VIEW_GROUPS.some((group) =>
    group.views.some(({ id }) => id === current),
  );
  useEffect(() => {
    if (!valid) router.replace(basePath);
  }, [valid, basePath, router]);
  // La autorización definitiva siempre se valida en backend.
  if (readSession()?.user.role !== "ADMINISTRADOR")
    return (
      <Feedback error="No tienes permiso para gestionar las fichas académicas." />
    );
  if (!valid) return null;
  return (
    <>
      <nav className={styles.views} aria-label="Fichas académicas">
        {VIEW_GROUPS.map((group) => (
          <div
            key={group.label}
            className={styles.viewGroup}
            role="group"
            aria-label={group.label}
          >
            <span className={styles.viewGroupLabel} aria-hidden="true">
              {group.label}
            </span>
            <div className={styles.segmented}>
              {group.views.map(({ id, label }) => (
                <button
                  key={id}
                  type="button"
                  aria-current={current === id ? "page" : undefined}
                  onClick={() => router.push(`${basePath}/${id}`)}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        ))}
      </nav>
      {current === "estudiantes" && <Students />}
      {current === "apoderados" && <GuardiansPanel />}
      {current === "parentescos" && <RelationshipsPanel />}
      {current === "docentes" && <TeachersPanel />}
    </>
  );
}
