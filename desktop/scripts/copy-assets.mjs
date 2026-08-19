// Copia os arquivos que o TypeScript ignora (HTML do seletor de tela).
import { copyFile, mkdir, readdir } from "node:fs/promises";
import { join } from "node:path";

await mkdir("dist", { recursive: true });

for (const arquivo of await readdir("src")) {
  if (arquivo.endsWith(".html") || arquivo.endsWith(".css")) {
    await copyFile(join("src", arquivo), join("dist", arquivo));
  }
}
console.log("Assets copiados para dist/");
