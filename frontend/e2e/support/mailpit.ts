/**
 * Leitura da caixa de entrada do Mailpit.
 *
 * O teste le o link de confirmacao do e-mail, exatamente como um usuario faria.
 * Nao ha atalho possivel: o banco guarda apenas o hash do token, entao consultar
 * o banco nao devolveria um link utilizavel. Uma decisao de seguranca da Fase 2
 * define como o teste precisa ser escrito.
 */

const MAILPIT_URL = process.env.MAILPIT_URL ?? "http://localhost:8025";

interface MailpitSummary {
  messages: Array<{ ID: string; To: Array<{ Address: string }>; Subject: string }>;
}

interface MailpitMessage {
  HTML: string;
  Text: string;
  Subject: string;
}

/** Apaga tudo. Cada teste comeca com a caixa vazia. */
export async function clearInbox(): Promise<void> {
  await fetch(`${MAILPIT_URL}/api/v1/messages`, { method: "DELETE" });
}

/** Espera o e-mail chegar e devolve seu conteudo. */
export async function waitForEmail(
  to: string,
  timeoutMs = 20_000,
): Promise<MailpitMessage> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    const response = await fetch(`${MAILPIT_URL}/api/v1/messages?limit=50`);
    if (response.ok) {
      const summary = (await response.json()) as MailpitSummary;
      const found = summary.messages.find((message) =>
        message.To.some(
          (recipient) => recipient.Address.toLowerCase() === to.toLowerCase(),
        ),
      );
      if (found) {
        const detail = await fetch(`${MAILPIT_URL}/api/v1/message/${found.ID}`);
        return (await detail.json()) as MailpitMessage;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`Nenhum e-mail para ${to} em ${timeoutMs}ms`);
}

/** Extrai o primeiro link que contenha um token. */
export function extractTokenLink(message: MailpitMessage): string {
  const match = message.HTML.match(/https?:\/\/[^"'\s]*token=[^"'\s&]+/);
  if (!match) {
    throw new Error(`Nenhum link com token em: ${message.Subject}`);
  }
  return match[0].replace(/&amp;/g, "&");
}
