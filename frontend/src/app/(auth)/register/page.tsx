"use client";

import Link from "next/link";
import { useState } from "react";
import { ApiError, api, errorMessage } from "@/lib/apiClient";
import { Alert, Button, Card, Field, Input } from "@/components/ui";

const MIN_PASSWORD_LENGTH = 12;

export default function RegisterPage() {
  const [form, setForm] = useState({
    username: "",
    email: "",
    password: "",
    displayName: "",
    // Honeypot: fica escondido e humanos nunca preenchem.
    website: "",
  });
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [loading, setLoading] = useState(false);

  function update(field: keyof typeof form, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    setLoading(true);
    try {
      await api.post("/auth/register", form);
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

  if (done) {
    return (
      <Card title="Confira seu e-mail">
        <Alert tone="success">
          Se o cadastro puder ser concluido, voce recebera um link de
          confirmacao. Ele vale por 24 horas.
        </Alert>
        <p className="mt-4 text-sm text-muted">
          A conta so fica ativa depois da confirmacao. Sem ela, o cadastro e
          removido automaticamente em 7 dias.
        </p>
        <div className="mt-6">
          <Link href="/login" className="text-amber hover:brightness-110">
            Voltar para o login
          </Link>
        </div>
      </Card>
    );
  }

  return (
    <Card title="Criar conta">
      <form onSubmit={submit} className="space-y-4">
        <Field
          label="Nome de usuario"
          error={fieldErrors.username}
          hint="3 a 20 caracteres: letras, numeros e _"
        >
          <Input
            value={form.username}
            onChange={(e) => update("username", e.target.value)}
            invalid={Boolean(fieldErrors.username)}
            autoComplete="username"
            required
            maxLength={20}
            pattern="[A-Za-z0-9_]{3,20}"
          />
        </Field>

        <Field label="Nome de exibicao" error={fieldErrors.displayName}>
          <Input
            value={form.displayName}
            onChange={(e) => update("displayName", e.target.value)}
            invalid={Boolean(fieldErrors.displayName)}
            required
            maxLength={50}
          />
        </Field>

        <Field label="E-mail" error={fieldErrors.email}>
          <Input
            type="email"
            value={form.email}
            onChange={(e) => update("email", e.target.value)}
            invalid={Boolean(fieldErrors.email)}
            autoComplete="email"
            required
            maxLength={254}
          />
        </Field>

        <Field
          label="Senha"
          error={fieldErrors.password}
          hint={`Minimo de ${MIN_PASSWORD_LENGTH} caracteres. Prefira uma frase a uma palavra com simbolos.`}
        >
          <Input
            type="password"
            value={form.password}
            onChange={(e) => update("password", e.target.value)}
            invalid={Boolean(fieldErrors.password)}
            autoComplete="new-password"
            required
            minLength={MIN_PASSWORD_LENGTH}
            maxLength={128}
          />
        </Field>

        {/* Honeypot. Fora da tela, sem tabIndex e sem autocomplete. */}
        <div aria-hidden className="absolute left-[-9999px] top-auto h-px w-px overflow-hidden">
          <label>
            Nao preencha este campo
            <input
              tabIndex={-1}
              autoComplete="off"
              value={form.website}
              onChange={(e) => update("website", e.target.value)}
            />
          </label>
        </div>

        {error && <Alert tone="error">{error}</Alert>}

        <Button type="submit" loading={loading} className="w-full">
          Criar conta
        </Button>
      </form>

      <p className="mt-6 text-sm text-muted">
        Ja tem conta?{" "}
        <Link href="/login" className="text-amber hover:brightness-110">
          Entrar
        </Link>
      </p>
    </Card>
  );
}
