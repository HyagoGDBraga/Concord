"use client";

/**
 * Canal de voz ativo.
 *
 * Existe para resolver um problema estrutural: a sala de voz vivia DENTRO da
 * página do canal. Sair dessa página — navegar para outro canal, abrir uma
 * conversa, qualquer coisa que desmontasse o componente — disparava a limpeza
 * que fechava todas as conexões e parava as trilhas. Quem estava compartilhando
 * a tela via a transmissão cair sozinha, e do outro lado a imagem
 * simplesmente sumia.
 *
 * Agora quem guarda "em que canal eu estou" é este contexto, montado na casca
 * do aplicativo. O painel de voz é renderizado ali, acima das rotas, e a
 * navegação deixa de ter qualquer efeito sobre a conexão — que é como qualquer
 * aplicativo de voz se comporta.
 */

import { createContext, useCallback, useContext, useMemo, useState } from "react";

export interface CanalDeVozAtivo {
  serverId: string;
  channelId: string;
  /** Nome do canal, só para exibir no painel encaixado. */
  channelName: string;
  serverName: string;
}

interface VoiceChannelState {
  ativo: CanalDeVozAtivo | null;
  entrar: (canal: CanalDeVozAtivo) => void;
  sair: () => void;
  /** Verdadeiro quando o canal informado é o que está ativo. */
  estaAtivo: (channelId: string) => boolean;
}

const VoiceChannelContext = createContext<VoiceChannelState | null>(null);

export function VoiceChannelProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [ativo, setAtivo] = useState<CanalDeVozAtivo | null>(null);

  const entrar = useCallback((canal: CanalDeVozAtivo) => {
    // Trocar de canal encerra o anterior: o componente é remontado com outra
    // chave, e a limpeza dele fecha as conexões antigas.
    setAtivo(canal);
  }, []);

  const sair = useCallback(() => setAtivo(null), []);

  const estaAtivo = useCallback(
    (channelId: string) => ativo?.channelId === channelId,
    [ativo],
  );

  const value = useMemo<VoiceChannelState>(
    () => ({ ativo, entrar, sair, estaAtivo }),
    [ativo, entrar, sair, estaAtivo],
  );

  return (
    <VoiceChannelContext.Provider value={value}>
      {children}
    </VoiceChannelContext.Provider>
  );
}

export function useVoiceChannel(): VoiceChannelState {
  const context = useContext(VoiceChannelContext);
  if (!context) {
    throw new Error("useVoiceChannel precisa estar dentro de VoiceChannelProvider");
  }
  return context;
}
