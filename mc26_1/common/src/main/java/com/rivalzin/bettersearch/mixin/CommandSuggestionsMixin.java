package com.rivalzin.bettersearch.mixin;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import com.rivalzin.bettersearch.client.CommandSearch;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Style;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

/**
 * Acrescenta sugestoes as listinhas de autocompletar do chat e dos comandos.
 *
 * <p>Injetamos no fim de {@code updateCommandInfo}, quando o Minecraft ja decidiu o que
 * sugerir, e encadeamos o nosso acrescimo no resultado. As sugestoes originais continuam
 * intactas e vem primeiro; so acrescentamos.
 *
 * <p><b>Detalhe que importa muito.</b> O Minecraft tem dois caminhos aqui:
 * <ul>
 *   <li><b>comandos</b> (a linha comeca com "/"): ele encadeia {@code updateUsageInfo}, que
 *       por sua vez ja monta a lista de sugestoes na tela. Precisamos reencadear isso, senao
 *       a lista mostrada seria a de antes do nosso acrescimo;</li>
 *   <li><b>chat comum</b>: ele <b>nao</b> chama {@code updateUsageInfo}, de proposito. A
 *       lista so nasce quando voce aperta TAB. Por isso o primeiro TAB abre a lista e os
 *       seguintes percorrem os nomes.</li>
 * </ul>
 *
 * <p>Chamar {@code updateUsageInfo} tambem no chat criava a lista cedo demais e fazia o
 * primeiro TAB ja aplicar um nome em vez de abrir a lista. Por isso o {@code if} abaixo.
 */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {

    @Shadow
    @Final
    private EditBox input;

    @Shadow
    private ParseResults<ClientSuggestionProvider> currentParse;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    /*
     * 26.1: este metodo deixou de ler o estado do objeto e passou a receber tudo por
     * parametro. Para nos isso e uma melhora - podemos entregar a NOSSA lista de sugestoes
     * a caixa de uso, em vez de torcer para o campo ja ter sido atualizado.
     */
    @Shadow
    private void updateUsageInfo(ParseResults<ClientSuggestionProvider> parse, Suggestions suggestions) {
    }

    /**
     * A ultima lista que nos mesmos produzimos.
     *
     * <p>O Minecraft nem sempre recalcula as sugestoes: quando o TAB aplica um nome ele
     * levanta a flag {@code keepSuggestions} e {@code updateCommandInfo} sai sem mexer no
     * campo. Sem esta guarda, nos empilhavamos um acrescimo sobre o acrescimo anterior - com
     * a posicao de texto <b>antiga</b> e a palavra <b>nova</b>. Era isso que fazia o TAB
     * grudar um id no outro e "multiplicar" o texto.
     */
    @Unique
    private CompletableFuture<Suggestions> bettersearch$lastAugmented;

    @Inject(method = "updateCommandInfo", at = @At("RETURN"))
    private void bettersearch$augmentSuggestions(CallbackInfo ci) {
        CompletableFuture<Suggestions> pending = this.pendingSuggestions;
        if (pending == null || pending == this.bettersearch$lastAugmented || !CommandSearch.isEnabled()) {
            return; // nada novo para acrescentar
        }
        final ParseResults<ClientSuggestionProvider> parse = this.currentParse;
        final String text = this.input.getValue();
        final int cursor = this.input.getCursorPosition();

        // thenCompose, e nao thenApply: a correcao de comando pode precisar perguntar ao
        // servidor quais opcoes valem naquele ponto, e essa resposta chega depois.
        this.pendingSuggestions = pending.thenCompose(suggestions -> parse != null
                ? CommandSearch.augmentCommandAsync(parse, cursor, suggestions)
                : CompletableFuture.completedFuture(CommandSearch.augmentChat(text, cursor, suggestions)));
        this.bettersearch$lastAugmented = this.pendingSuggestions;

        // Somente no caminho de comandos - ver o comentario da classe. Mexer nisto no chat
        // quebra o comportamento do TAB.
        if (parse != null) {
            this.pendingSuggestions.thenAccept(augmented -> {
                if (this.pendingSuggestions.isDone()) {
                    this.updateUsageInfo(parse, augmented);
                }
            });
        }
    }

    /**
     * Pinta de dourado, em vez de vermelho, o trecho que o jogo nao entendeu - mas so quando
     * o mod tem um conserto pronto para oferecer.
     *
     * <p>O vermelho do Minecraft quer dizer "isto nao existe e voce esta sem saida". Quando
     * ha uma sugestao a um TAB de distancia, isso deixa de ser verdade, e a cor deveria dizer
     * "olha aqui, e so aceitar". Sem conserto a oferecer, o vermelho continua vermelho.
     *
     * <p>O alvo e a <i>leitura do campo</i> {@code UNPARSED_STYLE}, e nao a n-esima chamada
     * de um metodo: se um dia o corpo de {@code formatText} mudar de forma, o nome do campo
     * provavelmente continua o mesmo. E, com {@code require = 0}, o mod segue funcionando
     * normalmente caso este ponto deixe de existir - so volta o vermelho.
     */
    @Redirect(
            method = "formatText",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/components/CommandSuggestions;"
                            + "UNPARSED_STYLE:Lnet/minecraft/network/chat/Style;",
                    opcode = Opcodes.GETSTATIC),
            require = 0)
    private static Style bettersearch$softenUnparsed() {
        return CommandSearch.unparsedStyle();
    }
}
