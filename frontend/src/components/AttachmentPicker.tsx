"use client";

/**
 * Anexar arquivos ao compositor de mensagens.
 *
 * Serve conversa direta e canal — muda só o destino. O arquivo sobe assim que
 * é escolhido, e não junto com a mensagem: assim o progresso é visível, e um
 * erro ao enviar o texto não obriga a subir tudo de novo.
 */

import { useRef, useState } from "react";
import {
  attachmentsApi,
  MAX_UPLOAD_BYTES,
  type AttachmentResponse,
} from "@/lib/chatApi";
import { errorMessage } from "@/lib/apiClient";
import { CloseIcon, PlusIcon } from "@/components/icons";

/** Tamanho legível, para a pessoa entender por que algo foi recusado. */
export function tamanhoLegivel(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function AttachmentPicker({
  destino,
  anexos,
  onChange,
  disabled,
}: {
  destino: { conversationId?: string; channelId?: string };
  anexos: AttachmentResponse[];
  onChange: (anexos: AttachmentResponse[]) => void;
  disabled?: boolean;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function escolher(evento: React.ChangeEvent<HTMLInputElement>) {
    const arquivos = Array.from(evento.target.files ?? []);
    // Limpa o input: sem isso, escolher o MESMO arquivo de novo não dispara
    // change e parece que o botão quebrou.
    evento.target.value = "";
    if (arquivos.length === 0) {
      return;
    }
    if (anexos.length + arquivos.length > 10) {
      setErro("No máximo 10 arquivos por mensagem.");
      return;
    }

    setErro(null);
    setEnviando(true);
    const enviados: AttachmentResponse[] = [];

    for (const arquivo of arquivos) {
      // Verificação no cliente é cortesia, não segurança — o servidor confere
      // de novo, e por conteúdo. Aqui ela evita subir 40 MB para receber erro.
      if (arquivo.size > MAX_UPLOAD_BYTES) {
        setErro(`"${arquivo.name}" passa de 5 MB.`);
        continue;
      }
      try {
        enviados.push(await attachmentsApi.uploadMessageFile(arquivo, destino));
      } catch (err) {
        setErro(errorMessage(err));
      }
    }

    setEnviando(false);
    if (enviados.length > 0) {
      onChange([...anexos, ...enviados]);
    }
  }

  function remover(id: string) {
    // Só tira da mensagem. O arquivo no servidor fica solto e o job de expurgo
    // o recolhe em 14 dias — apagar aqui exigiria um endpoint de exclusão que
    // abriria espaço para apagar anexo alheio por engano.
    onChange(anexos.filter((anexo) => anexo.id !== id));
  }

  return (
    <div className="attachment-picker">
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={disabled || enviando}
        className="attachment-add"
        title="Anexar arquivo (até 5 MB)"
        aria-label="Anexar arquivo"
      >
        {enviando ? <span className="attachment-spinner" /> : <PlusIcon size={18} />}
      </button>

      <input
        ref={inputRef}
        type="file"
        multiple
        onChange={(evento) => void escolher(evento)}
        className="hidden"
      />

      {(anexos.length > 0 || erro) && (
        <div className="attachment-tray">
          {anexos.map((anexo) => (
            <span key={anexo.id} className="attachment-chip">
              {anexo.image ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={anexo.url} alt="" />
              ) : (
                <span className="attachment-chip-ext">
                  {anexo.name.split(".").pop()?.slice(0, 4).toUpperCase() ?? "ARQ"}
                </span>
              )}
              <span className="attachment-chip-name">
                <strong>{anexo.name}</strong>
                <small>{tamanhoLegivel(anexo.sizeBytes)}</small>
              </span>
              <button
                type="button"
                onClick={() => remover(anexo.id)}
                aria-label={`Remover ${anexo.name}`}
              >
                <CloseIcon size={14} />
              </button>
            </span>
          ))}
          {erro && <span className="attachment-error">{erro}</span>}
        </div>
      )}
    </div>
  );
}

/**
 * Anexos dentro de uma mensagem já enviada.
 *
 * Imagem aparece inline; qualquer outra coisa vira um cartão de download. Um
 * arquivo desconhecido nunca é interpretado pelo navegador — o servidor já
 * responde com `attachment` e `nosniff`, e aqui o link só baixa.
 */
export function AttachmentList({ anexos }: { anexos: AttachmentResponse[] }) {
  if (anexos.length === 0) {
    return null;
  }
  return (
    <div className="attachment-list">
      {anexos.map((anexo) =>
        anexo.image ? (
          <a
            key={anexo.id}
            href={anexo.url}
            target="_blank"
            rel="noreferrer"
            className="attachment-image"
            title={`${anexo.name} — ${tamanhoLegivel(anexo.sizeBytes)}`}
          >
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={anexo.url} alt={anexo.name} loading="lazy" />
          </a>
        ) : (
          <a
            key={anexo.id}
            href={anexo.url}
            download={anexo.name}
            className="attachment-file"
          >
            <span className="attachment-file-ext">
              {anexo.name.split(".").pop()?.slice(0, 4).toUpperCase() ?? "ARQ"}
            </span>
            <span className="attachment-file-name">
              <strong>{anexo.name}</strong>
              <small>{tamanhoLegivel(anexo.sizeBytes)} · baixar</small>
            </span>
          </a>
        ),
      )}
    </div>
  );
}
