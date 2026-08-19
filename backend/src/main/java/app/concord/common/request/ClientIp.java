package app.concord.common.request;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolve o IP real do cliente.
 *
 * <p>Depende de {@code server.forward-headers-strategy=framework}: o
 * {@code ForwardedHeaderFilter} do Spring já processa {@code X-Forwarded-For}
 * antes de qualquer código da aplicação rodar, então
 * {@code getRemoteAddr()} devolve o IP de origem e não o do Caddy.
 *
 * <p>Ler o header manualmente seria pior: um cliente pode forjar
 * {@code X-Forwarded-For}, e sem saber quantos proxies existem à frente não há
 * como escolher a entrada correta com segurança.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getRemoteAddr();
        return (ip == null || ip.isBlank()) ? null : ip;
    }
}
