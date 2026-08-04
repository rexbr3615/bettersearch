package com.rivalzin.bettersearch.core;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalizacao de texto usada por todo o motor de busca.
 *
 * <p>Regras (aplicadas nesta ordem):
 * <ol>
 *   <li>minusculas ({@link Locale#ROOT}, para nao quebrar em turco);</li>
 *   <li>expansao de letras que NAO possuem decomposicao Unicode (ss, ae, oe, o, d, l, ...);</li>
 *   <li>NFKD + remocao de marcas combinantes -> "bau" == "bau", "acucar" == "acucar";</li>
 *   <li>tudo que nao for letra/digito vira espaco;</li>
 *   <li>espacos colapsados e aparados.</li>
 * </ol>
 *
 * <p>Nao ha nenhuma referencia a Minecraft aqui: esta classe compila e roda em Java puro.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /** Normaliza uma string. Nunca retorna {@code null}. */
    public static String normalize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String lower = input.toLowerCase(Locale.ROOT);

        // 1) Expansoes manuais: caracteres sem decomposicao canonica que precisam virar ASCII.
        StringBuilder expanded = new StringBuilder(lower.length() + 4);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            switch (c) {
                case 'ß' -> expanded.append("ss");   // ß  alemao
                case 'ẞ' -> expanded.append("ss");   // ẞ
                case 'æ' -> expanded.append("ae");   // æ
                case 'œ' -> expanded.append("oe");   // œ
                case 'ø' -> expanded.append('o');    // ø  nordico
                case 'đ' -> expanded.append('d');    // đ
                case 'ð' -> expanded.append('d');    // ð
                case 'þ' -> expanded.append("th");   // þ
                case 'ł' -> expanded.append('l');    // ł  polones
                case 'ı' -> expanded.append('i');    // ı  turco sem ponto
                case 'ħ' -> expanded.append('h');    // ħ
                case 'ŋ' -> expanded.append('n');    // ŋ
                case 'å' -> expanded.append('a');    // å (tem decomposicao, mas antecipamos)
                case 'ʔ', 'ʼ', '’' -> expanded.append(' '); // apostrofos tipograficos
                default -> expanded.append(c);
            }
        }

        // 2) NFKD separa a letra base das marcas de acento; removemos as marcas (categoria Mn).
        String decomposed = Normalizer.normalize(expanded, Normalizer.Form.NFKD);

        // 3) Filtro final: mantem letras e digitos, o resto vira separador.
        StringBuilder out = new StringBuilder(decomposed.length());
        boolean pendingSpace = false;
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            int type = Character.getType(c);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                continue; // acento solto -> descartado
            }
            if (Character.isLetterOrDigit(c)) {
                if (pendingSpace && out.length() > 0) {
                    out.append(' ');
                }
                pendingSpace = false;
                out.append(c);
            } else {
                pendingSpace = true;
            }
        }
        return out.toString();
    }

    /**
     * Assinatura de 64 bits com os caracteres presentes na string.
     *
     * <p>Usada como pre-filtro barato: se a consulta tem caracteres que o alvo nao possui,
     * a distancia de edicao e no minimo o numero de caracteres faltando. Colisoes so geram
     * falsos positivos (que o algoritmo real descarta), nunca falsos negativos.
     */
    public static long charMask(String normalized) {
        long mask = 0L;
        for (int i = 0; i < normalized.length(); i++) {
            mask |= 1L << (normalized.charAt(i) & 63);
        }
        return mask;
    }
}
