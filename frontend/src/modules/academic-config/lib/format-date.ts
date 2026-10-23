const months = [
  "Ene",
  "Feb",
  "Mar",
  "Abr",
  "May",
  "Jun",
  "Jul",
  "Ago",
  "Sep",
  "Oct",
  "Nov",
  "Dic",
];
/** "2025-03-03" → "03 Mar 2025", sin pasar por Date para evitar husos horarios. */
export function formatDate(iso: string) {
  const [year, month, day] = iso.split("-");
  const name = months[Number(month) - 1];
  return name ? `${day} ${name} ${year}` : iso;
}
