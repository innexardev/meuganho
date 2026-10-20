# Arquitetura do MeuGanho

## Decisão de stack: Kotlin nativo + Jetpack Compose

O MeuGanho roda horas ao lado do Uber Driver, do 99 Motorista e do app de navegação, e depende de APIs profundas do Android. Critérios, em ordem: estabilidade em segundo plano, custo de RAM/CPU/bateria e acesso direto às APIs do sistema.

| Critério | Kotlin + Compose | Flutter | React Native |
|---|---|---|---|
| AccessibilityService, NotificationListener, overlay, FGS | API nativa direta | Exige código nativo em Kotlin + canal | Exige módulo nativo + ponte |
| Runtime extra em memória | Nenhum | Engine Dart/Skia (~20–40 MB) | Hermes + ponte JS |
| Tamanho do APK de release | **1,7 MB** (medido) | ~8–15 MB | ~10–20 MB |
| Serviço em segundo plano sem UI | Processo leve, sem engine | Engine inicia ou o código é duplicado em Kotlin | Mesmo problema |
| Room, DataStore, coroutines, Flow | Nativos | Via plugins | Via plugins |

As partes críticas (serviço de acessibilidade, overlay, GPS) teriam de ser escritas em Kotlin em qualquer stack. Um framework multiplataforma só acrescentaria uma camada e um runtime, sem ganho, num app que é e continuará sendo só Android.

**Versões fixadas** (compatíveis entre si e com o cache local): Kotlin 2.1.20, AGP 8.12, Gradle 9.3.1, Compose BOM 2025.06.01, Room 2.7.2, minSdk 26 (Android 8), targetSdk 36. Atualize em bloco, nunca uma lib isolada.

**Dependências deliberadamente ausentes:**
- **Hilt/Dagger:** a injeção é manual em `AppContainer`. São cerca de 20 objetos, e a geração de código e o custo de inicialização não se pagam.
- **Google Play Services Location:** usamos o `LocationManager` nativo (com o provedor *fused* no Android 12+). Menos RAM e sem dependência do GMS.
- **Retrofit/OkHttp:** não há rede. O app nem declara a permissão `INTERNET`.
- **Bibliotecas de gráfico:** barras simples em Compose resolvem o analytics.
- **Timber:** `AppLog` tem cerca de 50 linhas e alimenta a tela de Diagnóstico.

## Módulo único, camadas por pacote

Usamos um único módulo Gradle (`:app`) com pacotes por camada. Multi-módulo traria build paralelo e fronteiras reforçadas pelo compilador, mas neste tamanho (cerca de 8 mil linhas) custa mais em configuração do que devolve. As regras de dependência abaixo são convenção e são verificáveis por `grep`.

```
features  ──►  data (repository/dao)  ──►  domain
    │                                        ▲
    ▼                                        │
core/designsystem          engine ──► capture ┘
                              ▲
platform (serviços Android) ──┘
```

| Camada | Pode depender de | NÃO pode depender de |
|---|---|---|
| `domain` | `core/money`, `core/time` | Android, Room, Compose, `capture`, `data` |
| `capture` | `domain`, `core` | Android (recebe `ScreenSnapshot`, não `AccessibilityNodeInfo`) |
| `data` | `domain`, Room, DataStore | `features`, `platform` |
| `engine` | `capture`, `domain`, `data` | Compose |
| `platform` | tudo acima | `features` (exceto `MainActivity` para PendingIntent) |
| `features` | `data`, `domain`, `core`, `engine` | `platform` diretamente, exceto permissões e serviço de turno |

A consequência é que o motor financeiro, a validação, a máquina de estados e os parsers rodam em JVM pura. Os 62 testes unitários executam em segundos sem emulador.

## Fluxo principal

```
Uber/99 na tela
  → MeuGanhoAccessibilityService (agrupa eventos, lê árvore em thread própria)
  → ScreenSnapshot (texto + ids de view)
  → CaptureCoordinator (fila limitada, Dispatchers.Default)
      → ParserRegistry → UberParserV1 / NinetyNineParserV1
      → classify → OFFER | TO_PICKUP | … | IDLE
      → OFFER: parse → dedup → OfferAssessment (confiança + validação) → status
               → OfferRecorder (Room) → overlay → TripTracker
      → demais telas: TripSignal → TripTracker → TripStateMachine → efeitos → Room
  → Room emite Flow → StatsRepository recalcula → Dashboard/Overlay atualizam
```

## Injeção de dependência e ciclo de vida

- `MeuGanhoApp` cria um único `AppContainer`. Tudo nele é `lazy`: nada é construído até ser usado.
- Os serviços acessam o container via `context.appContainer`.
- Os ViewModels são criados com `containerViewModel { XViewModel(it) }`.
- `appScope` (SupervisorJob + Default) roda o trabalho de fundo que precisa sobreviver às telas: captura e gravações.

## Threads

| Trabalho | Onde roda |
|---|---|
| Eventos de acessibilidade | Main (só agenda; custo ~0) |
| Leitura da árvore de nós | `HandlerThread("meuganho-capture")` |
| Parse, validação, gravação | `Dispatchers.Default` / executores do Room |
| Cálculo de resumo do overlay | `Dispatchers.Default` (`flowOn`) |
| Render do overlay e do Compose | Main |
| GPS | callbacks na Main, que só somam números em memória |
