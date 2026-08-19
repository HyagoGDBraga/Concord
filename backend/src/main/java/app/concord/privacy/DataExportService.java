package app.concord.privacy;

import app.concord.audit.AuditAction;
import app.concord.audit.AuditService;
import app.concord.call.Call;
import app.concord.call.CallRepository;
import app.concord.contact.Contact;
import app.concord.contact.ContactRepository;
import app.concord.conversation.Conversation;
import app.concord.conversation.ConversationRepository;
import app.concord.legal.ConsentService;
import app.concord.message.Message;
import app.concord.message.MessageRepository;
import app.concord.user.User;
import app.concord.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Exportação de dados do titular (Art. 18, V e VI da LGPD).
 *
 * <p>Regra que define o escopo: <b>exporta exatamente o que o titular já vê no
 * aplicativo</b>. Nada a mais, nada a menos.
 *
 * <p>Isso resolve o dilema das mensagens, que têm dois titulares. A conversa
 * inteira já está na tela dele; entregá-la em JSON não revela nada novo. Já os
 * dados do interlocutor entram só no mínimo que a interface mostra — username e
 * nome de exibição — e nunca o e-mail dele, que o titular jamais viu.
 *
 * <p>O que fica de fora e por quê:
 * <ul>
 *   <li>hash da senha — é credencial, não dado a ser devolvido;</li>
 *   <li>SDP e ICE — nunca foram gravados (Fase 5);</li>
 *   <li>audit_log — contém eventos sobre terceiros e é registro de segurança do
 *       controlador; o titular recebe o que lhe diz respeito por outro caminho,
 *       mediante pedido fundamentado.</li>
 * </ul>
 */
@Service
public class DataExportService {

    /** Teto por conversa. Evita que um pedido consuma memória sem limite. */
    private static final int MAX_MESSAGES_PER_CONVERSATION = 20_000;

    private final UserRepository userRepository;
    private final ContactRepository contactRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final CallRepository callRepository;
    private final ConsentService consentService;
    private final AuditService auditService;

    public DataExportService(UserRepository userRepository,
                             ContactRepository contactRepository,
                             ConversationRepository conversationRepository,
                             MessageRepository messageRepository,
                             CallRepository callRepository,
                             ConsentService consentService,
                             AuditService auditService) {
        this.userRepository = userRepository;
        this.contactRepository = contactRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.callRepository = callRepository;
        this.consentService = consentService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> export(User me) {
        Map<String, Object> root = new HashMap<>();

        root.put("formatoVersao", 1);
        root.put("geradoEm", Instant.now().toString());
        root.put("aviso", "Este arquivo contém dados pessoais. Guarde-o com cuidado.");

        root.put("perfil", perfil(me));
        root.put("contatos", contatos(me));
        root.put("conversas", conversas(me));
        root.put("consentimentos", consentService.historyOf(me.getId()).records());

        auditService.privacy(AuditAction.DATA_EXPORTED, me.getId(), me.getUsername(),
                me.getId(), Map.of());

        return root;
    }

    private Map<String, Object> perfil(User me) {
        Map<String, Object> perfil = new HashMap<>();
        perfil.put("id", me.getId().toString());
        perfil.put("nomeDeUsuario", me.getUsername());
        perfil.put("email", me.getEmail());
        perfil.put("nomeDeExibicao", me.getDisplayName());
        perfil.put("bio", me.getBio());
        perfil.put("papel", me.getRole().name());
        perfil.put("estadoDaConta", me.getStatus().name());
        perfil.put("criadaEm", me.getCreatedAt());
        perfil.put("emailVerificadoEm", me.getEmailVerifiedAt());
        perfil.put("ultimoAcessoEm", me.getLastLoginAt());
        // Sem hash de senha: é credencial, não dado do titular a ser devolvido.
        return perfil;
    }

    private List<Map<String, Object>> contatos(User me) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Contact contact : contactRepository.findAcceptedOf(me.getId())) {
            UUID outroId = contact.otherSide(me.getId());
            userRepository.findById(outroId).ifPresent(outro -> {
                Map<String, Object> item = new HashMap<>();
                item.put("nomeDeUsuario", outro.getUsername());
                item.put("nomeDeExibicao", outro.getDisplayName());
                item.put("contatoDesde", contact.getRespondedAt());
                // Sem o e-mail do outro: o titular nunca o viu na interface.
                resultado.add(item);
            });
        }
        return resultado;
    }

    private List<Map<String, Object>> conversas(User me) {
        List<Map<String, Object>> resultado = new ArrayList<>();

        for (Conversation conversation : conversationRepository.findAllOf(me.getId())) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", conversation.getId().toString());
            item.put("criadaEm", conversation.getCreatedAt());
            item.put("mensagens", mensagens(conversation.getId(), me));
            item.put("chamadas", chamadas(conversation.getId(), me));
            resultado.add(item);
        }
        return resultado;
    }

    private List<Map<String, Object>> mensagens(UUID conversationId, User me) {
        List<Message> mensagens = messageRepository.findLatest(conversationId,
                PageRequest.of(0, MAX_MESSAGES_PER_CONVERSATION));

        Map<UUID, String> nomes = new HashMap<>();
        List<Map<String, Object>> resultado = new ArrayList<>(mensagens.size());

        // Do mais antigo para o mais novo, como a conversa é lida.
        for (int i = mensagens.size() - 1; i >= 0; i--) {
            Message mensagem = mensagens.get(i);
            Map<String, Object> item = new HashMap<>();

            item.put("enviadaEm", mensagem.getCreatedAt());
            item.put("euEnviei", mensagem.getSenderId().equals(me.getId()));
            item.put("autor", nomes.computeIfAbsent(mensagem.getSenderId(),
                    id -> userRepository.findById(id)
                            .map(User::getUsername)
                            .orElse("usuário removido")));
            item.put("texto", mensagem.isDeleted() ? null : mensagem.getBody());
            item.put("apagada", mensagem.isDeleted());
            if (mensagem.getEditedAt() != null) {
                item.put("editadaEm", mensagem.getEditedAt());
            }
            resultado.add(item);
        }
        return resultado;
    }

    private List<Map<String, Object>> chamadas(UUID conversationId, User me) {
        List<Map<String, Object>> resultado = new ArrayList<>();

        for (Call call : callRepository.findByConversation(conversationId,
                PageRequest.of(0, 1000)).getContent()) {
            Map<String, Object> item = new HashMap<>();
            item.put("iniciadaEm", call.getCreatedAt());
            item.put("euLiguei", call.getCallerId().equals(me.getId()));
            item.put("tipo", call.getType().name());
            item.put("atendidaEm", call.getAnsweredAt());
            item.put("encerradaEm", call.getEndedAt());
            item.put("duracaoSegundos", call.duration().toSeconds());
            item.put("motivoDoEncerramento",
                    call.getEndReason() == null ? null : call.getEndReason().name());
            resultado.add(item);
        }
        return resultado;
    }
}
