"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { ApiError, api, errorMessage } from "@/lib/apiClient";
import { Alert, Button, Card, Field, Input, Spinner } from "@/components/ui";

function ResetPasswordContent() {
  const token = useSearchParams().get("token");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [done, setDone] = useState(false);
  const [loading, setLoading] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});

    if (password !== confirmation) {
      setFieldErrors({ confirmation: "As senhas nao sao iguais" });
      return;
    }

    setLoading(true);
    try {
      await api.post("/auth/password/reset", { token, newPassword: password });
      setDone(true);
    } catch (err) {
      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors);
      }
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  if (!token) {
    return (
      <Card title="Link invalido">
        <Alert tone="error">Token ausente no endereco.</Alert>
      </Card>
    );
  }

  if (done) {
    return (
      <Card title="Senha redefinida">
        <Alert tone="success">
          Sua senha foi alterada e todas as sessoes abertas foram encerradas.
        </Alert>
        <div className="mt-6">
          <Link href="/login" className="text-amber hover:brightness-110">
            Entrar com a nova senha
          </Link>
        </div>
      </Card>
    );
  }

  return (
    <Card title="Criar nova senha">
      <form onSubmit={submit} className="space-y-4">
        <Field
          label="Nova senha"
          error={fieldErrors.newPassword}
          hint="Minimo de 12 caracteres."
        >
          <Input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            required
            minLength={12}
            maxLength={128}
          />
        </Field>
        <Field label="Repita a nova senha" error={fieldErrors.confirmation}>
          <Input
            type="password"
            value={confirmation}
            onChange={(e) => setConfirmation(e.target.value)}
            autoComplete="new-password"
            required
          />
        </Field>
        {error && <Alert tone="error">{error}</Alert>}
        <p className="text-xs text-muted">
          Ao concluir, todos os dispositivos conectados serao desconectados.
        </p>
        <Button type="submit" loading={loading} className="w-full">
          Redefinir senha
        </Button>
      </form>
    </Card>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<Spinner />}>
      <ResetPasswordContent />
    </Suspense>
  );
}
