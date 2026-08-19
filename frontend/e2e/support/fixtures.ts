import type { Page } from "@playwright/test";
import { extractTokenLink, waitForEmail } from "./mailpit";

/** Senha que satisfaz a politica: 12+ caracteres, fora da lista de comuns. */
export const SENHA = "corrente-azul-38-vento";

export interface Conta {
  username: string;
  email: string;
  nome: string;
}

export function novaConta(prefixo = "e2e"): Conta {
  const sufixo = `${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
  const username = `${prefixo}_${sufixo}`.slice(0, 20);
  return {
    username,
    email: `${username}@exemplo.test`,
    nome: `Teste ${sufixo}`,
  };
}

/**
 * Cadastra, confirma o e-mail pelo link real e entra.
 *
 * Percorre o fluxo inteiro de proposito, sem inserir nada direto no banco: e o
 * caminho que o usuario faz, e o unico que prova que ele funciona.
 */
export async function cadastrarEEntrar(page: Page, conta: Conta): Promise<void> {
  await page.goto("/register");
  await page.getByLabel("Nome de usuario").fill(conta.username);
  await page.getByLabel("Nome de exibicao").fill(conta.nome);
  await page.getByLabel("E-mail").fill(conta.email);
  await page.getByLabel("Senha").fill(SENHA);
  await page.getByRole("button", { name: "Criar conta" }).click();

  await page.getByText("Confira seu e-mail").waitFor();

  const email = await waitForEmail(conta.email);
  await page.goto(extractTokenLink(email));
  await page.getByText("E-mail confirmado").waitFor();

  await entrar(page, conta);
}

export async function entrar(page: Page, conta: Conta): Promise<void> {
  await page.goto("/login");
  await page.getByLabel("Usuario ou e-mail").fill(conta.username);
  await page.getByLabel("Senha").fill(SENHA);
  await page.getByRole("button", { name: "Entrar" }).click();
  await page.getByText(`Ola, ${conta.nome}`).waitFor();
}

/** Deixa duas contas como contatos aceitos. */
export async function tornarContatos(
  paginaA: Page,
  contaB: Conta,
  paginaB: Page,
): Promise<void> {
  await paginaA.goto("/contacts");
  await paginaA.getByLabel("Nome de usuario").fill(contaB.username);
  await paginaA.getByRole("button", { name: "Enviar pedido" }).click();
  await paginaA.getByText("Pedido enviado.").waitFor();

  await paginaB.goto("/contacts");
  await paginaB.getByRole("button", { name: "Aceitar" }).click();
  await paginaB.getByText(contaB.username).first().waitFor();
}
