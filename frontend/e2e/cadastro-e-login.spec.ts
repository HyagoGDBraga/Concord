import { expect, test } from "@playwright/test";
import { clearInbox, extractTokenLink, waitForEmail } from "./support/mailpit";
import { SENHA, cadastrarEEntrar, entrar, novaConta } from "./support/fixtures";

test.describe("Cadastro e autenticacao", () => {
  test.beforeEach(async () => {
    await clearInbox();
  });

  test("cadastro exige confirmacao de e-mail antes do primeiro acesso", async ({
    page,
  }) => {
    const conta = novaConta("cad");

    await page.goto("/register");
    await page.getByLabel("Nome de usuario").fill(conta.username);
    await page.getByLabel("Nome de exibicao").fill(conta.nome);
    await page.getByLabel("E-mail").fill(conta.email);
    await page.getByLabel("Senha").fill(SENHA);
    await page.getByRole("button", { name: "Criar conta" }).click();

    await expect(page.getByText("Confira seu e-mail")).toBeVisible();

    // Tentar entrar antes de confirmar deve falhar com mensagem especifica.
    await page.goto("/login");
    await page.getByLabel("Usuario ou e-mail").fill(conta.username);
    await page.getByLabel("Senha").fill(SENHA);
    await page.getByRole("button", { name: "Entrar" }).click();
    await expect(page.getByRole("alert")).toContainText("Confirme seu e-mail");

    // Confirmar pelo link real do e-mail.
    const email = await waitForEmail(conta.email);
    expect(email.Subject).toContain("Confirme seu e-mail");
    await page.goto(extractTokenLink(email));
    await expect(page.getByText("E-mail confirmado")).toBeVisible();

    await entrar(page, conta);
    await expect(page.getByText(`Ola, ${conta.nome}`)).toBeVisible();
  });

  test("senha errada devolve mensagem generica", async ({ page }) => {
    const conta = novaConta("gen");
    await cadastrarEEntrar(page, conta);

    await page.goto("/settings");
    await page.getByRole("button", { name: "Sair" }).click();

    await page.goto("/login");
    await page.getByLabel("Usuario ou e-mail").fill(conta.username);
    await page.getByLabel("Senha").fill("senha-errada-mas-longa");
    await page.getByRole("button", { name: "Entrar" }).click();

    // A mensagem nao pode distinguir "conta nao existe" de "senha errada".
    await expect(page.getByRole("alert")).toContainText("Usuário ou senha inválidos");
  });

  test("area autenticada redireciona quem nao entrou", async ({ page }) => {
    await page.goto("/settings");
    await expect(page).toHaveURL(/\/login/);
  });

  test("o titular consegue baixar os proprios dados", async ({ page }) => {
    const conta = novaConta("exp");
    await cadastrarEEntrar(page, conta);

    await page.goto("/settings");

    const download = page.waitForEvent("download");
    await page.getByRole("button", { name: "Baixar meus dados" }).click();
    const arquivo = await download;

    expect(arquivo.suggestedFilename()).toContain("concord-meus-dados");
  });
});
