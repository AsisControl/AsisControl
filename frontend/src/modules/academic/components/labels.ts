/** Hora de Lima en formato 24 h: "16:05". */
export const timeLabel = (date: Date) =>
  date.toLocaleTimeString("es-PE", {
    timeZone: "America/Lima",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
/** "1 curso", "3 cursos". */
export const countLabel = (count: number, singular: string, plural: string) =>
  `${count} ${count === 1 ? singular : plural}`;
