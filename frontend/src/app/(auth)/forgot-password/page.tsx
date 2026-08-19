"use client";

import Link from "next/link";
import { useState } from "react";
import { api, errorMessage } from "@/lib/apiClient";
import { Alert, Button, Card, Field, Input } from "@/components/ui";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await api.post("/auth/password/forgot", { email });
      setSent(true);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  if (sent) {
    return (
      <Card title="Verifique seu e-mail">
        {/* Mensagem deliberadamente neutra: ela e a mesma exista ou nao a
            conta, para que a tela nao sirva de verificador de cadastros. */}
        <Alert tone="success">
          Se houver uma conta com este e-mail, o link de redefinicao foi
          enviado. Ele vale por 30 minutos.
        </Alert>
        <div className="mt-6">
          <Link href="/login" className="text-amber hover:brightness-110">
            Voltar para o login
          </Link>
        </div>
      </Card>
    );
  }

  return (
    <Card
      title="Esqueci minha senha"
      description="Enviaremos um link para redefinir."
    >
      <form onSubmit={submit} className="space-y-4">
        <Field label="E-mail">
          <Input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
            maxLength={254}
          />
        </Field>
        {error && <Alert tone="error">{error}</Alert>}
        <Button type="submit" loading={loading} className="w-full">
          Enviar link
        </Button>
      </form>
      <div className="mt-6 text-sm">
        <Link href="/login" className="text-muted hover:text-paper">
          Voltar
        </Link>
      </div>
    </Card>
  );
}
