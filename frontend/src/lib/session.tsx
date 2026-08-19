"use client";

/**
 * Estado da sessao no cliente.
 *
 * Deliberadamente sem biblioteca de estado global: e um unico objeto, lido de
 * um unico endpoint. `zustand` ou TanStack Query entrariam aqui sem resolver
 * nenhum problema que exista hoje — a Fase 3, com paginacao de mensagens, e o
 * momento certo para reavaliar.
 *
 * A fonte da verdade e sempre `GET /auth/me`. O cliente nao guarda identidade
 * em localStorage: se a sessao foi revogada no servidor, a proxima requisicao
 * devolve 401 e a interface reage.
 */

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { ApiError, api } from "./apiClient";
import type { Me } from "./types";

interface SessionState {
  user: Me | null;
  loading: boolean;
  /** Recarrega o perfil a partir do servidor. */
  refresh: () => Promise<Me | null>;
  /** Substitui o perfil em memoria apos uma alteracao ja confirmada. */
  setUser: (user: Me | null) => void;
  logout: () => Promise<void>;
}

const SessionContext = createContext<SessionState | null>(null);

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      const me = await api.get<Me>("/auth/me");
      setUser(me);
      return me;
    } catch (error) {
      // 401 aqui e o caso normal de visitante nao autenticado, nao um erro.
      if (!(error instanceof ApiError && error.isUnauthenticated)) {
        console.error("Falha ao carregar a sessao", error);
      }
      setUser(null);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.post("/auth/logout");
    } finally {
      setUser(null);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const value = useMemo<SessionState>(
    () => ({ user, loading, refresh, setUser, logout }),
    [user, loading, refresh, logout],
  );

  return (
    <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
  );
}

export function useSession(): SessionState {
  const context = useContext(SessionContext);
  if (!context) {
    throw new Error("useSession precisa estar dentro de SessionProvider");
  }
  return context;
}
