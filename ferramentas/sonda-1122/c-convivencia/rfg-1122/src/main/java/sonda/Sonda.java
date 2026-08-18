package sonda;

/**
 * Sonda da 1.12.2. Nao faz nada e nao entra em jar nenhum.
 *
 * <p>Abaixo da 1.14.4 nao existem mapeamentos da Mojang - o mundo aqui e MCP. Entao esta sonda
 * nao pergunta "os nomes modernos existem?" (nao existem mesmo); ela pergunta tres coisas, uma
 * por linha, para a LINHA que falhar ja dizer o diagnostico:
 */
public final class Sonda {

    private Sonda() {
    }

    // 1) e 2): referencias de CLASSE. Se falharem, o jar do Minecraft nem chegou ao classpath.
    static final Class<?> ITEM_STACK = net.minecraft.item.ItemStack.class;
    static final Class<?> MINECRAFT = net.minecraft.client.Minecraft.class;

    /**
     * 3): chamada de METODO com nome MCP. Se as classes acima resolverem e SO esta linha
     * falhar com "cannot find symbol: method getMinecraft()", o jar veio em nomes SRG
     * (func_71410_x) - ou seja, a ferramenta montou o jogo mas nao aplicou o stable_39.
     * Tambem e resposta: dai se conserta o canal de mapeamento, nao a ferramenta.
     */
    public static boolean deuCerto() {
        return net.minecraft.client.Minecraft.getMinecraft() != null;
    }
}
