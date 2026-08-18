package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchIndex;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Um indice montado fora da thread principal, com deteccao de "esta velho?".
 *
 * <p>Regra: enquanto o indice nao estiver pronto, {@link #get} devolve {@code null} e quem
 * chamou usa a busca original do jogo. Nunca se espera o indice ficar pronto - o jogo nao
 * pode travar por causa de uma barra de busca.
 *
 * <p>A identidade da fonte ({@code source}) e comparada por referencia e o tamanho por valor:
 * quando o Minecraft reconstroi a lista (novo mundo, permissao de operador, recarga de
 * receitas), o objeto muda e o indice e refeito sozinho.
 */
public final class AsyncIndex<T> {

    private final String name;

    // Tudo volatile porque este objeto e lido de mais de uma thread: o criativo e o JEI
    // perguntam da thread do jogo, mas o EMI e o REI perguntam das threads de busca deles.
    // Sem isto, a thread do REI poderia nunca enxergar que a montagem terminou.
    private volatile SearchIndex<T> index;
    private volatile Object readySource;
    private volatile int readySize = -1;
    private volatile long readyStamp = Long.MIN_VALUE;

    private volatile Object pendingSource;
    private volatile int pendingSize = -1;
    private volatile long pendingStamp = Long.MIN_VALUE;

    private volatile boolean building;
    private volatile boolean failed;

    public AsyncIndex(String name) {
        this.name = name;
    }

    /**
     * @param source objeto que identifica a lista de origem (comparado por referencia)
     * @param size   tamanho atual da lista de origem
     * @param stamp  contador que muda quando os idiomas ou a configuracao mudam
     * @param build  monta o indice; roda FORA da thread principal, entao precisa receber
     *               tudo o que precisa ja capturado
     * @return o indice pronto, ou {@code null} enquanto ele nao existir
     */
    public SearchIndex<T> get(Object source, int size, long stamp, Supplier<SearchIndex<T>> build) {
        return get(source, size, stamp, build, null);
    }

    /**
     * O indice pronto, ou {@code null} - sem disparar montagem nenhuma.
     *
     * <p>Existe para quem precisa preparar algo caro antes de pedir a montagem (copiar uma lista
     * de dezenas de milhares de elementos, por exemplo). Perguntando aqui primeiro, esse preparo
     * so acontece nas poucas vezes em que o indice realmente falta.
     */
    public SearchIndex<T> ready(Object source, int size, long stamp) {
        SearchIndex<T> current = index;
        if (current != null && readySource == source && readySize == size && readyStamp == stamp) {
            return current;
        }
        return null;
    }

    /**
     * @param onReady chamado na thread principal assim que o indice fica pronto. Serve para quem
     *                guarda o resultado em cache proprio e nunca mais perguntaria - o caso do JEI,
     *                que so refaz a lista quando o texto muda. Sem este empurrao, um indice que
     *                ficou pronto meio segundo depois da primeira tecla so seria usado na proxima.
     */
    public SearchIndex<T> get(Object source, int size, long stamp, Supplier<SearchIndex<T>> build,
                              Runnable onReady) {
        SearchIndex<T> current = index;
        if (current != null && readySource == source && readySize == size && readyStamp == stamp) {
            return current;
        }
        if (failed) {
            return null;
        }
        if (!building && (pendingSource != source || pendingSize != size || pendingStamp != stamp)) {
            start(source, size, stamp, build, onReady);
        }
        return null;
    }

    private synchronized void start(Object source, int size, long stamp, Supplier<SearchIndex<T>> build,
                                    Runnable onReady) {
        // Conferir de novo dentro da trava: duas threads podem ter passado pelo teste la fora.
        if (building || (pendingSource == source && pendingSize == size && pendingStamp == stamp)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        building = true;
        pendingSource = source;
        pendingSize = size;
        pendingStamp = stamp;

        CompletableFuture
                .supplyAsync(build, Util.backgroundExecutor())
                .whenComplete((built, error) -> minecraft.execute(() -> {
                    building = false;
                    if (error != null) {
                        BetterSearch.LOGGER.error("[{}] falha ao montar o indice de {}",
                                BetterSearch.MOD_NAME, name, error);
                        failed = true;
                        return;
                    }
                    index = built;
                    readySource = pendingSource;
                    readySize = pendingSize;
                    readyStamp = pendingStamp;
                    if (onReady != null) {
                        onReady.run();
                    }
                }));
    }

    public void invalidate() {
        index = null;
        readySource = null;
        readySize = -1;
        readyStamp = Long.MIN_VALUE;
        // Zerar o "pendente" tambem, senao a proxima chamada acharia que ja existe uma
        // montagem em andamento para esta mesma fonte e nunca reconstruiria.
        pendingSource = null;
        pendingSize = -1;
        pendingStamp = Long.MIN_VALUE;
    }
}
