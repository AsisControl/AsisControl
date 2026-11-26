"use client";

import { BulkImportDialog } from "@/modules/bulk-import";

export function CompetencyImportDialog({ onClose, onImported }: { onClose: () => void; onImported: () => void }) {
  return <BulkImportDialog
    title="Importar competencias"
    description="Descarga la plantilla y completa una fila por competencia."
    path="/api/competencias/importaciones"
    templateName="competencias.xlsx"
    fileLabel="Archivo Excel de competencias"
    onClose={onClose}
    onImported={() => onImported()} />;
}
