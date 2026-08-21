"use client";

import Image from "next/image";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useSession } from "@/lib/session";
import { Spinner } from "@/components/ui";

/**
 * Moldura das telas publicas de autenticacao.
 *
 * Duas colunas: formulario a esquerda, marca a direita. A imagem e decorativa —
 * fica fora do fluxo de leitura e some em tela estreita, onde roubaria o espaco
 * de que o formulario precisa.
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
    <div className="auth-split">
      <main className="auth-form-side">
        <div className="auth-form-inner">
          <header className="mb-8">
            <p className="font-mono text-xs uppercase tracking-[0.3em] text-muted">
              Concord
            </p>
            <div className="mt-2 h-px w-16 bg-amber" />
          </header>
          {loading ? <Spinner /> : children}
        </div>
      </main>

      {/* aria-hidden: a imagem nao carrega informacao. Um leitor de tela
          anunciando "Concorde sobre a Terra" antes do formulario so atrasaria
          quem quer entrar. */}
      <aside className="auth-brand-side" aria-hidden="true">
        <Image
          src="/concord-hero.png"
          alt=""
          fill
          priority
          sizes="(max-width: 900px) 0px, 50vw"
          className="auth-brand-image"
        />
        <div className="auth-brand-overlay" />
        <p className="auth-brand-caption">
          <span className="auth-brand-name">Concord</span>
          Voz, vídeo e tela — direto entre vocês.
        </p>
      </aside>
    </div>
  );
}
