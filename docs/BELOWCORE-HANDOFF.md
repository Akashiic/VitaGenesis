# Below Core só como core: o que sai e o que fica

**Status:** feito no Below Core 0.1.0-beta.4 (API 4, commit `df2c840`), conferido pelo VitaGenesis 0.1.0-beta.4
(`docs/AUDIT.md` §6). A lista abaixo fica como registro.

A partir do VitaGenesis 0.1.0-beta.3, o VitaGenesis gera **todo** o conteúdo da camada profunda: o bloco de
preenchimento, o piso de bedrock da 1.18, os minérios da 1.18 (com tufo e cascalho) e os minérios de outros mods nas
variantes de deepslate do Et Futurum, além das cavernas e dos recursos que ele já gerava. O Below Core passa a ser só o
core e a API.

O VitaGenesis funciona nos dois regimes, testado com o Below Core atual:
- **antes da remoção**: o Below Core ainda enche a camada e o VitaGenesis a reescreve igual. Os minérios do Below Core
  têm de estar desligados (`worldgen.ores=false` em `config/akashicbellow.cfg`), senão saem em dobro;
- **depois da remoção**: o Below Core entrega as seções vazias e o VitaGenesis as preenche. O self-test cobre esse
  caso: esvazia a camada de um chunk e confere que o VitaGenesis a refaz, mantendo os blocos postos por jogadores.

## Pode sair do Below Core (conteúdo)

- [ ] `gen/DeepTerrain.fill`: o bloco de preenchimento e o piso de bedrock. O estágio de terreno continua criando
  **todas** as seções de `minY..-1`, vazias, antes do `generateTerrain` (o contrato da API 3 já promete isso).
- [ ] `gen/ore/DeepFeatures` (a lista de minérios) e o `placeFeatures` do `DeepDecorator`.
- [ ] Configs `worldgen.deepBlock`, `worldgen.ores`, `worldgen.scaleBottomAnchoredCounts` e
  `worldgen.oreCountMultiplier`. O VitaGenesis tem as dele, na categoria `layer`.
- [ ] Na API: `getFillBlock`, `getFillMeta`, `isDeepFill` e `isBaseStone` (o VitaGenesis não usa mais nenhum deles).

## Precisa ficar no Below Core (core)

- [ ] **Primitivas de veio**: `IDeepRegion.placeOre` e `placeBlob`, o `OreFeature`, e a consulta ao
  `DeepslateOreRegistry` do Et Futurum (`deepVariant`). O VitaGenesis coloca todos os minérios por elas.
- [ ] **Hospedeiros dos veios**: `isDeepHost`/`isStoneHost` hoje dependem do `deepBlock` do Below Core. Sem ele, a regra
  fica fixa: deepslate do Et Futurum e tufo viram a variante de deepslate, pedra vira o minério comum.
- [ ] **Troca da bedrock antiga em Y 0..4** (`replaceLegacyBedrock`). É a única escrita em Y ≥ 0 e abre a passagem entre
  o mundo antigo e a camada nova. Tem de ficar no Below Core para a regra de ouro do VitaGenesis (nunca escrever em
  Y ≥ 0) continuar absoluta. Sem `deepBlock`, ela usa deepslate do Et Futurum ou pedra.
- [ ] **Segurança sem gerador** (recomendação): numa dimensão com camada profunda e **nenhum** gerador registrado
  (VitaGenesis removido ou desligado), um fallback mínimo de pedra e piso de bedrock, sem minérios. Sem isso, os chunks
  novos ficariam com o vazio abaixo de Y 0, ao lado de chunks antigos com camada, e quem cavasse cairia até a morte.
  Alternativa: recusar a camada nessa dimensão, com aviso no log.
- [ ] Todo o resto: armazenamento, trava do piso, ciclo de geração, registros por gerador, etapa de luz, fila depois
  do tick, região segura, modo estrito, guarda da geração legada, pacotes, cliente e compat.
- [ ] Os testes do próprio Below Core (self-test, sondas de compat, cliente) precisam do fallback acima ou de um
  gerador de teste.

## O que o VitaGenesis usa da API (para não quebrar)

`registerGenerator`, `apiVersion`, `getMinY`, `getDeepSection`, `isDecorated`, `isLightPending`, `getGeneratorIds`,
`getGeneratorStages`, `getGeneratorData`, `runDeepStages`, `DeepConstants.STAGE_ON_LIT`/`GEN_NONE`, e em
`IDeepRegion`: `world`, `seed`, `minY`, `centerChunkX/Z`, `chunkAt`, `getBlock`, `getMeta`, `getTileEntity`,
`setBlock`, `placeOre`, `placeBlob`; em `IDeepGenerator`: `generateTerrain`, `decorate`, `onLit`, `version`.
