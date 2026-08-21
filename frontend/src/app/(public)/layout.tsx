/**
 * Paginas legais, acessiveis sem sessao.
 *
 * Precisam ser publicas: quem esta se cadastrando ainda nao tem conta e mesmo
 * assim tem de poder ler o que esta aceitando.
 */
import Link from "next/link";

export default function PublicLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <main className="mx-auto w-full max-w-3xl px-6 py-12">
      {/* Link e nao <a>: navegacao interna sem recarregar a pagina, e o build
          de producao recusa <a> para rota do proprio aplicativo. */}
      <Link
        href="/"
        className="font-mono text-xs uppercase tracking-[0.3em] text-amber"
      >
        Concord
      </Link>
      <div className="mt-8 space-y-4 text-sm leading-relaxed text-paper">
        {children}
      </div>
    </main>
  );
}
