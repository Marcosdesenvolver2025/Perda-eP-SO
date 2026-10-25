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
 * Catálogo dos temas (313): fotos, Natureza 3D, Games 3D e as melhores coleções antigas.
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

    /** Coleção "Fotos": paisagens, animais, flores, cidades e praias (fotos enviadas pelo Marcos). */
    const val PHOTOS = "fotos"

    val all: List<Wallpaper> = listOf(
        // ---- Fotos (64)
        Wallpaper("ft_cachoeira", "Cachoeira", 0, 0xFFE0FF00, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "cachoeira foto montanhas natureza"),
        Wallpaper("ft_golfinhos", "Golfinhos", 0, 0xFF00A9FF, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "animais animal foto golfinho mar natureza oceano"),
        Wallpaper("ft_lencois_maranhenses", "Lençóis Maranhenses", 0, 0xFF3393FF, plans = setOf("go"), style = "photo", collection = PHOTOS, keywords = "brasil deserto dunas foto lagoas natureza"),
        Wallpaper("ft_ondas_turquesa", "Ondas Turquesa", 0, 0xFF00F1FF, plans = setOf("plus"), style = "photo", collection = PHOTOS, keywords = "aereo foto mar natureza ondas praia"),
        Wallpaper("ft_pier_no_lago", "Píer no Lago", 0, 0xFF2D91FF, style = "photo", collection = PHOTOS, keywords = "foto lago montanhas natureza pier"),
        Wallpaper("ft_raio_na_planicie", "Raio na Planície", 0, 0xFF339BFF, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "foto natureza nuvens raio tempestade"),
        Wallpaper("ft_raposa", "Raposa", 0, 0xFFA7FF00, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "animais animal foto natureza raposa"),
        Wallpaper("ft_salar_ao_entardecer", "Salar ao Entardecer", 0, 0xFF3374FF, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "deserto do foto natureza por sal sol"),
        Wallpaper("ft_tartaruga_marinha", "Tartaruga Marinha", 0, 0xFF00BCFF, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "animais animal coral foto mar natureza oceano tartaruga"),
        Wallpaper("ft_vale_das_palmeiras", "Vale das Palmeiras", 0, 0xFFD4FF00, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "foto montanhas natureza neblina palmeiras vale"),
        Wallpaper("ft_cogumelos", "Cogumelos", 99, 0xFFFF851B, style = "photo", collection = PHOTOS, keywords = "cogumelos floresta foto natureza"),
        Wallpaper("ft_corredor_de_concreto", "Corredor de Concreto", 99, 0xFFFFF318, style = "photo", collection = PHOTOS, keywords = "arquitetura cidade foto minimalista"),
        Wallpaper("ft_folha_de_bordo", "Folha de Bordo", 99, 0xFFFF0306, style = "photo", collection = PHOTOS, keywords = "folha foto natureza outono vermelho"),
        Wallpaper("ft_galantos_na_neve", "Galantos na Neve", 99, 0xFFDBFF2B, style = "photo", collection = PHOTOS, keywords = "flor flores foto inverno natureza neve"),
        Wallpaper("ft_gotas_de_chuva", "Gotas de Chuva", 99, 0xFF338DFF, style = "photo", collection = PHOTOS, keywords = "agua chuva foto gotas natureza"),
        Wallpaper("ft_bicicleta_no_campo", "Bicicleta no Campo", 149, 0xFFFFE600, style = "photo", collection = PHOTOS, keywords = "bicicleta campo flores foto natureza"),
        Wallpaper("ft_camelo", "Camelo", 149, 0xFFFF9433, style = "photo", collection = PHOTOS, keywords = "animais animal camelo deserto foto natureza"),
        Wallpaper("ft_canoa_vermelha", "Canoa Vermelha", 149, 0xFF3396FF, style = "photo", collection = PHOTOS, keywords = "canoa foto lago natureza neblina"),
        Wallpaper("ft_canoa_na_nevoa", "Canoa na Névoa", 149, 0xFF80ABFF, style = "photo", collection = PHOTOS, keywords = "canoa foto lago natureza neblina"),
        Wallpaper("ft_carvalho", "Carvalho", 149, 0xFFFFEC00, style = "photo", collection = PHOTOS, keywords = "arvore campo foto natureza verde"),
        Wallpaper("ft_foca", "Foca", 149, 0xFF0085FF, style = "photo", collection = PHOTOS, keywords = "animais animal foca foto mar natureza praia"),
        Wallpaper("ft_lontra_marinha", "Lontra-Marinha", 149, 0xFF0094FF, style = "photo", collection = PHOTOS, keywords = "animais animal foto lontra mar natureza"),
        Wallpaper("ft_luz_no_celeiro", "Luz no Celeiro", 149, 0xFFFF8B19, style = "photo", collection = PHOTOS, keywords = "celeiro fazenda feno foto luz natureza"),
        Wallpaper("ft_martim_pescador", "Martim-Pescador", 149, 0xFFFF7700, style = "photo", collection = PHOTOS, keywords = "animais animal aves foto natureza"),
        Wallpaper("ft_moinho_de_vento", "Moinho de Vento", 149, 0xFFFFA600, style = "photo", collection = PHOTOS, keywords = "campo do foto moinho natureza por sol"),
        Wallpaper("ft_muros_de_pedra", "Muros de Pedra", 149, 0xFFE0FF00, style = "photo", collection = PHOTOS, keywords = "campo colinas foto natureza verde"),
        Wallpaper("ft_perereca", "Perereca", 149, 0xFFA6FF29, style = "photo", collection = PHOTOS, keywords = "animais animal foto natureza sapo verde"),
        Wallpaper("ft_pisco_no_inverno", "Pisco no Inverno", 149, 0xFFFF7D00, style = "photo", collection = PHOTOS, keywords = "animais animal aves foto inverno natureza"),
        Wallpaper("ft_pocas_da_mare", "Poças da Maré", 149, 0xFFFF9300, style = "photo", collection = PHOTOS, keywords = "costa foto mar natureza pedras"),
        Wallpaper("ft_riacho_de_pedras", "Riacho de Pedras", 149, 0xFFFFB233, style = "photo", collection = PHOTOS, keywords = "agua foto natureza pedras rio"),
        Wallpaper("ft_suindara", "Suindara", 149, 0xFFFF7300, style = "photo", collection = PHOTOS, keywords = "animais animal aves coruja foto natureza"),
        Wallpaper("ft_bambuzal", "Bambuzal", 199, 0xFFCDFF00, style = "photo", collection = PHOTOS, keywords = "bambu floresta foto natureza trilha verde"),
        Wallpaper("ft_baoba", "Baobá", 199, 0xFFFF6000, style = "photo", collection = PHOTOS, keywords = "africa arvore do foto natureza por savana sol"),
        Wallpaper("ft_barco_no_lago_sereno", "Barco no Lago Sereno", 199, 0xFF337CFF, style = "photo", collection = PHOTOS, keywords = "barco foto lago natureza noite"),
        Wallpaper("ft_baia_rosada", "Baía Rosada", 199, 0xFFFF3F33, style = "photo", collection = PHOTOS, keywords = "do foto mar natureza por rosa sol"),
        Wallpaper("ft_cabana_no_lago", "Cabana no Lago", 199, 0xFFFF9200, style = "photo", collection = PHOTOS, keywords = "cabana foto lago natureza neblina outono"),
        Wallpaper("ft_caminho_nevado", "Caminho Nevado", 199, 0xFF1A68FF, style = "photo", collection = PHOTOS, keywords = "arvores estrada foto inverno natureza neve"),
        Wallpaper("ft_carroca_de_outono", "Carroça de Outono", 199, 0xFFFFA700, style = "photo", collection = PHOTOS, keywords = "cavalo fazenda foto natureza outono"),
        Wallpaper("ft_casinha_vermelha", "Casinha Vermelha", 199, 0xFF3388FF, style = "photo", collection = PHOTOS, keywords = "casa foto lago montanhas natureza"),
        Wallpaper("ft_colinas_lilases", "Colinas Lilases", 199, 0xFF336BFF, style = "photo", collection = PHOTOS, keywords = "colinas deserto foto natureza roxo"),
        Wallpaper("ft_coqueiro_na_praia", "Coqueiro na Praia", 199, 0xFF33A3FF, style = "photo", collection = PHOTOS, keywords = "coqueiro foto mar natureza praia tropical"),
        Wallpaper("ft_costa_rochosa", "Costa Rochosa", 199, 0xFFFFE121, style = "photo", collection = PHOTOS, keywords = "costa foto mar natureza ondas pedras"),
        Wallpaper("ft_dunas_alaranjadas", "Dunas Alaranjadas", 199, 0xFFFF6F00, style = "photo", collection = PHOTOS, keywords = "areia deserto dunas foto natureza"),
        Wallpaper("ft_farol", "Farol", 199, 0xFF339EFF, style = "photo", collection = PHOTOS, keywords = "costa farol foto mar natureza"),
        Wallpaper("ft_gato_preto", "Gato Preto", 199, 0xFFFFD600, style = "photo", collection = PHOTOS, keywords = "animais animal foto gato natureza"),
        Wallpaper("ft_gol_na_neblina", "Gol na Neblina", 199, 0xFFFFF21D, style = "photo", collection = PHOTOS, keywords = "campo foto futebol gol natureza neblina"),
        Wallpaper("ft_gorila", "Gorila", 199, 0xFFCBFF00, style = "photo", collection = PHOTOS, keywords = "animais animal floresta foto gorila natureza"),
        Wallpaper("ft_lua_sobre_o_deserto", "Lua sobre o Deserto", 199, 0xFF3377FF, style = "photo", collection = PHOTOS, keywords = "deserto foto lua natureza noite"),
        Wallpaper("ft_mesa_do_deserto", "Mesa do Deserto", 199, 0xFFFF7501, style = "photo", collection = PHOTOS, keywords = "canion deserto foto montanha natureza"),
        Wallpaper("ft_revoada_ao_por_do_sol", "Revoada ao Pôr do Sol", 199, 0xFFFF5933, style = "photo", collection = PHOTOS, keywords = "aves do foto lago natureza passaros por sol"),
        Wallpaper("ft_rosa_vermelha", "Rosa Vermelha", 199, 0xFFFF0021, style = "photo", collection = PHOTOS, keywords = "amor flor flores foto natureza rosa vermelha"),
        Wallpaper("ft_tucano", "Tucano", 199, 0xFFE1FF00, style = "photo", collection = PHOTOS, keywords = "animais animal aves foto natureza tropical tucano"),
        Wallpaper("ft_urzes_nas_colinas", "Urzes nas Colinas", 199, 0xFFFFD600, style = "photo", collection = PHOTOS, keywords = "colinas flores foto lago natureza"),
        Wallpaper("ft_vale_de_outono", "Vale de Outono", 199, 0xFFFF8527, style = "photo", collection = PHOTOS, keywords = "foto lago montanhas natureza outono vale"),
        Wallpaper("ft_casa_de_pedra_na_montanha", "Casa de Pedra na Montanha", 249, 0xFFFF8C00, style = "photo", collection = PHOTOS, keywords = "casa foto montanhas natureza outono"),
        Wallpaper("ft_ceu_rosado_na_praia", "Céu Rosado na Praia", 249, 0xFFFF6A33, style = "photo", collection = PHOTOS, keywords = "do foto natureza por praia rosa sol"),
        Wallpaper("ft_enseada_verde", "Enseada Verde", 249, 0xFFC8FF00, style = "photo", collection = PHOTOS, keywords = "costa foto mar natureza praia verde"),
        Wallpaper("ft_lago_da_cratera", "Lago da Cratera", 249, 0xFFFFF10F, style = "photo", collection = PHOTOS, keywords = "foto lago montanhas natureza vulcao"),
        Wallpaper("ft_mirante_do_mediterraneo", "Mirante do Mediterrâneo", 249, 0xFF339AFF, style = "photo", collection = PHOTOS, keywords = "costa foto mar montanhas natureza"),
        Wallpaper("ft_onda_de_vidro", "Onda de Vidro", 249, 0xFF3392FF, style = "photo", collection = PHOTOS, keywords = "foto mar natureza onda surf"),
        Wallpaper("ft_tempestade_no_campo", "Tempestade no Campo", 249, 0xFF3384FF, style = "photo", collection = PHOTOS, keywords = "celeiro chuva fazenda foto natureza nuvens"),
        Wallpaper("ft_terracos_de_arroz", "Terraços de Arroz", 249, 0xFFFFE200, style = "photo", collection = PHOTOS, keywords = "campo foto montanhas natureza neblina verde"),
        Wallpaper("ft_veleiro_no_azul", "Veleiro no Azul", 249, 0xFF018CFF, style = "photo", collection = PHOTOS, keywords = "azul barco foto mar natureza veleiro"),
        Wallpaper("ft_vista_da_cupula", "Vista da Cúpula", 249, 0xFFFF9833, style = "photo", collection = PHOTOS, keywords = "cidade foto italia telhados"),
        // ---- Natureza 3D (18)
        Wallpaper("nt_alpes_amanhecer", "Alpes ao Amanhecer", 0, 0xFF336BFF, plans = setOf("pro"), style = "render3d", collection = NATURE3D, keywords = "3d montanhas natureza"),
        Wallpaper("nt_alpes_ao_por_do_sol", "Alpes ao Pôr do Sol", 0, 0xFFFF621B, plans = setOf("plus"), style = "alpine", collection = NATURE3D, keywords = "3d alpes montanha natureza paisagem por sol"),
        Wallpaper("nt_aurora_violeta", "Aurora Violeta", 0, 0xFF8B4DFF, plans = setOf("plus"), style = "alpine", collection = NATURE3D, keywords = "3d aurora montanha natureza paisagem violeta"),
        Wallpaper("nt_aurora_no_fiorde", "Aurora no Fiorde", 0, 0xFF00FFD4, plans = setOf("pro"), style = "alpine", collection = NATURE3D, keywords = "3d aurora fiorde montanha natureza paisagem"),
        Wallpaper("nt_aurora_artica", "Aurora Ártica", 0, 0xFF00FFD2, plans = setOf("pro"), style = "render3d", collection = NATURE3D, keywords = "3d montanhas natureza noite"),
        Wallpaper("nt_fiorde", "Fiorde", 0, 0xFF3393FF, plans = setOf("pro"), style = "render3d", collection = NATURE3D, keywords = "3d mar montanhas natureza"),
        Wallpaper("nt_fiorde_dourado", "Fiorde Dourado", 0, 0xFF5C5EFF, plans = setOf("go"), style = "alpine", collection = NATURE3D, keywords = "3d dourado fiorde montanha natureza paisagem"),
        Wallpaper("nt_inverno_anoitecer", "Inverno ao Anoitecer", 0, 0xFF4D61FF, plans = setOf("pro"), style = "render3d", collection = NATURE3D, keywords = "3d montanhas natureza noite"),
        Wallpaper("nt_lago_esmeralda", "Lago Esmeralda", 0, 0xFF3397FF, plans = setOf("plus"), style = "render3d", collection = NATURE3D, keywords = "3d montanhas natureza"),
        Wallpaper("nt_montanhas_lilases", "Montanhas Lilases", 0, 0xFF6C5CFF, plans = setOf("pro"), style = "alpine", collection = NATURE3D, keywords = "3d lilases montanha montanhas natureza paisagem"),
        Wallpaper("nt_planeta_sobre_as_dunas", "Planeta sobre as Dunas", 0, 0xFFFF9A33, plans = setOf("pro"), style = "dunes3", collection = NATURE3D, keywords = "dunas espaco planeta sobre universo"),
        Wallpaper("nt_planeta_sobre_o_lago", "Planeta sobre o Lago", 0, 0xFF33A9FF, plans = setOf("plus"), style = "alpine", collection = NATURE3D, keywords = "espaco lago planeta sobre universo"),
        Wallpaper("nt_vale_verde", "Vale Verde", 0, 0xFF2E96FF, plans = setOf("pro"), style = "alpine", collection = NATURE3D, keywords = "3d montanha natureza paisagem vale verde"),
        Wallpaper("nt_via_lactea_montanhas", "Via Láctea nas Montanhas", 0, 0xFFFF9980, plans = setOf("pro"), style = "alpine", collection = NATURE3D, keywords = "3d lactea montanha montanhas nas natureza paisagem via"),
        Wallpaper("nt_dunas_douradas", "Dunas Douradas", 199, 0xFF3386FF, style = "render3d", collection = NATURE3D, keywords = "3d deserto natureza"),
        Wallpaper("nt_dunas_estrelas", "Dunas sob as Estrelas", 199, 0xFF4264FF, style = "render3d", collection = NATURE3D, keywords = "3d deserto espaco natureza noite"),
        Wallpaper("nt_alpes_ao_luar", "Alpes ao Luar", 299, 0xFF3865FF, style = "alpine", collection = NATURE3D, keywords = "3d alpes luar montanha natureza paisagem"),
        Wallpaper("nt_dunas_rosadas", "Dunas Rosadas", 299, 0xFF8252FF, style = "dunes3", collection = NATURE3D, keywords = "3d dunas montanha natureza paisagem rosadas"),
        // ---- Games 3D (3)
        Wallpaper("gm_estrada_synthwave", "Estrada Synthwave", 0, 0xFFFF00D7, plans = setOf("pro"), style = "render3d", collection = GAMES3D, keywords = "3d corrida games jogos neon"),
        Wallpaper("gm_mundo_low_poly", "Mundo Low-Poly", 249, 0xFF4D62FF, style = "render3d", collection = GAMES3D, keywords = "3d games jogos montanhas natureza"),
        Wallpaper("gm_mundo_de_blocos", "Mundo de Blocos", 299, 0xFF2E97FF, style = "render3d", collection = GAMES3D, keywords = "3d games jogos natureza"),
        // ---- Coleção clássica (16)
        Wallpaper("cl_eclipse_azul", "Eclipse Azul", 0, 0xFF009EFF, plans = setOf("pro"), style = "planet", keywords = "azul eclipse espaco planeta universo"),
        Wallpaper("noite_estrelada", "Noite Estrelada", 0, 0xFF809BFF, style = "mountains", keywords = "montanhas natureza noite"),
        Wallpaper("nevoa_verde", "Névoa Verde", 0, 0xFF33FFB8, style = "mountains", keywords = "montanhas natureza"),
        Wallpaper("cl_planeta_violeta", "Planeta Violeta", 0, 0xFF9B3DFF, plans = setOf("go"), style = "planet", keywords = "espaco planeta universo violeta"),
        Wallpaper("cl_planeta_de_cristal", "Planeta de Cristal", 0, 0xFFCD33FF, plans = setOf("pro"), style = "planet", keywords = "cristal espaco planeta universo"),
        Wallpaper("nebulosa_azul", "Nebulosa Azul", 99, 0xFF2BB9FF, style = "nebula", keywords = "espaco estrelas nebulosa"),
        Wallpaper("nebulosa_rosa", "Nebulosa Rosa", 99, 0xFFFF33B1, style = "nebula", keywords = "espaco estrelas nebulosa"),
        Wallpaper("pro_eclipse", "Eclipse", 149, 0xFFFF7A00, style = "planet", keywords = "espaco noite planeta"),
        Wallpaper("cl_eclipse_rosa", "Eclipse Rosa", 149, 0xFFFF0DA4, style = "planet", keywords = "eclipse espaco planeta rosa universo"),
        Wallpaper("cl_nevoa_azul", "Névoa Azul", 149, 0xFF3385FF, style = "mountains", keywords = "3d azul montanha natureza nevoa paisagem"),
        Wallpaper("cl_nevoa_ao_amanhecer", "Névoa ao Amanhecer", 149, 0xFFFF5633, style = "mountains", keywords = "3d amanhecer montanha natureza nevoa paisagem"),
        Wallpaper("pro_nebulosa_esmeralda", "Nebulosa Esmeralda", 199, 0xFF33FF9E, style = "nebula", keywords = "espaco estrelas nebulosa"),
        Wallpaper("cl_planeta_azul", "Planeta Azul", 199, 0xFF339BFF, style = "planet", keywords = "azul espaco planeta universo"),
        Wallpaper("cl_planeta_esmeralda", "Planeta Esmeralda", 199, 0xFF33FFB2, style = "planet", keywords = "esmeralda espaco planeta universo"),
        Wallpaper("cl_planeta_incandescente", "Planeta Incandescente", 199, 0xFFFF5A00, style = "planet", keywords = "espaco incandescente planeta universo"),
        Wallpaper("planeta_gelo", "Planeta de Gelo", 199, 0xFF33BEFF, style = "planet", keywords = "espaco planeta"),
        // ---- Heróis & Vilões (15)
        Wallpaper("hv_escudo_azul", "Escudo Azul", 0, 0xFF1BB3FF, plans = setOf("plus"), style = "hexshield", collection = HEROES, keywords = "3d azul escudo games herois"),
        Wallpaper("hv_fenda_dimensional", "Fenda Dimensional", 0, 0xFF9B38FF, plans = setOf("plus"), style = "rift", collection = HEROES, keywords = "herois raio"),
        Wallpaper("hv_fenda_esmeralda", "Fenda Esmeralda", 0, 0xFF00FF7F, plans = setOf("pro"), style = "rift", collection = HEROES, keywords = "3d esmeralda fenda games herois"),
        Wallpaper("hv_tempestade_violeta", "Tempestade Violeta", 0, 0xFF983DFF, plans = setOf("pro"), style = "storm", collection = HEROES, keywords = "3d games herois tempestade violeta"),
        Wallpaper("hv_impacto_azul", "Impacto Azul", 99, 0xFF006DFF, style = "comic", collection = HEROES, keywords = "herois quadrinhos"),
        Wallpaper("hv_impacto_dourado", "Impacto Dourado", 99, 0xFFFFB300, style = "comic", collection = HEROES, keywords = "3d dourado games herois impacto"),
        Wallpaper("hv_impacto_vermelho", "Impacto Vermelho", 99, 0xFFFF1300, style = "comic", collection = HEROES, keywords = "3d games herois impacto vermelho"),
        Wallpaper("hv_circuito_tatico", "Circuito Tático", 149, 0xFF33E4FF, style = "circuit", collection = HEROES, keywords = "herois neon tecnologia"),
        Wallpaper("hv_circuito_violeta", "Circuito Violeta", 149, 0xFFEB33FF, style = "circuit", collection = HEROES, keywords = "3d circuito games herois violeta"),
        Wallpaper("hv_escudo_dourado", "Escudo Dourado", 149, 0xFFFFCC33, style = "hexshield", collection = HEROES, keywords = "herois neon"),
        Wallpaper("hv_escudo_violeta", "Escudo Violeta", 149, 0xFFAC2FFF, style = "hexshield", collection = HEROES, keywords = "3d escudo games herois violeta"),
        Wallpaper("hv_amanhecer_rosado", "Amanhecer Rosado", 199, 0xFFFF338F, style = "dawncity", collection = HEROES, keywords = "3d amanhecer games herois rosado"),
        Wallpaper("hv_amanhecer_heroi", "Amanhecer do Herói", 199, 0xFFFF8229, style = "dawncity", collection = HEROES, keywords = "cidade cidades herois"),
        Wallpaper("hv_furia_tempestade", "Fúria da Tempestade", 199, 0xFF33A2FF, style = "storm", collection = HEROES, keywords = "herois raio tempestade"),
        Wallpaper("hv_tempestade_rubra", "Tempestade Rubra", 199, 0xFFFF092C, style = "storm", collection = HEROES, keywords = "3d games herois rubra tempestade"),
        // ---- Premium 3D (26)
        Wallpaper("pm_colmeia_neon", "Colmeia Neon", 0, 0xFFF012FF, plans = setOf("pro"), style = "hex3d", collection = PREMIUM, keywords = "3d abstrato colmeia neon premium"),
        Wallpaper("pm_crepusculo_violeta", "Crepúsculo Violeta", 0, 0xFFFF335B, plans = setOf("pro"), style = "sunsea", collection = PREMIUM, keywords = "3d do mar natureza por premium sol"),
        Wallpaper("pm_cubo_dourado", "Cubo Dourado", 0, 0xFFFFA600, plans = setOf("pro"), style = "cubes", collection = PREMIUM, keywords = "3d abstrato cubo dourado premium"),
        Wallpaper("pm_cubo_violeta", "Cubo Violeta", 0, 0xFFAA24FF, plans = setOf("plus"), style = "cubes", collection = PREMIUM, keywords = "3d abstrato cubo premium violeta"),
        Wallpaper("pm_mar_dourado", "Mar Dourado", 0, 0xFFFFAA00, plans = setOf("plus"), style = "sunsea", collection = PREMIUM, keywords = "3d abstrato dourado mar premium"),
        Wallpaper("pm_mar_rosado", "Mar Rosado", 0, 0xFFFF339E, plans = setOf("go"), style = "sunsea", collection = PREMIUM, keywords = "3d abstrato mar premium rosado"),
        Wallpaper("pm_mar_ao_luar", "Mar ao Luar", 0, 0xFF3867FF, plans = setOf("pro"), style = "sunsea", collection = PREMIUM, keywords = "3d abstrato luar mar premium"),
        Wallpaper("pm_orbe_esmeralda", "Orbe Esmeralda", 0, 0xFF55FF33, plans = setOf("pro"), style = "glass", collection = PREMIUM, keywords = "3d abstrato esmeralda orbe premium"),
        Wallpaper("pm_orbe_rubi", "Orbe Rubi", 0, 0xFFFF212C, plans = setOf("pro"), style = "glass", collection = PREMIUM, keywords = "3d abstrato orbe premium rubi"),
        Wallpaper("pm_seda_oceano", "Seda Oceano", 0, 0xFF695CFF, plans = setOf("plus"), style = "liquidwave", collection = PREMIUM, keywords = "3d abstrato oceano premium seda"),
        Wallpaper("pm_sol_de_fogo", "Sol de Fogo", 0, 0xFFFF3F00, plans = setOf("pro"), style = "sunsea", collection = PREMIUM, keywords = "3d do mar natureza por premium sol"),
        Wallpaper("pm_aguas_vivas", "Águas-vivas", 0, 0xFFF733FF, plans = setOf("pro"), style = "jellyfish", collection = PREMIUM, keywords = "agua aguas mar neon oceano viva vivas"),
        Wallpaper("pm_cubo_ciano", "Cubo Ciano", 99, 0xFF00FFEA, style = "cubes", collection = PREMIUM, keywords = "3d abstrato cubos premium"),
        Wallpaper("pm_seda_aurora", "Seda Aurora", 99, 0xFF33FFBF, style = "liquidwave", collection = PREMIUM, keywords = "3d abstrato noite ondas premium"),
        Wallpaper("pm_colmeia_esmeralda", "Colmeia Esmeralda", 199, 0xFF00FFC7, style = "hex3d", collection = PREMIUM, keywords = "3d abstrato colmeia esmeralda premium"),
        Wallpaper("pm_colmeia_noturna", "Colmeia Noturna", 199, 0xFF3384FF, style = "hex3d", collection = PREMIUM, keywords = "3d abstrato colmeia neon noite premium"),
        Wallpaper("pm_colmeia_turquesa", "Colmeia Turquesa", 199, 0xFF06D0FF, style = "hex3d", collection = PREMIUM, keywords = "3d abstrato colmeia premium turquesa"),
        Wallpaper("pm_cubo_azul_neon", "Cubo Azul Neon", 199, 0xFF0077FF, style = "cubes", collection = PREMIUM, keywords = "3d abstrato azul cubo neon premium"),
        Wallpaper("pm_cubo_rubro", "Cubo Rubro", 199, 0xFFFF0013, style = "cubes", collection = PREMIUM, keywords = "3d abstrato cubos premium"),
        Wallpaper("pm_seda_rubi", "Seda Rubi", 199, 0xFFFF0034, style = "liquidwave", collection = PREMIUM, keywords = "3d abstrato premium rubi seda"),
        Wallpaper("pm_seda_solar", "Seda Solar", 199, 0xFFFF9900, style = "liquidwave", collection = PREMIUM, keywords = "3d abstrato premium seda solar"),
        Wallpaper("pm_colmeia_violeta", "Colmeia Violeta", 249, 0xFFEB33FF, style = "hex3d", collection = PREMIUM, keywords = "3d abstrato colmeia neon premium"),
        Wallpaper("pm_mar_carmesim", "Mar Carmesim", 249, 0xFFFF0C10, style = "sunsea", collection = PREMIUM, keywords = "3d do mar natureza por premium sol"),
        Wallpaper("pm_mar_da_hora_azul", "Mar da Hora Azul", 249, 0xFF2E67FF, style = "sunsea", collection = PREMIUM, keywords = "3d abstrato azul hora mar premium"),
        Wallpaper("pm_orbe_noturno", "Orbe Noturno", 249, 0xFFB733FF, style = "glass", collection = PREMIUM, keywords = "3d abstrato esferas noite premium vidro"),
        Wallpaper("pm_sorriso_chuva", "Sorriso na Chuva", 249, 0xFFFFB400, style = "drops", collection = PREMIUM, keywords = "3d abstrato gotas premium"),
        // ---- Inspirações (26)
        Wallpaper("rf_coracao_apaixonado", "Coração Apaixonado", 0, 0xFFFF0021, plans = setOf("pro"), style = "love_heart2", collection = INSPIRATIONS, keywords = "apaixonado coracao emoji fofo rosto"),
        Wallpaper("rf_coracao_partido", "Coração Partido", 0, 0xFFFF0024, plans = setOf("go"), style = "broken_heart", collection = INSPIRATIONS, keywords = "coracao emoji fofo partido rosto"),
        Wallpaper("rf_coracao_partido_de_vidro", "Coração Partido de Vidro", 0, 0xFF2E65FF, plans = setOf("pro"), style = "diamond_heart", collection = INSPIRATIONS, keywords = "coracao emoji fofo partido rosto vidro"),
        Wallpaper("rf_lago_rosado", "Lago Rosado", 0, 0xFF625CFF, plans = setOf("plus"), style = "lake", collection = INSPIRATIONS, keywords = "lago natureza"),
        Wallpaper("rf_luzes_do_planeta", "Luzes do Planeta", 0, 0xFF1B9FFF, plans = setOf("pro"), style = "earth", collection = INSPIRATIONS, keywords = "espaco planeta terra"),
        Wallpaper("rf_mar_de_gelo", "Mar de Gelo", 0, 0xFFDB33FF, plans = setOf("pro"), style = "icysea", collection = INSPIRATIONS, keywords = "gelo mar natureza"),
        Wallpaper("rf_noite_polar", "Noite Polar", 0, 0xFF3867FF, plans = setOf("pro"), style = "icysea", collection = INSPIRATIONS, keywords = "gelo mar natureza noite"),
        Wallpaper("rf_olhos_apaixonados", "Olhos Apaixonados", 0, 0xFFFF004F, plans = setOf("plus"), style = "eyes6", collection = INSPIRATIONS, keywords = "apaixonados emoji fofo olhos rosto"),
        Wallpaper("rf_terra_noturna", "Terra Noturna", 0, 0xFF33ACFF, plans = setOf("pro"), style = "earth", collection = INSPIRATIONS, keywords = "espaco noite planeta terra"),
        Wallpaper("rf_estrada_na_neblina", "Estrada na Neblina", 99, 0xFF80E0FF, style = "aerialroad", collection = INSPIRATIONS, keywords = "aereo corrida estrada natureza"),
        Wallpaper("rf_cara_de_raiva", "Cara de Raiva", 149, 0xFFFF0000, style = "art", collection = INSPIRATIONS, keywords = "bravo cara emoji raiva rosto olhos"),
        Wallpaper("rf_olhos_chocados", "Olhos Chocados", 149, 0xFF00D1FF, style = "eyes3", collection = INSPIRATIONS, keywords = "chocados emoji fofo olhos rosto"),
        Wallpaper("rf_olhos_tristes", "Olhos Chorando", 149, 0xFF80C6FF, style = "eyes7", collection = INSPIRATIONS, keywords = "chorando emoji fofo olhos rosto"),
        Wallpaper("rf_olhos_de_julgamento", "Olhos de Julgamento", 149, 0xFFFFA800, style = "eyes7", collection = INSPIRATIONS, keywords = "emoji fofo julgamento olhos rosto"),
        Wallpaper("rf_olhos_no_escuro", "Olhos no Escuro", 149, 0xFF0569FF, style = "eyes2", collection = INSPIRATIONS, keywords = "emoji escuro fofo olhos rosto"),
        Wallpaper("rf_coracao_apaixonado_na_chuva", "Coração Apaixonado na Chuva", 199, 0xFFFF0021, style = "love_heart2", collection = INSPIRATIONS, keywords = "apaixonado chuva coracao emoji fofo rosto"),
        Wallpaper("rf_coracao_partido_na_chuva", "Coração Partido na Chuva", 199, 0xFFFF0029, style = "broken_heart_rain", collection = INSPIRATIONS, keywords = "chuva coracao emoji fofo partido rosto"),
        Wallpaper("rf_olhos_brilhantes", "Olhos Brilhantes", 199, 0xFF32CFFF, style = "eyes2", collection = INSPIRATIONS, keywords = "brilhantes emoji fofo olhos rosto"),
        Wallpaper("rf_olhos_na_chuva", "Olhos na Chuva", 199, 0xFF338AFF, style = "eyes3", collection = INSPIRATIONS, keywords = "chuva emoji fofo olhos rosto"),
        Wallpaper("rf_bordo_molhado", "Bordô Molhado", 249, 0xFFFF1800, style = "leaves", collection = INSPIRATIONS, keywords = "flores folhas natureza outono"),
        Wallpaper("rf_entardecer_no_lago", "Entardecer no Lago", 249, 0xFFFF6633, style = "lake", collection = INSPIRATIONS, keywords = "3d entardecer lago montanha natureza paisagem"),
        Wallpaper("rf_gelo_esmeralda", "Gelo Esmeralda", 249, 0xFF33FFD4, style = "icysea", collection = INSPIRATIONS, keywords = "3d esmeralda gelo montanha natureza paisagem"),
        Wallpaper("rf_gelo_ao_amanhecer", "Gelo ao Amanhecer", 249, 0xFFFF6533, style = "icysea", collection = INSPIRATIONS, keywords = "3d amanhecer gelo montanha natureza paisagem"),
        Wallpaper("rf_hora_azul_no_lago", "Hora Azul no Lago", 249, 0xFF33DBFF, style = "lake", collection = INSPIRATIONS, keywords = "lago natureza"),
        Wallpaper("rf_lago_lilas", "Lago Lilás", 249, 0xFFC933FF, style = "lake", collection = INSPIRATIONS, keywords = "3d lago lilas montanha natureza paisagem"),
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
