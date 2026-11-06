import { api } from "@/core/api/client";
import type { CommitmentChange, CommitmentInput, PaymentCommitment } from "../types";

export const COMMITMENTS_PATH = "/api/compromisos-pago";
export const ELIGIBLE_ENROLLMENTS_PATH = `${COMMITMENTS_PATH}/matriculas`;

export const getCommitment = (id: number) =>
  api<PaymentCommitment>(`${COMMITMENTS_PATH}/${id}`);

export const issueCommitment = (input: CommitmentInput) =>
  api<PaymentCommitment>(COMMITMENTS_PATH, {
    method: "POST",
    body: JSON.stringify(input),
  });

export const updateCommitment = (id: number, change: CommitmentChange) =>
  api<PaymentCommitment>(`${COMMITMENTS_PATH}/${id}`, {
    method: "PUT",
    body: JSON.stringify(change),
  });

export const annulCommitment = (id: number, motivo: string, version: number) =>
  api<PaymentCommitment>(`${COMMITMENTS_PATH}/${id}/anulacion`, {
    method: "POST",
    body: JSON.stringify({ motivo, version }),
  });
