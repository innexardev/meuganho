# Performance, bateria e memória

O MeuGanho fica aberto horas ao lado de Uber, 99, navegação, Bluetooth e GPS. Toda decisão abaixo segue a regra: **orientado a eventos, nunca polling**.

## Números da build

| Item | Valor |
|---|---|
| APK de release (R8 + shrink) | **1,7 MB** |
| Maior arquivo de código | 182 linhas |
| Permissão de internet | nenhuma |
| Dependências de runtime | AndroidX (core, activity, lifecycle, navigation, compose, room, datastore) + kotlinx (coroutines, serialization) |

## CPU

- **Filtro no sistema:** `packageNames` na configuração da Acessibilidade. Eventos de outros apps não chegam ao processo.
- **Agrupamento duplo:** `notificationTimeout=150ms` no sistema e janela de 250 ms no serviço. Rajadas viram no máximo ~4 leituras/s, e só enquanto Uber/99 estão mudando a tela.
- **Leitura limitada:** 400 nós, profundidade 30, só nós visíveis, travessia iterativa.
- **Hash de conteúdo:** a mesma tela em menos de 5 s não é reprocessada.
- **Fila limitada** (16, descarta a mais antiga): picos não acumulam trabalho.
- **Main thread:** só agenda eventos e desenha. Leitura da árvore em `HandlerThread`, parse/validação em `Dispatchers.Default`, resumo do overlay com `flowOn(Default)`.

## Banco

- Nenhuma consulta periódica: telas e overlay observam `Flow` do Room, que só reemite quando uma tabela muda.
- GPS grava **agregado**: acumula em memória e grava a cada ~300 m ou troca de segmento, com `UPDATE x = x + :m` (dezenas de escritas por turno, não milhares).
- Índices nas colunas de tempo/status usadas pelas consultas.
- Resumos diários são calculados sob demanda (dezenas de linhas por dia) em vez de mantidos em tabela.
- Percentis pessoais recalculados a cada 20 ofertas, não por oferta.

## GPS e bateria

- Localização **só** com turno aberto, GPS ativado nas configurações e turno não pausado. Sem turno, não há serviço nem localização.
- `LocationManager` nativo, provedor *fused* (Android 12+), intervalo 5 s e **distância mínima 15 m**: parado, o sistema praticamente não nos acorda.
- Filtros no acumulador descartam precisão > 35 m, saltos > 200 km/h e tremor < 8 m.
- Nenhum ponto é armazenado.
- Serviço em primeiro plano com notificação de importância baixa. O tempo usa `setUsesChronometer`: **zero atualizações** nossas.

## Memória

- Overlay em **Views nativas** (não Compose) dentro do serviço: poucas Views, criadas uma vez.
- Buffers com teto fixo: log (150 entradas), deduplicação (janela de 90 s), health (20 por plataforma), eventos de diagnóstico (5 min).
- `AppContainer` com tudo `lazy`.
- Snapshots brutos só para ofertas duvidosas, sanitizados e com retenção.

## Temporizadores que existem (e por quê)

| Temporizador | Quando roda |
|---|---|
| "Hoje" vira à meia-noite | 1 `delay` por dia |
| Relógio de 1 min | só enquanto uma tela ou o overlay está assinando (`WhileSubscribed`), para atualizar o tempo online do turno aberto |
| Esconder oferta do overlay | 1 `postDelayed` de 15 s por oferta |

## Logs

- `AppLog`: em release, só erros vão ao Logcat. Debug vai para um buffer circular em memória, exibido no Diagnóstico.
- Nada é gravado em arquivo.
- Texto de tela nunca é logado.

## Bateria no aparelho

A tela de Permissões oferece "Bateria sem restrição". Alguns fabricantes (Xiaomi, Samsung, Motorola) encerram serviços agressivamente, e isso é necessário para o GPS do turno sobreviver.
