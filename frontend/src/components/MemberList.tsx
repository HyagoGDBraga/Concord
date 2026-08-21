"use client";

/**
 * Lista de membros do servidor.
 *
 * Agrupada por cargo e depois por presença, que é a ordem em que a informação
 * é procurada: primeiro "quem manda aqui", depois "quem está agora".
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { serversApi, type ServerMember } from "@/lib/chatApi";
import { useRealtime, useRealtimeEvent } from "@/lib/realtime";
import { CrownIcon, ShieldIcon } from "@/components/icons";

type Cargo = "OWNER" | "MODERATOR" | "MEMBER";

const ROTULOS: Record<Cargo, string> = {
  OWNER: "Dono",
  MODERATOR: "Moderadores",
  MEMBER: "Membros",
};

/** Ordem de exibição dos grupos. */
const ORDEM: Cargo[] = ["OWNER", "MODERATOR", "MEMBER"];

export function MemberList({ serverId }: { serverId: string }) {
  const [members, setMembers] = useState<ServerMember[] | null>(null);
  const { onlineUserIds, voiceParticipantsByChannel } = useRealtime();

  /**
   * Quem esta em alguma sala de voz agora.
   *
   * A informacao ja chega pelos eventos de sala; aqui ela so e achatada num
   * conjunto para responder "esta em chamada?" sem percorrer canal por canal a
   * cada linha renderizada.
   */
  const emChamada = useMemo(() => {
    const conjunto = new Set<string>();
    for (const participantes of voiceParticipantsByChannel.values()) {
      for (const id of participantes) {
        conjunto.add(id);
      }
    }
    return conjunto;
  }, [voiceParticipantsByChannel]);

  const carregar = useCallback(() => {
    serversApi
      .members(serverId)
      .then(setMembers)
      .catch(() => setMembers([]));
  }, [serverId]);

  useEffect(carregar, [carregar]);

  // Alguém entrou pelo convite enquanto a lista estava aberta.
  useRealtimeEvent("SERVER_MEMBER_JOINED", carregar);

  const grupos = useMemo(() => {
    const porCargo = new Map<Cargo, ServerMember[]>();
    for (const cargo of ORDEM) {
      porCargo.set(cargo, []);
    }
    for (const membro of members ?? []) {
      porCargo.get(membro.role as Cargo)?.push(membro);
    }
    // Dentro de cada cargo: online primeiro, depois alfabético. Quem está
    // disponível agora é o que interessa na maior parte das vezes.
    for (const lista of porCargo.values()) {
      lista.sort((a, b) => {
        const aOnline = onlineUserIds.has(a.user.id) ? 0 : 1;
        const bOnline = onlineUserIds.has(b.user.id) ? 0 : 1;
        if (aOnline !== bOnline) {
          return aOnline - bOnline;
        }
        return (a.nickname ?? a.user.displayName).localeCompare(
          b.nickname ?? b.user.displayName,
          "pt-BR",
        );
      });
    }
    return porCargo;
  }, [members, onlineUserIds]);

  const total = members?.length ?? 0;
  const online = (members ?? []).filter((m) =>
    onlineUserIds.has(m.user.id),
  ).length;

  return (
    <aside className="member-list" aria-label="Membros do servidor">
      <p className="eyebrow member-list-title">
        Membros — {online} de {total} online
      </p>

      {members === null && (
        <p className="member-empty">Carregando…</p>
      )}

      {ORDEM.map((cargo) => {
        const lista = grupos.get(cargo) ?? [];
        if (lista.length === 0) {
          return null;
        }
        return (
          <section key={cargo} className="member-group">
            <h3 className="member-group-title">
              {ROTULOS[cargo]} — {lista.length}
            </h3>
            <ul>
              {lista.map((membro) => {
                const nome = membro.nickname ?? membro.user.displayName;
                const estaOnline = onlineUserIds.has(membro.user.id);
                return (
                  <li
                    key={membro.user.id}
                    className={`member-row ${estaOnline ? "" : "is-offline"}`}
                    title={
                      membro.nickname
                        ? `${membro.nickname} — @${membro.user.username}`
                        : `@${membro.user.username}`
                    }
                  >
                    <span className="member-avatar">
                      {membro.user.avatarUrl ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img src={membro.user.avatarUrl} alt="" />
                      ) : (
                        nome.slice(0, 2).toUpperCase()
                      )}
                      {/* Ponto de presença sobre o avatar, como no Discord:
                          não ocupa linha e é reconhecível de relance. */}
                      <span
                        className={`member-presence ${
                          estaOnline ? "is-online" : ""
                        }`}
                        aria-label={estaOnline ? "online" : "offline"}
                      />
                    </span>

                    <span className="member-name">
                      {nome}
                      {/* Contexto sob o nome: saber que alguem esta em chamada
                          muda o que voce faz — chamar ou mandar mensagem. */}
                      {emChamada.has(membro.user.id) && (
                        <span className="member-activity">Em chamada de voz</span>
                      )}
                      {/* Quando ha apelido no servidor, o @username fica
                          visivel embaixo: sem isso nao daria para mencionar a
                          pessoa, porque a mencao usa o username e nao o
                          apelido. */}
                      {membro.nickname && (
                        <span className="member-handle">
                          @{membro.user.username}
                        </span>
                      )}
                    </span>

                    {cargo === "OWNER" && (
                      <CrownIcon size={13} className="member-badge is-owner" />
                    )}
                    {cargo === "MODERATOR" && (
                      <ShieldIcon size={13} className="member-badge is-mod" />
                    )}
                  </li>
                );
              })}
            </ul>
          </section>
        );
      })}

      {members !== null && total === 0 && (
        <p className="member-empty">
          Você é o único aqui. Gere um convite para trazer alguém.
        </p>
      )}
    </aside>
  );
}
