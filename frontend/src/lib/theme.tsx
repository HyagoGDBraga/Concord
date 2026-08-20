"use client";

/**
 * Temas do Concord.
 *
 * Dois: "classic" (padrao — cinza frio e blurple, a paleta comprovada para
 * aplicativo de comunidade) e "terminal" (bitmap sobre grafite, mais autoral).
 *
 * Trocar de tema so troca variaveis CSS — nenhum componente sabe qual esta
 * ativo, e por isso adicionar um terceiro nao exige mexer em tela nenhuma.
 */

import { createContext, useCallback, useContext, useEffect, useState } from "react";

export type ThemeName = "terminal" | "classic";

const STORAGE_KEY = "concord.theme";

interface ThemeState {
  theme: ThemeName;
  setTheme: (theme: ThemeName) => void;
  toggle: () => void;
}

const ThemeContext = createContext<ThemeState | null>(null);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setThemeState] = useState<ThemeName>("classic");

  // Le a preferencia depois da montagem, nao durante o render: o servidor nao
  // tem acesso ao localStorage, e ler no render causaria divergencia de
  // hidratacao.
  useEffect(() => {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    if (saved === "classic" || saved === "terminal") {
      setThemeState(saved);
    }
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);

  const setTheme = useCallback((next: ThemeName) => {
    setThemeState(next);
    window.localStorage.setItem(STORAGE_KEY, next);
  }, []);

  const toggle = useCallback(() => {
    setThemeState((current) => {
      const next: ThemeName = current === "terminal" ? "classic" : "terminal";
      window.localStorage.setItem(STORAGE_KEY, next);
      return next;
    });
  }, []);

  return (
    <ThemeContext.Provider value={{ theme, setTheme, toggle }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme(): ThemeState {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error("useTheme precisa estar dentro de ThemeProvider");
  }
  return context;
}
