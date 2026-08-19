package app.concord.support;

import app.concord.email.EmailMessage;
import app.concord.email.EmailProvider;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base dos testes de integração.
 *
 * <p>Sobe a aplicação em porta real, com PostgreSQL de verdade via
 * Testcontainers. Não usa MockMvc de propósito: o Spring Session registra um
 * filtro de servlet que o MockMvc não inclui por padrão, e testar autenticação
 * sem o filtro de sessão testaria outra coisa.
 *
 * <p>O {@link EmailProvider} é substituído por um mock — os testes leem o link
 * de verificação a partir da mensagem capturada, exatamente como um usuário
 * leria do e-mail. Isso não é apenas conveniência: como só o hash do token é
 * persistido, ler o banco não permitiria recuperar o valor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    protected int port;

    @MockitoBean
    protected EmailProvider emailProvider;

    @Autowired
    protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    protected TestApiClient api;

    @BeforeEach
    void setUpClient() {
        Mockito.reset(emailProvider);
        Mockito.when(emailProvider.name()).thenReturn("mock");
        api = new TestApiClient(port, objectMapper);
    }

    /** Usuário de teste já cadastrado, verificado e autenticado. */
    public record TestUser(TestApiClient client, java.util.UUID id, String username) {
    }

    /**
     * Cria uma conta pronta para uso, com cliente HTTP próprio.
     *
     * <p>Cada usuário recebe seu próprio {@link TestApiClient} justamente para
     * que os cookies não se misturem — é o que permite simular duas pessoas
     * conversando, ou uma tentando acessar os dados da outra.
     */
    protected TestUser newUser() {
        TestApiClient client = new TestApiClient(port, objectMapper);
        String username = TestData.uniqueUsername();
        String email = TestData.emailFor(username);

        client.post("/auth/register", TestData.registerPayload(username, email));
        client.post("/auth/verify-email",
                java.util.Map.of("token", tokenFromEmail(lastEmailTo(email))));
        TestApiClient.Response login = client.post("/auth/login",
                TestData.loginPayload(username, TestData.VALID_PASSWORD));

        if (login.status() != 200) {
            throw new AssertionError("Falha ao autenticar usuário de teste: " + login.body());
        }
        return new TestUser(client,
                java.util.UUID.fromString(login.json().get("id").asText()), username);
    }

    /** Todas as mensagens enviadas até agora, na ordem. */
    protected List<EmailMessage> sentEmails() {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        Mockito.verify(emailProvider, Mockito.atLeastOnce()).send(captor.capture());
        return captor.getAllValues();
    }

    /** Última mensagem enviada para o endereço informado. */
    protected EmailMessage lastEmailTo(String address) {
        List<EmailMessage> all = sentEmails();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).to().equalsIgnoreCase(address)) {
                return all.get(i);
            }
        }
        throw new AssertionError("Nenhum e-mail enviado para " + address);
    }

    /** Extrai o parâmetro token=... do link contido no e-mail. */
    protected String tokenFromEmail(EmailMessage message) {
        Matcher matcher = Pattern.compile("token=([A-Za-z0-9_%\\-]+)").matcher(message.html());
        if (!matcher.find()) {
            throw new AssertionError("Nenhum token encontrado no e-mail: " + message.subject());
        }
        return java.net.URLDecoder.decode(matcher.group(1), java.nio.charset.StandardCharsets.UTF_8);
    }
}
