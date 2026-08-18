package com.rivalzin.bettersearch.mixin.rei;

import com.rivalzin.bettersearch.mixin.ModPresencePlugin;

/**
 * Liga o gancho do REI so quando o REI esta mesmo instalado.
 *
 * <p>A classe conferida aqui e do REI <b>5.x</b>: o
 * {@code me/shedaniel/rei/impl/client/search/SearchProviderImpl.class}, que as versoes novas
 * usam, so nasceu depois da reescrita da API e NAO existe nesta linha. Conferir a classe errada
 * daria no pior resultado possivel: o mixin nunca ligaria, sem erro nenhum, e a busca no REI
 * simplesmente nao funcionaria - sem nada no log para explicar.
 */
public class ReiMixinPlugin extends ModPresencePlugin {
    public ReiMixinPlugin() {
        super("roughlyenoughitems", "me/shedaniel/rei/gui/widget/EntryListWidget.class");
    }
}
