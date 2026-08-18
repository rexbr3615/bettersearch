package sonda;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

/**
 * Sonda da 1.16.5. Nao faz nada e nao entra em jar nenhum.
 *
 * <p>Ela toca de proposito nas TRES classes que o Better Search ataca com mixin. Se este
 * arquivo compilar, entao:
 *
 * <ul>
 *   <li>a ferramenta de build baixou e desofuscou a 1.16.5;</li>
 *   <li>ela entregou o Minecraft com os nomes OFICIAIS da Mojang (senao estes nomes nao
 *       existiriam - seriam func_ e class_);</li>
 *   <li>as tres classes que a gente precisa continuam existindo com esses nomes na 1.16.5.</li>
 * </ul>
 *
 * <p>Ou seja: e a resposta inteira da pergunta "da para portar mantendo um codigo so para os
 * dois loaders?" em um arquivo de dez linhas.
 */
public final class Sonda {

    private Sonda() {
    }

    public static boolean deuCerto() {
        return Minecraft.getInstance() != null
                && RecipeBookComponent.class != null
                && CreativeModeInventoryScreen.class != null;
    }
}
