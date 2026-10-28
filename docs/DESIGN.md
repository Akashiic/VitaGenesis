# VitaGenesis — Arquitetura

> Backport da geração de mundo da 1.18+ para Minecraft 1.7.10 (Forge 10.13.4.1614), feito para o modpack TEOF 4.1
> (servidor Crucible). Primeira parte: as cavernas da camada profunda do Below Core (Y < 0), com os blocos do
> Et Futurum Requiem (EFR) 2.6.59-GTNH.

## 1. Escopo e regra de ouro

O mundo de produção já está pré-gerado de Y 0 a 256. O Below Core acrescenta a camada abaixo de 0 e a gera chunk a
chunk quando cada um é carregado (inclusive os antigos, por retrogen). O VitaGenesis dá forma a essa camada como a
1.18 faz: cavernas, aquíferos, veios, biomas de caverna e seus recursos.

**Nada em Y ≥ 0 é alterado.** Não é só cuidado do VitaGenesis: toda escrita passa pela API do Below Core, que só
aceita `minY..-1`. As cavernas ainda se fecham antes de Y 0 (§4.4), então nunca se abrem para o mundo 1.7.10 de cima
(cuja camada Y 0 é sólida: era bedrock e o Below Core a troca pelo bloco profundo).

## 2. Integração com o Below Core

O VitaGenesis é um `IDeepGenerator` registrado em `BellowAPI.registerGenerator` (API ≥ 2, conferida em tempo de
execução com `BellowAPI.apiVersion()`). Ele compila contra o `-api.jar` do Below Core: nada fora do pacote
`com.akashiic.bellow.api` fica visível. A ordem por chunk espelha a 1.18 (ruído e carvers, depois features):

| etapa do Below Core | VitaGenesis | vizinhança | 1.18 |
|---|---|---|---|
| preenche o subsolo (deepslate, piso de bedrock) | — | — | `NOISE` (pedra) |
| `generateTerrain` | cavernas por ruído, aquíferos, veios de ferro, carvers (§3–§6) | só o chunk | `NOISE`, `CARVERS` |
| minérios do Below Core (respeitam o ar escavado: `discardChanceOnAirExposure`) | — | 3×3 | `FEATURES`/ores |
| `decorate` | recursos de caverna (§8) | 3×3 | `FEATURES` |
| `onDecorated` | luz e fluidos, quando os 5×5 estão decorados (§9) | 5×5 | `LIGHT` |

Cada etapa roda uma vez por chunk. Chunks cujo subsolo foi gerado antes de o VitaGenesis existir nunca chegam ao
`generateTerrain` dele, então ele não escava onde jogadores possam ter construído.

O estágio de terreno é função pura da semente, da posição do chunk e da configuração. Ele toca só o chunk e escreve
direto nas seções profundas, então as bordas casam em qualquer ordem de geração (medido: vizinhos concordam 99,3%
através da borda e 99,4% dentro do chunk).

## 3. Pilha de ruído (porte da 1.18)

`XoroshiroRandom` (Xoroshiro128++, `RandomSupport.upgradeSeedTo128bit`, fábrica posicional com `fromHashOf` por MD5 e
`at(x, y, z)` por `Mth.getSeed`), `ImprovedNoise`, `PerlinNoise` (inicialização moderna, `octave_<n>`), `NormalNoise`
(fator de entrada 1.0181268882175227, desvio-alvo 1/3) e a tabela de parâmetros do `NoiseData`. Cada ruído é
instanciado como no `RandomState`: `fromHashOf("minecraft:<nome>")` sobre a fábrica derivada da semente do mundo.

Não há um jar da 1.18 neste ambiente para comparar valor a valor. A fidelidade é sustentada pelo código portado linha a
linha e por propriedades medidas: desvio de 1/3 nos ruídos de muitas oitavas, máximo nunca excedido, uniformidade do
`nextInt` de Lemire, e as distribuições do §10.

## 4. Cavernas por ruído

`CaveNoise.base` é o ramo `underground` do roteador da 1.18: `cheese` (camadas, `cave_cheese`), entradas
(`cave_entrance` + espaguete 3D com raridade "weird scaled"), espaguete 2D (elevação e espessura moduladas), rugosidade
e pilares, com o *slide* do fundo. Depois vêm a interpolação trilinear numa grade de 4×8×4 blocos alinhada ao piso, o
`postProcess` (×0,64 e *squeeze*) e o mínimo com o *noodle* (quatro entradas interpoladas em separado).

### 4.1 O que é dobrado

Abaixo de 0 a densidade do terreno (`sloped_cheese`) é sempre muito maior que 1,5625. O roteador sempre escolhe o ramo
`underground`, e o termo `clamp(1,5 − 0,64·sloped_cheese, 0, 0,5)` do queijo vale 0. As duas simplificações são exatas
nesta faixa.

### 4.2 Âncoras verticais com piso variável

Em −64 as funções são as da 1.18. Para outros pisos, tudo o que a 1.18 ancora no fundo do mundo acompanha o piso por
`yRel = y − (minY + 64)`, enquanto os ruídos continuam amostrados nas coordenadas reais:

- *slide* do fundo (24 blocos);
- faixa do *noodle* (−60);
- gradiente do espaguete 2D;
- lava global (piso + 10);
- lava dos carvers (piso + 8);
- início dos carvers (piso + 8);
- faixa dos veios (piso + 4).

O gradiente das entradas (−10..30) é da superfície e fica absoluto. Com −128 a camada inteira tem cavernas e o perfil
do fundo desce junto (§10).

### 4.3 Aquíferos

Porte do `NoiseBasedAquifer`: centros numa grade de 16×12×16 deslocados aleatoriamente, os três mais próximos,
similaridade, barreira com ruído e pressão, nível aleatório por célula de 16×40×16, aquíferos de lava abaixo de −10 e
lava global abaixo de piso + 10.

A única mudança: o nível preliminar da superfície é a constante 64. Abaixo de 0 nenhuma célula está a menos de 12
blocos dela, e o mundo de cima (1.7.10) não é lido; isso também mantém o estágio restrito ao próprio chunk.

### 4.4 Fechamento sob Y 0 (selo e esmaecimento)

`seal` camadas logo abaixo de 0 ficam sempre sólidas (padrão 1). Nos `fade` blocos de baixo do selo (padrão 8), a
densidade recebe `0,5·t²`, acima do menor valor que o roteador produz (o *squeeze* fica em ±0,458), e os elipsoides dos
carvers encolhem na mesma proporção. As cavernas se fecham pelo contorno do ruído, sem teto plano em Y −1.

## 5. Carvers

Porte do `CaveWorldCarver` com `cave` (probabilidade 0,15, Y de piso + 8 a 180) e `cave_extra_underground` (0,07, até
47). As partidas vêm de todos os chunks num raio de 8, com `setLargeFeatureSeed(seed + índice, x, z)`, e os túneis têm
a mesma sequência de RNG da 1.18 (seno por tabela, como o `Mth` dela). O cânion começa em Y 10..67 e fica acima da
camada: não foi portado.

Túneis iniciados acima da camada são percorridos e recortados, como o fundo de um mundo 1.18. Uma partida que não
consegue descer até o teto (o túnel anda no máximo um bloco por passo) é pulada depois de consumir os mesmos sorteios.
Como na 1.18, os carvers não substituem bedrock nem fluidos, e o aquífero decide a substância de cada bloco escavado.

## 6. Veios de ferro

Porte do `OreVeinifier`: *toggle* e cristas interpolados, solidez 0,7, riqueza, `ore_gap`, 2% de blocos brutos.
Abaixo de 0 só existe o veio de ferro (o de cobre vive em Y 0..50). A faixa é `minY + 4..−8`, e com −64 fica a
−60..−8 da 1.18. Blocos: deepslate iron ore, raw iron block (`raw_ore_block:1`) e tufo do EFR. Os veios são raros e
grandes, como na 1.18: o fator de borda eleva o limiar em quase toda a faixa.

## 7. Biomas de caverna

Na 1.18 as cavernas verdejantes exigem umidade (`vegetation`) ≥ 0,7 e as de gotejamento continentalidade ≥ 0,8. Abaixo
de 0 a profundidade cai na faixa dos dois biomas, então a escolha depende só de x e z. Os ruídos climáticos são os da
1.18, com `shift`.

Medido com o porte fiel:

| ruído | desvio | o que isso dá |
|---|---|---|
| `vegetation` | 0,247 (as oitavas zeradas no fim reduzem o desvio) | umidade ≥ 0,7 em só **~0,2%** das colunas |
| `continentalness` | 0,377 | gotejamento em **~1,8%** das colunas |

Na 1.18 essa umidade é a mesma da superfície: as cavernas verdejantes ficam debaixo de terra úmida. Aqui a superfície
é 1.7.10 e guarda a umidade no `rainfall` do bioma, que entra na umidade com peso `lushRainfallWeight` (padrão 1,0):

| superfície | colunas com cavernas verdejantes |
|---|---|
| selva, pântano (0,9) | ~11% |
| floresta (0,8) | ~5% |
| planície (0,4) | ~0,03% |
| deserto (0) | 0% |

O gotejamento fica com o limiar fiel da 1.18 (só ruído), configurável.

## 8. Recursos de caverna

Ordem dos passos da 1.18. Cada recurso usa sua própria colocação da 1.18 (contagem, quadrado, faixa de altura,
varredura de ambiente, deslocamento aleatório, filtro de bioma) e um `Random` próprio semeado por (semente, chunk,
recurso). O resultado não depende da ordem de decoração. As alturas de `uniform(bottom, top)` acima do teto das
cavernas são sorteadas e descartadas (pertencem aos geradores 1.7.10), e as contagens são escaladas para pisos
diferentes de −64, como o Below Core faz com os minérios.

| passo | recurso (configuração 1.18) | adaptação |
|---|---|---|
| LOCAL_MODIFICATIONS | `amethyst_geode` (1/24) | só geodos inteiros sob o teto; `fastInvSqrt` da 1.18 molda as camadas; brotos com metas do EFR (`face + 6·estágio`) |
| | `large_dripstone` (gotejamento) | com vento; só em ar, água ou lava |
| UNDERGROUND_STRUCTURES | `monster_room_deep` (4 tentativas) | mobs e loot do pack via `DungeonHooks`/`ChestGenHooks`; baú virado para fora da parede |
| UNDERGROUND_ORES | `underwater_magma` | os minérios são do Below Core |
| UNDERGROUND_DECORATION | `glow_lichen` | busca de até 20 blocos (a intenção do `searchRange`; a 1.18.2 retesta o vizinho, versões seguintes andam); faces no TileEntity do EFR (`State`, bit `1 << ForgeDirection`); o espalhamento vira uma segunda face no mesmo bloco |
| | `dripstone_cluster`, `pointed_dripstone` | metas do EFR: 0..4 para baixo (ponta, frustum, meio, base, fusão), 5..9 para cima; sem waterlogging no EFR, só em ar |
| FLUID_SPRINGS | `spring_water`, `spring_lava` | a fonte começa a escorrer no estágio de luz (§9) |
| VEGETAL_DECORATION | musgo no teto com cipós, `cave_vines`, `lush_caves_clay`, musgo no chão com vegetação, `vines` | glow berries em 1/5; `MaxLength` do cipó = comprimento + o que a idade 23..25 de 25 deixa crescer; dripleaf, spore blossom e azaleia enraizada não existem no EFR |

Fora por falta de blocos no EFR 2.6.59: Ancient City, Deep Dark (só há sculk e catalisador), dripleaf, spore blossom,
raízes pendentes / terra enraizada, axolotl, glow squid. O `ore_clay` das cavernas verdejantes também ficou fora: ele
precisaria do `OreFeature`, que hoje é interno do Below Core. Em vez de duplicar código, a sugestão é expô-lo na API
do Below Core numa próxima versão; as poças e manchas de argila já existem.

## 9. Luz e fluidos

O terreno e a decoração escrevem sem luz: rodam com a vizinhança parcialmente carregada, onde o
`World.updateLightByType` vanilla desistiria em silêncio. Um chunk é iluminado quando os 5×5 em volta estão
carregados e decorados. Daí em diante nenhum gerador escreve no 3×3 que a luz dele alcança, porque uma decoração
escreve no máximo a um chunk do seu.

Um preenchimento por inundação (`DeepLighting.Flood`) parte das fontes do chunk (lava, líquen, glow berries, ametista,
magma) e aplica a regra exata do `World.computeLightValue`: `max(emissão, vizinho − max(1, opacidade))`, com emissores
opacos deixando a luz entrar. A inundação só aumenta valores, então a luz das fontes de cada chunk soma à de todos, em
qualquer ordem.

As seções alteradas são reenviadas aos jogadores (`BellowAPI.resendDeepSections`), e a marca "iluminado" vai para o
NBT do chunk (`Level.VitaGenesis`), então cada chunk é iluminado uma única vez. Valores dinâmicos de luz e opacidade
(métodos do Forge sobrescritos, por exemplo nos cipós do EFR) são consultados por posição; os demais usam o valor fixo.

No mesmo momento, fontes de água e lava paradas com ar ao lado ou embaixo recebem a notificação de vizinho que faz um
fluido parado escorrer no vanilla. Isso cobre nascentes e bordas de aquíferos, como o *fluid tick* da 1.18.

## 10. Testes

**Unitários** (`./gradlew test`), sem Minecraft:
- Xoroshiro: determinismo e uniformidade;
- `NormalNoise`: desvio e máximo;
- seno por tabela;
- terreno: piso, selo e lava profunda em −64 e −128, volume de cavernas por faixa de Y;
- continuidade nas bordas, determinismo e custo;
- regressão do dimensionamento da grade do aquífero (reprova sem a correção: `ArrayIndexOutOfBounds`).

**Servidor** (`tools/selftest.sh <piso>`, servidor dedicado com Below Core + EFR em JVMs separadas):

1. `bootstrap`: Below Core desligado; a área A vira um mundo 1.7.10 sem subsolo ("pré-gerado").
2. `full`:
   - a área A é carregada do disco (retrogen) e a B é gerada nova;
   - o teste procura a área verdejante (C) e a de gotejamento (D) mais próximas;
   - em 81 chunks de cada área confere: todos decorados e iluminados, piso de bedrock intacto, selo sólido, volume
     de cavernas plausível, lava presente, toda fonte de luz com a própria luz;
   - reacende com o motor vanilla em volta de centenas de fontes e exige que nada mude;
   - exige que os recursos típicos de C e D apareçam.
3. `reload`: uma JVM nova relê as quatro áreas e compara o checksum (blocos, metas, luz de bloco) e as marcas de luz.

| piso | volume de cavernas A / B / C / D | luz reacesa pelo motor vanilla | releitura |
|---|---|---|---|
| −64 | 3,5% / 11,2% / 10,9% / 12,2% | 0 células mudaram | idêntica, 324/324 marcas |
| −128 | 7,2% / 13,4% / 15,5% / 18,1% | 0 células mudaram | idêntica |

A diferença entre A e B é espacial: o gerador puro, no teste unitário, reproduz os mesmos 3,4% e 11,2% nas mesmas
coordenadas. Com isso, o caminho de retrogen e o de chunk novo são idênticos.

Em −64, a área verdejante teve 10 136 blocos de musgo, 980 tapetes, 429 azaleias (158 floridas), cipós com berries
em ~20%, 7 620 de argila e capim/vinhas. A de gotejamento teve 26 661 blocos de dripstone e ~4 400 pontas, com a
distribuição de espessuras esperada.

**Cliente headless** (`tools/clienttest.sh`, Xvfb + Mesa): um mundo singleplayer com a câmera dentro de uma caverna
verdejante e de uma de gotejamento. Cliente e servidor têm os mesmos blocos, a mesma luz de bloco (0 de 5 733 células
diferentes em cada área) e as mesmas faces de líquen (dados do TileEntity enviados com o chunk). Screenshots em
`run/client/screenshots/`, com luz natural e com visão noturna.

**Custo** medido no self-test em −64 (servidor dev, 1 532 chunks):

| estágio | custo por chunk |
|---|---|
| terreno | 2,73 ms |
| decoração | 1,01 ms |
| luz (inclui a checagem dos 5×5) | 0,47 ms |
| **total do VitaGenesis** | **≈ 4,2 ms** |

Para comparação, o Below Core mediu ~11 ms por chunk de geração com a camada de −64. O estágio de terreno puro, no
teste unitário, custa ~3,0 ms em −64 e ~3,5 ms em −128.

## 11. Riscos abertos (beta)

- **Opções experimentais do EFR**: musgo/azaleia e dripstone são "experimentos". O próprio EFR avisa que podem mudar
  e até trocar IDs entre versões. Ligar exige a mesma configuração no servidor e nos clientes.
- **Fluidos**: a ativação de bordas pode criar cachoeiras e encontros de lava com água (obsidiana/pedregulho), como na
  1.18. Desligue com `features.flowingFluids` se preferir fluidos parados.
- **Fidelidade**: as distribuições conferem com a 1.18 pelo que se pode medir aqui. Não houve comparação bloco a bloco
  com um mundo 1.18 da mesma semente (não há jar da 1.18 no ambiente). O líquen usa a busca andando (1.19+).
- **Crucible**: o VitaGenesis não tem mixins nem acesso a plugins; tudo passa pela API do Below Core, já testada no
  Crucible de produção. Um teste no Crucible com o pack inteiro ainda deve ser feito antes de produção.
- **Chunks na borda do que foi gerado** antes de instalar o VitaGenesis (terreno sem decoração) recebem os recursos
  sem as cavernas dele; os recursos se adaptam ao que encontram.
