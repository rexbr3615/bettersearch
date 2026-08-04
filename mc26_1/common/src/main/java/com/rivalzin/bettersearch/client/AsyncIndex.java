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

    private volatile SearchIndex<T> index;
    private Object readySource;
    private int readySize = -1;
    private long readyStamp = Long.MIN_VALUE;

    private Object pendingSource;
    private int pendingSize = -1;
    private long pendingStamp = Long.MIN_VALUE;

    private boolean building;
    private boolean failed;

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
        SearchIndex<T> current = index;
        if (current != null && readySource == source && readySize == size && readyStamp == stamp) {
            return current;
        }
        if (failed) {
            return null;
        }
        if (!building && (pendingSource != source || pendingSize != size || pendingStamp != stamp)) {
            start(source, size, stamp, build);
        }
        return null;
    }

    private void start(Object source, int size, long stamp, Supplier<SearchIndex<T>> build) {
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
