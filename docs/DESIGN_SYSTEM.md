# Design System

Direção: **painel de instrumentos à noite.** Fundo quase preto azulado, números grandes com dígitos tabulares, uma única cor de marca (menta) para dinheiro positivo. É feito para leitura em relance, dentro do carro, muitas vezes à noite. O tema escuro é o padrão; o claro está pronto (`MeuGanhoTheme(dark = false)`).

## Tokens

**Cor** (`Palette.kt`, fonte única para Compose e overlay):

| Token | Escuro | Uso |
|---|---|---|
| background / Ink | `#0A0E13` | fundo |
| surface / surfaceHigh | `#111720` / `#19212D` | cartões / trilhos |
| outline | `#263142` | bordas de 1 dp (no lugar de sombras) |
| textPrimary / textSecondary | `#EAF0F6` / `#8C99AA` | texto |
| brand = positive (Mint) | `#2EE6A6` | dinheiro, metas, marca |
| info (Sky) | `#5AB8FF` | ACEITÁVEL, dicas |
| warning (Amber) | `#FFB547` | pendente, incerto, revisar |
| negative (Coral) | `#FF5C6C` | RUIM, prejuízo, encerrar |
| uber / ninetyNine | `#EAF0F6` / `#FFD23F` | selos de plataforma |

As telas usam `Mg.colors.positive`, nunca um hexadecimal.

**Tipografia** (`Typography.kt`): fonte do sistema (0 KB no APK), com `fontFeatureSettings = "tnum"` em todos os estilos numéricos para que os valores não "dancem". Escala: `hero` 44 sp, `metric` 22, `metricSmall` 16, `title` 20, `body` 15, `label` 12 (caixa-alta, espaçado), `caption` 12.

**Espaçamento** (`Dimens.kt`): 4, 8, 12, 16, 24, 32 dp. Área de toque mínima de **56 dp** (acima dos 48 dp do Material, pensando em uso no carro).
**Raio:** 8, 14, 20 dp e pill.

## Componentes

| Componente | Função |
|---|---|
| `MgCard` | superfície com borda fina, clicável opcional, borda de destaque (âmbar para pendências) |
| `MetricTile` / `MetricGrid` | rótulo pequeno + número grande + subtítulo |
| `MoneyText` | valor em R$, opcionalmente colorido pelo sinal |
| `RatingBadge` | **símbolo + texto** (▲ EXCELENTE, ● ACEITÁVEL, ▼ RUIM, ? INCERTO): nunca só cor |
| `StatusPill` | ✓ AUTOMÁTICO, ✓✓ CONFIRMADO, ? REVISAR, ✕ INVÁLIDO |
| `PlatformBadge` | UBER / 99 |
| `GoalProgressBar` | "R$276 / R$350 · faltam R$74 · ~1h52" |
| `MgField`, `ChoiceRow`, `MultiChoiceRow` | formulários (numérico por padrão) |
| `MgScreen` | top bar + conteúdo rolável |
| `SectionTitle`, `InfoRow`, `EmptyState` | estrutura e estados vazios |

## Acessibilidade visual

- Nenhuma informação depende só de cor: símbolos ▲ ● ▼ ? ✓ ✕ acompanham tudo.
- Contraste: texto primário sobre a superfície acima de 13:1; menta sobre a superfície acima de 10:1.
- Números grandes, pouco texto e uma ação principal por tela.

## Overlay

Painel de Views nativas com as mesmas cores (`Palette`) e números tabulares. É arrastável, um toque expande/recolhe, e a posição é lembrada. Estados:

```
HOJE                    UBER · OFERTA              UBER · OFERTA
R$184                   R$2,03/km                  ? R$2,03/km
R$38,70/h · META 53%    R$58/h · ▲ EXCELENTE       ? INCERTO · confira na tela
                        R$ 28,70 · 14,2 km · 28 min
                        Após combustível R$ 23,30
```

## Identidade visual

**Conceito:** um **mostrador de painel** cujo arco preenchido e ponteiro sobem para a direita. É direção e desempenho (instrumento do carro) e ganho (ponteiro subindo) num símbolo só, sem o clichê de carro + cifrão.

| Asset | Arquivo |
|---|---|
| Ícone adaptativo | `mipmap-anydpi-v26/ic_launcher.xml` |
| Foreground (arco menta + ponteiro claro, dentro da zona segura de 66 dp) | `drawable/ic_launcher_foreground.xml` |
| Background (gradiente diagonal escuro) | `drawable/ic_launcher_background.xml` |
| Monocromático (ícones temáticos do Android 13+) | `drawable/ic_launcher_monochrome.xml` |
| Barra de status | `drawable/ic_stat_meuganho.xml` |
| Splash (Android 12+ nativa, retrocompatível) | tema `Theme.MeuGanho.Splash` com o foreground |

Todos os assets são vetoriais: nenhum PNG no APK.

## Cockpit (0.3.0)

- **Fonte:** Space Grotesk (SIL OFL 1.1, `res/font/space_grotesk.ttf`, variável) em números e títulos.
- **Fundo:** `MgBackground`, base escura com brilho menta no alto e azul embaixo. `MgCard` fica translúcido; `glow = true` dá borda em degradê aos blocos principais.
- **`RateGauge`:** mostrador de R$/hora com faixas pelos limites pessoais (vermelho < aceitável < âmbar < excelente < verde) e ponteiro animado.
- **`GoalRing`:** anel da meta.
- **Hoje:** mostrador → faturamento → bento (meta | R$/km + resultado) → corridas/km/horas → Uber x 99 em barra de proporção.
- **Navegação:** `CapsuleNav` flutuante com Hoje, Histórico, Análises e Mais, e botão central de turno (▶ inicia; com turno aberto mostra o tempo online).
