package com.rivalzin.bettersearch.mixin.rei;

import me.shedaniel.rei.api.EntryStack;
import me.shedaniel.rei.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Escreve o campo privado {@code allStacks} de volta.
 *
 * <p>O {@code @Redirect} de uma escrita de campo <b>substitui</b> a escrita: quem tem de guardar
 * o valor agora e a gente. Sem este acessor o campo simplesmente nunca seria preenchido e a
 * lista do REI apareceria vazia - um jeito silencioso e feio de quebrar.
 */
@Mixin(value = EntryListWidget.class, remap = false)
public interface EntryListWidgetAccessor {

    @Accessor("allStacks")
    void bettersearch$setAllStacks(List<EntryStack> valor);
}
