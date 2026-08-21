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

export type ThemeName = "classic" | "terminal" | "light" | "custom";

/** As cores que o tema personalizado permite ajustar. */
export interface CustomPalette {
  ink: string;
  panel: string;
  elevated: string;
  line: string;
  paper: string;
  muted: string;
  accent: string;
  mint: string;
  coral: string;
}

export const PALETA_PADRAO: CustomPalette = {
  ink: "#313338",
  panel: "#2b2d31",
  elevated: "#383a40",
  line: "#3f4147",
  paper: "#f2f3f5",
  muted: "#b5bac1",
  accent: "#5865f2",
  mint: "#57f287",
  coral: "#ed4245",
};

const PALETTE_KEY = "concord.palette";

const STORAGE_KEY = "concord.theme";

interface ThemeState {
  theme: ThemeName;
  setTheme: (theme: ThemeName) => void;
  toggle: () => void;
  /** Cores do tema personalizado. Só têm efeito quando theme === "custom". */
  palette: CustomPalette;
  setPaletteColor: (chave: keyof CustomPalette, valor: string) => void;
  resetPalette: () => void;
}

const ThemeContext = createContext<ThemeState | null>(null);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setThemeState] = useState<ThemeName>("classic");
  const [palette, setPalette] = useState<CustomPalette>(PALETA_PADRAO);

  // Le a preferencia depois da montagem, nao durante o render: o servidor nao
  // tem acesso ao localStorage, e ler no render causaria divergencia de
  // hidratacao.
  useEffect(() => {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    if (saved === "classic" || saved === "terminal" || saved === "light" || saved === "custom") {
      setThemeState(saved);
    }
    const cores = window.localStorage.getItem(PALETTE_KEY);
    if (cores) {
      try {
        // Mescla com o padrao: se uma versao futura acrescentar uma cor, o
        // valor salvo nao fica com um buraco.
        setPalette({ ...PALETA_PADRAO, ...JSON.parse(cores) });
      } catch {
        // Preferencia corrompida nao deve impedir o aplicativo de abrir.
      }
    }
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);

  /**
   * Aplica o tema personalizado escrevendo as variaveis CSS direto no <html>.
   *
   * Variavel inline vence a folha de estilo sem !important e sem gerar CSS em
   * tempo de execucao — e o mecanismo mais simples que permite o usuario mudar
   * qualquer cor.
   */
  useEffect(() => {
    const raiz = document.documentElement;
    const chaves: (keyof CustomPalette)[] = [
      "ink", "panel", "elevated", "line", "paper", "muted", "accent", "mint", "coral",
    ];

    if (theme !== "custom") {
      // Remove o que foi escrito antes, senao as cores personalizadas
      // vazariam para os outros temas.
      for (const chave of chaves) {
        raiz.style.removeProperty(`--color-${chave}`);
      }
      raiz.style.removeProperty("--color-amber");
      return;
    }
    for (const chave of chaves) {
      raiz.style.setProperty(`--color-${chave}`, palette[chave]);
    }
    // amber e o nome antigo do destaque; varios componentes ainda o usam.
    raiz.style.setProperty("--color-amber", palette.accent);
  }, [theme, palette]);

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

  const setPaletteColor = useCallback(
    (chave: keyof CustomPalette, valor: string) => {
      setPalette((atual) => {
        const proxima = { ...atual, [chave]: valor };
        window.localStorage.setItem(PALETTE_KEY, JSON.stringify(proxima));
        return proxima;
      });
    },
    [],
  );

  const resetPalette = useCallback(() => {
    setPalette(PALETA_PADRAO);
    window.localStorage.setItem(PALETTE_KEY, JSON.stringify(PALETA_PADRAO));
  }, []);

  return (
    <ThemeContext.Provider
      value={{ theme, setTheme, toggle, palette, setPaletteColor, resetPalette }}
    >
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
