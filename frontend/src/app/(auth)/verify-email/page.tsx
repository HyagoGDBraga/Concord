"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { api, errorMessage } from "@/lib/apiClient";
import { Alert, Card, Spinner } from "@/components/ui";

type State = "verificando" | "ok" | "erro";

function VerifyEmailContent() {
  const token = useSearchParams().get("token");
  const [state, setState] = useState<State>("verificando");
  const [message, setMessage] = useState("");
  // Em modo estrito o React monta o componente duas vezes; sem esta trava o
  // token de uso unico seria consumido e a segunda chamada falharia.
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
      .post("/auth/verify-email", { token })
      .then(() => setState("ok"))
      .catch((error) => {
        setState("erro");
        setMessage(errorMessage(error));
      });
  }, [token]);

  if (state === "verificando") {
    return (
      <Card title="Confirmando seu e-mail">
        <Spinner label="Validando o link" />
      </Card>
    );
  }

  if (state === "ok") {
    return (
      <Card title="E-mail confirmado">
        <Alert tone="success">Sua conta esta ativa. Ja pode entrar.</Alert>
        <div className="mt-6">
          <Link href="/login" className="text-amber hover:brightness-110">
            Ir para o login
          </Link>
        </div>
      </Card>
    );
  }

  return (
    <Card title="Nao foi possivel confirmar">
      <Alert tone="error">{message}</Alert>
      <p className="mt-4 text-sm text-muted">
        Links de confirmacao valem por 24 horas e so podem ser usados uma vez.
        Tente entrar para pedir um novo envio.
      </p>
      <div className="mt-6">
        <Link href="/login" className="text-amber hover:brightness-110">
          Voltar para o login
        </Link>
      </div>
    </Card>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={<Spinner />}>
      <VerifyEmailContent />
    </Suspense>
  );
}
