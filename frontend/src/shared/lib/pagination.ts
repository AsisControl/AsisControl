import type { Page } from "@/shared/types/api";
export function normalizePage<T>(raw: {
  content: T[];
  page?: number | { number: number; totalElements: number; totalPages: number };
  number?: number;
  totalElements?: number;
  totalPages?: number;
}): Page<T> {
  const metadata = typeof raw.page === "object" ? raw.page : raw;
  const page = typeof raw.page === "number" ? raw.page : metadata.number || 0;
  const totalPages = metadata.totalPages || 0;
  return {
    content: raw.content,
    page,
    totalElements: metadata.totalElements || 0,
    totalPages,
    first: page === 0,
    last: page >= totalPages - 1,
  };
}
