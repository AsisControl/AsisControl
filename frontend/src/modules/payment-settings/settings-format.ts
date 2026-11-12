export const MONTHS = ["Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"];
export const monthName = (month: number) => MONTHS[month - 1] ?? String(month);
export const soles = (amount: number) => new Intl.NumberFormat("es-PE", { style: "currency", currency: "PEN" }).format(amount);
