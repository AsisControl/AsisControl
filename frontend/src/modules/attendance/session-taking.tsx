"use client";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { message } from "@/core/api/client";
import { countLabel } from "@/shared/lib/formatters";
import { Feedback, StatusPill } from "@/shared/ui";
import { coincideBusqueda } from "./lib";
import { finalizarAsistencia, guardarBorrador } from "./services/asistencia-service";
import { MarkButtons } from "./session-marks";
import { Buscador, CheckIcon, ExitDialog, SessionHeader, StudentsTable, TotalesEstados } from "./session-ui";
import styles from "./attendance.module.css";
import type { EstadoMarca, Marca, SesionAsistencia } from "./types";

const marcasDe = (sesion: SesionAsistencia) =>
  Object.fromEntries(
    sesion.alumnos.filter((alumno) => alumno.estado).map((alumno) => [alumno.alumnoId, alumno.estado]),
  ) as Record<number, EstadoMarca>;

/** Toma del día: nada se guarda hasta que el docente elige guardar o finalizar. */
export function SessionTaking({
  sesion,
  onSaved,
}: {
  sesion: SesionAsistencia;
  onSaved: (sesion: SesionAsistencia, aviso: string) => void;
}) {
  const router = useRouter();
  const [marcas, setMarcas] = useState(() => marcasDe(sesion));
  const [busqueda, setBusqueda] = useState("");
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState("");
  const [exito, setExito] = useState("");
  const [salida, setSalida] = useState<string | null>(null);
  const historial = `/mis-cursos/${sesion.asignacionCursoId}/asistencia`;
  const total = sesion.alumnos.length;
  const marcados = sesion.alumnos.filter((alumno) => marcas[alumno.alumnoId]).length;
  const faltan = total - marcados;
  const hayCambios = sesion.alumnos.some((alumno) => (marcas[alumno.alumnoId] ?? null) !== alumno.estado);
  const porcentaje = total ? Math.round((marcados / total) * 100) : 0;
  const filas = sesion.alumnos.filter((alumno) => coincideBusqueda(alumno.nombreCompleto, busqueda));
  const lista = (): Marca[] =>
    Object.entries(marcas).map(([alumnoId, estado]) => ({ alumnoId: Number(alumnoId), estado }));
  const salir = (href: string) => (hayCambios ? setSalida(href) : router.push(href));
  const ejecutar = async (accion: () => Promise<SesionAsistencia>, aviso: string) => {
    setGuardando(true);
    setError("");
    setExito("");
    try {
      const actualizada = await accion();
      onSaved(actualizada, aviso);
      if (actualizada.estado !== "TOMADA") setExito(aviso);
    } catch (e) {
      setError(message(e));
    } finally {
      setGuardando(false);
    }
  };
  return (
    <section className={`${styles.pagina} ${styles.conBarra}`}>
      <SessionHeader
        sesion={sesion}
        onNavigate={salir}
        badge={
          sesion.estado === "BORRADOR" ? (
            <StatusPill state="BORRADOR" label="Borrador" tone="warning" />
          ) : (
            <StatusPill state="PENDIENTE" label="Pendiente" tone="neutral" />
          )
        }
      />
      <Feedback error={error} success={exito} />
      <div className={styles.franja}>
        <TotalesEstados alumnos={sesion.alumnos.map((alumno) => ({ estado: marcas[alumno.alumnoId] ?? null }))} />
        <div className={styles.progreso}>
          <strong>{marcados} de {total} marcados</strong>
          <div
            className={styles.barraAvance}
            role="progressbar"
            aria-label="Avance de la asistencia"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={porcentaje}
          >
            <span style={{ width: `${porcentaje}%` }} />
          </div>
          <span>{porcentaje}%</span>
        </div>
      </div>
      <div className={styles.lista}>
        <div className={styles.herramientas}>
          <Buscador valor={busqueda} onChange={setBusqueda} />
          <button
            type="button"
            className={styles.secundario}
            disabled={guardando || !total}
            title="Marca a todos como Presente, incluidos los ocultos por la búsqueda"
            onClick={() =>
              setMarcas(Object.fromEntries(sesion.alumnos.map((alumno) => [alumno.alumnoId, "PRESENTE"])))
            }
          >
            <CheckIcon /> Todos presentes
          </button>
        </div>
        <StudentsTable
          alumnos={filas}
          renderEstado={(alumno) => (
            <MarkButtons
              nombre={alumno.nombreCompleto}
              valor={marcas[alumno.alumnoId]}
              disabled={guardando}
              onChange={(estado) => setMarcas((actual) => ({ ...actual, [alumno.alumnoId]: estado }))}
            />
          )}
        />
      </div>
      <div className={styles.barra}>
        <div className={styles.barraInterior}>
          <span className={styles.mensajeBarra}>
            {faltan > 0 ? `Falta${faltan === 1 ? "" : "n"} ${countLabel(faltan, "estudiante", "estudiantes")}` : ""}
          </span>
          <div>
            <button type="button" className={styles.enlaceTexto} disabled={guardando} onClick={() => salir(historial)}>
              Cancelar
            </button>
            <button
              type="button"
              className={styles.secundario}
              disabled={guardando || marcados === 0 || !hayCambios}
              onClick={() => ejecutar(() => guardarBorrador(sesion.id, lista()), "Borrador guardado.")}
            >
              {guardando ? "Guardando…" : "Guardar borrador"}
            </button>
            <button
              type="button"
              className="primary-action"
              disabled={guardando || faltan > 0 || total === 0}
              onClick={() =>
                ejecutar(() => finalizarAsistencia(sesion.id, lista(), sesion.version), "Asistencia finalizada correctamente.")
              }
            >
              Finalizar asistencia
            </button>
          </div>
        </div>
      </div>
      {salida && <ExitDialog onStay={() => setSalida(null)} onLeave={() => router.push(salida)} />}
    </section>
  );
}
