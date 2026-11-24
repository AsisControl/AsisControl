import type { Page } from "@/shared/types/api";
export function Pager({
  data,
  loading,
  setPage,
}: {
  data: Page<unknown> | null;
  loading: boolean;
  setPage: (p: number) => void;
}) {
  if (!data) return null;
  return (
    <div className="pager">
      <span>
        {data.totalElements} registros · Página {data.page + 1} de{" "}
        {Math.max(1, data.totalPages)}
      </span>
      {data.totalElements > 0 && data.totalPages > 1 && <div>
        {!data.first && <button
          type="button"
          className="secondary-action"
          disabled={loading}
          onClick={() => setPage(data.page - 1)}
        >
          Anterior
        </button>}
        {!data.last && <button
          type="button"
          className="secondary-action"
          disabled={loading}
          onClick={() => setPage(data.page + 1)}
        >
          Siguiente
        </button>}
      </div>}
    </div>
  );
}
