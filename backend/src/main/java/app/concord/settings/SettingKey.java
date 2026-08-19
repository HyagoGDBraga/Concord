package app.concord.settings;

/** Chaves de configuração alteráveis em tempo de execução. */
public final class SettingKey {

    /** Cadastro aberto ao público. Alternável pelo administrador. */
    public static final String REGISTRATION_OPEN = "registration.open";

    /**
     * Marca persistente de que o primeiro administrador já foi promovido.
     * Depois de {@code true}, alterar a variável de ambiente não promove mais
     * ninguém — o estado do bootstrap vive no banco, não no ambiente.
     */
    public static final String ADMIN_BOOTSTRAP_COMPLETED = "admin.bootstrap.completed";

    private SettingKey() {
    }
}
