# Estrutura de pastas

Pacote raiz: `com.innexar.meuganho` (em `app/src/main/java/`). Cada arquivo tem uma responsabilidade. O maior tem 182 linhas.

```
app/                         Composição da aplicação
  AppContainer.kt            Injeção manual: banco, repositórios, motor de captura
  MeuGanhoApp.kt             Application: cria o container e os canais de notificação
  MainActivity.kt            Splash, onboarding ou navegação principal
  navigation/                Rotas e NavHost com barra inferior (Hoje, Turno, Histórico, Mais)

core/                        Utilidades sem regra de negócio
  money/Money.kt             Centavos (Long) <-> texto BR; parse de entrada
  money/Units.kt             km, duração, R$/km, R$/h, percentuais
  time/DateRange.kt          Intervalos de dia/semana/mês; formatos de data
  logging/AppLog.kt          Log por categoria + buffer em memória para o Diagnóstico
  ui/                        containerViewModel, compartilhar texto
  designsystem/theme/        Palette (ARGB), cores semânticas, tipografia, espaçamentos, tema
  designsystem/components/   MgCard, MetricTile/Grid, MoneyText, Badges, GoalProgressBar, Forms, MgScreen, EmptyState

domain/                      Regras puras (JVM, 100% testáveis)
  model/                     Platform, RideOffer, Captured<T>, Confidence, DataStatus, TripState, enums
  financial/                 CostModel, OfferAnalyzer, RatingThresholds, Rates, ConsumptionCalculator, GoalCalculator
  validation/                OfferValidator, StatusResolver, OfferAssessment, PersonalBaseline, TripValuePolicy
  trip/                      TripStateMachine (função pura) + sinais/efeitos
  stats/                     SummaryCalculator, PeriodSummary, Reconciliation, AnalyticsCalculator, EstimateVsReal, DailyReportFormatter

capture/                     Tela -> dados (sem Android)
  core/                      ScreenSnapshot, TextPatterns, AnchoredOfferExtractor, KeywordPlatformParser,
                             OfferDeduplicator, ParserHealthMonitor (circuit breaker), SnapshotSanitizer,
                             CaptureDiagnostics, ParserRegistry
  uber/                      UberPatterns (ÚNICO lugar com textos do Uber) + UberParserV1
  ninetynine/                NinetyNinePatterns (ÚNICO lugar com textos da 99) + NinetyNineParserV1

engine/                      Orquestração entre captura, domínio e banco
  CaptureCoordinator.kt      Pipeline de captura (fila, classificação, oferta, sinais)
  OfferRecorder.kt           Avalia e grava ofertas; guarda snapshot só se duvidosa
  TripTracker.kt             Aplica efeitos da máquina de estados no Room
  LiveTrip, OverlayOffer, ServiceStatus

data/                        Persistência
  local/entity/              Entidades Room (8 tabelas)
  local/dao/                 DAOs com Flow e incrementos atômicos
  local/MeuGanhoDatabase.kt  Banco v1 + Migrations explícitas (sem destructive)
  preferences/               AppSettings + SettingsRepository (DataStore)
  repository/                CostModelProvider, ShiftRepository, StatsRepository
  mapper/                    Entidade -> fatos de domínio
  backup/                    BackupFile versionado, BackupManager (JSON), TripCsv

platform/                    Integrações com o Android
  accessibility/             MeuGanhoAccessibilityService, NodeTreeReader
  overlay/                   OverlayController, OverlayView (Views nativas), OverlayFormatter
  location/                  ShiftLocationTracker (LocationManager), GpsDistanceAccumulator
  foreground/                ShiftForegroundService (só durante turno)
  notifications/             Canais, NotificationListener (fonte reserva)
  permissions/               PermissionChecker

features/                    Telas (Screen + ViewModel lado a lado)
  onboarding/ dashboard/ shift/ trips/ offers/ review/ history/ fuel/ expenses/
  vehicle/ goals/ analytics/ settings/ diagnostics/ more/
```

Testes em `app/src/test/`:

```
java/.../Fixtures.kt         Carrega telas anonimizadas
java/.../capture/            UberParserTest, NinetyNineParserTest, TextPatternsTest, SanitizerAndDedupTest
java/.../domain/             OfferAnalyzer, Validation, TripStateMachine, SummaryCalculator, Money, Consumption/Goal
java/.../platform/           GpsDistanceAccumulatorTest
resources/fixtures/uber/     14 telas (ofertas, ciclo da corrida, ganhos)
resources/fixtures/ninetynine/  4 telas
```

## Onde mexer quando…

| Situação | Arquivo |
|---|---|
| Uber mudou um texto | `capture/uber/UberPatterns.kt` + nova fixture |
| Uber mudou o layout inteiro | criar `UberParserV2`, registrar em `ParserRegistry` |
| Nova plataforma | `Platform` + `capture/<nome>/` + `accessibility_service_config.xml` + `<queries>` |
| Nova regra de plausibilidade | `domain/validation/OfferValidator.kt` + teste |
| Nova coluna no banco | entidade + `Migrations.kt` + `BackupFile.CURRENT_VERSION` |
| Nova cor/tamanho | `core/designsystem/theme/` (nunca direto na tela) |
