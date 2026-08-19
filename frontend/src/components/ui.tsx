"use client";

/**
 * Kit de interface do Concord.
 *
 * Escrito a mao, sem shadcn/ui. O plano da Fase 2 previa shadcn, mas ele e
 * distribuido por um gerador de codigo (`npx shadcn add`) que precisa rodar
 * contra o projeto; transcrever os componentes manualmente traria dezenas de
 * arquivos com risco de divergencia silenciosa. Sete componentes proprios
 * cobrem todas as telas desta fase e mantem o controle do visual.
 */

import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from "react";

/* ----------------------------------------------------------------- Button */

type ButtonVariant = "primary" | "secondary" | "danger" | "ghost";

const BUTTON_STYLES: Record<ButtonVariant, string> = {
  primary: "bg-amber text-ink hover:brightness-110",
  secondary: "border border-line bg-panel text-paper hover:border-muted",
  danger: "bg-coral text-ink hover:brightness-110",
  ghost: "text-muted hover:text-paper",
};

export function Button({
  variant = "primary",
  loading = false,
  className = "",
  children,
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  loading?: boolean;
}) {
  return (
    <button
      {...props}
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center gap-2 rounded px-4 py-2 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-50 ${BUTTON_STYLES[variant]} ${className}`}
    >
      {loading && (
        <span
          aria-hidden
          className="h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      )}
      {children}
    </button>
  );
}

/* ------------------------------------------------------------------ Field */

export function Field({
  label,
  error,
  hint,
  children,
}: {
  label: string;
  error?: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <label className="block">
      <span className="mb-1 block font-mono text-xs uppercase tracking-widest text-muted">
        {label}
      </span>
      {children}
      {hint && !error && (
        <span className="mt-1 block text-xs text-muted">{hint}</span>
      )}
      {error && (
        <span role="alert" className="mt-1 block text-xs text-coral">
          {error}
        </span>
      )}
    </label>
  );
}

/* ------------------------------------------------------------------ Input */

export function Input({
  invalid = false,
  className = "",
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }) {
  return (
    <input
      {...props}
      aria-invalid={invalid || undefined}
      className={`w-full rounded border bg-ink px-3 py-2 text-sm text-paper placeholder:text-muted/60 ${
        invalid ? "border-coral" : "border-line"
      } ${className}`}
    />
  );
}

export function Textarea({
  className = "",
  ...props
}: React.TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      {...props}
      className={`w-full rounded border border-line bg-ink px-3 py-2 text-sm text-paper placeholder:text-muted/60 ${className}`}
    />
  );
}

/* ------------------------------------------------------------------- Card */

export function Card({
  title,
  description,
  children,
  footer,
}: {
  title?: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <section className="rounded-md border border-line bg-panel p-6">
      {title && (
        <h2 className="text-lg font-semibold text-paper">{title}</h2>
      )}
      {description && (
        <p className="mt-1 text-sm text-muted">{description}</p>
      )}
      <div className={title || description ? "mt-5" : ""}>{children}</div>
      {footer && <div className="mt-5 border-t border-line pt-4">{footer}</div>}
    </section>
  );
}

/* ------------------------------------------------------------------ Alert */

type AlertTone = "info" | "success" | "error";

const ALERT_STYLES: Record<AlertTone, string> = {
  info: "border-line text-paper",
  success: "border-mint/50 text-mint",
  error: "border-coral/50 text-coral",
};

export function Alert({
  tone = "info",
  children,
}: {
  tone?: AlertTone;
  children: ReactNode;
}) {
  return (
    <div
      role={tone === "error" ? "alert" : "status"}
      className={`rounded border bg-ink/60 px-3 py-2 text-sm ${ALERT_STYLES[tone]}`}
    >
      {children}
    </div>
  );
}

/* ------------------------------------------------------------------ Badge */

export function Badge({
  tone = "neutral",
  children,
}: {
  tone?: "neutral" | "good" | "warn" | "bad";
  children: ReactNode;
}) {
  const tones = {
    neutral: "border-line text-muted",
    good: "border-mint/50 text-mint",
    warn: "border-amber/50 text-amber",
    bad: "border-coral/50 text-coral",
  } as const;
  return (
    <span
      className={`inline-block rounded border px-2 py-0.5 font-mono text-[11px] uppercase tracking-wider ${tones[tone]}`}
    >
      {children}
    </span>
  );
}

/* ---------------------------------------------------------------- Spinner */

export function Spinner({ label = "Carregando" }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 text-sm text-muted">
      <span
        aria-hidden
        className="h-4 w-4 animate-spin rounded-full border-2 border-muted border-t-transparent"
      />
      {label}
    </div>
  );
}
