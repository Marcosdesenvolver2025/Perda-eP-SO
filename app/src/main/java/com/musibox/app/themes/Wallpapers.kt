package com.musibox.app.themes

import androidx.compose.ui.graphics.Color

/**
 * Papel de parede do MusiBox (imagens originais em assets/wallpapers).
 * price em centavos: 0 = grátis (ou exclusivo de plano). plans = planos que liberam o tema
 * ("go", "plus" ou "pro"); quem tem plano não compra. Cada plano tem os seus temas (não se repetem).
 */
data class Wallpaper(
    val id: String,
    val name: String,
    val price: Int,
    val accentArgb: Long,
    val plans: Set<String> = emptySet(),
    val style: String = "",
    /** Coleção especial ("herois", "jogos"); vazio = coleção principal. */
    val collection: String = "",
    /** Palavras para a busca (além do nome), ex.: "praia mar por do sol". */
    val keywords: String = "",
    /** Lançamento para todos (ms). Antes disso só o Pro usa ("1 semana antes"). 0 = já lançado. */
    val releaseAt: Long = 0L,
) {
    val accent: Color get() = Color(accentArgb)

    /** Ainda em pré-lançamento (só o Pro tem acesso). */
    fun isEarly(now: Long = System.currentTimeMillis()): Boolean = releaseAt > now

    /** Lançado nos últimos 30 dias (ou ainda por lançar). */
    fun isNew(now: Long = System.currentTimeMillis()): Boolean = releaseAt > 0 && now < releaseAt + 30L * 24 * 60 * 60 * 1000

    /** Exclusivo de plano (Go, Plus ou Pro): não pode ser comprado. */
    val pro: Boolean get() = plans.isNotEmpty()
    val free: Boolean get() = price == 0 && plans.isEmpty()

    /** Produto da Google Play da faixa de preço (ex.: "papel_099" = R$ 0,99). */
    val productId: String get() = "papel_" + price.toString().padStart(3, '0')
    val assetUri: String get() = "file:///android_asset/wallpapers/$id.webp"

    /** Plano que libera o tema (cada tema exclusivo é de um só plano). */
    val planLabel: String
        get() = when {
            "go" in plans -> "Go"
            "plus" in plans -> "Plus"
            else -> "Pro"
        }

    /** Nome do plano que tem este tema ("Go", "Plus" ou "Pro"). */
    val plansText: String get() = "plano $planLabel"

    /** Preço da tabela (o preço real vem da Play Store quando disponível). */
    val priceLabel: String
        get() = when {
            pro -> planLabel
            price == 0 -> "Grátis"
            else -> "R$ " + "%d,%02d".format(price / 100, price % 100)
        }
}

/**
 * Catálogo dos temas (124): temas novos em alta qualidade, exclusivos do Pro e as melhores coleções antigas.
 * Preços populares (tabela A): R$ 0,99 a R$ 4,99. Exclusivos dos planos têm "plans" e não se compram.
 */
object Wallpapers {
    /** Coleção "Heróis & Vilões": clima de super-herói, sem personagens de terceiros. */
    const val HEROES = "herois"

    /** Coleção "Mundos de Jogo": caça e sobrevivência, ciberpunk, fantasia medieval e corrida urbana. */
    const val GAMES = "jogos"

    /** Coleção "Premium 3D": colmeias neon, facetas, lava, metal líquido, esferas de vidro, gotas, cubos e mar ao pôr do sol. */
    const val PREMIUM = "premium"

    /** Coleção "Inspirações": criada a partir das referências (olhos, fumaça, picos, chuva, tinta, flores, planeta, lagos…). */
    const val INSPIRATIONS = "inspiracoes"

    /** Coleção "Natureza 3D": paisagens renderizadas em 3D (lagos, fiorde, dunas, cânion, aurora, inverno). */
    const val NATURE3D = "natureza3d"

    /** Coleção "Games 3D": cenários de games renderizados em 3D (planeta alienígena, lava, cyberpunk, blocos…). */
    const val GAMES3D = "games3d"

    /** Coleção "Exclusivos Pro": temas feitos só para o plano Pro, cada um com um estilo único. */
    const val PRO_EXCLUSIVE = "exclusivos_pro"

    /** Coleção "Lugares Famosos": Rio, Santorini, Fuji, Veneza, Paris, Iguaçu, Machu Picchu… */
    const val PLACES = "lugares"

    /** Coleção "Anime & Games": cenas no estilo anime e game, com personagens originais. */
    const val ANIME = "anime"

    /** Coleção "Carros & Motos": esportivos, clássicos e motos sem marca nem logo. */
    const val CARS = "carros"

    /** Coleção "Natureza & Animais": animais, flores, paisagens e espaço. */
    const val NATURE = "natureza"

    /** Coleção "Populares": abstratos de luxo, fé, futebol, música e clima aconchegante. */
    const val POPULAR = "populares"

    val all: List<Wallpaper> = listOf(
        // ---- Exclusivos Pro (31)
        Wallpaper("px_aquarela", "Aquarela", 0, 0xFFFF8733, plans = setOf("pro"), style = "watercolor", collection = PRO_EXCLUSIVE, keywords = "aquarela arte exclusivo lago montanhas pintura pro"),
        Wallpaper("px_baleia_ao_por_do_sol", "Baleia ao Pôr do Sol", 0, 0xFFFFA733, plans = setOf("pro"), style = "fluke", collection = PRO_EXCLUSIVE, keywords = "baleia exclusivo mar natureza oceano por pro sol"),
        Wallpaper("px_baloes_ao_amanhecer", "Balões ao Amanhecer", 0, 0xFF3372FF, plans = setOf("pro"), style = "balloons", collection = PRO_EXCLUSIVE, keywords = "amanhecer baloes ceu exclusivo nevoa pro vale"),
        Wallpaper("px_bolhas_iridescentes", "Bolhas Iridescentes", 0, 0xFF9EFF33, plans = setOf("pro"), style = "bubbles", collection = PRO_EXCLUSIVE, keywords = "arco bolhas exclusivo iridescentes iris pro sabao"),
        Wallpaper("px_buraco_negro", "Buraco Negro", 0, 0xFFFF6000, plans = setOf("pro"), style = "blackhole", collection = PRO_EXCLUSIVE, keywords = "buraco espaco estrelas exclusivo galaxia negro pro universo"),
        Wallpaper("px_campo_de_lavanda", "Campo de Lavanda", 0, 0xFF9C3DFF, plans = setOf("pro"), style = "lavender", collection = PRO_EXCLUSIVE, keywords = "campo exclusivo flores lavanda por pro roxo sol"),
        Wallpaper("px_caverna_de_gelo", "Caverna de Gelo", 0, 0xFF0B88FF, plans = setOf("pro"), style = "icecave", collection = PRO_EXCLUSIVE, keywords = "azul caverna exclusivo gelo glaciar inverno pro"),
        Wallpaper("px_cometa", "Cometa", 0, 0xFFFF80CE, plans = setOf("pro"), style = "comet", collection = PRO_EXCLUSIVE, keywords = "cometa deserto espaco estrelas exclusivo noite pro"),
        Wallpaper("px_cordas_de_violao", "Cordas de Violão", 0, 0xFFFFB533, plans = setOf("pro"), style = "guitar", collection = PRO_EXCLUSIVE, keywords = "acustico cordas exclusivo madeira musica pro violao"),
        Wallpaper("px_dente_de_leao", "Dente-de-leão", 0, 0xFFFFA633, plans = setOf("pro"), style = "dandelion", collection = PRO_EXCLUSIVE, keywords = "dente exclusivo flor leao natureza por pro sol"),
        Wallpaper("px_disco_de_vinil", "Disco de Vinil", 0, 0xFFFF7931, plans = setOf("pro"), style = "vinyl", collection = PRO_EXCLUSIVE, keywords = "disco exclusivo musica pro retro vinil"),
        Wallpaper("px_fitas_de_luz", "Fitas de Luz", 0, 0xFF6E59FF, plans = setOf("pro"), style = "ribbons", collection = PRO_EXCLUSIVE, keywords = "abstrato exclusivo fitas luz neon pro"),
        Wallpaper("px_floco_de_neve", "Floco de Neve", 0, 0xFF3391FF, plans = setOf("pro"), style = "snowflake", collection = PRO_EXCLUSIVE, keywords = "cristal exclusivo floco gelo inverno neve pro"),
        Wallpaper("px_floresta_de_algas", "Floresta de Algas", 0, 0xFFFFC500, plans = setOf("pro"), style = "kelp", collection = PRO_EXCLUSIVE, keywords = "algas exclusivo floresta fundo mar oceano peixes pro"),
        Wallpaper("px_floresta_encantada", "Floresta Encantada", 0, 0xFF33FFC3, plans = setOf("pro"), style = "mushrooms", collection = PRO_EXCLUSIVE, keywords = "cogumelos encantada exclusivo floresta magica noite pro vagalumes"),
        Wallpaper("px_fogos_de_artificio", "Fogos de Artifício", 0, 0xFFFFEE33, plans = setOf("pro"), style = "fireworks", collection = PRO_EXCLUSIVE, keywords = "ano artificio cidade exclusivo festa fogos noite novo pro"),
        Wallpaper("px_geodo_de_ametista", "Geodo de Ametista", 0, 0xFF9C38FF, plans = setOf("pro"), style = "geode", collection = PRO_EXCLUSIVE, keywords = "ametista cristal exclusivo geodo mineral pedra pro roxo"),
        Wallpaper("px_globo_de_plasma", "Globo de Plasma", 0, 0xFFED33FF, plans = setOf("pro"), style = "plasma", collection = PRO_EXCLUSIVE, keywords = "eletrico energia exclusivo globo neon plasma pro raios"),
        Wallpaper("px_globo_espelhado", "Globo Espelhado", 0, 0xFFCE33FF, plans = setOf("pro"), style = "discoball", collection = PRO_EXCLUSIVE, keywords = "balada disco espelhado espelho exclusivo festa globo luzes pro"),
        Wallpaper("px_gota_de_mercurio", "Gota de Mercúrio", 0, 0xFF7F4DFF, plans = setOf("pro"), style = "mercury", collection = PRO_EXCLUSIVE, keywords = "3d abstrato cromo exclusivo gota liquido mercurio metal pro"),
        Wallpaper("px_lanternas_no_lago", "Lanternas no Lago", 0, 0xFFFF4A00, plans = setOf("pro"), style = "lanterns", collection = PRO_EXCLUSIVE, keywords = "exclusivo festival lago lanternas luzes noite pro"),
        Wallpaper("px_mandala_de_luz", "Mandala de Luz", 0, 0xFFBD33FF, plans = setOf("pro"), style = "mandala", collection = PRO_EXCLUSIVE, keywords = "exclusivo geometria luz mandala neon pro"),
        Wallpaper("px_mar_bioluminescente", "Mar Bioluminescente", 0, 0xFF00C2FF, plans = setOf("pro"), style = "biolum", collection = PRO_EXCLUSIVE, keywords = "azul bioluminescente brilho exclusivo mar noite ondas praia pro"),
        Wallpaper("px_montanhas_em_nanquim", "Montanhas em Nanquim", 0, 0xFFFFD680, plans = setOf("pro"), style = "inkwash", collection = PRO_EXCLUSIVE, keywords = "arte exclusivo montanhas nanquim oriental pintura pro sol vermelho"),
        Wallpaper("px_papel_recortado", "Papel Recortado", 0, 0xFFFF3334, plans = setOf("pro"), style = "papercut", collection = PRO_EXCLUSIVE, keywords = "arte camadas exclusivo montanhas papel por pro recortado sol"),
        Wallpaper("px_pena_de_pavao", "Pena de Pavão", 0, 0xFF33FF63, plans = setOf("pro"), style = "peacock", collection = PRO_EXCLUSIVE, keywords = "azul exclusivo natureza pavao pena pro verde"),
        Wallpaper("px_show_ao_vivo", "Show ao Vivo", 0, 0xFFD533FF, plans = setOf("pro"), style = "concert", collection = PRO_EXCLUSIVE, keywords = "exclusivo festa luzes musica palco pro show vivo"),
        Wallpaper("px_teclas_de_piano", "Teclas de Piano", 0, 0xFFFFE080, plans = setOf("pro"), style = "piano", collection = PRO_EXCLUSIVE, keywords = "classico exclusivo musica piano pro teclado teclas"),
        Wallpaper("px_tinta_na_agua", "Tinta na Água", 0, 0xFFFF3393, plans = setOf("pro"), style = "ink", collection = PRO_EXCLUSIVE, keywords = "abstrato agua cores exclusivo fluido pro tinta"),
        Wallpaper("px_vitral", "Vitral", 0, 0xFFFF6D00, plans = setOf("pro"), style = "stainedglass", collection = PRO_EXCLUSIVE, keywords = "colorido exclusivo igreja luz pro rosacea vidro vitral"),
        Wallpaper("px_vulcao_em_erupcao", "Vulcão em Erupção", 0, 0xFFFF4F00, plans = setOf("pro"), style = "volcano", collection = PRO_EXCLUSIVE, keywords = "3d erupcao exclusivo fogo lava montanha natureza noite pro vulcao"),
        // ---- Lugares Famosos (16)
        Wallpaper("hx_toquio_chuva", "Beco de Tóquio", 299, 0xFFFFD125, style = "ai", collection = PLACES, keywords = "beco chuva cidade japao lanternas neon noite rua toquio"),
        Wallpaper("hx_nova_york", "Nova York à Noite", 0, 0xFFFF8A00, plans = setOf("go"), style = "ai", collection = PLACES, keywords = "cidade eua luzes manhattan noite nova predios york"),
        Wallpaper("hx_kyoto_torii", "Caminho dos Torii", 0, 0xFFFF4F00, plans = setOf("plus"), style = "ai", collection = PLACES, keywords = "caminho dos japao kyoto templo torii vermelho"),
        Wallpaper("hx_positano", "Costa Amalfitana", 0, 0xFFFF7300, plans = setOf("plus"), style = "ai", collection = PLACES, keywords = "amalfitana casas coloridas costa italia mar positano verao"),
        Wallpaper("hx_moraine", "Lago Moraine", 0, 0xFFFFAC33, plans = setOf("plus"), style = "ai", collection = PLACES, keywords = "canada lago montanhas moraine natureza pinheiros turquesa"),
        Wallpaper("hx_lencois", "Lençóis ao Pôr do Sol", 0, 0xFFFF8B01, plans = setOf("plus"), style = "ai", collection = PLACES, keywords = "brasil deserto dunas lagoas lencois maranhao por sol"),
        Wallpaper("hx_machu_picchu", "Machu Picchu", 0, 0xFFFFAE00, plans = setOf("plus"), style = "ai", collection = PLACES, keywords = "amanhecer inca machu montanhas nuvens peru picchu ruinas"),
        Wallpaper("hx_paris_amanhecer", "Paris ao Amanhecer", 0, 0xFFFF9E33, plans = setOf("plus"), style = "ai", collection = PLACES, keywords = "amanhecer cidade franca paris telhados torre viagem"),
        Wallpaper("hx_piramides", "Pirâmides sob as Estrelas", 0, 0xFFFF9D33, plans = setOf("plus"), style = "ai", collection = PLACES, keywords = "deserto egito estrelas lactea noite piramides sob via"),
        Wallpaper("hx_veneza", "Veneza", 0, 0xFFFF9600, plans = setOf("plus"), style = "ai", collection = PLACES, keywords = "agua canal cidade gondola italia noite veneza viagem"),
        Wallpaper("hx_islandia_aurora", "Aurora na Islândia", 0, 0xFF00FF91, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "aurora boreal cachoeira estrelas islandia montanha noite"),
        Wallpaper("hx_iguacu", "Cataratas do Iguaçu", 0, 0xFFFF8100, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "arco brasil cachoeira cataratas foz iguacu iris natureza"),
        Wallpaper("hx_maldivas", "Maldivas", 0, 0xFF00F0FF, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "bangalo ferias ilha maldivas mar paraiso praia turquesa"),
        Wallpaper("hx_fuji_cerejeiras", "Monte Fuji e Cerejeiras", 0, 0xFFFF3339, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "cerejeira cerejeiras flores fuji japao montanha monte pagode sakura"),
        Wallpaper("hx_rio_entardecer", "Rio ao Entardecer", 0, 0xFFFF7600, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "acucar brasil cidade entardecer janeiro mar pao por rio sol"),
        Wallpaper("hx_santorini", "Santorini", 0, 0xFFFF9F1E, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "brancas casas grecia ilha mar por santorini sol viagem"),
        // ---- Anime & Games (14)
        Wallpaper("hx_trem_anime", "Trem ao Entardecer", 249, 0xFFFF8603, style = "ai", collection = ANIME, keywords = "anime desenho entardecer ferrovia por sol trem verao"),
        Wallpaper("hx_setup_gamer", "Setup Gamer", 0, 0xFF894DFF, plans = setOf("go"), style = "ai", collection = ANIME, keywords = "computador gamer jogos neon quarto rgb setup"),
        Wallpaper("hx_vila_pixel", "Vila Pixel", 0, 0xFFFF8200, plans = setOf("go"), style = "ai", collection = ANIME, keywords = "aconchegante art game noite pixel retro vagalumes vila"),
        Wallpaper("hx_astronauta_anime", "Garota Astronauta", 0, 0xFF5C5EFF, plans = setOf("plus"), style = "ai", collection = ANIME, keywords = "anime astronauta espaco estrelas garota planeta"),
        Wallpaper("hx_guerreira", "Guerreira de Fogo", 0, 0xFFFF7200, plans = setOf("plus"), style = "ai", collection = ANIME, keywords = "espada fantasia fogo game guerreira heroina rpg"),
        Wallpaper("hx_ilhas_flutuantes", "Ilhas Flutuantes", 0, 0xFFFFBA33, plans = setOf("plus"), style = "ai", collection = ANIME, keywords = "cachoeira castelo fantasia flutuantes game ilhas"),
        Wallpaper("hx_ninja_lua", "Ninja na Lua Cheia", 0, 0xFFFFA600, plans = setOf("plus"), style = "ai", collection = ANIME, keywords = "anime cheia japao lua ninja noite telhado"),
        Wallpaper("hx_mecha", "Robô Gigante", 0, 0xFF3367FF, plans = setOf("plus"), style = "ai", collection = ANIME, keywords = "anime chuva cidade gigante mecha noite robo"),
        Wallpaper("hx_ceu_anime", "Céu de Anime", 0, 0xFFFFC000, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "anime ceu desenho nuvens paisagem por sol"),
        Wallpaper("hx_cyberpunk", "Cidade Cyberpunk", 0, 0xFFF133FF, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "carros cidade cyberpunk futuro game neon voadores"),
        Wallpaper("hx_dragao", "Dragão nas Montanhas", 0, 0xFFFF6300, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "dragao epico fantasia game montanhas nas voando"),
        Wallpaper("hx_quarto_lofi", "Quarto Lo-fi", 0, 0xFFFFB433, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "anime chuva fones gato lofi musica noite quarto"),
        Wallpaper("hx_raposa_espiritual", "Raposa Espiritual", 0, 0xFF3370FF, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "anime espirito espiritual floresta kitsune magica raposa"),
        Wallpaper("hx_samurai", "Samurai e Cerejeiras", 0, 0xFFFF9080, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "anime cerejeira cerejeiras espada guerreiro japao lua samurai"),
        // ---- Carros & Motos (6)
        Wallpaper("hx_offroad", "Off-road na Lama", 199, 0xFFE0FF33, style = "ai", collection = CARS, keywords = "4x4 aventura carro lama off offroad road trilha"),
        Wallpaper("hx_moto_estrada", "Moto na Estrada", 0, 0xFFFF8800, plans = setOf("go"), style = "ai", collection = CARS, keywords = "curva esportiva estrada mar moto por sol velocidade"),
        Wallpaper("hx_muscle_deserto", "Muscle Car no Deserto", 0, 0xFFFF9800, plans = setOf("go"), style = "ai", collection = CARS, keywords = "car carro classico deserto estrada muscle por sol"),
        Wallpaper("hx_garagem", "Garagem dos Sonhos", 0, 0xFFFF840F, plans = setOf("plus"), style = "ai", collection = CARS, keywords = "carro dos garagem luxo luzes sonhos supercarro"),
        Wallpaper("hx_moto_cyber", "Moto Cyberpunk", 0, 0xFFB233FF, plans = setOf("plus"), style = "ai", collection = CARS, keywords = "cidade cyberpunk futurista moto neon noite"),
        Wallpaper("hx_hipercarro", "Hipercarro Neon", 0, 0xFFFF6B00, plans = setOf("pro"), style = "ai", collection = CARS, keywords = "carro esportivo hipercarro neon tunel velocidade"),
        // ---- Natureza & Animais (20)
        Wallpaper("hx_beija_flor", "Beija-flor", 199, 0xFFDDFF00, style = "ai", collection = NATURE, keywords = "beija colorido flor passaro"),
        Wallpaper("hx_girassois", "Campo de Girassóis", 199, 0xFFFF8F00, style = "ai", collection = NATURE, keywords = "campo flores girassois girassol por sol verao"),
        Wallpaper("hx_filhote", "Filhote de Cachorro", 249, 0xFFFFA600, style = "ai", collection = NATURE, keywords = "animal cachorro filhote flores fofo golden"),
        Wallpaper("hx_gatinho", "Gatinho", 249, 0xFFFF8100, style = "ai", collection = NATURE, keywords = "animal filhote fofo gatinho gato"),
        Wallpaper("hx_amazonia", "Rio Amazonas", 249, 0xFFFFB00B, style = "ai", collection = NATURE, keywords = "amanhecer amazonas amazonia brasil floresta neblina rio"),
        Wallpaper("hx_aguia", "Águia", 0, 0xFF008EFF, plans = setOf("go"), style = "ai", collection = NATURE, keywords = "aguia animal montanhas passaro voo"),
        Wallpaper("hx_borboleta_azul", "Borboleta Azul", 0, 0xFF339CFF, plans = setOf("go"), style = "ai", collection = NATURE, keywords = "azul borboleta flores morpho"),
        Wallpaper("hx_carpas", "Carpas Koi", 0, 0xFFFF5B00, plans = setOf("go"), style = "ai", collection = NATURE, keywords = "carpas japao koi lago lotus peixes"),
        Wallpaper("hx_rosas_orvalho", "Rosas com Orvalho", 0, 0xFFFF0011, plans = setOf("go"), style = "ai", collection = NATURE, keywords = "com flor orvalho romantico rosa rosas vermelha"),
        Wallpaper("hx_araras", "Araras na Amazônia", 0, 0xFFE8FF33, plans = setOf("plus"), style = "ai", collection = NATURE, keywords = "amazonia arara araras brasil floresta passaro vermelho"),
        Wallpaper("hx_cavalo_praia", "Cavalo na Praia", 0, 0xFFFFA507, plans = setOf("plus"), style = "ai", collection = NATURE, keywords = "animal branco cavalo mar por praia sol"),
        Wallpaper("hx_cerejeira", "Cerejeira Solitária", 0, 0xFFFF4233, plans = setOf("plus"), style = "ai", collection = NATURE, keywords = "arvore cerejeira estrelas flores noite sakura solitaria"),
        Wallpaper("hx_jubarte", "Jubarte no Azul", 0, 0xFFFF9933, plans = setOf("plus"), style = "ai", collection = NATURE, keywords = "azul baleia fundo jubarte mar oceano raios sol"),
        Wallpaper("hx_lua_gigante", "Lua Gigante", 0, 0xFFFF9F21, plans = setOf("plus"), style = "ai", collection = NATURE, keywords = "cheia gigante lua montanha noite"),
        Wallpaper("hx_saturno", "Saturno", 0, 0xFFFFAF33, plans = setOf("plus"), style = "ai", collection = NATURE, keywords = "aneis espaco planeta saturno"),
        Wallpaper("hx_tigre", "Tigre na Selva", 0, 0xFFFFBA1B, plans = setOf("plus"), style = "ai", collection = NATURE, keywords = "animal floresta olhar selva tigre"),
        Wallpaper("hx_astronauta", "Astronauta e a Terra", 0, 0xFFFF5A00, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "astronauta espaco nascer planeta sol terra"),
        Wallpaper("hx_leao", "Leão ao Pôr do Sol", 0, 0xFFFF9100, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "africa animal leao por rei savana sol"),
        Wallpaper("hx_lobo_lua", "Lobo e a Lua", 0, 0xFF0377FF, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "animal cheia lobo lua neve noite uivando"),
        Wallpaper("hx_onca", "Onça-Pintada", 0, 0xFFFFAF33, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "animal brasil jaguar onca pantanal pintada rio"),
        // ---- Populares (14)
        Wallpaper("hx_nuvens_rosa", "Nuvens de Algodão", 0, 0xFFFF4333, style = "ai", collection = POPULAR, keywords = "aesthetic algodao ceu nuvens pastel rosa"),
        Wallpaper("hx_chuva_janela", "Chuva na Janela", 199, 0xFFFF9523, style = "ai", collection = POPULAR, keywords = "bokeh chuva cidade gotas janela noite"),
        Wallpaper("hx_pomba_paz", "Pomba da Paz", 199, 0xFFFF9F00, style = "ai", collection = POPULAR, keywords = "espirito luz paz pomba religiao"),
        Wallpaper("hx_vidro_colorido", "Vidro Colorido", 199, 0xFFFFE400, style = "ai", collection = POPULAR, keywords = "abstrato colorido formas gradiente moderno vidro"),
        Wallpaper("hx_bola_chuva", "Bola na Chuva", 249, 0xFFE2FF33, style = "ai", collection = POPULAR, keywords = "bola chuva futebol gramado refletor"),
        Wallpaper("hx_cruz_amanhecer", "Cruz ao Amanhecer", 249, 0xFFFFA629, style = "ai", collection = POPULAR, keywords = "amanhecer cruz deus luz religiao"),
        Wallpaper("hx_festival", "Festival na Praia", 249, 0xFFFF5000, style = "ai", collection = POPULAR, keywords = "festival multidao musica por praia show sol"),
        Wallpaper("hx_rede_praia", "Rede na Praia", 249, 0xFF1BFAFF, style = "ai", collection = POPULAR, keywords = "coqueiro ferias mar nordeste praia rede turquesa"),
        Wallpaper("hx_microfone", "Microfone no Palco", 0, 0xFFFF8500, plans = setOf("go"), style = "ai", collection = POPULAR, keywords = "fumaca luzes microfone musica palco show"),
        Wallpaper("hx_estadio", "Estádio à Noite", 0, 0xFFD3FF04, plans = setOf("plus"), style = "ai", collection = POPULAR, keywords = "bola estadio futebol noite refletores"),
        Wallpaper("hx_marmore_ouro", "Mármore Negro e Ouro", 0, 0xFFFFB033, plans = setOf("plus"), style = "ai", collection = POPULAR, keywords = "dourado elegante luxo marmore negro ouro preto"),
        Wallpaper("hx_ondas_3d", "Ondas 3D", 0, 0xFF7557FF, plans = setOf("plus"), style = "ai", collection = POPULAR, keywords = "abstrato fluido iridescente ondas"),
        Wallpaper("hx_ouro_liquido", "Ouro Líquido", 0, 0xFFFFAF33, plans = setOf("pro"), style = "ai", collection = POPULAR, keywords = "abstrato dourado liquido luxo ouro"),
        Wallpaper("hx_vinil_cafe", "Vinil e Café", 0, 0xFFFF9015, plans = setOf("pro"), style = "ai", collection = POPULAR, keywords = "aconchegante cafe disco discos musica toca vinil"),
        // ---- Natureza 3D (4)
        Wallpaper("nt_alpes_ao_por_do_sol", "Alpes ao Pôr do Sol", 249, 0xFFFF621B, style = "alpine", collection = NATURE3D, keywords = "3d alpes montanha natureza paisagem por sol"),
        Wallpaper("nt_aurora_artica", "Aurora Ártica", 249, 0xFF00FFD2, style = "render3d", collection = NATURE3D, keywords = "3d montanhas natureza noite"),
        Wallpaper("nt_fiorde_dourado", "Fiorde Dourado", 249, 0xFF5C5EFF, style = "alpine", collection = NATURE3D, keywords = "3d dourado fiorde montanha natureza paisagem"),
        Wallpaper("nt_via_lactea_montanhas", "Via Láctea nas Montanhas", 249, 0xFFFF9980, style = "alpine", collection = NATURE3D, keywords = "3d lactea montanha montanhas nas natureza paisagem via"),
        // ---- Coleção clássica (3)
        Wallpaper("nevoa_verde", "Névoa Verde", 0, 0xFF33FFB8, style = "mountains", keywords = "montanhas natureza"),
        Wallpaper("noite_estrelada", "Noite Estrelada", 0, 0xFF809BFF, style = "mountains", keywords = "montanhas natureza noite"),
        Wallpaper("cl_planeta_violeta", "Planeta Violeta", 249, 0xFF9B3DFF, style = "planet", keywords = "espaco planeta universo violeta"),
        // ---- Heróis & Vilões (1)
        Wallpaper("hv_furia_tempestade", "Fúria da Tempestade", 199, 0xFF33A2FF, style = "storm", collection = HEROES, keywords = "herois raio tempestade"),
        // ---- Premium 3D (6)
        Wallpaper("pm_seda_rubi", "Seda Rubi", 199, 0xFFFF0034, style = "liquidwave", collection = PREMIUM, keywords = "3d abstrato premium rubi seda"),
        Wallpaper("pm_aguas_vivas", "Águas-vivas", 249, 0xFFF733FF, style = "jellyfish", collection = PREMIUM, keywords = "agua aguas mar neon oceano viva vivas"),
        Wallpaper("pm_colmeia_neon", "Colmeia Neon", 249, 0xFFF012FF, style = "hex3d", collection = PREMIUM, keywords = "3d abstrato colmeia neon premium"),
        Wallpaper("pm_mar_ao_luar", "Mar ao Luar", 249, 0xFF3867FF, style = "sunsea", collection = PREMIUM, keywords = "3d abstrato luar mar premium"),
        Wallpaper("pm_orbe_rubi", "Orbe Rubi", 249, 0xFFFF212C, style = "glass", collection = PREMIUM, keywords = "3d abstrato orbe premium rubi"),
        Wallpaper("pm_sol_de_fogo", "Sol de Fogo", 299, 0xFFFF3F00, style = "sunsea", collection = PREMIUM, keywords = "3d do mar natureza por premium sol"),
        // ---- Inspirações (9)
        Wallpaper("rf_cara_de_raiva", "Cara de Raiva", 149, 0xFFFF0000, style = "art", collection = INSPIRATIONS, keywords = "bravo cara emoji raiva rosto olhos"),
        Wallpaper("rf_olhos_tristes", "Olhos Chorando", 149, 0xFF80C6FF, style = "eyes7", collection = INSPIRATIONS, keywords = "chorando emoji fofo olhos rosto"),
        Wallpaper("rf_coracao_apaixonado", "Coração Apaixonado", 249, 0xFFFF0021, style = "love_heart2", collection = INSPIRATIONS, keywords = "apaixonado coracao emoji fofo rosto"),
        Wallpaper("rf_coracao_partido_de_vidro", "Coração Partido de Vidro", 249, 0xFF2E65FF, style = "diamond_heart", collection = INSPIRATIONS, keywords = "coracao emoji fofo partido rosto vidro"),
        Wallpaper("rf_gelo_esmeralda", "Gelo Esmeralda", 249, 0xFF33FFD4, style = "icysea", collection = INSPIRATIONS, keywords = "3d esmeralda gelo montanha natureza paisagem"),
        Wallpaper("rf_lago_lilas", "Lago Lilás", 249, 0xFFC933FF, style = "lake", collection = INSPIRATIONS, keywords = "3d lago lilas montanha natureza paisagem"),
        Wallpaper("rf_olhos_apaixonados", "Olhos Apaixonados", 249, 0xFFFF004F, style = "eyes6", collection = INSPIRATIONS, keywords = "apaixonados emoji fofo olhos rosto"),
        Wallpaper("rf_terra_noturna", "Terra Noturna", 249, 0xFF33ACFF, style = "earth", collection = INSPIRATIONS, keywords = "espaco noite planeta terra"),
        Wallpaper("rf_folhas_outono", "Folhas de Outono", 299, 0xFFFF6800, style = "leaves", collection = INSPIRATIONS, keywords = "flores folhas natureza outono"),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String?): Wallpaper? = id?.let { byId[it] }

    /** "R$ 1,99" a partir de centavos. */
    fun priceText(cents: Int): String = "R$ " + "%d,%02d".format(cents / 100, cents % 100)

    /** Faixas de preço vendidas na Play (um produto por faixa). */
    val tierProducts: List<String> get() = all.filter { !it.free && !it.pro }.map { it.productId }.distinct()

    /** Texto normalizado (sem acento, minúsculo) para comparar na busca. */
    fun normalize(text: String): String =
        java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private val searchText: Map<String, String> by lazy {
        all.associate { it.id to normalize(it.name + " " + it.keywords) }
    }

    /** O tema combina com a busca? (todas as palavras digitadas precisam aparecer no nome ou nas palavras-chave) */
    fun matches(w: Wallpaper, query: String): Boolean {
        val words = normalize(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val text = searchText[w.id] ?: return false
        return words.all { it in text }
    }

    /** Papel padrão do navegador (grátis). */
    const val DEFAULT_BROWSER = "noite_estrelada"
}
