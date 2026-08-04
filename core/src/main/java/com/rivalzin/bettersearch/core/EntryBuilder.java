package com.rivalzin.bettersearch.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Monta uma entrada do indice normalizando e deduplicando os textos.
 *
 * <p>A deduplicacao importa muito: "TNT" e "TNT" em 18 idiomas viraria 18 campos identicos.
 * Guardando so os textos distintos, um modpack com 20 mil itens continua leve.
 */
public final class EntryBuilder<T> {

    private final T value;
    private final List<SearchField> fields = new ArrayList<>(4);
    private final Set<String> seen = new HashSet<>(8);
    private String modId = "";

    public EntryBuilder(T value) {
        this.value = value;
    }

    /** Adiciona um texto ja normalizado. */
    public EntryBuilder<T> addNormalized(String normalized, byte source) {
        if (normalized != null && !normalized.isEmpty() && seen.add(normalized)) {
            fields.add(new SearchField(normalized, source));
        }
        return this;
    }

    /** Normaliza e adiciona um texto cru. */
    public EntryBuilder<T> add(String rawText, byte source) {
        return addNormalized(TextNormalizer.normalize(rawText), source);
    }

    public EntryBuilder<T> modId(String rawModId) {
        this.modId = TextNormalizer.normalize(rawModId);
        return this;
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    public SearchIndex.Entry<T> build() {
        return new SearchIndex.Entry<>(value, fields.toArray(new SearchField[0]), modId);
    }
}
