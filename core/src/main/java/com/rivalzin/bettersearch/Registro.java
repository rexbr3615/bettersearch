package com.rivalzin.bettersearch;

import java.lang.reflect.Method;

/**
 * O log do mod, sem depender de biblioteca de log nenhuma em tempo de compilacao.
 *
 * <p><b>Por que isto existe.</b> O {@code core/} e compilado dentro de nove versoes do
 * Minecraft, e elas nao concordam sobre qual biblioteca de log esta disponivel:
 *
 * <ul>
 *   <li>no <b>Forge 1.16.5</b> o classpath tem {@code log4j-api}, {@code log4j-core} e
 *       {@code log4j-slf4j18-impl} - e <b>nao tem</b> o {@code slf4j-api}. Isso esta escrito no
 *       relatorio de crash do proprio jogo, na lista "Minecraft classPath";</li>
 *   <li>no <b>Fabric 1.16.5</b> o slf4j existe, porque o Fabric Loader o empacota;</li>
 *   <li>da 1.18.2 em diante o Minecraft usa slf4j e ele esta sempre la.</li>
 * </ul>
 *
 * <p>O {@code core} chamava {@code LoggerFactory.getLogger} direto. Isso funcionou em oito
 * alvos e derrubou o nono com {@code NoClassDefFoundError: org/slf4j/LoggerFactory} - e nao no
 * carregamento do mod, mas no {@code <clinit>} da classe de constantes, ou seja, na primeira
 * vez que qualquer coisa do mod era tocada.
 *
 * <p><b>Por que reflexao e nao uma dependencia declarada.</b> Declarar log4j em dezoito
 * build.gradle resolveria a compilacao, mas continuaria apostando que a biblioteca escolhida
 * existe em execucao nas nove versoes - e essa aposta e justamente a que quebrou. Aqui nao ha
 * aposta: o {@code core} procura o que houver, na ordem, e cai para o {@code System.out} se nao
 * houver nada. Nenhum modulo precisa declarar nada, e nenhuma versao pode quebrar por isto.
 *
 * <p>O formato {@code {}} e o mesmo no log4j e no slf4j, entao os 325 pontos de chamada do
 * projeto continuam identicos - nao foi preciso mexer em nenhum deles.
 */
public final class Registro {

    /** Nomes de classe procurados, na ordem. O primeiro que existir vence. */
    private static final String[][] CANDIDATOS = {
            {"org.apache.logging.log4j.LogManager", "org.apache.logging.log4j.Logger"},
            {"org.slf4j.LoggerFactory", "org.slf4j.Logger"},
    };

    private final String nome;
    private final Object alvo;
    private final Method mDebug;
    private final Method mInfo;
    private final Method mWarn;
    private final Method mError;
    private final Method mDebugLigado;

    private Registro(String nome, Object alvo, Method mDebug, Method mInfo, Method mWarn,
                     Method mError, Method mDebugLigado) {
        this.nome = nome;
        this.alvo = alvo;
        this.mDebug = mDebug;
        this.mInfo = mInfo;
        this.mWarn = mWarn;
        this.mError = mError;
        this.mDebugLigado = mDebugLigado;
    }

    public static Registro criar(String nome) {
        for (String[] par : CANDIDATOS) {
            try {
                Class<?> fabrica = Class.forName(par[0]);
                Class<?> tipo = Class.forName(par[1]);
                Object logger = fabrica.getMethod("getLogger", String.class).invoke(null, nome);
                if (logger == null) {
                    continue;
                }
                return new Registro(nome, logger,
                        tipo.getMethod("debug", String.class, Object[].class),
                        tipo.getMethod("info", String.class, Object[].class),
                        tipo.getMethod("warn", String.class, Object[].class),
                        tipo.getMethod("error", String.class, Object[].class),
                        tipo.getMethod("isDebugEnabled"));
            } catch (Throwable ignorado) {
                // biblioteca ausente ou com outra forma: tenta a proxima
            }
        }
        return new Registro(nome, null, null, null, null, null, null);
    }

    /** Qual biblioteca acabou sendo usada. So para diagnostico. */
    public String backend() {
        return alvo == null ? "System.out" : alvo.getClass().getName();
    }

    /**
     * Vale a pena montar a mensagem de debug?
     *
     * <p>Existe porque o indice dos visualizadores registra uma linha por entrada ignorada, e
     * sao milhares. Sem esta pergunta, cada uma pagaria a montagem do texto mesmo com o debug
     * desligado.
     */
    public boolean debugLigado() {
        if (mDebugLigado == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(mDebugLigado.invoke(alvo));
        } catch (Throwable e) {
            return false;
        }
    }

    public void debug(String mensagem, Object... args) {
        if (alvo != null && !debugLigado()) {
            return;
        }
        despachar(mDebug, "DEBUG", mensagem, args);
    }

    public void info(String mensagem, Object... args) {
        despachar(mInfo, "INFO", mensagem, args);
    }

    public void warn(String mensagem, Object... args) {
        despachar(mWarn, "WARN", mensagem, args);
    }

    public void error(String mensagem, Object... args) {
        despachar(mError, "ERROR", mensagem, args);
    }

    private void despachar(Method metodo, String nivel, String mensagem, Object[] args) {
        if (alvo != null && metodo != null) {
            try {
                metodo.invoke(alvo, mensagem, args == null ? new Object[0] : args);
                return;
            } catch (Throwable e) {
                // cai para o System.out em vez de derrubar quem chamou. Um mod de busca nao
                // pode matar o jogo por causa de uma linha de log.
            }
        }
        System.out.println("[" + nome + "/" + nivel + "] " + formatar(mensagem, args));
    }

    /** Troca cada {@code {}} pelo argumento correspondente - o formato do log4j e do slf4j. */
    static String formatar(String mensagem, Object[] args) {
        if (mensagem == null) {
            return "null";
        }
        if (args == null || args.length == 0) {
            return mensagem;
        }
        StringBuilder saida = new StringBuilder(mensagem.length() + 32);
        int arg = 0;
        int i = 0;
        while (i < mensagem.length()) {
            if (i + 1 < mensagem.length() && mensagem.charAt(i) == '{' && mensagem.charAt(i + 1) == '}'
                    && arg < args.length) {
                saida.append(String.valueOf(args[arg++]));
                i += 2;
            } else {
                saida.append(mensagem.charAt(i++));
            }
        }
        // sobrou argumento sem lugar? mostra assim mesmo - perder informacao no log e pior
        while (arg < args.length) {
            saida.append(' ').append(String.valueOf(args[arg++]));
        }
        return saida.toString();
    }
}
