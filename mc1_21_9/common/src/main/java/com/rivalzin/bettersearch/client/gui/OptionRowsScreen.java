package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * Base das telas de configuracao do mod.
 *
 * <p>Layout: abas no topo, uma lista de opcoes a esquerda e um painel a direita que explica
 * a opcao sob o cursor. Cada linha traz o nome a esquerda, o controle a direita e um botao
 * de restaurar o padrao, que so acende quando aquela opcao foi mesmo alterada.
 *
 * <p>A rolagem e por linha inteira: as linhas fora da area visivel ficam invisiveis e as
 * visiveis sao reposicionadas. Como o Minecraft ja ignora widgets invisiveis ao desenhar e
 * ao processar cliques, nada aparece cortado pela metade e nao e preciso recortar a tela.
 *
 * <p>Usa apenas classes vanilla, entao a mesma interface serve num port para Fabric.
 */
public abstract class OptionRowsScreen extends Screen {

    protected static final String KEY_PREFIX = "bettersearch.config.";

    protected static final int ROW_HEIGHT = 24;
    protected static final int CONTROL_HEIGHT = 20;
    protected static final int SLIDER_WIDTH_MAX = 96;
    protected static final int RESET_SIZE = 20;
    protected static final int MARGIN = 6;
    protected static final int BUTTON_HEIGHT = 20;
    protected static final int BUTTON_GAP = 22;

    /**
     * Tamanho, em pixels de interface, das imagens de previa das opcoes.
     *
     * <p>Todas as imagens sao gravadas nesta mesma tela, ja centralizadas e com fundo
     * transparente em volta. Assim toda opcao ocupa exatamente o mesmo espaco no painel,
     * e desenhar e so copiar a textura inteira: nada estica, nada desalinha.
     */
    protected static final int PREVIEW_WIDTH = 200;
    protected static final int PREVIEW_HEIGHT = 104;

    /** Espaco entre a moldura e a imagem. */
    private static final int PREVIEW_PADDING = 3;

    /** Abaixo disto a imagem ficaria pequena demais para valer a pena; entao nem aparece. */
    private static final float PREVIEW_MIN_SCALE = 0.45F;

    /** Uma linha da lista: nome, explicacao, controle e o botao de restaurar. */
    protected static final class Row {
        final Component title;
        final Component description;
        final AbstractWidget control;
        final Button reset;              // pode ser nulo
        final BooleanSupplier modified;   // pode ser nulo
        ResourceLocation preview;         // pode ser nulo
        int y;

        Row(Component title, Component description, AbstractWidget control,
            Button reset, BooleanSupplier modified) {
            this.title = title;
            this.description = description;
            this.control = control;
            this.reset = reset;
            this.modified = modified;
        }

        /** Imagem mostrada no pe do painel enquanto o cursor estiver nesta linha. */
        public Row preview(ResourceLocation texture) {
            this.preview = texture;
            return this;
        }
    }

    protected final Screen parent;

    private final List<Row> rows = new ArrayList<>();
    private int scrollRow;
    private int visibleRows = 1;

    private int contentTop;
    private int contentBottom;
    private int listBottom;
    private int listX;
    private int listWidth;
    private int barWidth;
    private int panelX;
    private int panelWidth;
    private int sliderWidth = SLIDER_WIDTH_MAX;
    private float previewScale;
    private int previewTop;
    private Row hoveredRow;

    protected OptionRowsScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    // ------------------------------------------------------------------ a implementar

    /** Altura reservada, dentro do painel da direita, para os botoes fixos. */
    protected abstract int panelFooterHeight();

    /** Altura reservada no fim da COLUNA DA ESQUERDA (usada pelos links do rodape). */
    protected int listBottomInset() {
        return 0;
    }

    /** Cria as abas (opcional). */
    protected void buildTabs() {
    }

    /** Altura da faixa de abas; 0 quando a tela nao tem abas. */
    protected int tabsHeight() {
        return 0;
    }

    /** Cria as linhas da lista, usando {@link #addToggle}, {@link #addSlider}, {@link #addAction}. */
    protected abstract void buildRows();

    /** Cria os botoes fixos do painel da direita. */
    protected abstract void buildPanelFooter();

    /** Titulo mostrado no painel quando o cursor nao esta sobre nenhuma opcao. */
    protected abstract Component panelDefaultTitle();

    /** Texto mostrado no painel quando o cursor nao esta sobre nenhuma opcao. */
    protected abstract Component panelDefaultDescription();

    // ------------------------------------------------------------------ montagem

    @Override
    protected void init() {
        rows.clear();
        hoveredRow = null;

        int tabs = tabsHeight();
        contentTop = MARGIN + (tabs > 0 ? tabs + 4 : 0);
        contentBottom = this.height - MARGIN;

        // O teto de 224 nao e arbitrario: e a menor largura em que a imagem de previa cabe
        // no tamanho exato em que foi gravada. Um pixel a menos e ela teria de encolher,
        // e arte em pixel encolhida em fracao fica suja.
        panelWidth = Mth.clamp((this.width - 3 * MARGIN) * 36 / 100, 110, 224);
        panelX = this.width - MARGIN - panelWidth;
        listX = MARGIN;
        listWidth = panelX - MARGIN - listX;
        barWidth = listWidth - RESET_SIZE - 4;
        sliderWidth = Mth.clamp(barWidth * 45 / 100, 60, SLIDER_WIDTH_MAX);
        listBottom = contentBottom - listBottomInset();
        visibleRows = Math.max(1, (listBottom - contentTop) / ROW_HEIGHT);

        buildTabs();
        buildRows();
        buildPanelFooter();
        layoutRows();
        measurePreview();
    }

    /**
     * Decide, uma vez por tela, o tamanho da moldura de previa.
     *
     * <p>Duas regras que valem a pena registrar:
     *
     * <ul>
     *   <li>tamanho e posicao sao os mesmos para <b>todas</b> as opcoes, calculados a partir
     *       da opcao de texto mais comprido. Se cada uma escolhesse o proprio lugar, a imagem
     *       subiria e desceria conforme o cursor passa pela lista, que e exatamente a
     *       impressao de desleixo que se quer evitar;</li>
     *   <li>o texto vem primeiro. Em janela pequena a moldura encolhe, e se ainda assim nao
     *       sobrar espaco decente ela simplesmente nao aparece - melhor uma explicacao
     *       inteira do que uma tarja ilegivel espremendo a explicacao pela metade.</li>
     * </ul>
     */
    private void measurePreview() {
        previewScale = 0.0F;
        previewTop = 0;
        int textWidth = panelWidth - 16;
        int textNeeded = 0;
        for (Row row : rows) {
            if (row.preview == null) {
                continue;
            }
            int lines = this.font.split(row.title, textWidth).size()
                    + this.font.split(row.description, textWidth).size();
            textNeeded = Math.max(textNeeded, 8 + 10 * lines + 4);
        }
        if (textNeeded == 0) {
            return; // nenhuma opcao desta tela tem imagem
        }

        int top = contentTop + textNeeded + 4;
        int maxWidth = panelWidth - 16 - 2 * PREVIEW_PADDING;
        int maxHeight = (panelFooterTop() - 6) - top - 2 * PREVIEW_PADDING;
        float scale = Math.min(1.0F, Math.min(maxWidth / (float) PREVIEW_WIDTH,
                maxHeight / (float) PREVIEW_HEIGHT));
        if (scale >= PREVIEW_MIN_SCALE) {
            previewScale = scale;
            previewTop = top;
        }
    }

    protected final int listX() {
        return listX;
    }

    protected final int listWidth() {
        return listWidth;
    }

    protected final int panelX() {
        return panelX;
    }

    protected final int panelWidth() {
        return panelWidth;
    }

    protected final int contentTop() {
        return contentTop;
    }

    protected final int contentBottom() {
        return contentBottom;
    }

    protected final int listBottom() {
        return listBottom;
    }

    /** X onde um controle de largura {@code width} deve comecar para ficar colado a direita. */
    private int controlX(int width) {
        return listX + barWidth - 6 - width;
    }

    /**
     * Caminho da imagem de previa de uma opcao. O nome do arquivo e a propria chave da
     * opcao, entao nao ha lista para manter em dois lugares.
     */
    protected static ResourceLocation previewOf(String key) {
        return ResourceLocation.fromNamespaceAndPath("bettersearch", "textures/gui/options/" + key + ".png");
    }

    /**
     * O detalhe tecnico da opcao, que aparece so quando o cursor para em cima do controle.
     *
     * <p>Fica separado da descricao de proposito: a descricao do painel diz, em uma frase,
     * o que a opcao faz; esta dica diz o "porem" - e quem nao quiser saber nunca precisa ler.
     */
    private static void attachTip(AbstractWidget control, String key) {
        control.setTooltip(Tooltip.create(Component.translatable(KEY_PREFIX + key + ".tip")));
    }

    protected final Row addToggle(String key, BooleanSupplier getter, Consumer<Boolean> setter, boolean defaultValue) {
        Component title = Component.translatable(KEY_PREFIX + key);
        ToggleSwitch control = new ToggleSwitch(controlX(ToggleSwitch.WIDTH), 0,
                getter.getAsBoolean(), title, setter);
        attachTip(control, key);
        return addRow(title, Component.translatable(KEY_PREFIX + key + ".desc"), control,
                () -> setter.accept(defaultValue),
                () -> getter.getAsBoolean() != defaultValue);
    }

    protected final Row addSlider(String key, int min, int max, int step,
                                  IntSupplier getter, IntConsumer setter, int defaultValue,
                                  IntFunction<Component> valueLabel) {
        Component title = Component.translatable(KEY_PREFIX + key);
        IntSlider control = new IntSlider(controlX(sliderWidth), 0, sliderWidth, CONTROL_HEIGHT,
                min, max, step, getter.getAsInt(), valueLabel, setter);
        attachTip(control, key);
        return addRow(title, Component.translatable(KEY_PREFIX + key + ".desc"), control,
                () -> setter.accept(defaultValue),
                () -> getter.getAsInt() != defaultValue);
    }

    /** Linha cujo controle e um botao (abre outra tela, por exemplo). Sem botao de restaurar. */
    protected final Row addAction(String key, Component buttonLabel, Runnable action) {
        Component title = Component.translatable(KEY_PREFIX + key);
        Button control = Button.builder(buttonLabel, b -> action.run())
                .bounds(controlX(sliderWidth), 0, sliderWidth, CONTROL_HEIGHT)
                .build();
        attachTip(control, key);
        return addRow(title, Component.translatable(KEY_PREFIX + key + ".desc"), control, null, null);
    }

    /** Linha com um interruptor e um texto livre (usada pela lista de idiomas). */
    protected final Row addSwitchRow(Component title, Component description,
                                     BooleanSupplier getter, Consumer<Boolean> setter) {
        ToggleSwitch control = new ToggleSwitch(controlX(ToggleSwitch.WIDTH), 0,
                getter.getAsBoolean(), title, setter);
        return addRow(title, description, control, null, null);
    }

    private Row addRow(Component title, Component description, AbstractWidget control,
                       Runnable onReset, BooleanSupplier modified) {
        Button reset = null;
        if (onReset != null) {
            reset = Button.builder(Component.literal("↺"), b -> {
                onReset.run();
                rebuildWidgets();
            }).bounds(listX + barWidth + 4, 0, RESET_SIZE, RESET_SIZE)
                    .tooltip(Tooltip.create(Component.translatable(KEY_PREFIX + "reset_option")))
                    .build();
        }
        Row row = new Row(title, description, control, reset, modified);
        rows.add(row);
        addRenderableWidget(control);
        if (reset != null) {
            addRenderableWidget(reset);
        }
        return row;
    }

    /** Adiciona um widget fixo, cuja posicao quem chama ja definiu. */
    protected final <T extends AbstractWidget> T addFixed(T widget) {
        return addRenderableWidget(widget);
    }

    /** Y do primeiro botao do rodape do painel. */
    protected final int panelFooterTop() {
        return contentBottom - panelFooterHeight();
    }

    protected final void resetScroll() {
        scrollRow = 0;
    }

    private void layoutRows() {
        int maxScroll = Math.max(0, rows.size() - visibleRows);
        scrollRow = Mth.clamp(scrollRow, 0, maxScroll);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int slot = i - scrollRow;
            boolean shown = slot >= 0 && slot < visibleRows;
            row.y = contentTop + slot * ROW_HEIGHT;
            row.control.visible = shown;
            row.control.setY(row.y + (ROW_HEIGHT - row.control.getHeight()) / 2);
            if (row.reset != null) {
                row.reset.visible = shown;
                row.reset.setY(row.y + (ROW_HEIGHT - RESET_SIZE) / 2);
            }
        }
    }

    // ------------------------------------------------------------------ interacao

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean overList = mouseX >= listX && mouseX <= listX + listWidth
                && mouseY >= contentTop && mouseY <= listBottom;
        if (overList && rows.size() > visibleRows && scrollY != 0.0) {
            scrollRow -= (int) Math.signum(scrollY);
            layoutRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateHoveredRow(mouseX, mouseY);
        for (Row row : rows) {
            if (row.reset != null && row.modified != null) {
                row.reset.active = row.modified.getAsBoolean();
            }
        }
        updateFooterState();
        // Screen#render desenha o fundo (nosso override abaixo) e depois os widgets.
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderScrollbar(guiGraphics);
    }

    /** Chamado a cada quadro; sobrescreva para acender/apagar botoes do rodape. */
    protected void updateFooterState() {
    }

    private void updateHoveredRow(int mouseX, int mouseY) {
        if (mouseX < listX || mouseX > listX + listWidth) {
            return; // fora da lista: mantem a ultima opcao explicada
        }
        for (Row row : rows) {
            if (row.control.visible && mouseY >= row.y && mouseY < row.y + ROW_HEIGHT) {
                hoveredRow = row;
                return;
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        renderRows(guiGraphics);
        renderPanel(guiGraphics);
    }

    private void renderRows(GuiGraphics guiGraphics) {
        int labelLimit = barWidth - 14 - Math.max(ToggleSwitch.WIDTH, sliderWidth);
        for (Row row : rows) {
            if (!row.control.visible) {
                continue;
            }
            int top = row.y;
            int bottom = top + ROW_HEIGHT - 2;
            boolean hovered = row == hoveredRow;
            guiGraphics.fill(listX, top, listX + barWidth, bottom, hovered ? Theme.ROW_BG_HOVER : Theme.ROW_BG);
            if (hovered) {
                guiGraphics.fill(listX, top, listX + 2, bottom, Theme.ACCENT);
            }
            guiGraphics.drawString(this.font, ellipsize(row.title, labelLimit),
                    listX + 8, top + (ROW_HEIGHT - 2 - 8) / 2, hovered ? Theme.TITLE : Theme.TEXT);
        }
    }

    private void renderPanel(GuiGraphics guiGraphics) {
        guiGraphics.fill(panelX, contentTop, panelX + panelWidth, contentBottom, Theme.PANEL_BG);
        guiGraphics.fill(panelX, contentTop, panelX + panelWidth, contentTop + 1, Theme.ACCENT);

        int textX = panelX + 8;
        int textWidth = panelWidth - 16;
        int y = contentTop + 8;

        Component title = hoveredRow != null ? hoveredRow.title : panelDefaultTitle();
        Component description = hoveredRow != null ? hoveredRow.description : panelDefaultDescription();
        ResourceLocation preview = hoveredRow != null ? hoveredRow.preview : null;

        // A moldura sempre comeca na mesma altura, logo abaixo da descricao mais comprida
        // da tela. E isso que faz as previas parecerem alinhadas ao passar o cursor de uma
        // opcao para a outra, em vez de pular conforme o tamanho do texto acima.
        int textLimit = panelFooterTop() - 6;
        if (preview != null) {
            int frameTop = renderPreview(guiGraphics, preview);
            if (frameTop > 0) {
                textLimit = frameTop - 4;
            }
        }

        for (FormattedCharSequence line : this.font.split(title, textWidth)) {
            guiGraphics.drawString(this.font, line, textX, y, Theme.ACCENT);
            y += 10;
        }
        y += 4;
        for (FormattedCharSequence line : this.font.split(description, textWidth)) {
            if (y + 9 > textLimit) {
                break;
            }
            guiGraphics.drawString(this.font, line, textX, y, Theme.TEXT);
            y += 10;
        }
    }

    /**
     * Desenha a previa da opcao logo abaixo da descricao e devolve o topo da moldura
     * (0 quando ela nao coube nesta tela).
     *
     * <p>A escala e a mesma nos dois eixos e nunca passa de 1, entao a imagem jamais estica
     * nem fica borrada por ampliacao: ou aparece no tamanho em que foi gravada, ou encolhe
     * proporcionalmente quando a tela e pequena demais.
     */
    private int renderPreview(GuiGraphics guiGraphics, ResourceLocation texture) {
        float scale = previewScale;
        if (scale <= 0.0F) {
            return 0;
        }

        int drawWidth = Math.round(PREVIEW_WIDTH * scale);
        int drawHeight = Math.round(PREVIEW_HEIGHT * scale);
        int imageX = panelX + (panelWidth - drawWidth) / 2;
        int imageY = previewTop + PREVIEW_PADDING;

        int left = imageX - PREVIEW_PADDING;
        int top = previewTop;
        int right = imageX + drawWidth + PREVIEW_PADDING;
        int bottom = imageY + drawHeight + PREVIEW_PADDING;

        guiGraphics.fill(left, top, right, bottom, Theme.FRAME_BG);
        guiGraphics.fill(left, top, right, top + 1, Theme.FRAME_BORDER);
        guiGraphics.fill(left, bottom - 1, right, bottom, Theme.FRAME_BORDER);
        guiGraphics.fill(left, top, left + 1, bottom, Theme.FRAME_BORDER);
        guiGraphics.fill(right - 1, top, right, bottom, Theme.FRAME_BORDER);

        /*
         * 1.21.9. Ate a 1.21.1 era preciso ligar a mistura de cores na mao (RenderSystem
         * .enableBlend), senao a borda transparente da imagem virava um bloco preto. Aquelas
         * chamadas nao existem mais: desde a reescrita do renderizador, quem decide se ha
         * mistura e o "pipeline" passado ao blit - e o GUI_TEXTURED ja desenha com alfa.
         *
         * A ordem dos numeros tambem mudou: agora vem (u, v) antes do tamanho na tela, e o
         * tamanho da REGIAO lida da imagem vem separado do tamanho do arquivo. Aqui lemos a
         * imagem inteira, entao os tres pares sao iguais - e a diferenca entre o tamanho na
         * tela (drawWidth) e o da regiao (PREVIEW_WIDTH) e o que produz a escala.
         */
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, imageX, imageY,
                0.0F, 0.0F, drawWidth, drawHeight,
                PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_WIDTH, PREVIEW_HEIGHT);
        return top;
    }

    private void renderScrollbar(GuiGraphics guiGraphics) {
        int maxScroll = rows.size() - visibleRows;
        if (maxScroll <= 0) {
            return;
        }
        int x = listX + listWidth - 2;
        int top = contentTop;
        int bottom = Math.min(listBottom, contentTop + visibleRows * ROW_HEIGHT - 2);
        guiGraphics.fill(x, top, x + 2, bottom, Theme.SCROLL_TRACK);
        int track = bottom - top;
        int thumb = Math.max(16, track * visibleRows / rows.size());
        int thumbY = top + (track - thumb) * scrollRow / maxScroll;
        guiGraphics.fill(x, thumbY, x + 2, thumbY + thumb, Theme.SCROLL_THUMB);
    }

    /**
     * Corta o texto com reticencias para caber em {@code maxWidth}.
     *
     * <p>Cortar por largura, e nao quebrar linha, importa: quebrando, um nome como
     * "Typo Correction Threshold" viraria so "Typo" numa tela pequena.
     */
    private String ellipsize(Component text, int maxWidth) {
        String plain = text.getString();
        int limit = Math.max(16, maxWidth);
        if (this.font.width(plain) <= limit) {
            return plain;
        }
        return this.font.plainSubstrByWidth(plain, limit - this.font.width("...")) + "...";
    }
}
