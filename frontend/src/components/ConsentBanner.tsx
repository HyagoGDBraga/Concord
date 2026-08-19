"use client";

/**
 * Aviso de que os documentos legais mudaram.
 *
 * Aparece quando a versao vigente difere da que o usuario aceitou. Nao bloqueia
 * o uso do aplicativo de proposito: impedir alguem de ler as proprias mensagens
 * porque um texto juridico mudou seria usar o dado da pessoa como refem do
 * aceite.
 */

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/apiClient";
import { Button } from "@/components/ui";

interface ConsentStatus {
  termsVersion: string;
  privacyVersion: string;
  termsAccepted: boolean;
  privacyAccepted: boolean;
}

export function ConsentBanner() {
  const [status, setStatus] = useState<ConsentStatus | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setStatus(await api.get<ConsentStatus>("/legal/consents"));
    } catch {
      // Nao ha o que fazer se a consulta falhar; o aviso simplesmente nao
      // aparece.
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (!status || (status.termsAccepted && status.privacyAccepted)) {
    return null;
  }

  async function accept() {
    if (!status) {
      return;
    }
    setBusy(true);
    try {
      if (!status.termsAccepted) {
        await api.post("/legal/consents", {
          document: "TERMS_OF_USE",
          version: status.termsVersion,
        });
      }
      if (!status.privacyAccepted) {
        await api.post("/legal/consents", {
          document: "PRIVACY_POLICY",
          version: status.privacyVersion,
        });
      }
      await load();
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="border-b border-amber/40 bg-ink/80 px-6 py-3">
      <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-3 text-sm">
        <p className="flex-1 text-paper">
          Nossos{" "}
          <a href="/termos" target="_blank" rel="noreferrer" className="text-amber underline">
            Termos de Uso
          </a>{" "}
          e{" "}
          <a href="/privacidade" target="_blank" rel="noreferrer" className="text-amber underline">
            Politica de Privacidade
          </a>{" "}
          foram atualizados.
        </p>
        <Button loading={busy} onClick={() => void accept()}>
          Li e aceito
        </Button>
      </div>
    </div>
  );
}
