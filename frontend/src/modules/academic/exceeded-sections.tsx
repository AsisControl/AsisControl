import Link from "next/link";
import { hoursLabel } from "@/shared/lib/formatters";
import styles from "./study-plans.module.css";

/** OBS-01: sección y curso cuyo horario supera las horas del plan (SeccionExcedida). */
export type ExceededSection = {
  seccionId: number;
  seccion: string;
  cursoId: number;
  curso: string;
  horasPlan: number;
  minutosProgramados: number;
};

/** OBS-01: el plan se guarda igual; este aviso indica qué horarios hay que ajustar. */
export function ExceededSections({ sections }: { sections: ExceededSection[] }) {
  if (!sections.length) return null;
  return (
    <section className={styles.exceededNotice} role="status" aria-label="Secciones que exceden el plan">
      <strong>Estas secciones tienen más horas programadas que el plan:</strong>
      <ul>
        {sections.map((item) => (
          <li key={`${item.seccionId}-${item.cursoId}`}>
            {item.seccion} · {item.curso} · {hoursLabel(item.minutosProgramados)} programadas, el plan
            permite {item.horasPlan} h (sobra {hoursLabel(item.minutosProgramados - item.horasPlan * 60)})
          </li>
        ))}
      </ul>
      <p>
        Ajusta su horario en <Link href="/horarios/horario">Horarios › Horario</Link>.
      </p>
    </section>
  );
}