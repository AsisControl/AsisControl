"use client";
import { useEffect, useState } from "react";
import { message } from "@/core/api/client";
import { Dialog, Feedback, StatusPill } from "@/shared/ui";
import { dateLabel } from "@/shared/lib/formatters";
import { dueDateLabel, financialLabel, moneyLabel } from "./commitment-format";
import { getCommitment } from "./services/commitment-service";
import type { PaymentCommitment } from "./types";
import styles from "./finance.module.css";

export function CommitmentDetail({ id, canWrite, onClose, onAction }: {
  id: number;
  canWrite: boolean;
  onClose: () => void;
  onAction: (action: "edit" | "annul", commitment: PaymentCommitment) => void;
}) {
  const [record, setRecord] = useState<PaymentCommitment | null>(null);
  const [error, setError] = useState("");
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let active = true;
    getCommitment(id)
      .then((value) => {
        if (active) setRecord(value);
      })
      .catch((caught) => {
        if (active) setError(message(caught));
      });
    return () => { active = false; };
  }, [id, tick]);

  const eligible = record?.estadoAdministrativo === "ACTIVO" && record.montoPagado === 0;

  return (
    <Dialog
      title="Detalle del compromiso"
      description={record
        ? <span className={styles.codeText}>{record.alumno} · {record.codigoCompromiso}</span>
        : "Consulta los datos del compromiso."}
      onClose={onClose}
    >
      <Feedback error={error} />
      {error && (
        <button onClick={() => {
          setError("");
          setTick((value) => value + 1);
        }}>
          Reintentar consulta
        </button>
      )}
      {!record && !error && <p role="status">Cargando compromiso…</p>}
      {record && (
        <>
          <dl className="detail-grid">
            <div>
              <dt>Estudiante</dt>
              <dd>{record.alumno}<small>{record.codigoAlumno}</small></dd>
            </div>
            <div>
              <dt>Matrícula y año</dt>
              <dd>{record.codigoMatricula}<small>{record.anioAcademico}</small></dd>
            </div>
            <div>
              <dt>Concepto</dt>
              <dd>{record.concepto}</dd>
            </div>
            <div>
              <dt>Número de cuota</dt>
              <dd>{record.numeroCuota ?? "Sin cuota registrada"}</dd>
            </div>
            <div>
              <dt>Monto del compromiso</dt>
              <dd className={styles.money}>
                {moneyLabel(record.montoTotal, record.moneda)}
              </dd>
            </div>
            <div>
              <dt>Vencimiento</dt>
              <dd>{dueDateLabel(record.fechaVencimiento)}</dd>
            </div>
            <div>
              <dt>Pagado</dt>
              <dd className={styles.money}>
                {moneyLabel(record.montoPagado, record.moneda)}
              </dd>
            </div>
            <div>
              <dt>Saldo</dt>
              <dd className={styles.money}>
                {moneyLabel(record.saldo, record.moneda)}
              </dd>
            </div>
            <div>
              <dt>Estado del compromiso</dt>
              <dd>
                <StatusPill
                  state={record.estadoAdministrativo}
                  tone={record.estadoAdministrativo === "ACTIVO" ? "success" : "neutral"}
                />
              </dd>
            </div>
            <div>
              <dt>Situación de pago</dt>
              <dd>{financialLabel(record)}</dd>
            </div>
            <div>
              <dt>Emisión</dt>
              <dd>{dateLabel(record.fechaCreacion)}</dd>
            </div>
            <div>
              <dt>Última actualización</dt>
              <dd>{dateLabel(record.fechaActualizacion)}</dd>
            </div>
          </dl>
          {record.descripcion && (
            <p><strong>Descripción:</strong> {record.descripcion}</p>
          )}
          {record.moneda !== "PEN" && (
            <p className={styles.notice}>
              Este compromiso se conserva en su moneda original.
            </p>
          )}
          {record.estadoAdministrativo === "ANULADO" && (
            <div className={styles.notice}>
              <strong>Motivo de anulación</strong>
              <p>{record.motivoAnulacion || "Registro conservado en el historial."}</p>
              {record.fechaAnulacion && <p>{dateLabel(record.fechaAnulacion)}</p>}
            </div>
          )}
          {canWrite && record.estadoAdministrativo === "ACTIVO" && !eligible && (
            <p className={styles.notice}>
              Este compromiso ya tiene pagos registrados. No se puede modificar ni anular.
            </p>
          )}
          <div className={styles.detailActions}>
            {canWrite && eligible && (
              <>
                {record.moneda === "PEN" && (
                  <button onClick={() => onAction("edit", record)}>
                    Modificar monto o vencimiento
                  </button>
                )}
                <button onClick={() => onAction("annul", record)}>
                  Anular compromiso
                </button>
              </>
            )}
            <button onClick={onClose}>Cerrar</button>
          </div>
        </>
      )}
    </Dialog>
  );
}
