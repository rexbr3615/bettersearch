package mezz.jei;

import mezz.jei.ingredients.IngredientFilter;

/**
 * ESBOCO DE COMPILACAO - este arquivo NUNCA entra no jar (sourceSet jeiApi, compileOnly).
 *
 * <p>Por que esbocos em vez de uma dependencia Maven: o jar do pack do usuario e o JEI
 * 4.16.5.1027, que nao esta em Maven nenhum que se possa declarar (mesma situacao do JEI
 * 7.8.1.1019 na 1.16.5). Declarar outra versao seria compilar contra um jar que NAO e o que
 * roda. Aqui cada assinatura foi lida com javap do jar VERDADEIRO do usuario, e a secao 22
 * do verify.sh reconfere esboco por esboco contra esse jar - se o JEI mudar, o verify acusa.
 *
 * <p>So o que o gancho usa aparece aqui: esboco parcial e seguro porque a JVM resolve
 * membro a membro pelo descritor, nao pela classe inteira.
 */
public final class Internal {

    private Internal() {
    }

    /** javap: public static mezz.jei.ingredients.IngredientFilter getIngredientFilter() */
    public static IngredientFilter getIngredientFilter() {
        throw new UnsupportedOperationException("esboco de compilacao");
    }
}
