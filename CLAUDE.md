# Instruções para o Claude

## Idioma
- **Responda sempre em português do Brasil (pt-BR)**, em todas as mensagens ao usuário, inclusive relatórios,
  resumos e perguntas. Isso vale para qualquer sessão neste repositório.
- Código, identificadores e mensagens de commit continuam em inglês (padrão do projeto); comentários seguem o
  estilo do código ao redor.

## Projeto
- VitaGenesis: backport da geração de mundo da 1.18+ para Minecraft 1.7.10 / Forge 10.13.4.1614, para o modpack
  TEOF 4.1 (servidor Crucible). Primeira parte: a camada profunda do Below Core (Y < 0) inteira (preenchimento,
  piso, minérios e cavernas). O Below Core é só core/API; o conteúdo abaixo de 0 é do VitaGenesis
  (`docs/BELOWCORE-HANDOFF.md`).
- Depende do Below Core (API `com.akashiic.bellow.api`, versão ≥ 4) e usa os blocos do Et Futurum Requiem.
  Só servidor: não adiciona blocos, itens nem pacotes.
- Regra de ouro: **nada em Y ≥ 0 é alterado** (o mundo acima de 0 já está pré-gerado). Toda escrita passa pela API
  do Below Core, que só aceita `minY..-1`.
- Arquitetura e decisões em `docs/DESIGN.md`; uso em `README.md`.
- Prioridade: qualidade técnica e segurança acima de velocidade; testar com evidência antes de afirmar.
- Limitações do Below Core ou da API: pare e reporte ao usuário (ele corrige o Below Core); não contorne com
  mixins no Below Core.

## Build e testes
- Precisa de um checkout do Below Core compilado ao lado (`../BelowCore`, `./gradlew build` lá) ou
  `-Pbellow.dir=<caminho>`. O código compila contra o `-api.jar` do Below Core: só a API pública é visível.
- `VERSION=0.1.0-beta.4 ./gradlew build` (não há tag git); `./gradlew test` roda os testes unitários.
- `tools/selftest.sh [piso] ` (servidor dedicado, 3 fases: bootstrap → full → reload), por exemplo
  `tools/selftest.sh -64` e `tools/selftest.sh -128`.
