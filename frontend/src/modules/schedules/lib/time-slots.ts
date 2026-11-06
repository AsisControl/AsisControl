/** La jornada admite bloques consecutivos de media hora entre 07:00 y 20:00. */
export const SCHOOL_START = "07:00";
export const SCHOOL_END = "20:00";
const SLOT_MINUTES = 30;
const toMinutes = (time: string) => {
  const [hours, minutes] = time.split(":").map(Number);
  return hours * 60 + minutes;
};

export const SCHOOL_TIME_OPTIONS = Array.from({
  length: (toMinutes(SCHOOL_END) - toMinutes(SCHOOL_START)) / SLOT_MINUTES + 1,
}, (_, index) => {
  const minutes = toMinutes(SCHOOL_START) + index * SLOT_MINUTES;
  return `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
});

export function isSchoolTimeSlot(value: string): boolean {
  return SCHOOL_TIME_OPTIONS.includes(value);
}

export const isSchoolStartSlot = (value: string) =>
  value !== SCHOOL_END && isSchoolTimeSlot(value);
export const isSchoolEndSlot = (value: string) =>
  value !== SCHOOL_START && isSchoolTimeSlot(value);
