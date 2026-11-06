"use client";
import { useEffect, useMemo, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { message } from "@/core/api/client";
import { listarDocentes, QR_VIGENCIA_SEGUNDOS } from "./services/asistencia-docente-service";
import type { DocenteOption } from "./types";
import { useDynamicQr } from "./use-dynamic-qr";
import styles from "./teacher-check-in.module.css";

/**
 * Punto de marcación (administrador): muestra el QR dinámico que el docente
 * escanea desde su sesión. El código lo genera y valida el backend.
 */
export function CheckInPoint({ canWrite }: { canWrite: boolean }) {
  const [teachers, setTeachers] = useState<DocenteOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<DocenteOption | null>(null);
  const qr = useDynamicQr();

  useEffect(() => {
    if (!canWrite) return;
    let alive = true;
    listarDocentes()
      .then((list) => alive && setTeachers(list))
      .catch((problem) => alive && setLoadError(message(problem)))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [canWrite]);

  const visible = useMemo(() => {
    const term = query.trim().toLowerCase();
    return term ? teachers.filter((t) => t.label.toLowerCase().includes(term)) : teachers;
  }, [teachers, query]);

  function choose(teacher: DocenteOption) {
    setSelected(teacher);
    void qr.start(teacher.id);
  }

  function next() {
    qr.stop();
    setSelected(null);
  }

  return (
    <>
      <div className="page-title">
        <div>
          <h1>Asistencia</h1>
          <p>Punto de marcación docente con QR dinámico</p>
        </div>
      </div>

      {!canWrite ? (
        <article className="role-message">
          <span className="message-mark">!</span>
          <div>
            <p className="eyebrow">PUNTO DE MARCACIÓN</p>
            <h2>Tu usuario no tiene permiso para emitir códigos de asistencia.</h2>
          </div>
        </article>
      ) : (
        <div className={styles.layout}>
          <section className={`panel ${styles.card}`} aria-labelledby="docentes-title">
            <h2 id="docentes-title" className={styles.cardTitle}>
              1. Docente que marca
            </h2>
            <label className={styles.field}>
              <span>Buscar docente</span>
              <input
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Nombre o documento"
              />
            </label>
            {loading ? (
              <p className={styles.muted}>Cargando docentes…</p>
            ) : loadError ? (
              <p role="alert" className={styles.error}>
                {loadError}
              </p>
            ) : visible.length === 0 ? (
              <p className={styles.muted}>No hay docentes que coincidan.</p>
            ) : (
              <ul className={styles.teacherList}>
                {visible.map((teacher) => (
                  <li key={teacher.id}>
                    <button
                      type="button"
                      className={styles.teacherButton}
                      aria-pressed={selected?.id === teacher.id}
                      onClick={() => choose(teacher)}
                    >
                      {teacher.label}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className={`panel ${styles.card} ${styles.qrCard}`} aria-labelledby="qr-title">
            <h2 id="qr-title" className={styles.cardTitle}>
              2. Código QR
            </h2>
            {qr.status === "idle" && (
              <p className={styles.muted}>Selecciona un docente para mostrar su código QR.</p>
            )}
            {qr.status === "emitting" && <p className={styles.muted}>Generando código…</p>}
            {qr.status === "active" && qr.code && (
              <>
                <p className={styles.teacherName}>{selected?.label}</p>
                <div className={styles.qrFrame}>
                  <QRCodeSVG value={qr.code.token} size={260} level="M" marginSize={2} />
                </div>
                <p className={styles.countdown}>
                  Vence en <strong>{qr.remaining} s</strong> · se renueva automáticamente
                </p>
                <progress
                  className={styles.progress}
                  max={QR_VIGENCIA_SEGUNDOS}
                  value={Math.min(qr.remaining, QR_VIGENCIA_SEGUNDOS)}
                  aria-label="Tiempo restante del código"
                />
                <p className={styles.muted}>
                  El docente debe escanearlo desde <strong>Asistencia</strong> en su sesión.
                </p>
                <details className={styles.manual}>
                  <summary>Código para ingreso manual</summary>
                  <code>{qr.code.token}</code>
                </details>
                <button type="button" className={styles.secondaryButton} onClick={next}>
                  Cerrar código
                </button>
              </>
            )}
            {qr.status === "used" && (
              <div role="status" className={styles.success}>
                <p className={styles.bigMark}>✓</p>
                <p>
                  Asistencia registrada para <strong>{selected?.label}</strong>.
                </p>
                <button type="button" className="primary-action" onClick={next}>
                  Atender a otro docente
                </button>
              </div>
            )}
            {qr.status === "error" && (
              <div role="alert" className={styles.errorBox}>
                <p>{qr.error}</p>
                {selected && (
                  <button
                    type="button"
                    className="primary-action"
                    onClick={() => void qr.start(selected.id)}
                  >
                    Reintentar
                  </button>
                )}
              </div>
            )}
          </section>
        </div>
      )}
    </>
  );
}
