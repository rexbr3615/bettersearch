# Sonda da 1.16.5

Isto **não é o mod**. É um projetinho descartável com um único objetivo: descobrir **qual
ferramenta de build consegue montar o Minecraft 1.16.5 com os nomes oficiais da Mojang**, na
mesma versão de Gradle que o Better Search já usa.

Essa é a única pergunta que separa "a 1.16.5 é um port de dois dias" de "a 1.16.5 é um
projeto à parte", e ela não dá para responder lendo documentação — só rodando.

## Por que três pastas

| Pasta | Ferramenta | O que ela responderia |
|---|---|---|
| `a-archloom-forge` | Architectury Loom 1.17.491 | **o candidato favorito.** É um fork do Fabric Loom 1.17, a mesma geração do que o mod já usa, e cobre Forge a partir da 1.16 com mapeamentos da Mojang. Se este passar, o port é barato. |
| `b-loom-fabric` | Fabric Loom 1.17.17 | o lado Fabric. É exatamente o plugin que os outros 8 módulos Fabric já usam — a dúvida é só se ele ainda desce até a 1.16.5. |
| `c-forgegradle5` | ForgeGradle 5.1.77 | o plugin **oficial** da 1.16.5. Suspeito que ele não rode em Gradle 9 (é de outra época). Está aqui para eu **provar** isso em vez de supor. |

O ModDevGradle, que o mod usa da 1.17 à 1.20.1, está fora: a documentação dele diz
`MinecraftForge and Vanilla Minecraft versions 1.17 up to 1.20.1`. A 1.16.5 fica de fora, e
por isso ela não é "só mais uma cópia de pasta" como a 1.18.2 foi.

## Como rodar

```powershell
powershell -ExecutionPolicy Bypass -File rodar-sonda.ps1
```

Ele roda as três, aguenta as que falharem e imprime um resumo no fim. **Falha é resultado
válido** — é para isso que a sonda existe. Me manda a saída inteira.

Demora: a primeira pode levar vários minutos, porque baixa o Minecraft e um JDK 8.
