package app.concord.legal;

/**
 * Documentos cujo aceite é registrado.
 *
 * <p>A versão fica fora do enum, em configuração: o texto muda sem que o código
 * mude, e o que importa é qual versão a pessoa aceitou.
 */
public enum LegalDocument {
    TERMS_OF_USE,
    PRIVACY_POLICY
}
