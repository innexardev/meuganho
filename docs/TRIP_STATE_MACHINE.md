# Máquina de estados da corrida

Arquivo: `domain/trip/TripStateMachine.kt`. **Função pura**: `reduce(contexto, sinal, agora) → (novo contexto, efeitos)`. Nenhuma tela ou serviço decide transições. Todos enviam `TripSignal` e o `TripTracker` aplica os `TripEffect` no banco. Testes: `TripStateMachineTest`.

## Estados

| Estado | Significado | Segmento GPS |
|---|---|---|
| `IDLE` | Online, sem oferta | vazio |
| `OFFERED` | Oferta na tela, aguardando decisão | vazio |
| `TO_PICKUP` | Aceita, indo buscar (o aceite fica registrado em `acceptedAt`) | busca |
| `WAITING_PASSENGER` | No local de embarque | busca |
| `IN_TRIP` | Passageiro a bordo | pago |
| `COMPLETED` | Concluída | vazio |
| `CANCELLED` | Cancelada após aceitar | vazio |
| `REJECTED` | Oferta não aceita (recusada ou expirada) | vazio |
| `UNKNOWN` | Perdemos o fio (ex.: 3 h sem sinal) | vazio |

`ACCEPTED` não é um estado: aceitar é o instante da transição OFFERED → TO_PICKUP.

## Sinais (vindos da classificação de telas)

`OfferShown(offerId)`, `HeadingToPickup`, `WaitingPassenger`, `InTrip`, `Completed(finalCents?)`, `Cancelled`, `Idle`. As telas `DAILY_EARNINGS` também geram `Idle`.

## Transições

| De | Sinal | Para | Efeitos |
|---|---|---|---|
| sem corrida ativa | OfferShown | OFFERED | oferta anterior → NOT_ACCEPTED |
| OFFERED | HeadingToPickup (mesma plataforma) | TO_PICKUP | StartTrip(oferta), oferta → ACCEPTED |
| OFFERED | HeadingToPickup (outra plataforma) | TO_PICKUP | oferta → NOT_ACCEPTED, StartTrip(sem oferta) |
| IDLE/COMPLETED/… | Waiting/InTrip/HeadingToPickup | o estado da tela | StartTrip (usa a oferta da fila, se houver) |
| TO_PICKUP | WaitingPassenger | WAITING_PASSENGER | `pickupArrivedAt` |
| TO_PICKUP / WAITING | InTrip | IN_TRIP | `startedAt` |
| ativa | Completed(v) | COMPLETED | CompleteTrip(v) |
| COMPLETED sem valor | Completed(v) | COMPLETED | SetCompletedValue(v) (resumo aberto depois) |
| ativa | Cancelled | CANCELLED | CancelTrip |
| OFFERED | Cancelled | REJECTED | oferta → NOT_ACCEPTED |
| OFFERED | Idle **após 25 s** | REJECTED | oferta → NOT_ACCEPTED |
| OFFERED | Idle antes de 25 s | OFFERED | nada (piscada de tela) |
| IN_TRIP | Idle | COMPLETED | CompleteTrip(null): voltou ao mapa sem resumo |
| TO_PICKUP / WAITING | Idle | CANCELLED | CancelTrip |

## Regras especiais

- **Só avança.** Uma tela antiga que reaparece (WAITING depois de IN_TRIP) não faz o estado voltar.
- **Plataforma da corrida manda.** Durante uma corrida do Uber, telas da 99 não mudam o estado. A exceção é uma oferta, que entra na fila.
- **Fila de ofertas.** O Uber envia a próxima oferta durante a corrida. Ela fica em `queuedOfferId` e é associada à próxima corrida iniciada na mesma plataforma.
- **Corrida encadeada.** IN_TRIP + HeadingToPickup com oferta na fila → CompleteTrip(null) da atual e StartTrip da enfileirada (o Uber pula o resumo).
- **Abandono.** Corrida ativa sem nenhum sinal por 3 h vira UNKNOWN com status NEEDS_REVIEW.
- **Restauração.** Ao reiniciar o processo, `TripTracker.restore()` retoma a corrida ativa do banco.

## Status da corrida ao concluir

Definido por `TripValuePolicy` (ver [RIDE_CAPTURE.md](RIDE_CAPTURE.md#7-valor-da-corrida-e-reconciliação)). Corridas iniciadas sem oferta vista ou sem valor vão para Revisar. Canceladas sem taxa ficam `AUTO_ACCEPTED`, porque não têm valor a revisar e não poluem a fila.
