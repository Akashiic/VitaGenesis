# VitaGenesis

Backport da geração de mundo do **Minecraft 1.18+** para o **Minecraft 1.7.10** (Forge 10.13.4.1614).

Esta primeira parte gera a camada profunda do [Below Core](https://github.com/Akashiic/BelowCore) (Y < 0) inteira,
como na 1.18: deepslate, piso de bedrock, minérios (também os de outros mods, em deepslate) e as **cavernas da 1.18**,
com os blocos do **Et Futurum Requiem**. O Below Core é o core e a API (armazenamento, luz, pacotes); o conteúdo é do
VitaGenesis. Em um mundo já explorado, cada chunk antigo recebe o subsolo e as
cavernas quando é carregado. **Nada acima de Y 0 é alterado.**

Status: **beta** (`0.1.0-beta.5`). Arquitetura, decisões e medições: [docs/DESIGN.md](docs/DESIGN.md).

## O que gera abaixo de Y 0

| | origem 1.18 | blocos |
|---|---|---|
| A camada: preenchimento e piso | `NOISE`, regra de superfície `bedrock_floor` | deepslate (EFR; pedra sem ele), bedrock em gradiente nos 5 blocos do piso |
| Minérios, tufo e cascalho | `UNDERGROUND_ORES` do overworld | variantes de deepslate do EFR (ferro, ouro, redstone, diamante, lápis, esmeralda nas montanhas, cobre) |
| Minérios de outros mods (configuráveis) | — | variantes de deepslate do EFR (`moddedDeepslateOres`): vinteum, chimerite, blue topaz, cinábrio, âmbar e infundidos do Thaumcraft, certus, draconium, estanho, prata, chumbo, níquel, alumínio, urânio, platina, tório, tungstênio, titânio, zinco, magnésio, ósmio |
| Cavernas por ruído: queijo (salões), espaguete 2D/3D, macarrão (noodle), pilares | `NoiseRouterData` | ar |
| Aquíferos: água e lava em níveis próprios, com barreiras; lava em tudo abaixo de piso + 10 | `NoiseBasedAquifer` | água, lava |
| Túneis e salas | `CaveWorldCarver` (`cave`, `cave_extra_underground`) | ar, lava |
| Veios de ferro | `OreVeinifier` | deepslate iron ore, raw iron block, tufo (EFR) |
| Geodos de ametista | `GeodeFeature` | ametista, ametista em brotamento, brotos e cristais, calcita, basalto liso (EFR) |
| Cavernas de gotejamento | `LargeDripstone`, `DripstoneCluster`, `PointedDripstone` | dripstone block, pointed dripstone (EFR) |
| Cavernas verdejantes | vegetation patches, `cave_vines`, `vines`, `ore_clay` | musgo, tapete de musgo, azaleia (e florida), cipós com glow berries (EFR); argila, água, capim, vinhas |
| Líquen brilhante, magma submerso | `MultifaceGrowth`, `UnderwaterMagma` | glow lichen, magma (EFR) |
| Masmorras profundas | `monster_room_deep` | spawner e baú com os mobs e o loot de masmorra do pack (Forge `DungeonHooks`/`ChestGenHooks`) |
| Nascentes de água e lava | `SpringFeature` | água, lava |

Os blocos que emitem luz (lava, líquen, glow berries, ametista, magma) saem iluminados, e os fluidos com lado aberto
começam a escorrer, como na 1.18.

**Fora, por falta de blocos no Et Futurum 2.6.59:** Ancient City e Deep Dark (o EFR só tem sculk e catalisador:
faltam veia, sensor, shrieker, deepslate reforçado e Warden), dripleaf, spore blossom, raízes pendentes / terra
enraizada (e a azaleia enraizada, que cresce na superfície), axolotl e glow squid. Os demais recursos dessas
cavernas são gerados.

## Requisitos

| | versão |
|---|---|
| Below Core | API ≥ 4 (`0.1.0-beta.4` ou mais novo: só o core, sem conteúdo próprio) — obrigatório |
| Et Futurum Requiem | 2.6.59-GTNH testado — recomendado (sem ele: só cavernas, aquíferos, túneis e masmorras) |
| GTNHLib | 0.11.40+ |

**Só no servidor.** O VitaGenesis não adiciona blocos, itens nem pacotes; os clientes precisam apenas do que o pack já
tem (Below Core e Et Futurum).

## Instalação no servidor existente (mundo pré-gerado de 0 a 256)

1. **Backup do mundo.**
2. **Piso do Below Core**: o `general.minY` de `config/akashicbellow.cfg` é travado no primeiro boot com o Below Core
   (`<mundo>/akashicbellow.dat`). Para −128, ajuste **antes** desse boot; depois não dá para mudar. O VitaGenesis
   funciona com qualquer piso (testado em −64 e −128).
3. **Et Futurum**: musgo/azaleia e dripstone são "experimentos" do EFR, desligados por padrão. Para as cavernas
   verdejantes e de gotejamento, em `config/etfuturum/experiments.cfg`:
   `B:enableMossAzalea=true` e `B:enableDripstone=true`. Esses blocos passam a ser registrados, então a mesma
   configuração tem de ir **para os clientes também** (servidor e cliente precisam do mesmo conjunto de blocos).
4. Instale o Below Core (servidor e clientes) e o VitaGenesis (servidor) juntos. Os chunks recebem o subsolo e as
   cavernas à medida que são carregados; um pré-gerador que carregue os chunks também dispara a geração.

Chunks cujo subsolo foi gerado **sem** o VitaGenesis (por exemplo, se o Below Core rodar antes sozinho) nunca são
escavados depois: podem ter construções de jogadores. Por isso os dois entram juntos.

## Configuração (`config/vitagenesis.cfg`)

| opção | padrão | |
|---|---|---|
| `caves.enabled` | true | interruptor geral |
| `caves.retrogen` | true | escavar também o subsolo de chunks salvos antes do Below Core (só Y < 0) |
| `caves.noiseCaves` / `aquifers` / `carvers` / `oreVeins` | true | partes do estágio de terreno |
| `caves.seal` | 4 | camadas sólidas sempre mantidas logo abaixo de Y 0 (mínimo 3; com 4 o fogo nas cavernas chega no máximo a Y −2) |
| `caves.fade` | 8 | blocos abaixo do selo em que as cavernas se fecham suavemente (sem teto plano) |
| `features.geodes` / `dripstoneCaves` / `lushCaves` / `glowLichen` / `underwaterMagma` / `monsterRooms` / `springs` | true | recursos de caverna |
| `features.flowingFluids` | true | fluidos com lado aberto começam a escorrer |
| `layer.fillBlock` | auto | bloco da camada (`auto`: deepslate do EFR, ou pedra) |
| `layer.ores` | true | minérios, tufo e cascalho da 1.18 |
| `layer.oreCountMultiplier` / `scaleBottomAnchoredCounts` | 1.0 / true | multiplicador de todos os minérios; densidade da 1.18 com pisos diferentes de −64 |
| `layer.packOres` | true | lista embutida: todos os minérios do pack com variante de deepslate no EFR (Ars Magica 2, Thaumcraft, AE2, Draconic Evolution, metais, ósmio), densidade medida no pack (docs/DESIGN.md §2.1); atualizações do VitaGenesis trazem os valores novos |
| `layer.extraOres` | vazio | acrescenta ou substitui minérios: `minério;tamanho;veios por chunk;minY;maxY[;descarte no ar]`, alturas absolutas ou `bottom+N`; mesmo nome de minério substitui o da lista embutida, contagem 0 remove |
| `features.lushHumidity` | 0.7 | umidade mínima das cavernas verdejantes (1.18: 0.7) |
| `features.lushRainfallWeight` | 1.0 | peso da chuva do bioma 1.7.10 de cima na umidade (0 = só o ruído da 1.18) |
| `features.dripstoneContinentalness` | 0.8 | continentalidade mínima das cavernas de gotejamento (1.18: 0.8) |

Mudanças valem para chunks gerados depois; chunks já gerados nunca são regerados. Mudar as opções de `caves` num
mundo já explorado cria bordas que não casam (uma caverna cheia d'água ao lado de uma vazia pode inundá-la): o
Below Core grava a versão do gerador (com essas opções) por dimensão e avisa no log quando ela muda.

Com piso −16, a lava global da 1.18 (piso + 10) ocupa quase toda a camada; o comportamento pensado é para −64 ou mais
fundo. Auditoria, testes no pack real e a checklist de limitações do Below Core: [docs/AUDIT.md](docs/AUDIT.md).

## Compilar e testar

```bash
(cd ../BelowCore && VERSION=0.1.0-beta.4 ./gradlew build)   # o build usa os jars do Below Core ao lado
VERSION=0.1.0-beta.5 ./gradlew build                        # jar em build/libs (com testes unitários)
tools/selftest.sh -64                                       # servidor dedicado, 3 fases, piso -64
tools/selftest.sh -128                                      # idem, piso -128
tools/clienttest.sh                                         # cliente headless (xvfb-run; EGL/Mesa: libegl1 libegl-mesa0 libgles2)
tools/crucibletest.sh <cópia do servidor>                   # self-test no servidor real (Crucible + pack), linha de comando de produção
```

O self-test cria um mundo "pré-gerado" sem subsolo, liga a geração e verifica a retrogen dele e uma área nova,
procura uma área verdejante e uma de gotejamento, confere piso, selo, luz e recursos, e relê tudo numa JVM nova.
Relatórios em `run/server/vitagenesis-selftest-*.txt`. O teste de cliente põe a câmera dentro de uma caverna
verdejante e de uma de gotejamento, confere que o cliente recebeu os mesmos blocos, luz e faces de líquen que o
servidor e tira screenshots (`run/client/screenshots/`); rode-o depois do `selftest.sh`, de quem ele copia a
configuração de experimentos do EFR.
