# Roadmap

Estado em 27/09/2026. "Base entregue" significa implementado, compilando, com testes das regras e revisado por leitura, mas **ainda não validado em campo com Uber/99 reais**.

| Fase | Escopo | Estado |
|---|---|---|
| M0 | Auditoria e planejamento | ✅ Não havia projeto anterior ("Cálculo de Ganho" não encontrado); plano em `PLANO_MEUGANHO.md` |
| M1 | Fundação Android, arquitetura, Design System, identidade | ✅ Base entregue |
| M2 | Banco, veículo, configurações, motor financeiro | ✅ Base entregue + testes |
| M3 | Turnos, GPS, abastecimentos, despesas | ✅ Base entregue + testes do acumulador GPS |
| M4 | Captura Uber/99, confiança, NEEDS_REVIEW, reconciliação, circuit breaker | ✅ Base entregue + 18 fixtures · ⚠ **padrões V1 precisam de calibração com telas reais** |
| M5 | Máquina de estados + acompanhamento automático | ✅ Base entregue + testes |
| M6 | Overlay + dashboard em tempo real | ✅ Base entregue |
| M7 | Histórico, metas, analytics, estimativa x realidade | ✅ Base entregue |
| M8 | Backup JSON/CSV, diagnóstico, otimização | ✅ Base entregue |
| M9 | Testes de campo e estabilização | ⏳ **Próxima fase** |

## M9: plano de testes de campo

1. **Instalar e ativar** Acessibilidade, localização e bateria sem restrição. Confirmar no Diagnóstico que chegam eventos do Uber e da 99 (valida os nomes de pacote).
2. **Calibrar os parsers** (a tarefa mais importante): gravar pelo Diagnóstico pelo menos uma tela de cada tipo por plataforma (oferta de cada categoria, indo buscar, aguardando, em corrida, concluída, cancelada, ganhos do dia), transformar em fixtures e ajustar `UberPatterns`/`NinetyNinePatterns` até todos os testes passarem.
3. **Rodar 3 turnos reais** conferindo:
   - taxa de NEEDS_REVIEW (meta < 10%) e circuit breaker fechado;
   - reconciliação diária batendo com o app;
   - km do GPS x odômetro (diferença < 5%);
   - consumo de bateria do MeuGanho nas configurações do Android (meta < 3% por turno de 8 h).
4. **Ajustar limites de plausibilidade** com base nos dados reais.

## Depois do M9

- Testes instrumentados (Room migrations, serviço de acessibilidade com telas gravadas).
- Baseline Profile para inicialização mais rápida.
- Exportação automática periódica do backup para uma pasta escolhida.
- Região por corrida (opt-in, grade grosseira para preservar a privacidade).
- Keystore própria de release (hoje o release é assinado com a chave de debug, para uso pessoal).
