"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { api, errorMessage } from "@/lib/apiClient";
import { Alert, Card, Spinner } from "@/components/ui";

function ConfirmEmailChangeContent() {
  const token = useSearchParams().get("token");
  const [state, setState] = useState<"processando" | "ok" | "erro">("processando");
  const [message, setMessage] = useState("");
  const attempted = useRef(false);

  useEffect(() => {
    if (attempted.current) {
      return;
    }
    attempted.current = true;

    if (!token) {
      setState("erro");
      setMessage("Link invalido: token ausente.");
      return;
    }
    api
      .post("/auth/email-change/confirm", { token })
      .then(() => setState("ok"))
      .catch((error) => {
        setState("erro");
        setMessage(errorMessage(error));
      });
  }, [token]);

  return (
    <Card title="Troca de e-mail">
      {state === "processando" && <Spinner label="Confirmando" />}
      {state === "ok" && (
        <Alert tone="success">
          Endereco atualizado. Use o novo e-mail para entrar.
        </Alert>
      )}
      {state === "erro" && <Alert tone="error">{message}</Alert>}
      <div className="mt-6">
        <Link href="/login" className="text-amber hover:brightness-110">
          Ir para o login
        </Link>
      </div>
    </Card>
  );
}

export default function ConfirmEmailChangePage() {
  return (
    <Suspense fallback={<Spinner />}>
      <ConfirmEmailChangeContent />
    </Suspense>
  );
}
