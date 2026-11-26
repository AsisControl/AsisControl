import { expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import { EvidenceFileCard } from "../evidence-file-card";

test.each([
  ["foto.jpg", "application/octet-stream", 64, /Imagen · 64 B/],
  ["trabajo.docx", "application/octet-stream", 1024, /Word · 1 KiB/],
  ["datos.xlsx", "application/octet-stream", 1048576, /Excel · 1 MiB/],
  ["charla.pptx", "application/octet-stream", 2048, /PowerPoint · 2 KiB/],
  ["documento", "", 32, /Archivo · 32 B/],
])("Identifica el formato de %s y muestra su tamaño", (name, mime, bytes, label) => {
  render(<EvidenceFileCard name={name} mime={mime} bytes={bytes} />);
  expect(screen.getByText(name)).toBeTruthy();
  expect(screen.getByText(label)).toBeTruthy();
});
