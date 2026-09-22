# VOLANTE

**Cada carro, um volante.**

Jogo de dirigir que roda no navegador. Baliza, vaga, entrega, slalom, escolta,
economia de combustível e carga frágil — em quatro cenários e seis condições de
tempo, com o traçado do bairro sorteado a cada partida.

Sem dependência nenhuma: nem motor de jogo, nem biblioteca, nem arquivo de
imagem ou de som. É HTML, CSS e JavaScript puro — cerca de 7 mil linhas de jogo
e mais mil de teste.

---

## Jogar

```bash
cd jogo
node servidor-local.mjs      # http://localhost:8080
```

Serve qualquer servidor estático (`python3 -m http.server`, `npx serve`…). Não
dá para abrir por `file://`: o jogo usa módulos ES e o navegador recusa módulo
sem origem HTTP.

**Teclado** — `W`/`S` acelera e freia, `A`/`D` viram o volante, `espaço` é o
freio de mão, `C` troca a câmera, `shift` olha para trás, `Esc` pausa.

**No dedo** — gire o volante no canto de baixo. Os pedais ficam do outro lado.
Parado com o freio afundado, a ré engata sozinha.

---

## De onde veio

A referência é o **DR Driving** e os jogos de dirigir de celular da mesma
escola: chão pintado visto em perspectiva, carro montado com blocos, e uma
baliza que não perdoa. O que se manteve:

| | Como é aqui |
|---|---|
| A paisagem | uma imagem do bairro visto de cima, jogada no chão em perspectiva, com o carro andando por cima dela |
| Os modelos | caixa, caixa afunilada e cilindro — sem textura, sem malha importada |
| A física | modelo de bicicleta com deriva de pneu, transferência de peso e câmbio |
| O ritmo | serviço curto, objetivo claro, nota em estrelas, dinheiro, carro novo |

O que mudou, e por quê:

- **O volante é o controle, e é diferente em cada carro.** Não é botão de
  esquerda/direita: é um volante que você gira com o dedo. O Besouro tem aro de
  marfim com dois raios, a picape tem couro costurado à vista, o furgão tem um
  prato de ônibus com cinco raios vazados, e o elétrico tem um manche que nem
  redondo é. Trocar de carro troca o volante na sua mão.
- **Nada se repete.** O bairro é sorteado, a hora do dia muda, o tempo muda, e
  são sete tarefas diferentes em vez de uma só. A mesma rua às sete da manhã e
  debaixo de chuva à noite parece outro lugar — e a chuva não é pintura: tira
  aderência de verdade.
- **Serviço curto.** Nenhuma missão passa de dois minutos.
- **Rua livre.** Sem relógio, sem cobrança, só dirigir.

---

## Como funciona por dentro

```
src/
  nucleo/       matemática, sorteio com semente, entrada (teclado e dedo)
  motor/        câmera, céu, chão em perspectiva, malhas, modelos, desenho
  jogo/         física, carros, volantes, colisão, mundo, missões, som, save
  interface/    painel dentro do canvas, telas em HTML
testes/         80 testes, sem navegador
```

### O chão

O bairro inteiro é pintado **uma vez**, de cima, numa imagem de 1600×1600 —
asfalto, faixa, meio-fio, vaga, remendo, bueiro, mancha de óleo, sombra dos
prédios. Depois disso, a cada quadro, essa imagem é jogada no plano do chão em
perspectiva, pixel a pixel.

O que faz isso caber em tempo real: a câmera não tem rolagem, então **toda linha
horizontal da tela cruza o chão a uma distância constante**. Dá para calcular a
distância uma vez por linha e depois só caminhar em linha reta pela imagem,
somando um passo fixo por pixel — quatro contas por pixel. É a mesma ideia dos
jogos de corrida de 16 bits, só que com a câmera livre em vez de presa.

Cada linha carrega também a sua névoa, o que sai de graça pelo mesmo motivo.
E o rastro de pneu é escrito direto nos bytes dessa imagem: fica lá até o fim
da missão, sem custar nada por quadro.

### Os objetos

Carro, prédio, árvore, cone e poste são malhas de bloco projetadas pela **mesma
câmera** que desenha o chão — é o que faz a roda encostar no asfalto em vez de
flutuar. Cada face é descartada, recortada no plano de perto, ordenada por
profundidade e pintada com sombreamento chato.

O espaço da câmera guarda (direita, **baixo**, frente) em vez de (direita, cima,
frente). Parece detalhe, mas com o eixo vertical apontando para baixo a base
fica destra e a orientação do polígono na tela passa a ser a mesma do 3D — aí dá
para jogar fora a face virada para o outro lado olhando só a área com sinal do
polígono já projetado, sem calcular normal nenhuma.

### A física

Modelo de bicicleta: os dois pneus da frente viram um, os de trás também. A
força lateral de cada eixo sai do **ângulo de deriva** — o ângulo entre para
onde o pneu aponta e para onde ele está de fato indo — saturando no limite de
aderência. É daí que saem a subesterçagem, o rabo saindo na aceleração e o carro
se firmando quando você alivia.

Três coisas que decidem a sensação:

1. **Abaixo de 2,2 m/s o modelo troca** para esterçamento geométrico puro. O
   modelo de deriva divide pela velocidade e explode perto de zero — e zero é
   justamente a faixa da baliza, onde o carro precisa obedecer redondo.
2. **O esterço máximo cai com a velocidade.** Sem isso, um toque no volante a
   80 km/h capota o carro e dirigir vira loteria.
3. **A aderência limita o motor e o freio, não só a curva.** É o que faz o carro
   patinar na largada, frear mais longe na chuva e a tração integral valer a
   pena no barro.

O passo é **fixo em 1/120 s**, com o desenho livre por cima. Física de passo
variável é física diferente em cada máquina: a mesma baliza se comportaria de um
jeito num celular de 30 quadros e de outro num monitor de 144.

### As missões

Três coisas variam sozinhas: **o que** você faz (7 tipos), **onde** (4 cenários)
e **com que tempo** (6 climas). São 168 combinações antes de contar o traçado,
que é sorteado, e o carro, que muda o jeito de tudo.

A nota é de 1 a 3 estrelas: terminar vale uma, e as outras duas vêm de **como**
você terminou — sem bater, com tempo de sobra, com combustível no tanque, com a
carga inteira. É o que separa "passou" de "passou bem".

### O som

Não há arquivo de áudio no projeto. O motor são três osciladores afinados pela
rotação passando por um filtro que abre com o acelerador; o pneu é ruído branco
num passa-faixa estreito; a batida é um estouro curto de ruído. O elétrico apita
em vez de roncar, porque a curva de torque dele é outra.

---

## Os carros

Oito, em ordem de preço. Cada um muda o jogo inteiro — e cada um tem o seu
volante.

| Carro | Classe | Tração | O volante |
|---|---|---|---|
| Besouro 61 | clássico | traseira | marfim, dois raios deitados, anel de buzina cromado |
| Pipoca | popular | dianteira | plástico preto, três raios, calo para o polegar |
| Diplomata | sedã | traseira | aro de madeira em duas faixas, quatro raios claros |
| Pão Quente | furgão | dianteira | prato de ônibus, cinco raios vazados |
| Boiadeira | picape | integral | couro grosso com costura à vista |
| Areião | fora de estrada | traseira | tubo amarelo vazado, quatro raios |
| Faísca | esportivo | traseira | fundo chato, marca vermelha no topo, camurça |
| Silêncio | elétrico | integral | manche — nem é um círculo |

---

## Testes

```bash
cd jogo && npm test        # 80 testes, sem navegador e sem instalar nada
```

Rodam em Node puro porque física, mundo, missões e colisão não tocam em `canvas`
nem em `document`. O que a suíte garante:

- **física** — acelera, freia até parar de vez, anda de ré, vira para os dois
  lados simetricamente, obedece em manobra, escorrega mais na chuva, freia mais
  longe na chuva, gasta menos com pé leve, e dá sempre o mesmo resultado para a
  mesma entrada
- **mundo** — varre 160 bairros sorteados: sempre tem rua, o carro sempre nasce
  no asfalto e nunca dentro de um poste, nenhum obstáculo fica no meio da pista
  e nada nasce em cima de outra coisa
- **missões** — monta 336 missões e confere que toda uma tem objetivo, larga no
  asfalto, cabe no relógio, e que a vaga cabe o carro que vai estacionar nela
- **colisão** — casos com coordenada conferível no papel, inclusive o carro a
  144 km/h contra a parede
- **volantes** — que nenhum é igual a outro, em estilo, emblema, apelido ou cor

Três bugs foram encontrados por esses testes e não por jogar: poste plantado no
meio do asfalto, carro nascendo fora da rua na escolta, e portões de slalom
estourando o fim da rua.

---

## Publicar

É um site estático: sobe a pasta `jogo/` em qualquer lugar e funciona. Sem
build, sem passo de compilação, sem `node_modules`.
