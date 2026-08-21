-- =============================================================================
-- Concord — V12__server_icon.sql
-- Ícone do servidor.
--
-- A V9 acrescentou icon_attachment_id a "concord_servers", mas faltava a coluna
-- que a interface realmente lê: a URL pronta. Guardar a URL evita uma junção a
-- cada listagem de servidores — e a listagem acontece em toda navegação, na
-- barra lateral.
-- =============================================================================

ALTER TABLE concord_servers
    ADD COLUMN icon_url TEXT;

COMMENT ON COLUMN concord_servers.icon_url IS
    'Caminho do anexo do ícone (/api/attachments/<id>). Nulo = usa as iniciais.';
