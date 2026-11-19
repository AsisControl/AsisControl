/** "2025-03-03" → "03/03/2025", sin pasar por Date para evitar husos horarios. */
export function formatDate(iso: string) {
  const [year, month, day] = iso.split("-");
  return year && month && day ? `${day}/${month}/${year}` : iso;
}
