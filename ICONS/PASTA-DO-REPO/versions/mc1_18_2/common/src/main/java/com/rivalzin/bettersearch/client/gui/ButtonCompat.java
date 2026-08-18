package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * O construtor fluente de {@link Button}, que so chegou na 1.19.4.
 *
 * <p>Na 1.19.2 o botao ainda se cria com {@code new Button(x, y, largura, altura, texto,
 * acao)}. Reproduzir aqui a forma {@code builder(...).bounds(...).tooltip(...).build()} deixa
 * as telas escritas exatamente como nas outras versoes - a diferenca da versao mora neste
 * arquivo, e nao espalhada por nove chamadas.
 *
 * <p>A dica vai para o {@link Tips}, que e quem sabe desenha-la nesta versao.
 */
public final class ButtonCompat {

    private ButtonCompat() {
    }

    public static Builder builder(Component texto, Button.OnPress acao) {
        return new Builder(texto, acao);
    }

    public static final class Builder {
        private final Component texto;
        private final Button.OnPress acao;
        private int x;
        private int y;
        private int largura = 150;
        private int altura = 20;
        private Component dica;

        private Builder(Component texto, Button.OnPress acao) {
            this.texto = texto;
            this.acao = acao;
        }

        public Builder bounds(int x, int y, int largura, int altura) {
            this.x = x;
            this.y = y;
            this.largura = largura;
            this.altura = altura;
            return this;
        }

        public Builder tooltip(Component dica) {
            this.dica = dica;
            return this;
        }

        public Button build() {
            Button botao = new Button(x, y, largura, altura, texto, acao);
            Tips.set(botao, dica);
            return botao;
        }
    }
}
