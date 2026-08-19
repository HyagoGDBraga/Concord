"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useSession } from "@/lib/session";
import { Spinner } from "@/components/ui";

/**
 * Moldura das telas publicas de autenticacao.
 *
 * Quem ja tem sessao valida nao deveria ver a tela de login: e redirecionado
 * para dentro do aplicativo.
 */
export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, loading } = useSession();
  const router = useRouter();

  useEffect(() => {
    if (!loading && user) {
      router.replace("/");
    }
  }, [loading, user, router]);

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-md flex-col justify-center px-6 py-12">
      <header className="mb-8">
        <p className="font-mono text-xs uppercase tracking-[0.3em] text-muted">
          Concord
        </p>
        <div className="mt-2 h-px w-16 bg-amber" />
      </header>
      {loading ? <Spinner /> : children}
    </main>
  );
}
