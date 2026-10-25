import { StatusPill } from "@/shared/ui";
/** Estado del aula con los colores del prototipo: disponible en verde, inhabilitada en ámbar. */
export function ClassroomStatus({ active }: { active: boolean }) {
  return active ? (
    <StatusPill state="DISPONIBLE" label="Disponible" tone="success" />
  ) : (
    <StatusPill state="INHABILITADA" label="Inhabilitada" tone="warning" />
  );
}
