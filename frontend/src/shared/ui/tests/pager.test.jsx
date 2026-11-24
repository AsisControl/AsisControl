import { render, screen } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import { Pager } from "../pager";

test.each([
  [0, 3, ["Siguiente"]],
  [1, 3, ["Anterior", "Siguiente"]],
  [2, 3, ["Anterior"]],
  [0, 1, []],
  [0, 0, []],
])("página %s de %s muestra solo los botones aplicables", (page, totalPages, labels) => {
  const setPage = vi.fn();
  render(<Pager data={{ page, totalPages, totalElements: totalPages, first: page === 0,
    last: page >= totalPages - 1, content: [] }} loading={false} setPage={setPage} />);
  expect(screen.queryAllByRole("button").map((button) => button.textContent)).toEqual(labels);
  for (const label of labels) {
    screen.getByRole("button", { name: label }).click();
    expect(setPage).toHaveBeenLastCalledWith(page + (label === "Anterior" ? -1 : 1));
  }
});
