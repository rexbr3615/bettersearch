package mezz.jei.gui.ingredients;

import java.util.List;

/**
 * ESBOCO DE COMPILACAO - nunca empacotado. Parcial de proposito: so os quatro metodos que o
 * indice usa (javap do jar real; a interface verdadeira tem quinze).
 */
public interface IIngredientListElement<V> {

    V getIngredient();

    String getDisplayName();

    String getResourceId();

    List<String> getTooltipStrings();
}
