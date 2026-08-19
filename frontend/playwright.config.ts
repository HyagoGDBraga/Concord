import { defineConfig, devices } from "@playwright/test";

/**
 * Testes ponta a ponta.
 *
 * Rodam contra o ambiente do docker compose ja em pe — nao contra um servidor
 * de desenvolvimento isolado. E a diferenca entre testar a aplicacao e testar
 * uma montagem que so existe no teste: aqui o Caddy, o cookie de sessao, o
 * WebSocket e o Mailpit sao os mesmos que o usuario encontra.
 */
export default defineConfig({
  testDir: "./e2e",
  // Sem paralelismo: os testes compartilham um unico banco, e cadastro tem
  // limite de 3 por hora por IP. Execucao serial e mais lenta e muito menos
  // confusa de depurar.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },

  reporter: process.env.CI ? [["html"], ["github"]] : [["list"]],

  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost",
    // Evidencia so quando falha: video de teste que passou e lixo em disco.
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },

  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        // Concede microfone e camera sem dialogo. Sem isso, qualquer teste que
        // toque em chamada trava esperando um clique que ninguem vai dar.
        permissions: ["microphone", "camera"],
        launchOptions: {
          args: [
            "--use-fake-ui-for-media-stream",
            "--use-fake-device-for-media-stream",
          ],
        },
      },
    },
  ],
});
