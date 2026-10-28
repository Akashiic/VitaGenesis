# Auditoria 1 — VitaGenesis 0.1.0-beta.1 sobre Below Core 0.1.0-beta.2

Rodada de auditorias especializadas (somente leitura, com evidência no código e em harness) e inspeções pragmáticas
no pack real. Cada item tem a forma **hipótese → comportamento → veredito → ação**.

Vereditos: **DEFEITO** (reproduzido ou provado no código), **RISCO** (caminho plausível, sem reprodução),
**LIMITAÇÃO** (falta na API do Below Core), **OK** (verificado, sem problema).

## 1. Teste no pack real (TEOF 4.1, Crucible)

Cópia do servidor de produção (`server.jar` Crucible, 300+ mods, plugins, configs e agentes do pacote do servidor),
com a linha de comando de produção (Java 21 + lwjgl3ify, G1, 9 GB) e o self-test em 3 JVMs: `tools/crucibletest.sh`.
Seed aleatória, piso −64, configs do pacote (EFR: musgo/azaleia ligado, **dripstone desligado**).

| verificação | resultado |
|---|---|
| boot com todos os mods, gerador registrado no Below Core, API ≥ 2 | PASS |
| regra de ouro: retrogen não muda Y ≥ 0 (CRC de 121 chunks pré-gerados, antes e depois) | PASS, 0 alterados |
| retrogen (81 chunks do disco), área nova, área verdejante | PASS: piso, selo, luz = motor vanilla, fontes de luz, líquen, musgo |
| cavernas de gotejamento | puladas: o EFR do pack tem `enableDripstone=false` |
| chunk decorado sem o VitaGenesis não é tocado pela luz | PASS |
| costura: chunk iluminado quando o vizinho decorado volta do disco (JVM nova) | PASS |
| releitura em JVM nova: blocos, metadados e luz idênticos; marcas restauradas | PASS (243/243) |
| custo por chunk no pack (VG): terreno 4,0–4,8 ms, decoração 1,3–1,4 ms, luz 1,0–1,8 ms por chunk iluminado | retrogen 9,5–16 ms/chunk no total; chunk novo 43–57 ms (inclui o terragen 1.7.10 e todos os mods) |
| erros do VitaGenesis no log (`Light stage failed`, exceções) | nenhum; só o aviso esperado de dripstone desligado |

Rodada final, com todas as correções da seção 2 (seed aleatória nova): **PASS** nas 3 fases no pack real e em
`tools/selftest.sh -64` e `-128`.

O único FAIL da primeira rodada foi o volume de cavernas da área B (2,0 %, limite do teste 3 %). Reproduzido fora do
jogo com a mesma seed (2,2 %); em 60 áreas 9×9 aleatórias de duas seeds o volume vai de 1,7 % a 17 % (10 a 15 % das
áreas abaixo de 3 %). É a variação do ruído da 1.18, não defeito: o limite do teste foi para 1 %.

## 2. VitaGenesis — achados e correções

| # | hipótese | veredito | ação |
|---|---|---|---|
| V1 | Luz e ativação de fluidos rodavam dentro da carga de chunk (`onDecorated`). Se a carga acontece enquanto um jogador coloca um bloco, o Forge/Crucible captura os snapshots e pode reverter tudo; dentro de `scheduledUpdatesAreImmediate` (WorldGenLiquids, estruturas) o fluido ativado escorre recursivamente | RISCO | **corrigido**: `onDecorated` só enfileira; a luz roda no fim do tick do mundo |
| V2 | Exceção no estágio de luz chamado pelo tick (bloco de mod em `getLightValue`, vizinho de fluido) sobe até o loop do servidor: crash | RISCO alto | **corrigido**: try/catch por chunk com log; o chunk só é marcado iluminado se terminar, e tenta de novo quando voltar à fila |
| V3 | Pré-gerador ou voo rápido enfileiram centenas de chunks: luz + fluidos sem limite num tick | RISCO | **corrigido**: orçamento de 10 ms por tick do mundo; o resto fica para o tick seguinte |
| V4 | Cada chunk iluminado reenviava até 9 chunks ao cliente (cada coluna ~9×) | RISCO (rede) | **corrigido**: um reenvio por chunk por tick, com as máscaras somadas |
| V5 | Exceção numa feature pulava `markOurs`: chunk escavado pelo VG nunca iluminado nem com fluidos ativados | DEFEITO | **corrigido**: `markOurs` em `finally` |
| V6 | `deepBlock` do Below Core = tufo (ou minério/bloco de ferro): o terreno identificava veio pelo bloco, a camada inteira virava "veio" e não saía nenhuma caverna de ruído, aquífero ou veio, sem log | DEFEITO (com essa config) | **corrigido**: veio marcado pela posição gravada neste passe |
| V7 | `seal` 1 ou 2 (aceito pela config): o bloco aberto mais alto é `−2 − seal` e o fogo da 1.7.10 sobe 4 blocos sem checar obstrução, alcançando Y 0/1 | RISCO (regra de ouro em jogo) | **corrigido**: mínimo 3; DESIGN corrigido (com 4, o fogo chega a −2, não −1) |
| V8 | Config de terreno ou versão do gerador muda entre sessões: bordas não casam, caverna cheia d'água ao lado de uma vazia inunda ao ativar os fluidos | RISCO | **mitigado**: `vitagenesis.txt` no save guarda as settings; mudança gera aviso no log ao iniciar |
| V9 | Comparações `== GEN_DECORATED` quebram se o Below Core criar um estado novo | RISCO | **corrigido**: `>=` |
| V10 | Caches por seed (ruído, features) nunca liberados (Mystcraft, singleplayer) | RISCO baixo | **corrigido**: limpos no `FMLServerStoppedEvent`, junto com a fila |
| V11 | Custo do passe de ruído cresce com a profundidade: ~3 ms/chunk em −64, ~6 em −128, 14–18 em −512 (harness, sem o MC) | RISCO de TPS em −512 | aberto: aceitável em −64/−128; otimizações (pular células sólidas garantidas, interpolação incremental) se o piso for −512 |
| V12 | Mais de uma dimensão com camada profunda: mesma seed (`DerivedWorldInfo`) → cavernas iguais nas mesmas coordenadas | RISCO cosmético | aberto: o pack só tem a dimensão 0; misturar o id da dimensão mudaria dimensões já geradas |
| V13 | Phosphor (clientes com ArchaicFix): emissor opaco (magma, luz 3) dá 3 no Phosphor e `vizinho − 1` no vanilla | OK (sem efeito visual) | nenhuma: a face opaca usa a luz do vizinho |
| V14 | Luz alta velha não é corrigida quando uma feature escreve por cima num chunk da fronteira iluminado antes pelo Phosphor | RISCO baixo | aberto (o Flood só aumenta valores) |
| V15 | Piso −16: lava global (`piso + 10`) e o *slide* de 24 blocos maiores que a camada → quase toda caverna vira lava | OK (comportamento da 1.18) | nota |
| V16 | Escrita em Y ≥ 0 ou no piso, divisões por zero, `nextInt(≤0)`, grade do aquífero, determinismo, ordem de eventos de carga/NBT (também no `ChunkIOProvider` do Crucible), TileEntities, blocos que caem, interação com minérios e o guard legado, self-tests no jar de produção | OK | — |

Testes que cobrem as correções: o self-test roda a fila de luz como o tick (`processPending` até esvaziar) em todas as
fases, e as fases de costura e "não é nosso" passam pela fila; `tools/selftest.sh -64`, `-128` e o pack real.

## 3. Below Core — checklist de limitações (para corrigir no Below Core)

Consolidado da auditoria da API (somente leitura) e dos achados do VitaGenesis. Em ordem de prioridade. Os itens marcados
com "VG contorna" já têm solução local no VitaGenesis, que pode ser removida quando o Below Core os resolver.

**Correção (dados e estabilidade)**
- [ ] `DeepRegion.setBlock` (e o `ensureDeepSection` da luz) aceita chunk de formato NEWER (somente leitura): a escrita se
  perde no save e o cliente diverge. Recusar como o `World` já faz.
- [ ] `DeepDecorator.tryDecorate` só pega `RuntimeException` e grava DECORATED antes: se `placeFeatures` lançar, os vizinhos
  tocados não são marcados nem reenviados (veios perdidos no save) e os geradores são pulados. Pegar `Throwable` e
  marcar/reenviar num `finally`.
- [ ] `rethrowFatal` deixa o `StackOverflowError` passar como erro comum: o desempilhamento por `originalLoadChunk` pode
  deixar `loadingChunks` e o gerador vanilla corrompidos. Não engolir, ou limitar a profundidade.
- [ ] Reentrância: decorações disparadas durante outra decoração ou um `onDecorated` deveriam ir para uma fila esvaziada
  no fim da decoração externa.

**Contrato da API**
- [ ] `onDecorated` (e etapas futuras) a partir de uma fila no fim do tick do mundo, fora de carga de chunk e sem
  `captureBlockSnapshots` (VG contorna: fila própria).
- [ ] Etapa de luz gerenciada pelo Below Core: `IDeepRegion.markLight(x, y, z)` coleta emissores, o Below Core ilumina
  quando o 5×5 estiver decorado, tenta de novo ao carregar do disco e persiste (VG contorna: `DeepLighting`).
- [ ] Luz profunda nas bordas de chunk no cliente: os flags de borda do ArchaicFix (máscara `short`, 16 seções) só cobrem
  Y ≥ 0, então a luz profunda cortada numa borda nunca é refeita pelo Phosphor. Agendar a verificação das bordas
  profundas ao carregar.
- [ ] Dados por chunk e por gerador dentro de `akashicbellow.deep` (`Gens: {id: {terrain, decorated, version, failed,
  data}}`), preservados quando o addon está ausente (VG contorna: NBT `Level.VitaGenesis` via `ChunkDataEvent`, que se
  perde no `cloneChunk`). Responde também "gerado com qual gerador" (chunk salvo em TERRAIN e carregado sem o gerador
  hoje é decorado sem ele, e o VG nunca acende a lava desse chunk).
- [ ] Persistir em `akashicbellow.dat` a lista e a ordem dos geradores por dimensão e avisar quando mudar (VG contorna
  só para as próprias settings: `vitagenesis.txt`).
- [ ] `BellowAPI.isDecorated(chunk)` (estado ≥ DECORATED) e a regra documentada de que estados novos são ≥ 3, sem subir
  `V` (VG contorna: `>=`).
- [ ] Expor o bloco de preenchimento: `getFillBlock()` / `isDeepFill(block, meta)`, `isBaseStone`, `isDeepHost`. Hoje o
  terreno do VG trata como pedra qualquer sólido sem TileEntity: blocos que jogadores ou outro gerador puseram num
  chunk ainda GEN_NONE são escavados.
- [ ] Expor `IDeepRegion.placeOre(...)` / `placeBlob(...)` (o `OreFeature` do Below Core): o `ore_clay` da 1.18 ficou de
  fora do VG por isso.
- [ ] Estado de cada vizinho na região (decorado antes ou agora, `inhabitedTime`): evitar features sobre construções
  em vizinhos decorados há tempo.
- [ ] Registro com metadados: `contractVersion()`, prioridade e `appliesTo(WorldServer)`.

**Superfície da API (endurecimento)**
- [ ] Tirar do contrato público `bellow$setGenState`, `bellow$setDeepSections`, `bellow$setFromDisk` e `setMinY` no
  servidor; trocar `bellow$getDeepSections()` no javadoc de `IDeepGenerator` por `getDeepSection` (expõe slots
  dormentes abaixo do piso).
- [ ] Modo estrito opcional: durante as chamadas a geradores, `World.setBlock` em Y ≥ 0 ou fora do 3×3 registra erro e é
  recusado (pega violações do contrato em teste; `world()` entrega o `WorldServer` cru).

**Desempenho**
- [ ] Juntar os reenvios da decoração por tick e por chunk (hoje 9 chunks por decoração, seções inteiras; a −512 até 32
  por coluna). O VG já junta os da luz.

**Roadmap (Y ≥ 0, terragen 1.18 com cliffs)**
- [ ] Gancho para chunks novos antes da população, com `freshChunk` persistente também no `decorate`, faixa de Y no
  contrato e recálculo de heightmap e skylight depois de escrever. Hoje a região recusa Y ≥ 0 e não há sinal de chunk
  novo fora do `generateTerrain`.

## 4. Rodada 2 — Below Core 0.1.0-beta.3 (API 3) e VitaGenesis 0.1.0-beta.2

O Below Core resolveu a checklist da seção 3, exceto três itens recusados com justificativa: luz nas bordas no cliente
(as flags de borda do ArchaicFix 0.8.0 só existem para a luz do céu, que é 0 abaixo de Y 0; o caso real é coberto
pela etapa de luz dele), `contractVersion` (um padrão viria do jar do Below Core, não do gerador; o `apiVersion()`
cobre) e o gancho para Y ≥ 0 (roadmap).

O VitaGenesis adotou a API 3 e removeu os contornos próprios:

| antes (API 2) | agora (API 3) |
|---|---|
| fila, flood e orçamento de luz próprios (`DeepLighting`), marcas `Ours`/`Lit` em `Level.VitaGenesis` | etapa de luz do Below Core; fluidos no `onLit`; marca "decorado pelo VitaGenesis" em `getGeneratorData` |
| terreno escavava todo sólido sem TileEntity | só o preenchimento do Below Core e a pedra base (`isDeepFill`/`isBaseStone`) |
| `vitagenesis.txt` com as settings | `version()`; o Below Core grava por dimensão e avisa |
| `ore_clay` fora (precisava do `OreFeature` interno) | `IDeepRegion.placeBlob` |
| self-test com a fila própria | `BellowAPI.runDeepStages`; costura: chunk salvo com luz pendente é iluminado depois de reiniciar |

Testes desta rodada (todos **PASS**):

| teste | resultado |
|---|---|
| `tools/selftest.sh -64` e `-128` (3 fases) | PASS; luz do Below Core igual ao motor vanilla em volta de todas as fontes medidas |
| controle negativo: `onLit` sem a marca "decorado pelo VitaGenesis" | reprova (a água do "jogador" escorre), como esperado |
| `tools/clienttest.sh` | PASS: cliente com os blocos, a luz e as faces de líquen do servidor |
| pack real (Crucible, Below Core beta.3, seed nova) | PASS nas 3 fases: regra de ouro (121 chunks, 0 alterados), retrogen, área nova, verdejante (com `ore_clay`), costura com luz pendente, chunk decorado com o VG desligado, releitura |
| custo no pack (VG) | terreno 3,5 ms, decoração 1,1 ms, fluidos 0,3 ms por chunk; retrogen 10 ms/chunk no total, chunk novo 43 ms |
| log do pack | sem erros do VitaGenesis ou do Below Core; só o aviso esperado de dripstone desligado |

Para a produção: `AkashicBellow-0.1.0-beta.3.jar` no servidor **e nos clientes**, `VitaGenesis-0.1.0-beta.2.jar` só no
servidor, piso decidido antes do primeiro boot.
