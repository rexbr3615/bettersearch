package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.gui.GuiButton;

import java.util.function.Consumer;

/**
 * O construtor fluente de botao das outras versoes, para o {@code GuiButton} da 1.12.2.
 *
 * <p>Aqui o botao vanilla nao carrega acao nenhuma - o clique chega na tela, por
 * {@code actionPerformed}. O {@code build()} devolve entao um {@link BotaoComAcao}: por fora
 * e o botao classico texturizado de sempre (mesma cara dos botoes do jogo, como o
 * {@code Button} vanilla usado nas outras versoes), por dentro ele guarda a acao e a entrega
 * pelo {@link Acionavel} quando o {@link OptionRowsScreen} despacha.
 *
 * <p>A acao recebe o proprio botao ({@code Consumer<GuiButton>}), como o {@code OnPress} de
 * la - as nove chamadas nas telas ficam identicas, {@code b -> ...} e tudo.
 *
 * <p>A dica vai para o {@link Tips}, que e quem sabe desenha-la nesta versao.
 */
public final class ButtonCompat {

    private ButtonCompat() {
    }

    public static Builder builder(String texto, Consumer<GuiButton> acao) {
        return new Builder(texto, acao);
    }

    /** O botao vanilla desta era, com a acao guardada dentro. */
    public static final class BotaoComAcao extends GuiButton implements Acionavel {

        private final Consumer<GuiButton> acao;

        BotaoComAcao(int x, int y, int largura, int altura, String texto, Consumer<GuiButton> acao) {
            /*
             * id 0 para todos: o id so serve ao actionPerformed baseado em numero, que as
             * nossas telas nao usam - o despacho e por instancia, via Acionavel.
             */
            super(0, x, y, largura, altura, texto);
            this.acao = acao;
        }

        @Override
        public void aoApertar() {
            acao.accept(this);
        }
    }

    public static final class Builder {
        private final String texto;
        private final Consumer<GuiButton> acao;
        private int x;
        private int y;
        private int largura = 150;
        private int altura = 20;
        private String dica;

        private Builder(String texto, Consumer<GuiButton> acao) {
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

        public Builder tooltip(String dica) {
            this.dica = dica;
            return this;
        }

        public GuiButton build() {
            BotaoComAcao botao = new BotaoComAcao(x, y, largura, altura, texto, acao);
            Tips.set(botao, dica);
            return botao;
        }
    }
}
