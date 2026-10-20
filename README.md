# MeuGanho

Computador de bordo financeiro para motoristas de Uber e 99. App Android nativo, offline e leve (APK de 1,7 MB), que roda ao lado dos apps das plataformas e responde, em segundos, **"essa corrida vale a pena?"** e, no fim do dia, **"quanto eu realmente ganhei?"**.

- **Antes da corrida:** lê a oferta na tela e mostra R$/km, R$/h, combustível e resultado estimado num painel flutuante, com classificação EXCELENTE / ACEITÁVEL / RUIM pelos seus limites.
- **Durante:** acompanha a corrida automaticamente (indo buscar, esperando, em corrida) e mede os km com GPS.
- **Depois:** registra o valor real, compara com o que a oferta prometia e reconcilia o dia com o total mostrado pela própria plataforma.
- **No dia:** faturamento, caixa, resultado estimado, metas, km vazio x pago, tempo ocioso, Uber x 99.

**Dado duvidoso não contamina a estatística.** Todo valor capturado tem origem e nível de confiança. O que o app não tem certeza de ter lido corretamente vai para **Revisar**, aparece separado como "pendente" e só entra nos totais depois de confirmado. Detalhes em [docs/RIDE_CAPTURE.md](docs/RIDE_CAPTURE.md).

## Compilar e instalar

Requisitos: JDK 17 e Android SDK 36 (configure `sdk.dir` em `local.properties` ou `ANDROID_HOME`).

```bash
./gradlew testDebugUnitTest      # 62 testes das regras, parsers e máquina de estados
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk (R8, 1,7 MB)
adb install -r app/build/outputs/apk/release/app-release.apk
```

O release é assinado com a chave de debug (uso pessoal). Troque por uma keystore própria antes de distribuir.

### Ver as telas sem celular

`./gradlew testDebugUnitTest --tests '*ScreensTest*'` renderiza todas as telas (e o painel flutuante) com dados de exemplo, no tamanho de um Galaxy A32, em `app/build/screenshots/`. Use antes e depois de mudar qualquer layout.

## Primeiro uso

1. Abra o app e siga **Permissões**. Nenhuma é obrigatória; sem elas, registre corridas manualmente.
   - **Acessibilidade** ("MeuGanho: leitura de ofertas"): captura automática. Lê só Uber Driver e 99 Motorista e nunca toca em botões.
   - **Localização:** km do turno. Só com turno ativo e sem guardar trajeto.
   - **Bateria sem restrição:** evita que o Android encerre o turno.
2. **Mais > Veículo:** consumo e custos por km (sem isso, o custo usa 10 km/l).
3. **Mais > Configurações:** preço do combustível e seus limites de R$/km e R$/h.
4. **Turno > Iniciar turno** e abra o Uber/99. O painel flutuante aparece sobre eles.
5. **Mais > Diagnóstico:** confirme que chegam eventos do Uber e da 99.

> ⚠ **Calibração necessária.** Os padrões de leitura (V1) foram escritos a partir do layout conhecido dos apps e ainda não foram validados com telas reais. Nos primeiros turnos, use **Diagnóstico > Gravar próxima tela** para gerar telas sanitizadas e ajustar `UberPatterns.kt` / `NinetyNinePatterns.kt`. O processo está em [docs/RIDE_CAPTURE.md](docs/RIDE_CAPTURE.md#10-calibração-com-telas-reais-fluxo-de-trabalho). Enquanto isso, o app se protege sozinho: leituras duvidosas vão para Revisar e, se forem muitas, a análise daquela plataforma é suspensa.

## Distribuição e atualização

- Página e APKs: https://innexar.com.br/meuganho, servidos pelo container `meuganho-dist` (`/srv/innexar/production/meuganho-dist`), independente do site.
- Nova versão: subir `versionCode`/`versionName` em `app/build.gradle.kts` e rodar `scripts/publish.sh "o que mudou"`. O app instalado detecta sozinho (a cada 6 h ou em Configurações > Atualizações).
- Chave de assinatura: `/srv/innexar/secrets/meuganho-release.*`. **Sem ela não há atualização por cima**: mantenha backup fora do servidor.

## Privacidade

- Corridas, valores, turnos e custos ficam só no aparelho.
- A internet é usada para: verificar/baixar atualizações e enviar amostras de tela **sanitizadas** (sem nomes, endereços ou recados) para calibração. O envio pode ser desligado em Configurações.
- Backup automático do Google desligado. O backup é manual (JSON/CSV) para onde você escolher.
- Telas guardadas para revisão têm nomes e endereços removidos e expiram em 7 dias.

## Documentação

| Documento | Conteúdo |
|---|---|
| [PLANO_MEUGANHO.md](PLANO_MEUGANHO.md) | Visão geral, decisões, riscos, roadmap: comece aqui |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Stack, camadas, fluxo, threads |
| [docs/FOLDER_STRUCTURE.md](docs/FOLDER_STRUCTURE.md) | Onde fica cada coisa e onde mexer |
| [docs/RIDE_CAPTURE.md](docs/RIDE_CAPTURE.md) | Captura Uber/99, confiança, NEEDS_REVIEW, reconciliação, calibração |
| [docs/TRIP_STATE_MACHINE.md](docs/TRIP_STATE_MACHINE.md) | Estados e transições da corrida |
| [docs/FINANCIAL_ENGINE.md](docs/FINANCIAL_ENGINE.md) | Fórmulas e as três visões financeiras |
| [docs/DATA_MODEL.md](docs/DATA_MODEL.md) | Tabelas, convenções, migrations, backup |
| [docs/PERFORMANCE.md](docs/PERFORMANCE.md) | CPU, banco, GPS, bateria, memória |
| [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md) | Tokens, componentes, overlay, identidade visual |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Fases M0–M9 e plano de testes de campo |
