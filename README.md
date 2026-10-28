# VitaGenesis

Backport da geração de mundo do **Minecraft 1.18+** para o **Minecraft 1.7.10** (Forge 10.13.4.1614).

Esta primeira parte traz as **cavernas da 1.18** para a camada profunda do [Below Core](https://github.com/Akashiic/BelowCore)
(Y < 0), com os blocos do **Et Futurum Requiem**. Em um mundo já explorado, cada chunk antigo recebe o subsolo e as
cavernas quando é carregado. **Nada acima de Y 0 é alterado.**

Status: **beta** (`0.1.0-beta.1`). Arquitetura, decisões e medições: [docs/DESIGN.md](docs/DESIGN.md).

## O que gera abaixo de Y 0

| | origem 1.18 | blocos |
|---|---|---|
| Cavernas por ruído: queijo (salões), espaguete 2D/3D, macarrão (noodle), pilares | `NoiseRouterData` | ar |
| Aquíferos: água e lava em níveis próprios, com barreiras; lava em tudo abaixo de piso + 10 | `NoiseBasedAquifer` | água, lava |
| Túneis e salas | `CaveWorldCarver` (`cave`, `cave_extra_underground`) | ar, lava |
| Veios de ferro | `OreVeinifier` | deepslate iron ore, raw iron block, tufo (EFR) |
| Geodos de ametista | `GeodeFeature` | ametista, ametista em brotamento, brotos e cristais, calcita, basalto liso (EFR) |
| Cavernas de gotejamento | `LargeDripstone`, `DripstoneCluster`, `PointedDripstone` | dripstone block, pointed dripstone (EFR) |
| Cavernas verdejantes | vegetation patches, `cave_vines`, `vines` | musgo, tapete de musgo, azaleia (e florida), cipós com glow berries (EFR); argila, água, capim, vinhas |
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
| Below Core | API ≥ 2 (`0.1.0-beta.2` ou mais novo) — obrigatório |
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
| `caves.seal` | 1 | camadas sólidas sempre mantidas logo abaixo de Y 0 |
| `caves.fade` | 8 | blocos abaixo do selo em que as cavernas se fecham suavemente (sem teto plano) |
| `features.geodes` / `dripstoneCaves` / `lushCaves` / `glowLichen` / `underwaterMagma` / `monsterRooms` / `springs` | true | recursos de caverna |
| `features.flowingFluids` | true | fluidos com lado aberto começam a escorrer |
| `features.lushHumidity` | 0.7 | umidade mínima das cavernas verdejantes (1.18: 0.7) |
| `features.lushRainfallWeight` | 1.0 | peso da chuva do bioma 1.7.10 de cima na umidade (0 = só o ruído da 1.18) |
| `features.dripstoneContinentalness` | 0.8 | continentalidade mínima das cavernas de gotejamento (1.18: 0.8) |

Mudanças valem para chunks gerados depois; chunks já gerados nunca são regerados.

## Compilar e testar

```bash
(cd ../BelowCore && VERSION=0.1.0-beta.2 ./gradlew build)   # o build usa os jars do Below Core ao lado
VERSION=0.1.0-beta.1 ./gradlew build                        # jar em build/libs (com testes unitários)
tools/selftest.sh -64                                       # servidor dedicado, 3 fases, piso -64
tools/selftest.sh -128                                      # idem, piso -128
tools/clienttest.sh                                         # cliente headless (xvfb-run; EGL/Mesa: libegl1 libegl-mesa0 libgles2)
```

O self-test cria um mundo "pré-gerado" sem subsolo, liga a geração e verifica a retrogen dele e uma área nova,
procura uma área verdejante e uma de gotejamento, confere piso, selo, luz e recursos, e relê tudo numa JVM nova.
Relatórios em `run/server/vitagenesis-selftest-*.txt`. O teste de cliente põe a câmera dentro de uma caverna
verdejante e de uma de gotejamento, confere que o cliente recebeu os mesmos blocos, luz e faces de líquen que o
servidor e tira screenshots (`run/client/screenshots/`); rode-o depois do `selftest.sh`, de quem ele copia a
configuração de experimentos do EFR.
