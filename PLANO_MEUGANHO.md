# Plano do MeuGanho

Assistente financeiro Android para motoristas de aplicativo: analisa ofertas antes da corrida, acompanha tempo, km e estado durante a corrida, calcula o ganho real depois e consolida faturamento, custos, metas e lucro do dia.

Este documento é o ponto de entrada. Cada seção resume a decisão e aponta para o documento detalhado em `docs/`.

---

## Estado atual

- **Não existia projeto anterior.** Não havia nenhum app "Cálculo de Ganho" em `/srv/innexar/development`, e o MeuGanho foi criado do zero em `meuganho/`.
- **Ambiente:** JDK 17, Android SDK 36 (build-tools 35/36), Gradle 9.3.1 em cache, acesso aos repositórios Maven.
- **Entregue nesta etapa** (fases M1–M8, "base entregue"):

| Verificação | Resultado |
|---|---|
| `assembleDebug` / `assembleRelease` (R8) | ✅ APK release de **1,7 MB** |
| `testDebugUnitTest` | ✅ **62 testes, 0 falhas** |
| `lintDebug` | ✅ 0 erros (2 avisos informativos) |
| Arquivos Kotlin | 140 de produção (maior: 182 linhas) + 12 de teste + 18 fixtures de tela |

- **Ainda não validado:** execução em aparelho com Uber/99 reais. Os padrões de texto V1 dos parsers precisam ser calibrados com telas reais (fase M9, ver [Riscos](#riscos-técnicos)).

## Código reutilizável

Nada foi reaproveitado, porque não havia base. Dentro do MeuGanho, os pontos pensados para reuso são:
- `KeywordPlatformParser` + `AnchoredOfferExtractor`: uma nova plataforma é só um objeto de padrões e uma classe de poucas linhas.
- O domínio (`financial`, `validation`, `trip`, `stats`) é Kotlin puro, reutilizável em qualquer módulo JVM.
- Design System (`core/designsystem`), usado por todas as telas e pelo overlay (via `Palette`).

## Problemas encontrados

Problemas de desenho identificados e resolvidos durante a construção:

| Problema | Solução |
|---|---|
| "Primeiro R$ da tela" pega promoção, dinâmica ou cartão antigo | Extração por âncoras, exclusão de linhas de promoção, `+R$` tratado como dinâmica, vários preços → LOW |
| Botão "Aceitar viagem" virava âncora de viagem | Linhas com palavra de aceite são excluídas como âncora |
| Snapshot guardava nome do passageiro ("Buscando Maria") | Sanitização palavra a palavra com lista de permissão |
| Aviso de promoção mandava para revisão até ofertas com preço seguro | Aviso só quando o preço não é HIGH |
| Últimos metros de uma corrida iam para "vazio" quando ela terminava | Acumulador GPS indexado por (segmento + id da corrida) |
| Uber pula o resumo em corridas encadeadas | Transição explícita IN_TRIP + busca com oferta na fila |
| AAPT exige `-v26` para ícone adaptativo mesmo com minSdk 26 | Pasta mantida, aviso do lint suprimido e documentado |

## Stack recomendada

**Kotlin 2.1 + Jetpack Compose (Material 3 como base) + Room + DataStore + Coroutines/Flow**, módulo único, injeção manual, `LocationManager` nativo, overlay em Views nativas, sem rede.

## Justificativa técnica

Resumo (detalhes em [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)):
- AccessibilityService, NotificationListener, overlay e serviço em primeiro plano são APIs **nativas**. Em Flutter ou React Native elas seriam escritas em Kotlin de qualquer forma, com uma ponte e um runtime extra (engine Dart ou JS) ocupando RAM o dia inteiro.
- APK de 1,7 MB e nenhum runtime além do ART.
- Room e DataStore são reativos (Flow), o que viabiliza o "zero polling".
- Hilt, Play Services e bibliotecas de gráfico/log foram deixados de fora: não pagam o custo neste tamanho de app.

## Arquitetura

Camadas por pacote, com regras de dependência explícitas: `domain` não conhece Android e `capture` recebe texto, não nós de acessibilidade. Fluxo:

```
Uber/99 → AccessibilityService → ScreenSnapshot → CaptureCoordinator
  → Parser (classify/parse) → OfferAssessment (confiança + validação) → status
  → Room → Flow → StatsRepository → Dashboard / Overlay
  → TripSignal → TripStateMachine (pura) → TripTracker → Room
```

## Árvore completa de diretórios proposta

A árvore implementada, com a responsabilidade de cada pasta, está em [docs/FOLDER_STRUCTURE.md](docs/FOLDER_STRUCTURE.md). Raiz:

```
meuganho/
├── PLANO_MEUGANHO.md  README.md  docs/
├── build.gradle.kts  settings.gradle.kts  gradle/libs.versions.toml
└── app/
    ├── schemas/                      schema Room exportado (v1)
    ├── lint.xml  proguard-rules.pro
    └── src/
        ├── main/java/com/innexar/meuganho/
        │   ├── app/          composição, Activity, navegação
        │   ├── core/         money, time, logging, ui, designsystem
        │   ├── domain/       model, financial, validation, trip, stats
        │   ├── capture/      core, uber, ninetynine
        │   ├── engine/       coordenador de captura, gravador, rastreador de corrida
        │   ├── data/         local (entity, dao), preferences, repository, mapper, backup
        │   ├── platform/     accessibility, overlay, location, foreground, notifications, permissions
        │   └── features/     15 telas (Screen + ViewModel)
        ├── main/res/         ícones vetoriais, temas, config da Acessibilidade
        └── test/             testes + fixtures/uber, fixtures/ninetynine
```

## Responsabilidade de cada módulo

| Pacote | Responsabilidade | Nº de arquivos |
|---|---|---|
| `app` | Container de dependências, Application, Activity, navegação | 5 |
| `core` | Dinheiro, unidades, datas, logs, tokens e componentes visuais | 19 |
| `domain` | Regras financeiras, validação, máquina de estados, estatísticas | 28 |
| `capture` | Snapshot → dados; padrões por plataforma; dedup, saúde, sanitização | 15 |
| `engine` | Orquestra captura, domínio e banco | 6 |
| `data` | Room, DataStore, repositórios de composição, backup | 22 |
| `platform` | Serviços e integrações Android | 11 |
| `features` | Telas e ViewModels | 34 |

## Confiabilidade dos dados capturados

Este é o centro do projeto, desenvolvido a partir da ideia original de `confidenceScore` + `NEEDS_REVIEW`. Detalhes completos em [docs/RIDE_CAPTURE.md](docs/RIDE_CAPTURE.md).

1. **Confiança por campo, com origem e motivo** (`Captured<T>`): em níveis discretos (HIGH/MEDIUM/LOW), não um float sem explicação.
2. **Confiança da oferta = mínimo dos campos críticos**, nunca a média.
3. **Dois eixos independentes:** confiança do parser ("li o campo certo?") e validação ("faz sentido?"). A validação é em camadas: absoluta, coerência (velocidade implícita), taxa e **percentis do histórico pessoal**.
4. **Status com regra de uso explícita:** só `CONFIRMED`/`AUTO_ACCEPTED` entram nas estatísticas, e `NEEDS_REVIEW` aparece **separado como pendente** em todos os totais. Nunca é descartado em silêncio.
5. **Reconciliação diária** com o "Ganhos de hoje" da própria plataforma, como checksum do dia.
6. **Overlay honesto:** sem veredito sobre dado incerto ("? INCERTO").
7. **Revisão fora do volante**, com um toque para confirmar, corrigir ou descartar. A correção **não sobrescreve** o valor capturado.
8. **Evidência e reprocessamento:** `parserVersion` + `versionCode` do app em cada captura e snapshot sanitizado para itens duvidosos, com retenção limitada.
9. **Circuit breaker:** muitas capturas duvidosas desligam a análise daquela plataforma e alertam no Diagnóstico.
10. **Dinheiro em centavos (`Long`)** em todo o app.

## Modelo de dados

Oito tabelas Room (`vehicles`, `shifts`, `offers`, `trips`, `fuel_logs`, `expenses`, `goals`, `platform_totals`) + DataStore para configurações. Os resumos diários são calculados, não armazenados. Migrations são explícitas e o destrutivo é proibido. Detalhes: [docs/DATA_MODEL.md](docs/DATA_MODEL.md).

## Fluxo de captura Uber

`com.ubercab.driver` → leitura agrupada (250 ms) → `UberParserV1` (padrões em `UberPatterns.kt`):
- **Oferta:** preço isolado ou `fare*`; "X min (Y km) de distância" = busca; "Viagem de X min (Y km)" = viagem; `+R$` = dinâmica; categorias UberX/Comfort/Black/…
- **Ciclo:** "Buscando" → "Aguardando"/"Iniciar viagem" → "Finalizar viagem"/"Deixando" → "Viagem concluída" (valor final) · "Viagem cancelada".
- **Ganhos de hoje** → total para reconciliação.
- Corridas encadeadas (próxima oferta durante a corrida) são tratadas pela fila da máquina de estados.

## Fluxo de captura 99

`com.app99.driver` → `NinetyNineParserV1` (padrões em `NinetyNinePatterns.kt`):
- **Oferta:** "Embarque: X min · Y km" = busca; "Corrida de X min · Y km" = viagem; **faixa de preço** (R$ 18–22) → LOW → revisão; categorias 99Pop/Comfort/Táxi/Negocia/…
- **Ciclo:** "Cheguei"/"a caminho do embarque" → "Iniciar corrida" → "Finalizar corrida" → "Corrida finalizada"/"Você recebeu R$ X".
- Nenhuma regra da 99 fica no código do Uber, e vice-versa.

## Máquina de estados

`IDLE → OFFERED → TO_PICKUP → WAITING_PASSENGER → IN_TRIP → COMPLETED`, mais `CANCELLED`, `REJECTED` e `UNKNOWN`. É uma função pura com todas as transições em um arquivo, só avança, trata piscadas de tela (25 s), fila de ofertas, corridas encadeadas, outras plataformas durante uma corrida, abandono após 3 h e restauração após o processo ser morto. Detalhes: [docs/TRIP_STATE_MACHINE.md](docs/TRIP_STATE_MACHINE.md).

## Motor financeiro

R$/km e R$/h sobre o km e o tempo **totais** (a busca incluída), combustível com consumo observado > cadastrado > padrão, custos fixos por km, e três visões separadas (bruto, caixa, resultado operacional **estimado**). Tempos separados (online, busca, espera, corrida, ocioso), metas com previsão de tempo, estimativa x realidade. Detalhes: [docs/FINANCIAL_ENGINE.md](docs/FINANCIAL_ENGINE.md).

## Estratégia GPS

- Só com turno aberto, não pausado e GPS habilitado; serviço em primeiro plano do tipo `location`.
- `LocationManager` (provedor *fused* no Android 12+), 5 s / 15 m.
- Filtros: precisão > 35 m, saltos > 200 km/h e tremor < 8 m são descartados.
- **Nenhum ponto é armazenado:** metros agregados por segmento (vazio/busca/pago) e por corrida, gravados a cada ~300 m.
- O Detalhe do dia compara GPS com odômetro; o odômetro tem prioridade quando completo.

## Estratégia de performance

Orientada a eventos: filtro de pacotes no sistema, agrupamento de eventos, leitura limitada fora da main thread, fila limitada, hash de telas repetidas, Flows do Room em vez de consultas periódicas, gravações agregadas, buffers com teto e overlay em Views nativas. Detalhes: [docs/PERFORMANCE.md](docs/PERFORMANCE.md).

## Estratégia de bateria

Sem turno não há GPS nem serviço. Com turno, o GPS respeita a distância mínima (parado ≈ silêncio), a notificação usa o cronômetro do sistema (zero atualizações), não há wakelocks, alarmes ou rede, e os únicos temporizadores rodam enquanto há alguém olhando. A tela de Permissões orienta a liberar a otimização de bateria (fabricantes agressivos).

## Design System

"Painel de instrumentos à noite": escuro por padrão, menta para dinheiro, números tabulares, toque mínimo de 56 dp e símbolo + texto em todo status. Tokens em `core/designsystem/theme` e 15 componentes. Ícone: mostrador com ponteiro subindo (adaptativo, monocromático, splash, barra de status), 100% vetorial. Detalhes: [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md).

## Telas

| Tela | Rota | Destaques |
|---|---|---|
| Splash | tema nativo | fica até as configurações carregarem |
| Onboarding / Permissões | 1º uso · `permissions` | explica o porquê de cada permissão; todas opcionais |
| Dashboard (Hoje) | `dashboard` | faturamento + pendente, resultado, caixa, 6 métricas, meta, Uber x 99, conferência com o app |
| Iniciar turno / Turno ativo | `shift` | plataformas, odômetro, preço; tempo online, estado ao vivo, pausa, + corrida, + despesa, encerrar |
| Oferta detectada | overlay | R$/km, R$/h, classificação ou "? INCERTO" |
| Ofertas recebidas | `offers` | aceitas e não aceitas, análise, status e motivos |
| Revisar | `review` | confirmar / corrigir / descartar; tela capturada sanitizada |
| Registrar corrida | `manual_trip` | fallback manual (CONFIRMED) |
| Histórico | `history` | hoje, ontem, semana, mês, 30 dias, período personalizado |
| Detalhe do dia (fechamento) | `day/{epochDay}` | resumo completo, odômetro x GPS, reconciliação, prometido x real por corrida, compartilhar |
| Abastecimentos / Novo | `fuel`, `fuel/new` | consumo real; preencher 2 de 3 campos |
| Despesas | `expenses` | por categoria no mês |
| Veículo | `vehicle` | consumo, FLEX, manutenção e depreciação por km |
| Metas | `goals` | diária/semanal/mensal, bruta/líquida |
| Análises | `analytics` | R$/h por hora e dia, Uber x 99, oferta x realidade |
| Configurações | `settings` | limites, custos, overlay, GPS, retenção, backup |
| Diagnóstico | `diagnostics` | serviços, eventos por plataforma, saúde do parser, gravar tela, log |

## Permissões Android necessárias

| Permissão | Para quê | Obrigatória? |
|---|---|---|
| Serviço de Acessibilidade | Ler ofertas e telas de corrida do Uber/99 (só esses pacotes) | Para captura automática |
| `ACCESS_FINE/COARSE_LOCATION` | km do turno | Opcional |
| `FOREGROUND_SERVICE` + `_LOCATION` | GPS durante o turno | Com localização |
| `POST_NOTIFICATIONS` | Notificação do turno (Android 13+) | Recomendada |
| Leitor de notificações | Fonte reserva e diagnóstico | Opcional |
| Otimização de bateria desligada | Evitar que o sistema mate o turno | Recomendada |
| ~~`SYSTEM_ALERT_WINDOW`~~ | **Não é necessária:** o overlay é janela do serviço de Acessibilidade | — |
| ~~`INTERNET`~~ | **Não declarada:** o app é 100% offline | — |

Sem nenhuma permissão, o app funciona com registro manual de corridas.

## Riscos técnicos

| Risco | Impacto | Mitigação |
|---|---|---|
| **Padrões V1 não batem com as telas reais** | Muitas ofertas em revisão; overlay "incerto" | Circuit breaker, gravação de tela no Diagnóstico, fixtures + testes, parsers versionados. **Calibração é a 1ª tarefa do M9** |
| Uber/99 mudam o layout | Parser quebra | Idem; `versionCode` registrado em cada captura liga a quebra à atualização |
| Nome de pacote da 99 diferente | Nenhum evento da 99 | Diagnóstico mostra "último evento: nunca"; ajustar em 3 lugares documentados |
| Fabricantes matam serviços em segundo plano | Km do turno incompleto | FGS, orientação de bateria, religar GPS ao abrir o app, odômetro como referência |
| Política da Play Store para Acessibilidade | Publicação | Uso pessoal (sideload); `isAccessibilityTool=false` e descrição honesta prontos para uma eventual declaração |
| Aceite inferido (não há evento "aceitou") | Oferta marcada errada | Janela de 25 s, sinais de busca/espera/corrida, tudo testado |
| Reinício do FGS de localização em segundo plano (Android 14+) | GPS não volta sozinho | Religar ao abrir o app; botão "Ativar GPS do turno" |

## Testes

62 testes unitários JVM (≈ 8 s):
- **Financeiro:** exemplo da especificação (R$ 2,02/km, R$ 61,50/h), dois critérios de classificação, custo operacional, consumo tanque-cheio, metas (1h52).
- **Validação:** negativo → INVALID, tempo zero, distância absurda, velocidade impossível, confiança baixa, faixa pessoal, política de valor final.
- **Máquina de estados:** ciclo completo, piscada, expiração, substituição, corrida encadeada, outra plataforma, cancelamento, valor tardio, abandono, não retroceder.
- **Parsers (regressão com 18 fixtures):** oferta padrão, promoção, dinâmica, ids de view, dois preços, números soltos, vírgula perdida (R$ 2850), ciclo completo, valor final, ganhos do dia; 99 Pop, faixa de preço.
- **Infra:** sanitização (nome e endereço removidos), deduplicação, circuit breaker, acumulador GPS, formatação BR.

## Roadmap M0–M9

| Fase | Estado |
|---|---|
| M0 Auditoria e planejamento | ✅ |
| M1 Fundação + arquitetura + Design System | ✅ base entregue |
| M2 Banco + veículo + configurações + motor financeiro | ✅ base entregue |
| M3 Turnos + GPS + abastecimentos + despesas | ✅ base entregue |
| M4 Captura Uber/99 | ✅ base entregue · ⚠ calibrar com telas reais |
| M5 Máquina de estados + acompanhamento automático | ✅ base entregue |
| M6 Overlay + dashboard em tempo real | ✅ base entregue |
| M7 Histórico + metas + analytics | ✅ base entregue |
| M8 Backup + diagnóstico + otimização | ✅ base entregue |
| M9 Testes de campo e estabilização | ⏳ próxima fase |

Plano detalhado do M9 em [docs/ROADMAP.md](docs/ROADMAP.md).

## Ordem exata de implementação

A ordem seguida, em que cada passo compila sobre o anterior:

1. Esqueleto Gradle (versões fixadas) e validação da resolução de dependências.
2. `core/money`, `core/time`, `core/logging`.
3. `domain/model`, com o modelo de confiança (`Captured`, `Confidence`, `DataStatus`).
4. `domain/financial` → `domain/validation` → `domain/trip` → `domain/stats`.
5. `capture/core` (snapshot, padrões de texto, extrator, dedup, circuit breaker, sanitização) → Uber → 99.
6. `data` (entidades, DAOs, banco, DataStore, repositórios, backup).
7. `engine` (gravador de ofertas, rastreador de corridas, coordenador).
8. `app/AppContainer` → `platform` (Acessibilidade, overlay, GPS, FGS, notificações, permissões).
9. Design System → navegação → telas (Dashboard, Turno, Revisar, Histórico, …, Diagnóstico).
10. Recursos (manifest, config da Acessibilidade, temas, ícones vetoriais).
11. Fixtures + testes → lint → release com R8.
12. **Próximo:** M9, começando pela calibração dos parsers com telas reais.
