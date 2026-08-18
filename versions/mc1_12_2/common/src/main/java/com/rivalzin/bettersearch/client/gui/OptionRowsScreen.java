package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Mouse;

import java.io.IOException;
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
 * <p>E o mesmo desenho das outras versoes, traduzido para o mundo {@code GuiScreen}:
 * {@code fill} vira {@code Gui.drawRect}, {@code font.split} vira
 * {@code listFormattedStringToWidth}, a roda do mouse vira {@code handleMouseInput} lendo o
 * LWJGL, e o clique dos widgets chega por {@code actionPerformed} e e despachado pelo
 * {@link Acionavel}. Usa apenas classes vanilla.
 */
public abstract class OptionRowsScreen extends GuiScreen {

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
        final String title;
        final String description;
        final GuiButton control;
        final GuiButton reset;            // pode ser nulo
        final BooleanSupplier modified;   // pode ser nulo
        ResourceLocation preview;         // pode ser nulo
        int y;

        Row(String title, String description, GuiButton control,
            GuiButton reset, BooleanSupplier modified) {
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

    protected final GuiScreen parent;

    /** Titulo da tela; nas outras versoes vai ao Screen, aqui ninguem o desenha. */
    protected final String screenTitle;

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
    private boolean rebuildQueued;

    protected OptionRowsScreen(String title, GuiScreen parent) {
        this.screenTitle = title;
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
    protected abstract String panelDefaultTitle();

    /** Texto mostrado no painel quando o cursor nao esta sobre nenhuma opcao. */
    protected abstract String panelDefaultDescription();

    // ------------------------------------------------------------------ montagem

    /**
     * Pede a remontagem dos widgets da tela - no COMECO DO PROXIMO QUADRO, nao agora.
     *
     * <p>Nas outras versoes isto remonta na hora. Aqui nao pode: o {@code mouseClicked} do
     * GuiScreen desta era CONTINUA varrendo a buttonList depois do {@code actionPerformed}
     * (nao ha break no laco). Remontar no meio poria widgets novos - nas MESMAS coordenadas,
     * como o proprio botao de restaurar - na frente do laco, recebendo o mesmo clique de
     * novo. A LanguageSelectScreen das outras versoes ja adiava exatamente assim para o
     * filtro; aqui o adiamento vale para todos os caminhos e elimina a classe inteira de bug.
     */
    protected void rebuildWidgets() {
        rebuildQueued = true;
    }

    /** {@code setWorldAndResolution} limpa a buttonList e chama {@code initGui} de novo. */
    private void runQueuedRebuild() {
        if (rebuildQueued) {
            rebuildQueued = false;
            if (this.mc != null) {
                this.setWorldAndResolution(this.mc, this.width, this.height);
            }
        }
    }

    @Override
    public void initGui() {
        rows.clear();
        hoveredRow = null;

        int tabs = tabsHeight();
        contentTop = MARGIN + (tabs > 0 ? tabs + 4 : 0);
        contentBottom = this.height - MARGIN;

        // O teto de 224 nao e arbitrario: e a menor largura em que a imagem de previa cabe
        // no tamanho exato em que foi gravada. Um pixel a menos e ela teria de encolher,
        // e arte em pixel encolhida em fracao fica suja.
        panelWidth = MathHelper.clamp((this.width - 3 * MARGIN) * 36 / 100, 110, 224);
        panelX = this.width - MARGIN - panelWidth;
        listX = MARGIN;
        listWidth = panelX - MARGIN - listX;
        barWidth = listWidth - RESET_SIZE - 4;
        sliderWidth = MathHelper.clamp(barWidth * 45 / 100, 60, SLIDER_WIDTH_MAX);
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
            int lines = this.fontRenderer.listFormattedStringToWidth(row.title, textWidth).size()
                    + this.fontRenderer.listFormattedStringToWidth(row.description, textWidth).size();
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
        return new ResourceLocation("bettersearch", "textures/gui/options/" + key + ".png");
    }

    /**
     * O detalhe tecnico da opcao, que aparece so quando o cursor para em cima do controle.
     *
     * <p>Fica separado da descricao de proposito: a descricao do painel diz, em uma frase,
     * o que a opcao faz; esta dica diz o "porem" - e quem nao quiser saber nunca precisa ler.
     */
    private static void attachTip(GuiButton control, String key) {
        Tips.set(control, ComponentCompat.translatable(KEY_PREFIX + key + ".tip"));
    }

    protected final Row addToggle(String key, BooleanSupplier getter, Consumer<Boolean> setter, boolean defaultValue) {
        String title = ComponentCompat.translatable(KEY_PREFIX + key);
        ToggleSwitch control = new ToggleSwitch(controlX(ToggleSwitch.WIDTH), 0,
                getter.getAsBoolean(), title, setter);
        attachTip(control, key);
        return addRow(title, ComponentCompat.translatable(KEY_PREFIX + key + ".desc"), control,
                () -> setter.accept(defaultValue),
                () -> getter.getAsBoolean() != defaultValue);
    }

    protected final Row addSlider(String key, int min, int max, int step,
                                  IntSupplier getter, IntConsumer setter, int defaultValue,
                                  IntFunction<String> valueLabel) {
        String title = ComponentCompat.translatable(KEY_PREFIX + key);
        IntSlider control = new IntSlider(controlX(sliderWidth), 0, sliderWidth, CONTROL_HEIGHT,
                min, max, step, getter.getAsInt(), valueLabel, setter);
        attachTip(control, key);
        return addRow(title, ComponentCompat.translatable(KEY_PREFIX + key + ".desc"), control,
                () -> setter.accept(defaultValue),
                () -> getter.getAsInt() != defaultValue);
    }

    /** Linha cujo controle e um botao (abre outra tela, por exemplo). Sem botao de restaurar. */
    protected final Row addAction(String key, String buttonLabel, Runnable action) {
        String title = ComponentCompat.translatable(KEY_PREFIX + key);
        GuiButton control = ButtonCompat.builder(buttonLabel, b -> action.run())
                .bounds(controlX(sliderWidth), 0, sliderWidth, CONTROL_HEIGHT)
                .build();
        attachTip(control, key);
        return addRow(title, ComponentCompat.translatable(KEY_PREFIX + key + ".desc"), control, null, null);
    }

    /** Linha com um interruptor e um texto livre (usada pela lista de idiomas). */
    protected final Row addSwitchRow(String title, String description,
                                     BooleanSupplier getter, Consumer<Boolean> setter) {
        ToggleSwitch control = new ToggleSwitch(controlX(ToggleSwitch.WIDTH), 0,
                getter.getAsBoolean(), title, setter);
        return addRow(title, description, control, null, null);
    }

    private Row addRow(String title, String description, GuiButton control,
                       Runnable onReset, BooleanSupplier modified) {
        GuiButton reset = null;
        if (onReset != null) {
            reset = ButtonCompat.builder(ComponentCompat.literal("↺"), b -> {
                onReset.run();
                rebuildWidgets();
            }).bounds(listX + barWidth + 4, 0, RESET_SIZE, RESET_SIZE)
                    .tooltip((ComponentCompat.translatable(KEY_PREFIX + "reset_option")))
                    .build();
        }
        Row row = new Row(title, description, control, reset, modified);
        rows.add(row);
        addButton(control);
        if (reset != null) {
            addButton(reset);
        }
        return row;
    }

    /** Adiciona um widget fixo, cuja posicao quem chama ja definiu. */
    protected final <T extends GuiButton> T addFixed(T widget) {
        return addButton(widget);
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
        scrollRow = MathHelper.clamp(scrollRow, 0, maxScroll);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int slot = i - scrollRow;
            boolean shown = slot >= 0 && slot < visibleRows;
            row.y = contentTop + slot * ROW_HEIGHT;
            row.control.visible = shown;
            row.control.y = row.y + (ROW_HEIGHT - row.control.height) / 2;
            if (row.reset != null) {
                row.reset.visible = shown;
                row.reset.y = row.y + (ROW_HEIGHT - RESET_SIZE) / 2;
            }
        }
    }

    // ------------------------------------------------------------------ interacao

    /** O clique dos widgets: o GuiScreen entrega aqui, o {@link Acionavel} decide o que fazer. */
    @Override
    protected void actionPerformed(GuiButton botao) {
        if (botao instanceof Acionavel) {
            ((Acionavel) botao).aoApertar();
        }
    }

    /** Esc fecha COM as regras da tela ({@code onClose} salva na tela de configuracao). */
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // Keyboard.KEY_ESCAPE; literal para nao depender do esboco do LWJGL
            onClose();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    /**
     * A roda do mouse. Nesta era ela nao chega por metodo da tela: e preciso ler o evento
     * do proprio LWJGL dentro de {@code handleMouseInput} (o jeito classico, o mesmo do JEI).
     * A posicao do cursor vem em pixels da janela com origem embaixo; a conversao para
     * coordenadas de interface e a mesma que o GuiScreen faz para o clique.
     */
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int delta = Mouse.getEventDWheel();
        if (delta != 0) {
            int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            mouseScrolled(mouseX, mouseY, Integer.signum(delta));
        }
    }

    /** {@code direction} +1 = roda para cima (lista sobe), -1 = para baixo. */
    private void mouseScrolled(int mouseX, int mouseY, int direction) {
        boolean overList = mouseX >= listX && mouseX <= listX + listWidth
                && mouseY >= contentTop && mouseY <= listBottom;
        if (overList && rows.size() > visibleRows && direction != 0) {
            scrollRow -= direction;
            layoutRows();
        }
    }

    // ------------------------------------------------------------------ barra de rolagem

    /*
     * A barra e fina de proposito - mas area de clique fina e um castigo. Ela desenha com
     * 2 pixels, engorda ao passar o mouse, engorda mais ao ser segurada, e aceita clique numa
     * faixa bem mais larga do que aparenta.
     */
    private static final int SCROLLBAR_WIDTH = 2;
    private static final int SCROLLBAR_WIDTH_HOVER = 3;
    private static final int SCROLLBAR_WIDTH_HELD = 4;
    /** Folga invisivel de cada lado, so para o clique. */
    private static final int SCROLLBAR_GRAB = 3;

    private boolean scrollbarHeld;
    /** Onde, dentro do polegar, o clique pegou. E o que impede ele de pular ao ser agarrado. */
    private int scrollbarGrabOffset;

    private int scrollbarRight() {
        return listX + listWidth;
    }

    private int scrollbarTop() {
        return contentTop;
    }

    private int scrollbarBottom() {
        return Math.min(listBottom, contentTop + visibleRows * ROW_HEIGHT - 2);
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - visibleRows);
    }

    private int thumbHeight() {
        int track = scrollbarBottom() - scrollbarTop();
        return Math.max(16, track * visibleRows / Math.max(1, rows.size()));
    }

    private int thumbTop() {
        int max = maxScroll();
        if (max <= 0) {
            return scrollbarTop();
        }
        int usable = Math.max(0, (scrollbarBottom() - scrollbarTop()) - thumbHeight());
        return scrollbarTop() + usable * scrollRow / max;
    }

    private boolean overScrollbar(int mouseX, int mouseY) {
        int right = scrollbarRight();
        return maxScroll() > 0
                && mouseX >= right - SCROLLBAR_WIDTH_HELD - SCROLLBAR_GRAB
                && mouseX <= right + SCROLLBAR_GRAB
                && mouseY >= scrollbarTop() && mouseY <= scrollbarBottom();
    }

    /** @return {@code true} se a barra ficou com o clique (e a tela nao deve trata-lo) */
    private boolean beginScrollbarDrag(int mouseX, int mouseY, int button) {
        if (button != 0 || !overScrollbar(mouseX, mouseY)) {
            return false;
        }
        int thumb = thumbHeight();
        int top = thumbTop();
        // Clicou NO polegar: guarda onde pegou, para ele nao dar um salto.
        // Clicou na trilha: o polegar vem centralizar no cursor.
        scrollbarGrabOffset = (mouseY >= top && mouseY < top + thumb) ? (mouseY - top) : thumb / 2;
        scrollbarHeld = true;
        updateScrollbarDrag(mouseY);
        return true;
    }

    private boolean updateScrollbarDrag(int mouseY) {
        if (!scrollbarHeld) {
            return false;
        }
        int max = maxScroll();
        int usable = (scrollbarBottom() - scrollbarTop()) - thumbHeight();
        if (max > 0 && usable > 0) {
            double travelled = mouseY - scrollbarGrabOffset - scrollbarTop();
            scrollRow = MathHelper.clamp((int) Math.round(travelled * max / usable), 0, max);
            layoutRows();
        }
        return true;
    }

    private void endScrollbarDrag() {
        scrollbarHeld = false;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (beginScrollbarDrag(mouseX, mouseY, mouseButton)) {
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (updateScrollbarDrag(mouseY)) {
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        endScrollbarDrag();
        super.mouseReleased(mouseX, mouseY, state);
    }

    /** Fecha a tela voltando para a anterior. A tela de configuracao salva antes, no override. */
    public void onClose() {
        this.mc.displayGuiScreen(this.parent);
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        runQueuedRebuild();
        updateHoveredRow(mouseX, mouseY);
        for (Row row : rows) {
            if (row.reset != null && row.modified != null) {
                row.reset.enabled = row.modified.getAsBoolean();
            }
        }
        updateFooterState();
        // Nesta era o Screen#drawScreen NAO desenha fundo nem paineis sozinho - so os botoes.
        // A ordem aqui e a das outras versoes: fundo, linhas e painel por baixo, widgets por
        // cima, barra de rolagem e dica por ultimo.
        drawDefaultBackground();
        renderRows();
        renderPanel();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawExtraWidgets(mouseX, mouseY, partialTicks);
        renderScrollbar(mouseX, mouseY);
        renderTip(mouseX, mouseY);
    }

    /**
     * Widgets que nao moram na buttonList (a caixa de busca da tela de idiomas). Desenhados
     * depois dos botoes e antes da dica, para a caixinha da dica sair por cima de tudo.
     */
    protected void drawExtraWidgets(int mouseX, int mouseY, float partialTicks) {
    }

    /**
     * Desenha a dica do controle sob o cursor.
     *
     * <p>Nas versoes novas isto e do jogo ({@code setTooltip}). Aqui o texto fica no
     * {@link Tips} e a caixinha e desenhada na mao, por ultimo - senao ela sairia por baixo
     * dos widgets. A quebra em linhas usa a mesma largura das outras versoes.
     */
    private void renderTip(int mouseX, int mouseY) {
        for (GuiButton widget : this.buttonList) {
            if (!widget.visible) {
                continue;
            }
            boolean over = mouseX >= widget.x && mouseY >= widget.y
                    && mouseX < widget.x + widget.width && mouseY < widget.y + widget.height;
            if (!over) {
                continue;
            }
            String dica = Tips.of(widget);
            if (dica != null) {
                drawHoveringText(this.fontRenderer.listFormattedStringToWidth(
                        dica, Math.max(this.width / 2, 170)), mouseX, mouseY);
            }
            return;
        }
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

    private void renderRows() {
        int labelLimit = barWidth - 14 - Math.max(ToggleSwitch.WIDTH, sliderWidth);
        for (Row row : rows) {
            if (!row.control.visible) {
                continue;
            }
            int top = row.y;
            int bottom = top + ROW_HEIGHT - 2;
            boolean hovered = row == hoveredRow;
            Gui.drawRect(listX, top, listX + barWidth, bottom, hovered ? Theme.ROW_BG_HOVER : Theme.ROW_BG);
            if (hovered) {
                Gui.drawRect(listX, top, listX + 2, bottom, Theme.ACCENT);
            }
            // Com sombra, como o GuiComponent.drawString das outras versoes.
            this.fontRenderer.drawStringWithShadow(ellipsize(row.title, labelLimit),
                    listX + 8, top + (ROW_HEIGHT - 2 - 8) / 2, hovered ? Theme.TITLE : Theme.TEXT);
        }
    }

    private void renderPanel() {
        Gui.drawRect(panelX, contentTop, panelX + panelWidth, contentBottom, Theme.PANEL_BG);
        Gui.drawRect(panelX, contentTop, panelX + panelWidth, contentTop + 1, Theme.ACCENT);

        int textX = panelX + 8;
        int textWidth = panelWidth - 16;
        int y = contentTop + 8;

        String title = hoveredRow != null ? hoveredRow.title : panelDefaultTitle();
        String description = hoveredRow != null ? hoveredRow.description : panelDefaultDescription();
        ResourceLocation preview = hoveredRow != null ? hoveredRow.preview : null;

        // A moldura sempre comeca na mesma altura, logo abaixo da descricao mais comprida
        // da tela. E isso que faz as previas parecerem alinhadas ao passar o cursor de uma
        // opcao para a outra, em vez de pular conforme o tamanho do texto acima.
        int textLimit = panelFooterTop() - 6;
        if (preview != null) {
            int frameTop = renderPreview(preview);
            if (frameTop > 0) {
                textLimit = frameTop - 4;
            }
        }

        // Sem sombra, como o font.draw das outras versoes.
        for (String line : this.fontRenderer.listFormattedStringToWidth(title, textWidth)) {
            this.fontRenderer.drawString(line, textX, y, Theme.ACCENT);
            y += 10;
        }
        y += 4;
        for (String line : this.fontRenderer.listFormattedStringToWidth(description, textWidth)) {
            if (y + 9 > textLimit) {
                break;
            }
            this.fontRenderer.drawString(line, textX, y, Theme.TEXT);
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
    private int renderPreview(ResourceLocation texture) {
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

        Gui.drawRect(left, top, right, bottom, Theme.FRAME_BG);
        Gui.drawRect(left, top, right, top + 1, Theme.FRAME_BORDER);
        Gui.drawRect(left, bottom - 1, right, bottom, Theme.FRAME_BORDER);
        Gui.drawRect(left, top, left + 1, bottom, Theme.FRAME_BORDER);
        Gui.drawRect(right - 1, top, right, bottom, Theme.FRAME_BORDER);

        // O drawRect desta era desliga o blend ao terminar E deixa a cor global na cor do
        // ultimo retangulo - dois motivos para arrumar a casa antes da textura: sem o blend
        // a borda transparente da imagem viraria bloco preto solido; sem repor a cor, a
        // imagem sairia tingida de ciano (a cor da moldura que acabou de ser desenhada).
        // Os fatores 770/771/1/0 sao exatamente os do defaultBlendFunc das outras versoes.
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        // Nesta era quem amarra a textura ao proximo desenho e o TextureManager, por
        // bindTexture() - como na 1.16.5. O drawScaledCustomSizeModalRect e o blit com
        // escala: regiao (0,0)-(200,104) da textura de 200x104, desenhada em drawWidth x
        // drawHeight. Conferido com javap: (x, y, u, v, uWidth, vHeight, width, height,
        // tileWidth, tileHeight).
        this.mc.getTextureManager().bindTexture(texture);
        Gui.drawScaledCustomSizeModalRect(imageX, imageY, 0.0F, 0.0F, PREVIEW_WIDTH, PREVIEW_HEIGHT,
                drawWidth, drawHeight, PREVIEW_WIDTH, PREVIEW_HEIGHT);
        GlStateManager.disableBlend();
        return top;
    }

    private void renderScrollbar(int mouseX, int mouseY) {
        if (maxScroll() <= 0) {
            return;
        }
        int top = scrollbarTop();
        int bottom = scrollbarBottom();
        int right = scrollbarRight();
        boolean active = scrollbarHeld || overScrollbar(mouseX, mouseY);

        // A trilha nao muda de largura - so o polegar cresce, ancorado na borda direita.
        Gui.drawRect(right - SCROLLBAR_WIDTH, top, right, bottom, Theme.SCROLL_TRACK);

        int width = scrollbarHeld ? SCROLLBAR_WIDTH_HELD
                : (active ? SCROLLBAR_WIDTH_HOVER : SCROLLBAR_WIDTH);
        int thumb = thumbHeight();
        int thumbY = thumbTop();
        Gui.drawRect(right - width, thumbY, right, thumbY + thumb,
                active ? Theme.KNOB_HOVER : Theme.SCROLL_THUMB);
    }

    /**
     * Corta o texto com reticencias para caber em {@code maxWidth}.
     *
     * <p>Cortar por largura, e nao quebrar linha, importa: quebrando, um nome como
     * "Typo Correction Threshold" viraria so "Typo" numa tela pequena.
     */
    private String ellipsize(String text, int maxWidth) {
        int limit = Math.max(16, maxWidth);
        if (this.fontRenderer.getStringWidth(text) <= limit) {
            return text;
        }
        return this.fontRenderer.trimStringToWidth(text, limit - this.fontRenderer.getStringWidth("...")) + "...";
    }
}
