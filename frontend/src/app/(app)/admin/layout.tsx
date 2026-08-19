"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useSession } from "@/lib/session";
import { Spinner } from "@/components/ui";

/**
 * Guarda de papel para as telas administrativas.
 *
 * Fica aninhado dentro de (app), entao herda a moldura e a guarda de sessao —
 * aqui so se acrescenta a verificacao de ADMIN.
 *
 * Como no resto do frontend, isto e conveniencia de navegacao. A autorizacao
 * real esta no backend, que responde 404 em /api/admin/** para quem nao e
 * administrador, independentemente do que a interface decida mostrar.
 */
export default function AdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, loading } = useSession();
  const router = useRouter();

  useEffect(() => {
    if (!loading && user && user.role !== "ADMIN") {
      router.replace("/");
    }
  }, [loading, user, router]);

  if (!user || user.role !== "ADMIN") {
    return <Spinner label="Verificando permissao" />;
  }
  return <>{children}</>;
}
