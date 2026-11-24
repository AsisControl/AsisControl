import { act, renderHook, waitFor } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import { usePaged } from "../use-paged";
import { api } from "@/core/api/client";

vi.mock("@/core/api/client", () => ({ api: vi.fn(), message: String }));

test("corrige la página tras eliminar y reinicia al cambiar el filtro", async () => {
  let totalPages = 3;
  api.mockImplementation(async (path) => {
    const params = new URLSearchParams(path.split("?")[1]);
    return { content: [], number: Number(params.get("page")), totalPages, totalElements: totalPages };
  });
  const { result, rerender } = renderHook(({ filter }) => usePaged("/registros", "", filter), {
    initialProps: { filter: "" },
  });
  await waitFor(() => expect(result.current.data?.page).toBe(0));
  act(() => result.current.setPage(2));
  await waitFor(() => expect(result.current.data?.page).toBe(2));
  totalPages = 2;
  act(() => result.current.refresh());
  await waitFor(() => expect(result.current.data?.page).toBe(1));
  api.mockClear();
  rerender({ filter: "estado=ACTIVO" });
  await waitFor(() => expect(result.current.data?.page).toBe(0));
  expect(api.mock.calls.map(([path]) => path)).toEqual(["/registros?page=0&size=20&estado=ACTIVO"]);
});
