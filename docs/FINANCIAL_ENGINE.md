# Motor financeiro

Pacotes `domain/financial` e `domain/stats`. Tudo em JVM pura, com dinheiro em centavos (`Long`). Testes: `OfferAnalyzerTest`, `ConsumptionAndGoalTest`, `SummaryCalculatorTest`.

## Custo por km (`CostModel`)

```
combustível/km  = preço do litro / km por litro
custos fixos/km = manutenção (pneus, óleo, revisões) + depreciação + outros
custo operacional(m) = m/1000 × (combustível/km + custos fixos/km)
```

**Consumo usado**, em ordem de preferência (`CostModelProvider`):
1. **Observado** nos abastecimentos (tanque cheio → tanque cheio), com pelo menos 2 medições e só do combustível em uso.
2. Consumo médio cadastrado no veículo (FLEX com etanol: `ethanolKmPerLiter`, ou 70% do consumo com gasolina).
3. Sem veículo: 10 km/l, sem custos fixos. O Dashboard avisa para cadastrar.

## Análise da oferta (`OfferAnalyzer`)

```
km total    = km até o passageiro + km da viagem   (a busca não é paga, mas gasta)
tempo total = tempo até o passageiro + tempo da viagem
R$/km  = valor × 1000 / metros totais
R$/h   = valor × 3600 / segundos totais
combustível          = custo de combustível(km total)
após combustível     = valor − combustível
resultado operacional = valor − custo operacional(km total)
```

Exemplo da especificação (testado): R$ 28,70, 1,8 km/4 min + 12,4 km/24 min → **14,2 km, 28 min, R$ 2,02/km, R$ 61,50/h**.

## Classificação (`RatingThresholds`, editável em Configurações)

| Nível | Regra padrão |
|---|---|
| ▲ EXCELENTE | ≥ R$ 2,00/km **e** ≥ R$ 45/h |
| ● ACEITÁVEL | ≥ R$ 1,60/km **e** ≥ R$ 35/h |
| ▼ RUIM | abaixo |
| ? INCERTO | dado não confiável ou parser em pane: **sem veredito** |

Os dois critérios são exigidos juntos: R$/km alto com trânsito parado continua sendo uma corrida ruim.

## Três visões que nunca se confundem (`PeriodSummary`)

| Visão | Fórmula | Natureza |
|---|---|---|
| **Faturamento bruto** | soma das corridas `CONFIRMED`/`AUTO_ACCEPTED` concluídas (+ canceladas com taxa) | fato (com pendentes à parte) |
| **Caixa** | bruto − combustível **comprado** no período − despesas | dinheiro no bolso |
| **Resultado operacional estimado** | bruto − custo/km × km total − despesas | **estimativa**, rotulada como tal |

O Dashboard e o fechamento diário dizem explicitamente "estimativa, não valor contábil".

## Quilômetros

`km total` = o maior entre (a) odômetro, se **todos** os turnos do período têm início e fim informados, senão o GPS do turno, e (b) a soma dos km das corridas. `km vazio = total − km pago`. O Detalhe do dia compara odômetro e GPS lado a lado.

## Tempos (não são equivalentes)

| Tempo | Origem |
|---|---|
| Trabalhado | início → fim do turno (turno aberto usa "agora") |
| Online | trabalhado − pausas |
| Indo buscar | `acceptedAt` → `pickupArrivedAt` |
| Esperando passageiro | `pickupArrivedAt` → `startedAt` |
| Em corrida | `startedAt` → `endedAt` |
| Ocioso (esperando/entre corridas) | online − (busca + espera + corrida) |

Taxas: **R$/h online** (a que decide se vale estar na rua), **R$/h em corrida** e R$/km sobre o km total.

## Metas (`GoalCalculator`)

`progresso = atual / alvo`, `falta = alvo − atual`, `tempo estimado = falta ÷ R$/h online atual`. Meta bruta usa o faturamento; meta líquida usa o resultado estimado. Exemplo testado: R$ 276 / R$ 350, R$ 39,80/h → 79%, faltam R$ 74, ~1h52.

## Estimativa x realidade (`EstimateVsReal`)

Para corridas concluídas e confiáveis com estimativa completa: R$/h e R$/km prometidos x reais, minutos a mais por corrida e quantas "pareciam boas e não foram" (R$/h real < 80% do prometido).

## Analytics local (`AnalyticsCalculator`)

R$/h **online** por hora do dia e por dia da semana (cada turno é fatiado em horas cheias; faixas com menos de 30 min são ocultadas), Uber x 99 (ticket, R$/km, R$/h em corrida), ticket médio, tempo e km médios, % de km vazio e % de tempo ocioso. Resultado por região não está implementado, porque exigiria guardar localização por corrida.
