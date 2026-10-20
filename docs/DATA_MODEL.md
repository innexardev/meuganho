# Modelo de dados

Room/SQLite, arquivo `meuganho.db`, **versão 1**. Schema exportado em `app/schemas/` (versionado).

## Convenções

| Grandeza | Tipo | Unidade |
|---|---|---|
| Dinheiro | `Long` | **centavos** (nunca `Double`) |
| Distância | `Int`/`Long` | metros |
| Duração | `Int` | segundos |
| Instantes | `Long` | epoch millis |
| Custos por km | `Double` | centavos/km (resultado final sempre arredondado para centavos) |
| Enums | texto | `name` do enum (conversão nativa do Room) |

## Tabelas

### `vehicles`
Fabricante, modelo, versão, ano, `fuelType` (GASOLINE/ETHANOL/FLEX/DIESEL/CNG), odômetro, consumo cidade/estrada/médio, `ethanolKmPerLiter` (FLEX), `maintenanceCentsPerKm`, `depreciationCentsPerKm`, `isActive`. O veículo ativo é o de maior id com `isActive = 1`.

### `shifts`
Início/fim, odômetro inicial/final (opcionais), plataformas (`"UBER,NINETY_NINE"`), pausas (`pausedMillis`, `pausedAt`), **km do GPS já agregados** por segmento (`emptyMeters`, `pickupMeters`, `paidMeters`), preço do combustível no início. Índice em `startedAt`.

> Não guardamos pontos GPS. O `LocationSummary` pedido no escopo é justamente estes três contadores por turno e os dois por corrida (`gpsPickupMeters`, `gpsPaidMeters`), gravados por incremento atômico (`UPDATE … SET x = x + :m`).

### `offers`
Toda oferta exibida, aceita ou não. Valor e campos lidos, `surgeCents`, `category`, `fieldConfidence` ("valor=HIGH;km da viagem=MEDIUM"), `confidence` (mínimo dos críticos), `status`, `reasons`, `outcome` (PENDING/ACCEPTED/NOT_ACCEPTED), `parserVersion`, `sourceAppVersion`, `channel`, `rawSnapshot` (sanitizado, temporário), `correctedCents`, `reviewedAt`, `fingerprint`. Índices em `detectedAt` e `status`.

### `trips`
Corrida aceita. `state` (TripState), `status` (DataStatus), timestamps de cada fase (`acceptedAt`, `pickupArrivedAt`, `startedAt`, `endedAt`), **estimativa** copiada da oferta (`estimatedCents`, `estPickup*`, `estTrip*`), **realidade** (`finalCents`, `valueSource`, km por GPS), `correctedCents`, `manualMeters`, `isManual`. Índices em `acceptedAt`, `shiftId`, `offerId`.

Valores derivados (propriedades, não colunas):
- `effectiveCents` = corrigido > final > estimado; cancelada só com final/corrigido.
- `paidMeters` = GPS > manual > estimado.
- `pickupMeters` = GPS > estimado.

### `fuel_logs`
Data, odômetro, combustível, preço/L, litros, total, `fullTank`, observação.

### `expenses`
Data, turno (opcional), categoria (Alimentação, Pedágio…), valor, observação.

### `goals`
`period` (DAILY/WEEKLY/MONTHLY), `kind` (GROSS/NET), `targetCents`, `isActive`.

### `platform_totals`
Leituras do "Ganhos de hoje" de cada plataforma (`platform`, `epochDay`, `totalCents`, `capturedAt`, `parserVersion`), usadas na reconciliação. Uma linha nova só quando o valor muda.

## O que NÃO é tabela, e por quê

- **DailySummary:** calculado sob demanda por `SummaryCalculator` a partir das tabelas. Guardar resumo criaria dados que podem ficar desatualizados após uma revisão ou correção. O cálculo é barato: um dia tem dezenas de linhas.
- **Settings:** DataStore Preferences (`AppSettings`), assíncrono e reativo.

## Migrations

- **Proibido** `fallbackToDestructiveMigration`: apagaria o histórico financeiro.
- Cada mudança de schema cria `MIGRATION_n_n+1` em `Migrations.kt` (há exemplo comentado) e sobe `version` em `MeuGanhoDatabase`.
- O JSON do schema novo é gerado em `app/schemas/` e deve ser commitado.

## Backup

`BackupFile` (JSON, kotlinx.serialization) com `formatVersion = 1`, `exportedAt`, `appVersion` e todas as tabelas. `rawSnapshot` não entra. A restauração:
- recusa arquivos de versão mais nova que o app;
- apaga e reinsere tudo **numa única transação** (tudo ou nada).

O CSV de corridas usa `;` e vírgula decimal (abre direto no Excel/Sheets em PT-BR).

O backup automático do Google está desligado (`allowBackup=false`, `data_extraction_rules.xml`). A transferência direta entre aparelhos é permitida, para a troca de celular.
