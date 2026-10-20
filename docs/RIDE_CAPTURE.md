# Captura de ofertas e corridas (Uber / 99)

> Princípio: nenhum dado capturado entra nas estatísticas sem origem rastreável.
> É preferível "não sei" a um número errado, e um dado pendente **nunca** é descartado em silêncio.

## 1. Canais de captura

| Canal | Uso | Confiança máxima |
|---|---|---|
| **AccessibilityService** (principal) | Lê a árvore de nós das janelas do Uber Driver e do 99 Motorista | HIGH |
| **NotificationListener** (reserva) | Texto de notificações; só vira oferta se a Acessibilidade estiver desligada, para não duplicar | MEDIUM |
| OCR | **Não implementado.** A árvore de acessibilidade já traz o texto estruturado. Fica como último recurso se algum app passar a desenhar a oferta como imagem | n/a |

O serviço declara `packageNames="com.ubercab.driver,com.app99.driver"`: o sistema **não nos acorda** para nenhum outro app. Nenhum clique é executado; aceitar ou recusar é sempre decisão do motorista.

## 2. Pipeline

1. **Agrupamento de eventos** (`MeuGanhoAccessibilityService`): o primeiro evento agenda uma leitura para 250 ms depois, e os demais eventos da janela são absorvidos. Uma contagem regressiva que gera 30 eventos/s resulta em ~4 leituras/s.
2. **Leitura** (`NodeTreeReader`): travessia iterativa, no máximo 400 nós e profundidade 30, **só nós visíveis ao usuário** (cartões antigos escondidos na árvore gerariam "ofertas fantasmas"). Coleta `text`/`contentDescription` e o `viewId`.
3. **Snapshot** (`ScreenSnapshot`): linhas de texto + `versionCode` do app de origem.
4. **Fila** (`CaptureCoordinator`): capacidade 16 e `DROP_OLDEST`. Telas idênticas em menos de 5 s são ignoradas pelo hash do conteúdo.
5. **Classificação** (`KeywordPlatformParser.classify`), em ordem de prioridade: `OFFER` (R$ + "Aceitar") > `TRIP_COMPLETED` > `TRIP_CANCELLED` > `IN_TRIP` > `WAITING_PASSENGER` > `TO_PICKUP` > `DAILY_EARNINGS` > `IDLE` > `UNKNOWN`.
6. **Oferta**: parse → deduplicação → avaliação (confiança + validação) → status → Room → overlay → máquina de estados.
7. **Demais telas**: viram `TripSignal` para a [máquina de estados](TRIP_STATE_MACHINE.md).

## 3. Modelo normalizado

Uber e 99 produzem o mesmo `RideOffer`. O motor financeiro nunca sabe de qual app veio o texto.

```kotlin
data class Captured<T>(val value: T?, val source: FieldSource, val confidence: Confidence, val reasons: List<String>)

RideOffer(platform, detectedAt,
          offeredCents, pickupMeters, pickupSeconds, tripMeters, tripSeconds: Captured<…>,
          surgeCents, category, parserVersion, sourceAppVersion, channel, warnings)
```

`FieldSource`, do sinal mais forte para o mais fraco: `NODE_ID` (id da view) > `TEXT_ANCHOR` (linha com palavra-âncora) > `REGEX` (número solto) > `NOTIFICATION` > `MISSING`.

## 4. Extração por âncoras (`AnchoredOfferExtractor`)

**Preço.** Nunca é "o primeiro R$ da tela".
1. Linha com id de view de preço (`fare`, `price`…) → HIGH.
2. Faixa "R$ 18,00 - R$ 22,00" → menor valor, **LOW** (vai para revisão).
3. Exatamente um valor isolado numa linha → HIGH.
4. Vários valores isolados (ex.: cartão anterior ainda na árvore) → **LOW**.
5. Um único valor dentro de frase → MEDIUM; vários → LOW.
- Linhas de promoção/desafio/meta (`ganhe`, `desafio`, `missão`…) são excluídas antes da busca.
- `+R$ 6,50` é tratado como dinâmica (`surgeCents`), nunca como preço.

**Busca e viagem.**
- Linha-âncora ("de distância", "Embarque", "Viagem de", ou id de view `pickup`/`trip_eta`) com tempo **e** distância → HIGH.
- Valores nas linhas vizinhas → MEDIUM.
- Sem âncora, dois pares tempo/distância na tela → ordem busca→viagem com **LOW**.
- Nada encontrado → `MISSING` (LOW).
- Botões ("Aceitar viagem") contêm palavras de âncora e são ignorados como âncora.

## 4.1 Layout real do Uber (calibrado com prints de 28/09/2026)

```
UberX            Exclusivo
R$ 35,93                         ← preço isolado (HIGH)
R$1,58/km aprox.                 ← R$/km do PRÓPRIO Uber sobre o km total
7 min (2.8 km)                   ← busca (1º par do cartão, sem palavra-âncora)
‹endereço›
43 minutos (20.0 km)             ← viagem (2º par)
‹endereço›
Aceitar | Selecionar             ← "Selecionar" = cartão do Radar de Viagens
```

**Prova cruzada (`PlatformRateCheck`):** se valor ÷ (km busca + km viagem) bate com o "R$x/km aprox." (tolerância de 3%), o preço sobe para HIGH e as distâncias e tempos para pelo menos MEDIUM. A soma não diz qual trecho é qual, por isso não sobem para HIGH. Se não bate, alerta → revisão. Nosso R$/km usa o mesmo arredondamento do Uber.

**Várias ofertas na tela (`OfferBlocks`, Radar de Viagens):** os cartões são separados pela estrutura (cada cartão é um bloco clicável, registrado em `ScreenLine.group`). Se não houver estrutura, a separação é pelo texto (uma fatia por preço isolado, com o preço limitado a MEDIUM). Cada cartão vira uma oferta, e o painel mostra até 4. Com várias na tela não dá para saber qual será aceita, então a corrida nasce sem oferta vinculada e o valor vem da tela final.

**App em segundo plano:** são lidas **todas** as janelas do pacote (a oferta costuma ser uma janela flutuante sobre outro app). Se a tela não mostrou oferta nos últimos 10 s, a **notificação** vira fonte (máx. MEDIUM), e a leitura da tela que vier depois **substitui** esse registro em vez de duplicar.

## 5. Confiança e status (a regra central)

Dois eixos **independentes**:

| Eixo | Pergunta | Onde |
|---|---|---|
| Confiança do parser | "Li o campo certo?" | `Captured.confidence` por campo |
| Validação | "O valor faz sentido?" | `OfferValidator` |

**A confiança da oferta é o MÍNIMO dos campos críticos** (valor, km/tempo de busca, km/tempo de viagem). A média é proibida: se o valor tem confiança LOW e o resto HIGH, a média pareceria boa e esconderia justamente o campo mais importante.

**Validação em camadas** (`OfferValidator`):

| Camada | Regras | Severidade |
|---|---|---|
| Absoluta | valor ≤ 0; campo negativo | HARD → INVALID |
| Absoluta | valor < R$ 3 ou > R$ 500; viagem > 150 km; busca > 25 km; tempo/distância zero | SOFT |
| Coerência | velocidade implícita (km ÷ tempo) fora de 4–100 km/h, para trechos ≥ 1 km | SOFT |
| Taxa | R$/km fora de R$ 0,50–R$ 10,00 | SOFT |
| Pessoal | R$/km fora dos percentis 1–99 do **seu** histórico (≥ 30 ofertas confiáveis) | SOFT |
| Parser | alertas do extrator (faixa de preço, promoção com preço incerto, busca/viagem pela ordem) | SOFT |

**Status** (`StatusResolver`):

| Status | Quando | Entra nas estatísticas? |
|---|---|---|
| `INVALID` | Qualquer problema HARD | Não (fica visível no Diagnóstico/Ofertas) |
| `NEEDS_REVIEW` | Campo crítico LOW **ou** qualquer problema SOFT | **Separado:** "R$ 184 + R$ 28,50 pendente" |
| `AUTO_ACCEPTED` | Tudo HIGH/MEDIUM e nenhuma validação falhou | Sim |
| `CONFIRMED` | O usuário confirmou ou corrigiu na tela Revisar | Sim |
| `DUPLICATE` | Mesma impressão digital em 90 s | Não é gravado |

Pendente **não some**. Esconder um item em revisão distorceria o dia para baixo, que é outra forma de contaminar as estatísticas. Dashboard, Histórico, fechamento diário e overlay mostram o pendente à parte.

## 6. Overlay honesto

Se a oferta não é `AUTO_ACCEPTED` ou o parser está em pane, o overlay mostra os números com **"?"**, em âmbar, e **nenhum veredito** (EXCELENTE/RUIM). Um veredito sobre dado incerto é pior do que nenhum, porque é exatamente a decisão que o motorista toma em 15 segundos.

## 7. Valor da corrida e reconciliação

Fontes, da mais fraca para a mais forte: estimativa da oferta → tela de corrida concluída → correção do usuário. Nada é sobrescrito: `estimatedCents`, `finalCents` e `correctedCents` convivem, e o valor efetivo é o mais forte disponível.

`TripValuePolicy`:
- valor final entre 50% e 250% da oferta → `AUTO_ACCEPTED`; fora disso → `NEEDS_REVIEW` (um dos dois lados foi lido errado);
- sem tela final e oferta confiável → usa a estimativa (`AUTO_ACCEPTED`), e a reconciliação pega a diferença;
- sem tela final e oferta duvidosa, ou sem valor nenhum → `NEEDS_REVIEW`.

**Reconciliação diária (checksum):** quando uma tela "Ganhos de hoje" é lida, o total da plataforma é gravado em `platform_totals`. `Reconciliation` compara com a soma das corridas capturadas (tolerância R$ 1,00):
- ✓ bate com o app;
- ⚠ diferença de R$ X: corrida perdida, gorjeta, dinâmica somada depois ou valor lido errado;
- sem leitura do app: indefinido. **Nunca** mostramos "ok" sem evidência.

## 8. Circuit breaker (`ParserHealthMonitor`)

Janela das últimas 20 ofertas por plataforma. Com ≥ 10 amostras e mais de 30% `NEEDS_REVIEW`/`INVALID`, o circuito abre:
- o overlay mostra "Análise indisponível: app mudou?";
- o Diagnóstico mostra a taxa e o `versionCode` do app de origem;
- a captura continua medindo e o circuito fecha sozinho quando a taxa normaliza (ou manualmente em "Reativar análise").

Uma atualização do Uber, portanto, não contamina uma semana de dados sem ninguém perceber.

## 9. Privacidade e evidência

- `SnapshotSanitizer` mascara **palavra por palavra** com lista de permissão: mantém só palavras-chave das plataformas, números, valores e unidades. "Buscando Maria" vira "Buscando ‹texto›"; "Rua das Flores, 123" vira "‹texto› 123".
- Snapshots sanitizados são guardados **só** em ofertas `NEEDS_REVIEW`/`INVALID`, apagados após a retenção (7 dias por padrão) ou ao revisar, e excluídos do backup.
- Cada captura registra `parserVersion` e `sourceAppVersion`, o que liga uma quebra a uma atualização específica do app.
- Logs nunca contêm texto bruto de tela.

## 10. Calibração com telas reais (fluxo de trabalho)

Desde a 0.1.1 a calibração é **automática**:

1. O app grava amostras (`SamplingPolicy`): toda oferta nova (máx. 1 a cada 15 s), ofertas não lidas e telas desconhecidas inéditas (máx. 1/min), e as demais telas 1 a cada 10 min por tipo. O texto é sanitizado **antes** de tocar o disco.
2. `SampleUploader` envia em lotes para `innexar.com.br/meuganho/api/samples`: ofertas e telas desconhecidas em até ~1 min, as demais com 5 na fila ou após 2 min. Sem rede, as amostras esperam (fila máx. 1000). Pode ser desligado em Configurações > Calibração.
3. No servidor: `scripts/samples.py` (resumo), `samples.py list OFFER`, `samples.py show N` e `samples.py fixture N nome`, que transforma uma amostra em fixture de teste.
4. Ajustar `UberPatterns.kt`/`NinetyNinePatterns.kt` até o teste da fixture nova passar.
5. Subir a versão e rodar `scripts/publish.sh "notas"`. O app se atualiza sozinho (`UpdateManager`), e **o site não é rebuildado**.

Manual (sem internet): Diagnóstico > Calibração > Compartilhar, ou "Gravar tela manualmente".
4. Ajustar `UberPatterns.kt` até o teste passar, sem quebrar as fixtures existentes.
5. Se o layout mudou radicalmente: criar `UberParserV2` (mantendo a V1 e as fixtures dela) e trocar em `ParserRegistry`.

## 11. Limitações conhecidas

- Os nomes de pacote (`com.ubercab.driver`, `com.app99.driver`) devem ser confirmados no aparelho. Se a 99 usar outro, ajuste `Platform.kt`, `accessibility_service_config.xml` e `<queries>`.
- Aceitar a oferta é inferido pela transição OFFERED → TO_PICKUP. Sem ver a tela de busca, a oferta vira "não aceita" após 25 s.
- Destino/região não são capturados na V1 (privacidade e baixa confiabilidade).

## Robustez (0.6.0)

- **Fila de 128 telas** com contagem de descarte (Diagnóstico → "Telas descartadas"). Nunca deveria passar de zero.
- **Taxa de acerto real da 99**: cada toque de oferta (fora de corrida, com a 99 na frente) espera uma oferta lida em até 8 s.
  Sem oferta = "perdida", e a última imagem lida vai como amostra `OFFER_MISSED` para calibração.
- **Vigia da 99 a cada 0,7 s** com turno aberto (1,5 s sem turno); OCR só quando a imagem muda.
- **Nova tentativa**: oferta lida pela metade por imagem pede outra captura em 350 ms (máx. 2 em 5 s).
- **Conciliador** (`engine/TripReconciler`, `domain/trip/OfferMatcher`): liga corridas sem oferta à oferta certa
  (mesmo valor oficial até 60 min antes; ao vivo, a última oferta até 3 min antes, exceto Radar ambíguo) e
  corrige "canceladas" com ≥ 1 km de GPS com passageiro. Roda na abertura, a cada 10 min de turno e após cada leitura oficial.
- **Regras remotas** (`capture/core/RemoteRules`, servidor `/meuganho/rules.json`): palavras-chave extras por tela,
  só acrescentam, cache em disco. Corrige leitura sem versão nova do app.
- **Simulador** (`src/test/.../sim`): pipeline real + Room em memória + telas reais com relógio controlado.
  Cenários de Uber e 99 simultâneos. `scripts/placar.py` mede a precisão real por dia.

## Simulação de turnos no servidor (0.6.1)

`scripts/simular.sh [turnos]` gera turnos completos (`sim/ShiftGenerator`: Uber e 99 juntos, Radar, rajadas,
cancelamentos, dinheiro, telas perdidas, ruído de OCR real), passa pelo pipeline real (`sim/Simulator`) e compara
com o gabarito (`sim/Scorer`). Relatório em https://innexar.com.br/meuganho/sim/ (histórico em `sim/history.json`).
`scripts/publish.sh` roda 20 turnos como trava: abaixo dos pisos de `ShiftSimulationTest`, nada é publicado.
Sem emulador: o servidor não tem KVM. A simulação cobre tudo DEPOIS da leitura da tela (texto → parser →
corridas → valores); a leitura em si (Acessibilidade/OCR no aparelho) se mede com o placar da 99 no Diagnóstico.
