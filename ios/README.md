# MeuGanho para iPhone — leitura de ofertas e cálculo

Só isto: enquanto você usa a Uber ou a 99, o MeuGanho lê a oferta na tela e mostra uma notificação com
R$/km, R$/h, R$/min, lucro (descontando o custo do carro) e a classificação pelas suas metas.

Como funciona: a **Transmissão de tela** do iOS manda a imagem da tela para a extensão do MeuGanho, que lê o
texto no próprio iPhone (nenhuma imagem sai do aparelho). A leitura e as contas são as mesmas do Android
(testadas com as mesmas telas reais: `Tests/Fixtures`).

## Instalar no seu iPhone (conta Apple gratuita)

1. No Mac: instale o **Xcode** (App Store) e o **XcodeGen**:
   ```
   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"   # se ainda não tem o Homebrew
   brew install xcodegen
   ```
2. Descompacte o pacote e gere o projeto:
   ```
   cd MeuGanho-iOS
   xcodegen
   open MeuGanho.xcodeproj
   ```
3. No Xcode, para **cada** alvo (`MeuGanho` e `Broadcast`): aba **Signing & Capabilities** → **Team** = seu Apple ID
   ("Personal Team").
   - Se aparecer que o identificador já existe, troque `com.innexar` por algo seu (ex.: `com.seunome`) nos **dois**
     alvos, mantendo o final `.broadcast` na extensão.
   - Se a conta gratuita recusar **App Groups**: remova essa capability dos dois alvos. Tudo funciona; só a extensão
     passa a usar os custos/metas padrão e a lista "Últimas ofertas" fica vazia.
4. No iPhone: **Ajustes › Privacidade e Segurança › Modo de Desenvolvedor** → ligar (reinicia o iPhone).
5. Ligue o iPhone no Mac pelo cabo, escolha-o no topo do Xcode e aperte **▶︎ (Run)**.
6. Na primeira vez: **Ajustes › Geral › VPN e Gerenciamento de Dispositivo** → confie no seu Apple ID.

## Usar

1. Abra o MeuGanho, permita as **notificações** e toque em **"Testar notificação"** (deve aparecer uma oferta de exemplo).
2. Ajuste combustível, consumo, manutenção e as suas metas de R$/km e R$/h.
3. Toque no botão de transmissão → **MeuGanho** → **Iniciar transmissão**. Aparece o indicador vermelho no topo.
4. Abra a Uber ou a 99. Cada oferta vira uma notificação por ~7 segundos.
5. **Foco "Direção"**: Ajustes › Foco › Direção › Apps permitidos → adicione o MeuGanho, senão as notificações
   ficam escondidas enquanto você dirige.

Com a conta gratuita o app **expira a cada 7 dias**: ligue no Mac e aperte ▶︎ de novo.

## Testes (no Mac)

`⌘U` no Xcode roda `Tests/ParityTests.swift`: as mesmas telas reais do Android têm que dar os mesmos números.
O gabarito (`Tests/Fixtures/expected.json`) é gerado pelo Android (`IphoneParityExportTest`).

## Arquivos

- `Shared/` — leitura da oferta e cálculo (porte do Android; corrigiu de um lado, corrija do outro).
- `Broadcast/SampleHandler.swift` — extensão de Transmissão: quadros → OCR (Vision) → leitura → notificação.
- `App/MeuGanhoApp.swift` — tela única: iniciar transmissão, testar, custos, metas, últimas ofertas.
