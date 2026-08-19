"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ApiError, api, errorMessage } from "@/lib/apiClient";
import { useSession } from "@/lib/session";
import type { Me } from "@/lib/types";
import { Alert, Button, Card, Field, Input } from "@/components/ui";

export default function LoginPage() {
  const router = useRouter();
  const { setUser } = useSession();

  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [needsVerification, setNeedsVerification] = useState(false);
  const [resent, setResent] = useState(false);
  const [loading, setLoading] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setNeedsVerification(false);
    setLoading(true);
    try {
      const me = await api.post<Me>("/auth/login", {
        usernameOrEmail: identifier,
        password,
      });
      setUser(me);
      router.replace("/");
    } catch (err) {
      // EMAIL_NOT_VERIFIED e o unico caso em que a resposta e especifica: a
      // informacao ja e conhecida por quem se cadastrou, e sem ela o usuario
      // ficaria preso sem entender o motivo.
      if (err instanceof ApiError && err.code === "EMAIL_NOT_VERIFIED") {
        setNeedsVerification(true);
      }
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }

  async function resendVerification() {
    try {
      await api.post("/auth/verify-email/resend", { email: identifier });
    } finally {
      setResent(true);
    }
  }

  return (
    <Card title="Entrar" description="Use seu nome de usuario ou e-mail.">
      <form onSubmit={submit} className="space-y-4">
        <Field label="Usuario ou e-mail">
          <Input
            value={identifier}
            onChange={(e) => setIdentifier(e.target.value)}
            autoComplete="username"
            required
            maxLength={254}
          />
        </Field>

        <Field label="Senha">
          <Input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
            maxLength={128}
          />
        </Field>

        {error && <Alert tone="error">{error}</Alert>}

        {needsVerification && !resent && identifier.includes("@") && (
          <Button type="button" variant="secondary" onClick={resendVerification}>
            Reenviar e-mail de confirmacao
          </Button>
        )}
        {resent && (
          <Alert tone="success">
            Se houver uma conta pendente com este e-mail, o link foi reenviado.
          </Alert>
        )}

        <Button type="submit" loading={loading} className="w-full">
          Entrar
        </Button>
      </form>

      <div className="mt-6 flex justify-between text-sm">
        <Link href="/forgot-password" className="text-muted hover:text-paper">
          Esqueci minha senha
        </Link>
        <Link href="/register" className="text-amber hover:brightness-110">
          Criar conta
        </Link>
      </div>
    </Card>
  );
}
