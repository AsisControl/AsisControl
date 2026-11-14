"use client";
import { useEffect, useState } from "react";
import { api, message } from "@/core/api/client";

export function useSettingsList<T>(path: string) {
  const [items, setItems] = useState<T[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true;
    api<T[]>(path).then((data) => { if (active) { setItems(data); setError(""); setLoading(false); } })
      .catch((caught) => { if (active) { setError(message(caught)); setLoading(false); } });
    return () => { active = false; };
  }, [path, revision]);
  return { items, loading, error, refresh() { setLoading(true); setRevision((value) => value + 1); } };
}
