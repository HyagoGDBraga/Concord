"use client";

import { ActivityHub } from "@/components/ActivityHub";

/**
 * Pagina inicial.
 *
 * A tela de boas-vindas anterior tinha tres atalhos fixos — util no primeiro
 * dia, vazia a partir do segundo. Quem abre o aplicativo quer saber o que esta
 * acontecendo agora, nao onde ficam os menus.
 */
export default function HomePage() {
  return <ActivityHub />;
}
