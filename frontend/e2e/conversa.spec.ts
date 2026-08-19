import { expect, test } from "@playwright/test";
import { clearInbox } from "./support/mailpit";
import { cadastrarEEntrar, novaConta, tornarContatos } from "./support/fixtures";

/**
 * Duas pessoas conversando de verdade, em dois contextos de navegador
 * independentes — cookies separados, como duas maquinas diferentes.
 *
 * E o teste que mais se aproxima do uso real: cobre cadastro, contato,
 * conversa, entrega em tempo real pelo WebSocket e presenca, tudo em um
 * caminho so.
 */
test.describe("Conversa entre duas pessoas", () => {
  test.beforeEach(async () => {
    await clearInbox();
  });

  test("mensagem enviada aparece na tela do destinatario sem recarregar", async ({
    browser,
  }) => {
    const contextoAna = await browser.newContext();
    const contextoBruno = await browser.newContext();
    const paginaAna = await contextoAna.newPage();
    const paginaBruno = await contextoBruno.newPage();

    try {
      const ana = novaConta("ana");
      const bruno = novaConta("bru");

      await cadastrarEEntrar(paginaAna, ana);
      await cadastrarEEntrar(paginaBruno, bruno);

      await tornarContatos(paginaAna, bruno, paginaBruno);

      // Ana abre a conversa e escreve.
      await paginaAna.goto("/contacts");
      await paginaAna.getByRole("button", { name: "Conversar" }).click();
      await expect(paginaAna).toHaveURL(/\/conversations\//);

      const texto = `mensagem de teste ${Date.now()}`;
      await paginaAna.getByLabel("Mensagem").fill(texto);
      await paginaAna.getByRole("button", { name: "Enviar" }).click();
      await expect(paginaAna.getByText(texto)).toBeVisible();

      // Bruno abre a conversa. A mensagem ja esta la.
      await paginaBruno.goto("/conversations");
      await paginaBruno.getByText(ana.nome).click();
      await expect(paginaBruno.getByText(texto)).toBeVisible();

      // Ana escreve de novo. Bruno recebe SEM recarregar — este assert e o que
      // prova que o WebSocket esta entregando.
      const segunda = `chegou em tempo real ${Date.now()}`;
      await paginaAna.getByLabel("Mensagem").fill(segunda);
      await paginaAna.getByRole("button", { name: "Enviar" }).click();

      await expect(paginaBruno.getByText(segunda)).toBeVisible({ timeout: 15_000 });

      // O cabecalho indica conexao ativa.
      await expect(paginaBruno.getByText("Ao vivo")).toBeVisible();
    } finally {
      await contextoAna.close();
      await contextoBruno.close();
    }
  });

  test("nao se conversa com quem nao e contato", async ({ browser }) => {
    const contexto = await browser.newContext();
    const pagina = await contexto.newPage();

    try {
      const conta = novaConta("sem");
      await cadastrarEEntrar(pagina, conta);

      await pagina.goto("/conversations");
      await expect(pagina.getByText("Nenhuma conversa ainda")).toBeVisible();

      await pagina.goto("/contacts");
      await expect(
        pagina.getByText("Nenhum contato ainda", { exact: false }),
      ).toBeVisible();
    } finally {
      await contexto.close();
    }
  });

  test("usuario comum nao alcanca o painel administrativo", async ({ page }) => {
    const conta = novaConta("adm");
    await cadastrarEEntrar(page, conta);

    // O link nem aparece na navegacao.
    await expect(page.getByRole("link", { name: "Usuarios" })).toHaveCount(0);

    // E ir direto pela URL tambem nao leva a lugar nenhum.
    await page.goto("/admin/users");
    await expect(page).toHaveURL("/");
  });
});
