package com.musibox.app.themes

import androidx.compose.ui.graphics.Color

/**
 * Papel de parede do MusiBox (imagens originais em assets/wallpapers).
 * price em centavos: 0 = grátis (ou exclusivo de plano). plans = planos que liberam o tema
 * ("go", "plus", "pro"); quem tem plano não compra. O Pro libera todos os exclusivos.
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

    /** Plano mais barato que libera o tema. */
    val planLabel: String
        get() = when {
            "go" in plans -> "Go"
            "plus" in plans -> "Plus"
            else -> "Pro"
        }

    /** "Go, Plus e Pro", "Plus e Pro" ou "Pro". */
    val plansText: String
        get() {
            val names = buildList {
                if ("go" in plans) add("Go")
                if ("plus" in plans) add("Plus")
                add("Pro")
            }
            return if (names.size == 1) names[0] else names.dropLast(1).joinToString(", ") + " e " + names.last()
        }

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
        Wallpaper("ft_golfinhos", "Golfinhos", 0, 0xFF00A9FF, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "animais animal foto golfinho mar natureza oceano"),
        Wallpaper("ft_lencois_maranhenses", "Lençóis Maranhenses", 0, 0xFF52A3FF, plans = setOf("go", "plus", "pro"), style = "photo", collection = PHOTOS, keywords = "brasil deserto dunas foto lagoas natureza"),
        Wallpaper("ft_pier_no_lago", "Píer no Lago", 0, 0xFF3494FF, style = "photo", collection = PHOTOS, keywords = "foto lago montanhas natureza pier"),
        Wallpaper("ft_raio_na_planicie", "Raio na Planície", 0, 0xFF52AAFF, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "foto natureza nuvens raio tempestade"),
        Wallpaper("ft_raposa", "Raposa", 0, 0xFFA7FF00, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "animais animal foto natureza raposa"),
        Wallpaper("ft_salar_ao_entardecer", "Salar ao Entardecer", 0, 0xFF5289FF, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "deserto do foto natureza por sal sol"),
        Wallpaper("ft_tartaruga_marinha", "Tartaruga Marinha", 0, 0xFF00BCFF, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "animais animal coral foto mar natureza oceano tartaruga"),
        Wallpaper("ft_cogumelos", "Cogumelos", 99, 0xFFFF8923, style = "photo", collection = PHOTOS, keywords = "cogumelos floresta foto natureza"),
        Wallpaper("ft_corredor_de_concreto", "Corredor de Concreto", 99, 0xFFFFF420, style = "photo", collection = PHOTOS, keywords = "arquitetura cidade foto minimalista"),
        Wallpaper("ft_folha_de_bordo", "Folha de Bordo", 99, 0xFFFF0C0F, style = "photo", collection = PHOTOS, keywords = "folha foto natureza outono vermelho"),
        Wallpaper("ft_galantos_na_neve", "Galantos na Neve", 99, 0xFFDCFF32, style = "photo", collection = PHOTOS, keywords = "flor flores foto inverno natureza neve"),
        Wallpaper("ft_gotas_de_chuva", "Gotas de Chuva", 99, 0xFF529EFF, style = "photo", collection = PHOTOS, keywords = "agua chuva foto gotas natureza"),
        Wallpaper("ft_bicicleta_no_campo", "Bicicleta no Campo", 149, 0xFFFFE600, style = "photo", collection = PHOTOS, keywords = "bicicleta campo flores foto natureza"),
        Wallpaper("ft_camelo", "Camelo", 149, 0xFFFFA452, style = "photo", collection = PHOTOS, keywords = "animais animal camelo deserto foto natureza"),
        Wallpaper("ft_canoa_vermelha", "Canoa Vermelha", 149, 0xFF52A6FF, style = "photo", collection = PHOTOS, keywords = "canoa foto lago natureza neblina"),
        Wallpaper("ft_canoa_na_nevoa", "Canoa na Névoa", 149, 0xFF80ABFF, style = "photo", collection = PHOTOS, keywords = "canoa foto lago natureza neblina"),
        Wallpaper("ft_carvalho", "Carvalho", 149, 0xFFFFEC00, style = "photo", collection = PHOTOS, keywords = "arvore campo foto natureza verde"),
        Wallpaper("ft_foca", "Foca", 149, 0xFF0085FF, style = "photo", collection = PHOTOS, keywords = "animais animal foca foto mar natureza praia"),
        Wallpaper("ft_lontra_marinha", "Lontra-Marinha", 149, 0xFF0094FF, style = "photo", collection = PHOTOS, keywords = "animais animal foto lontra mar natureza"),
        Wallpaper("ft_luz_no_celeiro", "Luz no Celeiro", 149, 0xFFFF8F21, style = "photo", collection = PHOTOS, keywords = "celeiro fazenda feno foto luz natureza"),
        Wallpaper("ft_martim_pescador", "Martim-Pescador", 149, 0xFFFF7700, style = "photo", collection = PHOTOS, keywords = "animais animal aves foto natureza"),
        Wallpaper("ft_moinho_de_vento", "Moinho de Vento", 149, 0xFFFFA600, style = "photo", collection = PHOTOS, keywords = "campo do foto moinho natureza por sol"),
        Wallpaper("ft_muros_de_pedra", "Muros de Pedra", 149, 0xFFE0FF00, style = "photo", collection = PHOTOS, keywords = "campo colinas foto natureza verde"),
        Wallpaper("ft_perereca", "Perereca", 149, 0xFFA9FF30, style = "photo", collection = PHOTOS, keywords = "animais animal foto natureza sapo verde"),
        Wallpaper("ft_pisco_no_inverno", "Pisco no Inverno", 149, 0xFFFF7D00, style = "photo", collection = PHOTOS, keywords = "animais animal aves foto inverno natureza"),
        Wallpaper("ft_pocas_da_mare", "Poças da Maré", 149, 0xFFFF9300, style = "photo", collection = PHOTOS, keywords = "costa foto mar natureza pedras"),
        Wallpaper("ft_riacho_de_pedras", "Riacho de Pedras", 149, 0xFFFFB63C, style = "photo", collection = PHOTOS, keywords = "agua foto natureza pedras rio"),
        Wallpaper("ft_suindara", "Suindara", 149, 0xFFFF7300, style = "photo", collection = PHOTOS, keywords = "animais animal aves coruja foto natureza"),
        Wallpaper("ft_bambuzal", "Bambuzal", 199, 0xFFCEFF04, style = "photo", collection = PHOTOS, keywords = "bambu floresta foto natureza trilha verde"),
        Wallpaper("ft_baoba", "Baobá", 199, 0xFFFF6000, style = "photo", collection = PHOTOS, keywords = "africa arvore do foto natureza por savana sol"),
        Wallpaper("ft_barco_no_lago_sereno", "Barco no Lago Sereno", 199, 0xFF4487FF, style = "photo", collection = PHOTOS, keywords = "barco foto lago natureza noite"),
        Wallpaper("ft_baia_rosada", "Baía Rosada", 199, 0xFFFF5C52, style = "photo", collection = PHOTOS, keywords = "do foto mar natureza por rosa sol"),
        Wallpaper("ft_cabana_no_lago", "Cabana no Lago", 199, 0xFFFF9200, style = "photo", collection = PHOTOS, keywords = "cabana foto lago natureza neblina outono"),
        Wallpaper("ft_caminho_nevado", "Caminho Nevado", 199, 0xFF0057FF, style = "photo", collection = PHOTOS, keywords = "arvores estrada foto inverno natureza neve"),
        Wallpaper("ft_carroca_de_outono", "Carroça de Outono", 199, 0xFFFFA700, style = "photo", collection = PHOTOS, keywords = "cavalo fazenda foto natureza outono"),
        Wallpaper("ft_casinha_vermelha", "Casinha Vermelha", 199, 0xFF529AFF, style = "photo", collection = PHOTOS, keywords = "casa foto lago montanhas natureza"),
        Wallpaper("ft_colinas_lilases", "Colinas Lilases", 199, 0xFF5281FF, style = "photo", collection = PHOTOS, keywords = "colinas deserto foto natureza roxo"),
        Wallpaper("ft_coqueiro_na_praia", "Coqueiro na Praia", 199, 0xFF3EA8FF, style = "photo", collection = PHOTOS, keywords = "coqueiro foto mar natureza praia tropical"),
        Wallpaper("ft_costa_rochosa", "Costa Rochosa", 199, 0xFFFFE229, style = "photo", collection = PHOTOS, keywords = "costa foto mar natureza ondas pedras"),
        Wallpaper("ft_dunas_alaranjadas", "Dunas Alaranjadas", 199, 0xFFFF6F00, style = "photo", collection = PHOTOS, keywords = "areia deserto dunas foto natureza"),
        Wallpaper("ft_farol", "Farol", 199, 0xFF52ACFF, style = "photo", collection = PHOTOS, keywords = "costa farol foto mar natureza"),
        Wallpaper("ft_gato_preto", "Gato Preto", 199, 0xFFFFD600, style = "photo", collection = PHOTOS, keywords = "animais animal foto gato natureza"),
        Wallpaper("ft_gol_na_neblina", "Gol na Neblina", 199, 0xFFFFF225, style = "photo", collection = PHOTOS, keywords = "campo foto futebol gol natureza neblina"),
        Wallpaper("ft_gorila", "Gorila", 199, 0xFFCBFF00, style = "photo", collection = PHOTOS, keywords = "animais animal floresta foto gorila natureza"),
        Wallpaper("ft_lua_sobre_o_deserto", "Lua sobre o Deserto", 199, 0xFF4281FF, style = "photo", collection = PHOTOS, keywords = "deserto foto lua natureza noite"),
        Wallpaper("ft_mesa_do_deserto", "Mesa do Deserto", 199, 0xFFFF7A0A, style = "photo", collection = PHOTOS, keywords = "canion deserto foto montanha natureza"),
        Wallpaper("ft_revoada_ao_por_do_sol", "Revoada ao Pôr do Sol", 199, 0xFFFF6E4C, style = "photo", collection = PHOTOS, keywords = "aves do foto lago natureza passaros por sol"),
        Wallpaper("ft_rosa_vermelha", "Rosa Vermelha", 199, 0xFFFF0021, style = "photo", collection = PHOTOS, keywords = "amor flor flores foto natureza rosa vermelha"),
        Wallpaper("ft_tucano", "Tucano", 199, 0xFFE1FF00, style = "photo", collection = PHOTOS, keywords = "animais animal aves foto natureza tropical tucano"),
        Wallpaper("ft_urzes_nas_colinas", "Urzes nas Colinas", 199, 0xFFFFD600, style = "photo", collection = PHOTOS, keywords = "colinas flores foto lago natureza"),
        Wallpaper("ft_vale_de_outono", "Vale de Outono", 199, 0xFFFF892E, style = "photo", collection = PHOTOS, keywords = "foto lago montanhas natureza outono vale"),
        Wallpaper("ft_cachoeira", "Cachoeira", 249, 0xFFE0FF00, style = "photo", collection = PHOTOS, keywords = "cachoeira foto montanhas natureza"),
        Wallpaper("ft_casa_de_pedra_na_montanha", "Casa de Pedra na Montanha", 249, 0xFFFF8C00, style = "photo", collection = PHOTOS, keywords = "casa foto montanhas natureza outono"),
        Wallpaper("ft_ceu_rosado_na_praia", "Céu Rosado na Praia", 249, 0xFFFF8052, style = "photo", collection = PHOTOS, keywords = "do foto natureza por praia rosa sol"),
        Wallpaper("ft_enseada_verde", "Enseada Verde", 249, 0xFFC8FF00, style = "photo", collection = PHOTOS, keywords = "costa foto mar natureza praia verde"),
        Wallpaper("ft_lago_da_cratera", "Lago da Cratera", 249, 0xFFFFF117, style = "photo", collection = PHOTOS, keywords = "foto lago montanhas natureza vulcao"),
        Wallpaper("ft_mirante_do_mediterraneo", "Mirante do Mediterrâneo", 249, 0xFF4AA5FF, style = "photo", collection = PHOTOS, keywords = "costa foto mar montanhas natureza"),
        Wallpaper("ft_onda_de_vidro", "Onda de Vidro", 249, 0xFF52A2FF, style = "photo", collection = PHOTOS, keywords = "foto mar natureza onda surf"),
        Wallpaper("ft_tempestade_no_campo", "Tempestade no Campo", 249, 0xFF5297FF, style = "photo", collection = PHOTOS, keywords = "celeiro chuva fazenda foto natureza nuvens"),
        Wallpaper("ft_terracos_de_arroz", "Terraços de Arroz", 249, 0xFFFFE200, style = "photo", collection = PHOTOS, keywords = "campo foto montanhas natureza neblina verde"),
        Wallpaper("ft_veleiro_no_azul", "Veleiro no Azul", 249, 0xFF0990FF, style = "photo", collection = PHOTOS, keywords = "azul barco foto mar natureza veleiro"),
        Wallpaper("ft_vista_da_cupula", "Vista da Cúpula", 249, 0xFFFF9D3D, style = "photo", collection = PHOTOS, keywords = "cidade foto italia telhados"),
        Wallpaper("ft_ondas_turquesa", "Ondas Turquesa", 299, 0xFF00F1FF, style = "photo", collection = PHOTOS, keywords = "aereo foto mar natureza ondas praia"),
        Wallpaper("ft_vale_das_palmeiras", "Vale das Palmeiras", 299, 0xFFD4FF03, style = "photo", collection = PHOTOS, keywords = "foto montanhas natureza neblina palmeiras vale"),
        // ---- Natureza 3D (18)
        Wallpaper("nt_alpes_ao_por_do_sol", "Alpes ao Pôr do Sol", 0, 0xFFFF6722, plans = setOf("plus", "pro"), style = "alpine", collection = NATURE3D, keywords = "3d alpes montanha natureza paisagem por sol"),
        Wallpaper("nt_aurora_violeta", "Aurora Violeta", 0, 0xFF8E52FF, plans = setOf("plus", "pro"), style = "alpine", collection = NATURE3D, keywords = "3d aurora montanha natureza paisagem violeta"),
        Wallpaper("nt_aurora_no_fiorde", "Aurora no Fiorde", 0, 0xFF00FFD4, plans = setOf("pro"), style = "alpine", collection = NATURE3D, keywords = "3d aurora fiorde montanha natureza paisagem"),
        Wallpaper("nt_aurora_artica", "Aurora Ártica", 0, 0xFF00FFD2, plans = setOf("pro"), style = "render3d", collection = NATURE3D, keywords = "3d montanhas natureza noite"),
        Wallpaper("nt_fiorde_dourado", "Fiorde Dourado", 0, 0xFF5253FF, plans = setOf("go", "plus", "pro"), style = "alpine", collection = NATURE3D, keywords = "3d dourado fiorde montanha natureza paisagem"),
        Wallpaper("nt_montanhas_lilases", "Montanhas Lilases", 0, 0xFF6352FF, plans = setOf("pro"), style = "alpine", collection = NATURE3D, keywords = "3d lilases montanha montanhas natureza paisagem"),
        Wallpaper("nt_planeta_sobre_as_dunas", "Planeta sobre as Dunas", 0, 0xFFFFA952, plans = setOf("pro"), style = "dunes3", collection = NATURE3D, keywords = "dunas espaco planeta sobre universo"),
        Wallpaper("nt_planeta_sobre_o_lago", "Planeta sobre o Lago", 0, 0xFF52B6FF, plans = setOf("plus", "pro"), style = "alpine", collection = NATURE3D, keywords = "espaco lago planeta sobre universo"),
        Wallpaper("nt_vale_verde", "Vale Verde", 0, 0xFF3599FF, plans = setOf("pro"), style = "alpine", collection = NATURE3D, keywords = "3d montanha natureza paisagem vale verde"),
        Wallpaper("nt_via_lactea_montanhas", "Via Láctea nas Montanhas", 0, 0xFFFF9980, plans = setOf("pro"), style = "alpine", collection = NATURE3D, keywords = "3d lactea montanha montanhas nas natureza paisagem via"),
        Wallpaper("nt_dunas_douradas", "Dunas Douradas", 199, 0xFF5298FF, style = "render3d", collection = NATURE3D, keywords = "3d deserto natureza"),
        Wallpaper("nt_dunas_estrelas", "Dunas sob as Estrelas", 199, 0xFF5270FF, style = "render3d", collection = NATURE3D, keywords = "3d deserto espaco natureza noite"),
        Wallpaper("nt_alpes_amanhecer", "Alpes ao Amanhecer", 299, 0xFF5281FF, style = "render3d", collection = NATURE3D, keywords = "3d montanhas natureza"),
        Wallpaper("nt_alpes_ao_luar", "Alpes ao Luar", 299, 0xFF5279FF, style = "alpine", collection = NATURE3D, keywords = "3d alpes luar montanha natureza paisagem"),
        Wallpaper("nt_dunas_rosadas", "Dunas Rosadas", 299, 0xFF8252FF, style = "dunes3", collection = NATURE3D, keywords = "3d dunas montanha natureza paisagem rosadas"),
        Wallpaper("nt_lago_esmeralda", "Lago Esmeralda", 299, 0xFF52A6FF, style = "render3d", collection = NATURE3D, keywords = "3d montanhas natureza"),
        Wallpaper("nt_fiorde", "Fiorde", 399, 0xFF52A3FF, style = "render3d", collection = NATURE3D, keywords = "3d mar montanhas natureza"),
        Wallpaper("nt_inverno_anoitecer", "Inverno ao Anoitecer", 399, 0xFF5265FF, style = "render3d", collection = NATURE3D, keywords = "3d montanhas natureza noite"),
        // ---- Games 3D (3)
        Wallpaper("gm_mundo_low_poly", "Mundo Low-Poly", 249, 0xFF5266FF, style = "render3d", collection = GAMES3D, keywords = "3d games jogos montanhas natureza"),
        Wallpaper("gm_mundo_de_blocos", "Mundo de Blocos", 299, 0xFF359AFF, style = "render3d", collection = GAMES3D, keywords = "3d games jogos natureza"),
        Wallpaper("gm_estrada_synthwave", "Estrada Synthwave", 399, 0xFFFF00D7, style = "render3d", collection = GAMES3D, keywords = "3d corrida games jogos neon"),
        // ---- Coleção clássica (16)
        Wallpaper("cl_eclipse_azul", "Eclipse Azul", 0, 0xFF009EFF, plans = setOf("pro"), style = "planet", keywords = "azul eclipse espaco planeta universo"),
        Wallpaper("noite_estrelada", "Noite Estrelada", 0, 0xFF809BFF, style = "mountains", keywords = "montanhas natureza noite"),
        Wallpaper("nevoa_verde", "Névoa Verde", 0, 0xFF52FFC3, style = "mountains", keywords = "montanhas natureza"),
        Wallpaper("cl_planeta_violeta", "Planeta Violeta", 0, 0xFF9C52FF, plans = setOf("go", "plus", "pro"), style = "planet", keywords = "espaco planeta universo violeta"),
        Wallpaper("cl_planeta_de_cristal", "Planeta de Cristal", 0, 0xFFFF52DF, plans = setOf("pro"), style = "planet", keywords = "cristal espaco planeta universo"),
        Wallpaper("nebulosa_azul", "Nebulosa Azul", 99, 0xFF52CCFF, style = "nebula", keywords = "espaco estrelas nebulosa"),
        Wallpaper("nebulosa_rosa", "Nebulosa Rosa", 99, 0xFFFF52BA, style = "nebula", keywords = "espaco estrelas nebulosa"),
        Wallpaper("pro_eclipse", "Eclipse", 149, 0xFFFF7A00, style = "planet", keywords = "espaco noite planeta"),
        Wallpaper("cl_eclipse_rosa", "Eclipse Rosa", 149, 0xFFFF15A7, style = "planet", keywords = "eclipse espaco planeta rosa universo"),
        Wallpaper("cl_nevoa_azul", "Névoa Azul", 149, 0xFF5297FF, style = "mountains", keywords = "3d azul montanha natureza nevoa paisagem"),
        Wallpaper("cl_nevoa_ao_amanhecer", "Névoa ao Amanhecer", 149, 0xFFFF7052, style = "mountains", keywords = "3d amanhecer montanha natureza nevoa paisagem"),
        Wallpaper("pro_nebulosa_esmeralda", "Nebulosa Esmeralda", 199, 0xFF52FFAD, style = "nebula", keywords = "espaco estrelas nebulosa"),
        Wallpaper("cl_planeta_azul", "Planeta Azul", 199, 0xFF046FFF, style = "planet", keywords = "azul espaco planeta universo"),
        Wallpaper("cl_planeta_esmeralda", "Planeta Esmeralda", 199, 0xFF52FFBB, style = "planet", keywords = "esmeralda espaco planeta universo"),
        Wallpaper("cl_planeta_incandescente", "Planeta Incandescente", 199, 0xFFFF4800, style = "planet", keywords = "espaco incandescente planeta universo"),
        Wallpaper("planeta_gelo", "Planeta de Gelo", 199, 0xFF52C1FF, style = "planet", keywords = "espaco planeta"),
        // ---- Heróis & Vilões (15)
        Wallpaper("hv_escudo_azul", "Escudo Azul", 0, 0xFF23B5FF, plans = setOf("plus", "pro"), style = "hexshield", collection = HEROES, keywords = "3d azul escudo games herois"),
        Wallpaper("hv_fenda_esmeralda", "Fenda Esmeralda", 0, 0xFF05FF81, plans = setOf("pro"), style = "rift", collection = HEROES, keywords = "3d esmeralda fenda games herois"),
        Wallpaper("hv_tempestade_violeta", "Tempestade Violeta", 0, 0xFFA252FF, plans = setOf("pro"), style = "storm", collection = HEROES, keywords = "3d games herois tempestade violeta"),
        Wallpaper("hv_impacto_azul", "Impacto Azul", 99, 0xFF006DFF, style = "comic", collection = HEROES, keywords = "herois quadrinhos"),
        Wallpaper("hv_impacto_dourado", "Impacto Dourado", 99, 0xFFFFB300, style = "comic", collection = HEROES, keywords = "3d dourado games herois impacto"),
        Wallpaper("hv_impacto_vermelho", "Impacto Vermelho", 99, 0xFFFF1300, style = "comic", collection = HEROES, keywords = "3d games herois impacto vermelho"),
        Wallpaper("hv_circuito_tatico", "Circuito Tático", 149, 0xFF4FE8FF, style = "circuit", collection = HEROES, keywords = "herois neon tecnologia"),
        Wallpaper("hv_circuito_violeta", "Circuito Violeta", 149, 0xFFEE52FF, style = "circuit", collection = HEROES, keywords = "3d circuito games herois violeta"),
        Wallpaper("hv_escudo_dourado", "Escudo Dourado", 149, 0xFFFFCE3D, style = "hexshield", collection = HEROES, keywords = "herois neon"),
        Wallpaper("hv_escudo_violeta", "Escudo Violeta", 149, 0xFFAF36FF, style = "hexshield", collection = HEROES, keywords = "3d escudo games herois violeta"),
        Wallpaper("hv_amanhecer_rosado", "Amanhecer Rosado", 199, 0xFFFF529E, style = "dawncity", collection = HEROES, keywords = "3d amanhecer games herois rosado"),
        Wallpaper("hv_amanhecer_heroi", "Amanhecer do Herói", 199, 0xFFFF8630, style = "dawncity", collection = HEROES, keywords = "cidade cidades herois"),
        Wallpaper("hv_fenda_dimensional", "Fenda Dimensional", 199, 0xFFA54DFF, style = "rift", collection = HEROES, keywords = "herois raio"),
        Wallpaper("hv_furia_tempestade", "Fúria da Tempestade", 199, 0xFF52B0FF, style = "storm", collection = HEROES, keywords = "herois raio tempestade"),
        Wallpaper("hv_tempestade_rubra", "Tempestade Rubra", 199, 0xFFFF1133, style = "storm", collection = HEROES, keywords = "3d games herois rubra tempestade"),
        // ---- Premium 3D (26)
        Wallpaper("pm_colmeia_neon", "Colmeia Neon", 0, 0xFFF11AFF, plans = setOf("pro"), style = "hex3d", collection = PREMIUM, keywords = "3d abstrato colmeia neon premium"),
        Wallpaper("pm_cubo_dourado", "Cubo Dourado", 0, 0xFFFFA600, plans = setOf("pro"), style = "cubes", collection = PREMIUM, keywords = "3d abstrato cubo dourado premium"),
        Wallpaper("pm_cubo_violeta", "Cubo Violeta", 0, 0xFFA20DFF, plans = setOf("plus", "pro"), style = "cubes", collection = PREMIUM, keywords = "3d abstrato cubo premium violeta"),
        Wallpaper("pm_mar_rosado", "Mar Rosado", 0, 0xFFFF52AC, plans = setOf("go", "plus", "pro"), style = "sunsea", collection = PREMIUM, keywords = "3d abstrato mar premium rosado"),
        Wallpaper("pm_mar_ao_luar", "Mar ao Luar", 0, 0xFF527AFF, plans = setOf("pro"), style = "sunsea", collection = PREMIUM, keywords = "3d abstrato luar mar premium"),
        Wallpaper("pm_orbe_esmeralda", "Orbe Esmeralda", 0, 0xFF6EFF52, plans = setOf("pro"), style = "glass", collection = PREMIUM, keywords = "3d abstrato esmeralda orbe premium"),
        Wallpaper("pm_seda_oceano", "Seda Oceano", 0, 0xFF5F52FF, plans = setOf("plus", "pro"), style = "liquidwave", collection = PREMIUM, keywords = "3d abstrato oceano premium seda"),
        Wallpaper("pm_aguas_vivas", "Águas-vivas", 0, 0xFFF852FF, plans = setOf("pro"), style = "jellyfish", collection = PREMIUM, keywords = "agua aguas mar neon oceano viva vivas"),
        Wallpaper("pm_cubo_ciano", "Cubo Ciano", 99, 0xFF00FFEA, style = "cubes", collection = PREMIUM, keywords = "3d abstrato cubos premium"),
        Wallpaper("pm_seda_aurora", "Seda Aurora", 99, 0xFF52FFC9, style = "liquidwave", collection = PREMIUM, keywords = "3d abstrato noite ondas premium"),
        Wallpaper("pm_colmeia_esmeralda", "Colmeia Esmeralda", 199, 0xFF00FFC7, style = "hex3d", collection = PREMIUM, keywords = "3d abstrato colmeia esmeralda premium"),
        Wallpaper("pm_colmeia_noturna", "Colmeia Noturna", 199, 0xFF4891FF, style = "hex3d", collection = PREMIUM, keywords = "3d abstrato colmeia neon noite premium"),
        Wallpaper("pm_colmeia_turquesa", "Colmeia Turquesa", 199, 0xFF0ED1FF, style = "hex3d", collection = PREMIUM, keywords = "3d abstrato colmeia premium turquesa"),
        Wallpaper("pm_cubo_azul_neon", "Cubo Azul Neon", 199, 0xFF0077FF, style = "cubes", collection = PREMIUM, keywords = "3d abstrato azul cubo neon premium"),
        Wallpaper("pm_cubo_rubro", "Cubo Rubro", 199, 0xFFFF0013, style = "cubes", collection = PREMIUM, keywords = "3d abstrato cubos premium"),
        Wallpaper("pm_seda_rubi", "Seda Rubi", 199, 0xFFFF1559, style = "liquidwave", collection = PREMIUM, keywords = "3d abstrato premium rubi seda"),
        Wallpaper("pm_seda_solar", "Seda Solar", 199, 0xFFFF9900, style = "liquidwave", collection = PREMIUM, keywords = "3d abstrato premium seda solar"),
        Wallpaper("pm_colmeia_violeta", "Colmeia Violeta", 249, 0xFFEE52FF, style = "hex3d", collection = PREMIUM, keywords = "3d abstrato colmeia neon premium"),
        Wallpaper("pm_crepusculo_violeta", "Crepúsculo Violeta", 249, 0xFFFF5273, style = "sunsea", collection = PREMIUM, keywords = "3d do mar natureza por premium sol"),
        Wallpaper("pm_mar_carmesim", "Mar Carmesim", 249, 0xFFFF1417, style = "sunsea", collection = PREMIUM, keywords = "3d do mar natureza por premium sol"),
        Wallpaper("pm_mar_dourado", "Mar Dourado", 249, 0xFFFFAA00, style = "sunsea", collection = PREMIUM, keywords = "3d abstrato dourado mar premium"),
        Wallpaper("pm_mar_da_hora_azul", "Mar da Hora Azul", 249, 0xFF2C65FF, style = "sunsea", collection = PREMIUM, keywords = "3d abstrato azul hora mar premium"),
        Wallpaper("pm_orbe_noturno", "Orbe Noturno", 249, 0xFFC14EFF, style = "glass", collection = PREMIUM, keywords = "3d abstrato esferas noite premium vidro"),
        Wallpaper("pm_orbe_rubi", "Orbe Rubi", 249, 0xFFFF2933, style = "glass", collection = PREMIUM, keywords = "3d abstrato orbe premium rubi"),
        Wallpaper("pm_sorriso_chuva", "Sorriso na Chuva", 249, 0xFFFFB400, style = "drops", collection = PREMIUM, keywords = "3d abstrato gotas premium"),
        Wallpaper("pm_sol_de_fogo", "Sol de Fogo", 399, 0xFFFF3F00, style = "sunsea", collection = PREMIUM, keywords = "3d do mar natureza por premium sol"),
        // ---- Inspirações (26)
        Wallpaper("rf_coracao_apaixonado", "Coração Apaixonado", 0, 0xFFFF0021, plans = setOf("pro"), style = "love_heart2", collection = INSPIRATIONS, keywords = "apaixonado coracao emoji fofo rosto"),
        Wallpaper("rf_coracao_partido", "Coração Partido", 0, 0xFFFF0024, plans = setOf("go", "plus", "pro"), style = "broken_heart", collection = INSPIRATIONS, keywords = "coracao emoji fofo partido rosto"),
        Wallpaper("rf_coracao_partido_de_vidro", "Coração Partido de Vidro", 0, 0xFF0043FF, plans = setOf("pro"), style = "diamond_heart", collection = INSPIRATIONS, keywords = "coracao emoji fofo partido rosto vidro"),
        Wallpaper("rf_olhos_apaixonados", "Olhos Apaixonados", 0, 0xFFFF0050, plans = setOf("plus", "pro"), style = "eyes6", collection = INSPIRATIONS, keywords = "apaixonados emoji fofo olhos rosto"),
        Wallpaper("rf_terra_noturna", "Terra Noturna", 0, 0xFF49B5FF, plans = setOf("pro"), style = "earth", collection = INSPIRATIONS, keywords = "espaco noite planeta terra"),
        Wallpaper("rf_estrada_na_neblina", "Estrada na Neblina", 99, 0xFF80E0FF, style = "aerialroad", collection = INSPIRATIONS, keywords = "aereo corrida estrada natureza"),
        Wallpaper("rf_cara_de_raiva", "Cara de Raiva", 149, 0xFFFF0000, style = "art", collection = INSPIRATIONS, keywords = "bravo cara emoji raiva rosto olhos"),
        Wallpaper("rf_olhos_chocados", "Olhos Chocados", 149, 0xFF00D1FF, style = "eyes3", collection = INSPIRATIONS, keywords = "chocados emoji fofo olhos rosto"),
        Wallpaper("rf_olhos_tristes", "Olhos Chorando", 149, 0xFF80C5FF, style = "eyes7", collection = INSPIRATIONS, keywords = "chorando emoji fofo olhos rosto"),
        Wallpaper("rf_olhos_de_julgamento", "Olhos de Julgamento", 149, 0xFFFFA800, style = "eyes7", collection = INSPIRATIONS, keywords = "emoji fofo julgamento olhos rosto"),
        Wallpaper("rf_olhos_no_escuro", "Olhos no Escuro", 149, 0xFF0066FF, style = "eyes2", collection = INSPIRATIONS, keywords = "emoji escuro fofo olhos rosto"),
        Wallpaper("rf_coracao_apaixonado_na_chuva", "Coração Apaixonado na Chuva", 199, 0xFFFF0021, style = "love_heart2", collection = INSPIRATIONS, keywords = "apaixonado chuva coracao emoji fofo rosto"),
        Wallpaper("rf_coracao_partido_na_chuva", "Coração Partido na Chuva", 199, 0xFFFF0029, style = "broken_heart_rain", collection = INSPIRATIONS, keywords = "chuva coracao emoji fofo partido rosto"),
        Wallpaper("rf_olhos_brilhantes", "Olhos Brilhantes", 199, 0xFF52DFFF, style = "eyes2", collection = INSPIRATIONS, keywords = "brilhantes emoji fofo olhos rosto"),
        Wallpaper("rf_olhos_na_chuva", "Olhos na Chuva", 199, 0xFF529EFF, style = "eyes3", collection = INSPIRATIONS, keywords = "chuva emoji fofo olhos rosto"),
        Wallpaper("rf_bordo_molhado", "Bordô Molhado", 249, 0xFFFF1800, style = "leaves", collection = INSPIRATIONS, keywords = "flores folhas natureza outono"),
        Wallpaper("rf_entardecer_no_lago", "Entardecer no Lago", 249, 0xFFFF7D52, style = "lake", collection = INSPIRATIONS, keywords = "3d entardecer lago montanha natureza paisagem"),
        Wallpaper("rf_gelo_esmeralda", "Gelo Esmeralda", 249, 0xFF3DFFD6, style = "icysea", collection = INSPIRATIONS, keywords = "3d esmeralda gelo montanha natureza paisagem"),
        Wallpaper("rf_gelo_ao_amanhecer", "Gelo ao Amanhecer", 249, 0xFFFF7C52, style = "icysea", collection = INSPIRATIONS, keywords = "3d amanhecer gelo montanha natureza paisagem"),
        Wallpaper("rf_hora_azul_no_lago", "Hora Azul no Lago", 249, 0xFF52E0FF, style = "lake", collection = INSPIRATIONS, keywords = "lago natureza"),
        Wallpaper("rf_lago_lilas", "Lago Lilás", 249, 0xFFD152FF, style = "lake", collection = INSPIRATIONS, keywords = "3d lago lilas montanha natureza paisagem"),
        Wallpaper("rf_lago_rosado", "Lago Rosado", 249, 0xFF5852FF, style = "lake", collection = INSPIRATIONS, keywords = "lago natureza"),
        Wallpaper("rf_mar_de_gelo", "Mar de Gelo", 249, 0xFFE052FF, style = "icysea", collection = INSPIRATIONS, keywords = "gelo mar natureza"),
        Wallpaper("rf_folhas_outono", "Folhas de Outono", 299, 0xFFFF6800, style = "leaves", collection = INSPIRATIONS, keywords = "flores folhas natureza outono"),
        Wallpaper("rf_noite_polar", "Noite Polar", 299, 0xFF527AFF, style = "icysea", collection = INSPIRATIONS, keywords = "gelo mar natureza noite"),
        Wallpaper("rf_luzes_do_planeta", "Luzes do Planeta", 499, 0xFF22A2FF, style = "earth", collection = INSPIRATIONS, keywords = "espaco planeta terra"),
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
