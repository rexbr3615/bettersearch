package com.rivalzin.bettersearch.client.gui;

import com.rivalzin.bettersearch.client.LanguageCatalog;
import com.rivalzin.bettersearch.core.SearchSettings;
import com.rivalzin.bettersearch.core.TextNormalizer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Liga e desliga, um a um, todos os idiomas que o jogo conhece.
 *
 * <p>A lista vem do proprio Minecraft, entao inclui idiomas trazidos por resource packs e
 * nunca fica desatualizada. Com mais de cem entradas, ha uma barra de busca no painel da
 * direita: ela filtra por nome ou por codigo e usa a mesma normalizacao do mod, entao
 * digitar "portugues" encontra "Português".
 *
 * <p>O idioma em uso no jogo funciona sempre, ligado ou nao: o nome que aparece na tela ja
 * e indexado direto do item.
 *
 * <p>A caixa de busca desta era e o {@code GuiTextField}, que nao e botao: ela nao entra na
 * buttonList, e tecla, clique, tique e desenho passam por ela na mao - o padrao vanilla
 * (GuiCreateWorld faz igual). O "responder" das outras versoes vira comparar o texto depois
 * de cada tecla.
 */
public final class LanguageSelectScreen extends OptionRowsScreen {

    private final SearchSettings settings;
    private final List<LanguageCatalog.Entry> languages;
    private final String currentCode;

    private GuiTextField searchBox;
    private String filter = "";
    private int shownCount;

    public LanguageSelectScreen(GuiScreen parent, SearchSettings settings) {
        super(ComponentCompat.translatable("bettersearch.config.languages.title"), parent);
        this.settings = settings;
        this.languages = LanguageCatalog.available();
        this.currentCode = LanguageCatalog.currentCode();
    }

    @Override
    protected int panelFooterHeight() {
        return 3 * BUTTON_GAP + 4;
    }

    @Override
    protected void buildRows() {
        shownCount = 0;
        for (LanguageCatalog.Entry language : languages) {
            if (!matchesFilter(language)) {
                continue;
            }
            shownCount++;
            String code = language.code();
            boolean isCurrent = code.equals(currentCode);
            String title = ComponentCompat.literal(language.displayName());
            String description = isCurrent
                    ? ComponentCompat.translatable("bettersearch.config.languages.entry.current", code)
                    : ComponentCompat.translatable("bettersearch.config.languages.entry", code);
            addSwitchRow(title, description, () -> isEnabled(code), value -> setEnabled(code, value));
        }
    }

    private boolean matchesFilter(LanguageCatalog.Entry language) {
        if (filter.isEmpty()) {
            return true;
        }
        return TextNormalizer.normalize(language.displayName()).contains(filter)
                || language.code().contains(filter);
    }

    @Override
    protected void buildPanelFooter() {
        int x = panelX() + 6;
        int width = panelWidth() - 12;
        int y = panelFooterTop() + 4;

        // A mesma caixa e reaproveitada entre reconstrucoes, para nao perder o que foi
        // digitado nem o cursor quando a lista e refiltrada a cada tecla.
        if (searchBox == null) {
            searchBox = new GuiTextField(0, this.fontRenderer, x, y, width, BUTTON_HEIGHT);
            searchBox.setMaxStringLength(32);
            searchBox.setFocused(true); // o setInitialFocus das outras versoes
        } else {
            searchBox.x = x;
            searchBox.y = y;
            searchBox.width = width;
        }

        int third = (width - 4) / 3;
        addFixed(ButtonCompat.builder(ComponentCompat.translatable("bettersearch.config.select_all"), b -> {
            settings.languages = allCodes();
            rebuildWidgets();
        }).bounds(x, y + BUTTON_GAP, third, BUTTON_HEIGHT).build());

        addFixed(ButtonCompat.builder(ComponentCompat.translatable("bettersearch.config.select_none"), b -> {
            settings.languages = new ArrayList<>();
            rebuildWidgets();
        }).bounds(x + third + 2, y + BUTTON_GAP, third, BUTTON_HEIGHT).build());

        addFixed(ButtonCompat.builder(ComponentCompat.translatable("bettersearch.config.select_default"), b -> {
            settings.languages = new ArrayList<>(SearchSettings.DEFAULT_LANGUAGES);
            rebuildWidgets();
        }).bounds(x + 2 * (third + 2), y + BUTTON_GAP, width - 2 * (third + 2), BUTTON_HEIGHT).build());

        addFixed(ButtonCompat.builder(ComponentCompat.translatable("gui.done"), b -> onClose())
                .bounds(x, y + 2 * BUTTON_GAP, width, BUTTON_HEIGHT).build());
    }

    // ------------------------------------------------------------------ a caixa de busca

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (searchBox != null && searchBox.textboxKeyTyped(typedChar, keyCode)) {
            // O "responder" desta era: a caixa consumiu a tecla, compara o texto de agora.
            String normalized = TextNormalizer.normalize(searchBox.getText());
            if (!normalized.equals(filter)) {
                filter = normalized;
                resetScroll();
                // Ja adiado para o comeco do proximo quadro pela base - e o needsRebuild
                // que as outras versoes faziam na mao, pelo mesmo motivo.
                rebuildWidgets();
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (searchBox != null) {
            // Antes da base, como o vanilla faz: a caixa tambem PERDE o foco em clique fora.
            searchBox.mouseClicked(mouseX, mouseY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (searchBox != null) {
            searchBox.updateCursorCounter(); // o piscar do cursor
        }
    }

    @Override
    protected void drawExtraWidgets(int mouseX, int mouseY, float partialTicks) {
        if (searchBox == null) {
            return;
        }
        searchBox.drawTextBox();
        // O texto-fantasma (setSuggestion das outras versoes): o GuiTextField desta era nao
        // tem; o convite e desenhado em cinza por cima quando o campo esta vazio.
        if (searchBox.getText().isEmpty()) {
            this.fontRenderer.drawString(
                    ComponentCompat.translatable("bettersearch.config.languages.search"),
                    searchBox.x + 4, searchBox.y + (searchBox.height - 8) / 2, 0xFF808080);
        }
    }

    @Override
    protected String panelDefaultTitle() {
        return ComponentCompat.translatable("bettersearch.config.languages.title");
    }

    @Override
    protected String panelDefaultDescription() {
        if (shownCount == 0) {
            return ComponentCompat.translatable("bettersearch.config.languages.none_found");
        }
        return ComponentCompat.translatable("bettersearch.config.languages.summary",
                enabledCount(), languages.size(), currentCode);
    }

    // ------------------------------------------------------------------ estado

    private boolean isEnabled(String code) {
        return settings.indexesAllLanguages() || settings.languages.contains(code);
    }

    private int enabledCount() {
        int count = 0;
        for (LanguageCatalog.Entry language : languages) {
            if (isEnabled(language.code())) {
                count++;
            }
        }
        return count;
    }

    private void setEnabled(String code, boolean enabled) {
        // Troca o coringa "*" pela lista explicita, senao desligar um idioma nao teria efeito.
        if (settings.indexesAllLanguages()) {
            settings.languages = allCodes();
        }
        if (enabled) {
            if (!settings.languages.contains(code)) {
                settings.languages.add(code);
            }
        } else {
            settings.languages.remove(code);
        }
    }

    private List<String> allCodes() {
        List<String> all = new ArrayList<>(languages.size());
        for (LanguageCatalog.Entry language : languages) {
            all.add(language.code());
        }
        return all;
    }
}
