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
        // ---- Fotos (200)
        Wallpaper("ft_acampamento_estrelado", "Acampamento Estrelado", 0, 0xFFE69012, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "barraca espaco estrelas foto lactea natureza noite via"),
        Wallpaper("ft_acima_das_nuvens", "Acima das Nuvens", 0, 0xFF6DA7E6, plans = setOf("go", "plus", "pro"), style = "photo", collection = PHOTOS, keywords = "ceu foto montanhas natureza nuvens"),
        Wallpaper("ft_acacia_ao_por_do_sol", "Acácia ao Pôr do Sol", 0, 0xFFF59B40, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "africa arvore do foto natureza por savana sol"),
        Wallpaper("ft_aurora_sobre_o_gelo", "Aurora sobre o Gelo", 0, 0xFF75E673, plans = setOf("plus", "pro"), style = "photo", collection = PHOTOS, keywords = "aurora boreal foto gelo inverno natureza noite"),
        Wallpaper("ft_baloes_ao_amanhecer", "Balões ao Amanhecer", 0, 0xFFF5AD6C, plans = setOf("go", "plus", "pro"), style = "photo", collection = PHOTOS, keywords = "amanhecer baloes ceu foto natureza viagem"),
        Wallpaper("ft_campos_de_lavanda", "Campos de Lavanda", 0, 0xFFDCE653, plans = setOf("plus", "pro"), style = "photo", collection = PHOTOS, keywords = "campo flores foto lavanda natureza roxo"),
        Wallpaper("ft_casa_branca_no_mar", "Casa Branca no Mar", 0, 0xFF6DABE6, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "azul casa foto grecia mar natureza"),
        Wallpaper("ft_cauda_de_baleia", "Cauda de Baleia", 0, 0xFF73AAE6, plans = setOf("plus", "pro"), style = "photo", collection = PHOTOS, keywords = "animais animal baleia foto mar natureza oceano"),
        Wallpaper("ft_cervo_na_nevoa", "Cervo na Névoa", 0, 0xFFE6C8B2, plans = setOf("plus", "pro"), style = "photo", collection = PHOTOS, keywords = "animais animal cervo floresta foto natureza neblina"),
        Wallpaper("ft_flor_de_lotus", "Flor de Lótus", 0, 0xFFE6738D, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "flor flores foto lago lotus natureza rosa"),
        Wallpaper("ft_golfinhos", "Golfinhos", 0, 0xFF12AAE6, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "animais animal foto golfinho mar natureza oceano"),
        Wallpaper("ft_janela_do_aviao", "Janela do Avião", 0, 0xFFEDAB59, plans = setOf("plus", "pro"), style = "photo", collection = PHOTOS, keywords = "aviao ceu do foto natureza nuvens por sol viagem"),
        Wallpaper("ft_lencois_maranhenses", "Lençóis Maranhenses", 0, 0xFF73A7E6, plans = setOf("go", "plus", "pro"), style = "photo", collection = PHOTOS, keywords = "brasil deserto dunas foto lagoas natureza"),
        Wallpaper("ft_leao", "Leão", 0, 0xFFE68826, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "animais animal felino foto leao natureza rei"),
        Wallpaper("ft_panda", "Panda", 0, 0xFFCFE65B, plans = setOf("go", "plus", "pro"), style = "photo", collection = PHOTOS, keywords = "animais animal bambu foto natureza panda"),
        Wallpaper("ft_ponte_na_floresta", "Ponte na Floresta", 0, 0xFFE6BAB2, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "floresta foto natureza neblina pinheiros ponte"),
        Wallpaper("ft_pier_no_lago", "Píer no Lago", 0, 0xFF5699E6, style = "photo", collection = PHOTOS, keywords = "foto lago montanhas natureza pier"),
        Wallpaper("ft_raio_na_planicie", "Raio na Planície", 0, 0xFF6BA7E6, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "foto natureza nuvens raio tempestade"),
        Wallpaper("ft_raposa", "Raposa", 0, 0xFFA4E635, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "animais animal foto natureza raposa"),
        Wallpaper("ft_rio_glacial", "Rio Glacial", 0, 0xFF73BAE6, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "aereo foto geleira montanhas natureza rio"),
        Wallpaper("ft_rio_de_janeiro", "Rio de Janeiro", 0, 0xFF6EACE6, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "acucar brasil cidade de foto mar pao praia rio"),
        Wallpaper("ft_salar_ao_entardecer", "Salar ao Entardecer", 0, 0xFF7392E6, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "deserto do foto natureza por sal sol"),
        Wallpaper("ft_tartaruga_marinha", "Tartaruga Marinha", 0, 0xFF12B2E6, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "animais animal coral foto mar natureza oceano tartaruga"),
        Wallpaper("ft_teleferico_na_neve", "Teleférico na Neve", 0, 0xFF739FE6, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "foto inverno montanhas natureza neve pinheiros"),
        Wallpaper("ft_vila_no_penhasco", "Vila no Penhasco", 0, 0xFF73A9E6, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "cidade colorida foto italia mar"),
        Wallpaper("ft_vulcao_ao_entardecer", "Vulcão ao Entardecer", 0, 0xFFE69C73, plans = setOf("pro"), style = "photo", collection = PHOTOS, keywords = "do foto montanha natureza por sol vulcao"),
        Wallpaper("ft_bolotas_de_carvalho", "Bolotas de Carvalho", 99, 0xFFE6C22C, style = "photo", collection = PHOTOS, keywords = "carvalho folhas foto natureza outono"),
        Wallpaper("ft_broto_de_samambaia", "Broto de Samambaia", 99, 0xFFB5E636, style = "photo", collection = PHOTOS, keywords = "foto natureza planta samambaia verde"),
        Wallpaper("ft_cacto", "Cacto", 99, 0xFFE6AF65, style = "photo", collection = PHOTOS, keywords = "cacto deserto foto natureza planta"),
        Wallpaper("ft_caracol", "Caracol", 99, 0xFFE6DF62, style = "photo", collection = PHOTOS, keywords = "caracol foto musgo natureza"),
        Wallpaper("ft_coelhos", "Coelhos", 99, 0xFFCDE666, style = "photo", collection = PHOTOS, keywords = "animais animal coelho foto natureza"),
        Wallpaper("ft_cogumelos", "Cogumelos", 99, 0xFFE6A05F, style = "photo", collection = PHOTOS, keywords = "cogumelos floresta foto natureza"),
        Wallpaper("ft_corredor_de_concreto", "Corredor de Concreto", 99, 0xFFE6D655, style = "photo", collection = PHOTOS, keywords = "arquitetura cidade foto minimalista"),
        Wallpaper("ft_costela_de_adao", "Costela-de-Adão", 99, 0xFF7DE673, style = "photo", collection = PHOTOS, keywords = "folha foto gotas natureza planta verde"),
        Wallpaper("ft_escada_espiral", "Escada Espiral", 99, 0xFFE6AD73, style = "photo", collection = PHOTOS, keywords = "arquitetura escada foto natureza"),
        Wallpaper("ft_espuma_do_mar", "Espuma do Mar", 99, 0xFFE6A573, style = "photo", collection = PHOTOS, keywords = "areia foto mar natureza ondas praia"),
        Wallpaper("ft_eucalipto", "Eucalipto", 99, 0xFFE6C173, style = "photo", collection = PHOTOS, keywords = "folhas foto gotas natureza planta"),
        Wallpaper("ft_folha_de_bordo", "Folha de Bordo", 99, 0xFFE62C30, style = "photo", collection = PHOTOS, keywords = "folha foto natureza outono vermelho"),
        Wallpaper("ft_folhas_de_ginkgo", "Folhas de Ginkgo", 99, 0xFFE69912, style = "photo", collection = PHOTOS, keywords = "amarelo folhas foto natureza outono"),
        Wallpaper("ft_galantos_na_neve", "Galantos na Neve", 99, 0xFFCFE664, style = "photo", collection = PHOTOS, keywords = "flor flores foto inverno natureza neve"),
        Wallpaper("ft_gota_na_folha", "Gota na Folha", 99, 0xFFC3E629, style = "photo", collection = PHOTOS, keywords = "folha foto gota natureza verde"),
        Wallpaper("ft_gotas_de_chuva", "Gotas de Chuva", 99, 0xFF73A2E6, style = "photo", collection = PHOTOS, keywords = "agua chuva foto gotas natureza"),
        Wallpaper("ft_guarda_sol", "Guarda-Sol", 99, 0xFFE6AB73, style = "photo", collection = PHOTOS, keywords = "areia foto natureza praia verao"),
        Wallpaper("ft_hortensia_azul", "Hortênsia Azul", 99, 0xFF4D7DE6, style = "photo", collection = PHOTOS, keywords = "azul flor flores foto natureza"),
        Wallpaper("ft_joaninha", "Joaninha", 99, 0xFFE6BB68, style = "photo", collection = PHOTOS, keywords = "foto inseto joaninha natureza trigo"),
        Wallpaper("ft_margarida", "Margarida", 99, 0xFFE6A812, style = "photo", collection = PHOTOS, keywords = "flor flores foto margarida natureza"),
        Wallpaper("ft_musgo", "Musgo", 99, 0xFFD6E620, style = "photo", collection = PHOTOS, keywords = "floresta foto musgo natureza verde"),
        Wallpaper("ft_ourico", "Ouriço", 99, 0xFFB2E637, style = "photo", collection = PHOTOS, keywords = "animais animal foto natureza ourico"),
        Wallpaper("ft_papoula", "Papoula", 99, 0xFFE65712, style = "photo", collection = PHOTOS, keywords = "do flor flores foto natureza papoula por sol"),
        Wallpaper("ft_pinha", "Pinha", 99, 0xFFE69756, style = "photo", collection = PHOTOS, keywords = "floresta foto natureza outono pinha"),
        Wallpaper("ft_pinheiro_solitario", "Pinheiro Solitário", 99, 0xFFB4C7E9, style = "photo", collection = PHOTOS, keywords = "foto inverno minimalista natureza neve pinheiro"),
        Wallpaper("ft_sacadas_floridas", "Sacadas Floridas", 99, 0xFFE6AF73, style = "photo", collection = PHOTOS, keywords = "cidade flores foto janelas"),
        Wallpaper("ft_seixos_na_agua", "Seixos na Água", 99, 0xFFE6B96B, style = "photo", collection = PHOTOS, keywords = "agua foto natureza pedras rio"),
        Wallpaper("ft_suculenta", "Suculenta", 99, 0xFFE6A973, style = "photo", collection = PHOTOS, keywords = "foto natureza planta suculenta verde"),
        Wallpaper("ft_vitorias_regias", "Vitórias-Régias", 99, 0xFFBBE660, style = "photo", collection = PHOTOS, keywords = "agua folhas foto gotas lago natureza"),
        Wallpaper("ft_agua_cristalina", "Água Cristalina", 99, 0xFF73E6D5, style = "photo", collection = PHOTOS, keywords = "agua foto mar natureza transparente"),
        Wallpaper("ft_abelha_no_girassol", "Abelha no Girassol", 149, 0xFFECAC13, style = "photo", collection = PHOTOS, keywords = "abelha flores foto girassol inseto natureza"),
        Wallpaper("ft_ave_do_paraiso", "Ave-do-Paraíso", 149, 0xFFE67E12, style = "photo", collection = PHOTOS, keywords = "flor flores foto laranja natureza tropical"),
        Wallpaper("ft_bicicleta_no_campo", "Bicicleta no Campo", 149, 0xFFE6CC32, style = "photo", collection = PHOTOS, keywords = "bicicleta campo flores foto natureza"),
        Wallpaper("ft_bonde_verde", "Bonde Verde", 149, 0xFFE69173, style = "photo", collection = PHOTOS, keywords = "bonde cidade foto lisboa rua"),
        Wallpaper("ft_borboleta_monarca", "Borboleta Monarca", 149, 0xFFDBE659, style = "photo", collection = PHOTOS, keywords = "borboleta flores foto inseto natureza"),
        Wallpaper("ft_betulas_douradas", "Bétulas Douradas", 149, 0xFFE6AA12, style = "photo", collection = PHOTOS, keywords = "amarelo arvores foto natureza outono"),
        Wallpaper("ft_cabana_na_floresta", "Cabana na Floresta", 149, 0xFFE6E26C, style = "photo", collection = PHOTOS, keywords = "arvores casa floresta foto natureza"),
        Wallpaper("ft_cabra_da_montanha", "Cabra-da-Montanha", 149, 0xFFE6CFB2, style = "photo", collection = PHOTOS, keywords = "animais animal cabra foto montanhas natureza"),
        Wallpaper("ft_camelo", "Camelo", 149, 0xFFE6A873, style = "photo", collection = PHOTOS, keywords = "animais animal camelo deserto foto natureza"),
        Wallpaper("ft_campo_de_trigo", "Campo de Trigo", 149, 0xFFE6A74E, style = "photo", collection = PHOTOS, keywords = "campo fazenda foto natureza trigo verao"),
        Wallpaper("ft_canguru", "Canguru", 149, 0xFFE6BF57, style = "photo", collection = PHOTOS, keywords = "animais animal canguru foto natureza"),
        Wallpaper("ft_canoa_vermelha", "Canoa Vermelha", 149, 0xFF73A9E6, style = "photo", collection = PHOTOS, keywords = "canoa foto lago natureza neblina"),
        Wallpaper("ft_canoa_na_nevoa", "Canoa na Névoa", 149, 0xFFB2C0E6, style = "photo", collection = PHOTOS, keywords = "canoa foto lago natureza neblina"),
        Wallpaper("ft_capivara", "Capivara", 149, 0xFFCCE617, style = "photo", collection = PHOTOS, keywords = "animais animal brasil capivara foto natureza"),
        Wallpaper("ft_cardo_roxo", "Cardo Roxo", 149, 0xFFE6B273, style = "photo", collection = PHOTOS, keywords = "do flor flores foto natureza por roxo sol"),
        Wallpaper("ft_carvalho", "Carvalho", 149, 0xFFE6D237, style = "photo", collection = PHOTOS, keywords = "arvore campo foto natureza verde"),
        Wallpaper("ft_cristais_de_gelo", "Cristais de Gelo", 149, 0xFF73A7E6, style = "photo", collection = PHOTOS, keywords = "cristais foto gelo inverno natureza"),
        Wallpaper("ft_cao_pastor", "Cão Pastor", 149, 0xFFE6D862, style = "photo", collection = PHOTOS, keywords = "animais animal cachorro cao foto natureza"),
        Wallpaper("ft_escadaria_amarela", "Escadaria Amarela", 149, 0xFFE6A74E, style = "photo", collection = PHOTOS, keywords = "cidade escada flores foto"),
        Wallpaper("ft_escadaria_de_pedra", "Escadaria de Pedra", 149, 0xFF73ADE6, style = "photo", collection = PHOTOS, keywords = "cidade escada foto pedra"),
        Wallpaper("ft_esquilo", "Esquilo", 149, 0xFFC7E628, style = "photo", collection = PHOTOS, keywords = "animais animal esquilo foto natureza"),
        Wallpaper("ft_fardos_de_feno", "Fardos de Feno", 149, 0xFFE69238, style = "photo", collection = PHOTOS, keywords = "campo fazenda feno foto natureza verao"),
        Wallpaper("ft_flores_de_cerejeira", "Flores de Cerejeira", 149, 0xFF73A5E6, style = "photo", collection = PHOTOS, keywords = "cerejeira flores foto natureza primavera"),
        Wallpaper("ft_foca", "Foca", 149, 0xFFE6C9B2, style = "photo", collection = PHOTOS, keywords = "animais animal foca foto mar natureza praia"),
        Wallpaper("ft_garca", "Garça", 149, 0xFFC1E661, style = "photo", collection = PHOTOS, keywords = "animais animal aves foto garca lago natureza"),
        Wallpaper("ft_guaxinim", "Guaxinim", 149, 0xFFC1E65C, style = "photo", collection = PHOTOS, keywords = "animais animal foto guaxinim natureza"),
        Wallpaper("ft_hibisco_vermelho", "Hibisco Vermelho", 149, 0xFFE64630, style = "photo", collection = PHOTOS, keywords = "flor flores foto hibisco natureza tropical"),
        Wallpaper("ft_lavanda_ao_por_do_sol", "Lavanda ao Pôr do Sol", 149, 0xFFFDD27F, style = "photo", collection = PHOTOS, keywords = "do flores foto lavanda natureza por sol"),
        Wallpaper("ft_libelula_azul", "Libélula Azul", 149, 0xFFBBE635, style = "photo", collection = PHOTOS, keywords = "azul foto inseto libelula natureza"),
        Wallpaper("ft_libelula_dourada", "Libélula Dourada", 149, 0xFFE6B766, style = "photo", collection = PHOTOS, keywords = "foto inseto libelula natureza trigo"),
        Wallpaper("ft_lontra_marinha", "Lontra-Marinha", 149, 0xFFE6AF73, style = "photo", collection = PHOTOS, keywords = "animais animal foto lontra mar natureza"),
        Wallpaper("ft_luz_no_celeiro", "Luz no Celeiro", 149, 0xFFE69B52, style = "photo", collection = PHOTOS, keywords = "celeiro fazenda feno foto luz natureza"),
        Wallpaper("ft_magnolia", "Magnólia", 149, 0xFFB2C0E6, style = "photo", collection = PHOTOS, keywords = "flor flores foto natureza primavera rosa"),
        Wallpaper("ft_martim_pescador", "Martim-Pescador", 149, 0xFFE67515, style = "photo", collection = PHOTOS, keywords = "animais animal aves foto natureza"),
        Wallpaper("ft_moinho_de_vento", "Moinho de Vento", 149, 0xFFE69E12, style = "photo", collection = PHOTOS, keywords = "campo do foto moinho natureza por sol"),
        Wallpaper("ft_muros_de_pedra", "Muros de Pedra", 149, 0xFFD0E622, style = "photo", collection = PHOTOS, keywords = "campo colinas foto natureza verde"),
        Wallpaper("ft_oliveiras", "Oliveiras", 149, 0xFFE6BB73, style = "photo", collection = PHOTOS, keywords = "arvores campo foto natureza oliveiras"),
        Wallpaper("ft_orquidea_branca", "Orquídea Branca", 149, 0xFFE6BC51, style = "photo", collection = PHOTOS, keywords = "branca flor flores foto natureza orquidea"),
        Wallpaper("ft_papagaio_do_mar", "Papagaio-do-Mar", 149, 0xFF73A4E6, style = "photo", collection = PHOTOS, keywords = "animais animal aves foto mar natureza"),
        Wallpaper("ft_passarela_no_parque", "Passarela no Parque", 149, 0xFFD9E63D, style = "photo", collection = PHOTOS, keywords = "cidade foto parque ponte"),
        Wallpaper("ft_perereca", "Perereca", 149, 0xFF9FE634, style = "photo", collection = PHOTOS, keywords = "animais animal foto natureza sapo verde"),
        Wallpaper("ft_pisco_no_inverno", "Pisco no Inverno", 149, 0xFFE67812, style = "photo", collection = PHOTOS, keywords = "animais animal aves foto inverno natureza"),
        Wallpaper("ft_portal_gotico", "Portal Gótico", 149, 0xFFE6C8B2, style = "photo", collection = PHOTOS, keywords = "arquitetura catedral foto igreja natureza"),
        Wallpaper("ft_pocas_da_mare", "Poças da Maré", 149, 0xFFE69734, style = "photo", collection = PHOTOS, keywords = "costa foto mar natureza pedras"),
        Wallpaper("ft_patio_das_laranjeiras", "Pátio das Laranjeiras", 149, 0xFFE67A27, style = "photo", collection = PHOTOS, keywords = "cidade fonte foto laranjas patio"),
        Wallpaper("ft_riacho_de_pedras", "Riacho de Pedras", 149, 0xFFE6AD63, style = "photo", collection = PHOTOS, keywords = "agua foto natureza pedras rio"),
        Wallpaper("ft_rua_colonial", "Rua Colonial", 149, 0xFFE67F54, style = "photo", collection = PHOTOS, keywords = "cidade colorida foto rua"),
        Wallpaper("ft_ruela_branca", "Ruela Branca", 149, 0xFFE6DD61, style = "photo", collection = PHOTOS, keywords = "cidade flores foto grecia rua"),
        Wallpaper("ft_suindara", "Suindara", 149, 0xFFE6C173, style = "photo", collection = PHOTOS, keywords = "animais animal aves coruja foto natureza"),
        Wallpaper("ft_arcadas_na_chuva", "Arcadas na Chuva", 199, 0xFFE68E38, style = "photo", collection = PHOTOS, keywords = "chuva cidade foto noite rua"),
        Wallpaper("ft_bambuzal", "Bambuzal", 199, 0xFFC6E645, style = "photo", collection = PHOTOS, keywords = "bambu floresta foto natureza trilha verde"),
        Wallpaper("ft_baoba", "Baobá", 199, 0xFFE6722E, style = "photo", collection = PHOTOS, keywords = "africa arvore do foto natureza por savana sol"),
        Wallpaper("ft_barco_no_lago_sereno", "Barco no Lago Sereno", 199, 0xFF4085E6, style = "photo", collection = PHOTOS, keywords = "barco foto lago natureza noite"),
        Wallpaper("ft_barquinho_no_porto", "Barquinho no Porto", 199, 0xFF73A7E6, style = "photo", collection = PHOTOS, keywords = "barco cidade foto mar porto"),
        Wallpaper("ft_baia_rosada", "Baía Rosada", 199, 0xFFEB7F54, style = "photo", collection = PHOTOS, keywords = "do foto mar natureza por rosa sol"),
        Wallpaper("ft_beco_do_castelo", "Beco do Castelo", 199, 0xFFB2C4E6, style = "photo", collection = PHOTOS, keywords = "castelo cidade foto pedra rua"),
        Wallpaper("ft_bosque_de_jacintos", "Bosque de Jacintos", 199, 0xFFDEE637, style = "photo", collection = PHOTOS, keywords = "flores floresta foto natureza primavera roxo"),
        Wallpaper("ft_cabana_no_lago", "Cabana no Lago", 199, 0xFFE69731, style = "photo", collection = PHOTOS, keywords = "cabana foto lago natureza neblina outono"),
        Wallpaper("ft_camaleao", "Camaleão", 199, 0xFFBEE619, style = "photo", collection = PHOTOS, keywords = "animais animal camaleao foto natureza verde"),
        Wallpaper("ft_caminho_nevado", "Caminho Nevado", 199, 0xFFB2C2E6, style = "photo", collection = PHOTOS, keywords = "arvores estrada foto inverno natureza neve"),
        Wallpaper("ft_campo_de_papoulas", "Campo de Papoulas", 199, 0xFFE6CA43, style = "photo", collection = PHOTOS, keywords = "campo flores foto natureza papoulas"),
        Wallpaper("ft_canal_veneziano", "Canal Veneziano", 199, 0xFFE69B73, style = "photo", collection = PHOTOS, keywords = "barco canal cidade foto veneza"),
        Wallpaper("ft_canal_com_bicicleta", "Canal com Bicicleta", 199, 0xFFE6D95B, style = "photo", collection = PHOTOS, keywords = "bicicleta canal cidade foto"),
        Wallpaper("ft_carroca_de_outono", "Carroça de Outono", 199, 0xFFE6A32F, style = "photo", collection = PHOTOS, keywords = "cavalo fazenda foto natureza outono"),
        Wallpaper("ft_cascata_na_mata", "Cascata na Mata", 199, 0xFFB8E623, style = "photo", collection = PHOTOS, keywords = "cachoeira floresta foto natureza rio"),
        Wallpaper("ft_casinha_vermelha", "Casinha Vermelha", 199, 0xFFB2CAE6, style = "photo", collection = PHOTOS, keywords = "casa foto lago montanhas natureza"),
        Wallpaper("ft_cavalo", "Cavalo", 199, 0xFFE6C04F, style = "photo", collection = PHOTOS, keywords = "animais animal campo cavalo foto natureza"),
        Wallpaper("ft_colinas_lilases", "Colinas Lilases", 199, 0xFF7398E6, style = "photo", collection = PHOTOS, keywords = "colinas deserto foto natureza roxo"),
        Wallpaper("ft_coqueiro_na_praia", "Coqueiro na Praia", 199, 0xFF428FE6, style = "photo", collection = PHOTOS, keywords = "coqueiro foto mar natureza praia tropical"),
        Wallpaper("ft_coruja_das_neves", "Coruja-das-Neves", 199, 0xFFB2C8E6, style = "photo", collection = PHOTOS, keywords = "animais animal aves coruja foto natureza"),
        Wallpaper("ft_costa_rochosa", "Costa Rochosa", 199, 0xFFE6CD5C, style = "photo", collection = PHOTOS, keywords = "costa foto mar natureza ondas pedras"),
        Wallpaper("ft_dunas_alaranjadas", "Dunas Alaranjadas", 199, 0xFFE6721A, style = "photo", collection = PHOTOS, keywords = "areia deserto dunas foto natureza"),
        Wallpaper("ft_escadinha_para_o_mar", "Escadinha para o Mar", 199, 0xFF73B0E6, style = "photo", collection = PHOTOS, keywords = "branca casa escada foto mar natureza"),
        Wallpaper("ft_estrada_na_serra", "Estrada na Serra", 199, 0xFFDEE65B, style = "photo", collection = PHOTOS, keywords = "estrada foto montanhas natureza pinheiros viagem"),
        Wallpaper("ft_farol", "Farol", 199, 0xFF73ADE6, style = "photo", collection = PHOTOS, keywords = "costa farol foto mar natureza"),
        Wallpaper("ft_flamingos", "Flamingos", 199, 0xFFB2C7E6, style = "photo", collection = PHOTOS, keywords = "animais animal aves flamingo foto natureza rosa"),
        Wallpaper("ft_garganta_do_riacho", "Garganta do Riacho", 199, 0xFFE6B66C, style = "photo", collection = PHOTOS, keywords = "canion foto natureza pedras riacho"),
        Wallpaper("ft_gato_preto", "Gato Preto", 199, 0xFFE6CFB2, style = "photo", collection = PHOTOS, keywords = "animais animal foto gato natureza"),
        Wallpaper("ft_girafa", "Girafa", 199, 0xFFE6B273, style = "photo", collection = PHOTOS, keywords = "africa animais animal foto girafa natureza savana"),
        Wallpaper("ft_girassois", "Girassóis", 199, 0xFFE6A912, style = "photo", collection = PHOTOS, keywords = "campo flores foto girassol natureza verao"),
        Wallpaper("ft_gol_na_neblina", "Gol na Neblina", 199, 0xFFE6CF73, style = "photo", collection = PHOTOS, keywords = "campo foto futebol gol natureza neblina"),
        Wallpaper("ft_gorila", "Gorila", 199, 0xFFBAE617, style = "photo", collection = PHOTOS, keywords = "animais animal floresta foto gorila natureza"),
        Wallpaper("ft_lua_sobre_o_deserto", "Lua sobre o Deserto", 199, 0xFF608DE6, style = "photo", collection = PHOTOS, keywords = "deserto foto lua natureza noite"),
        Wallpaper("ft_mesa_do_deserto", "Mesa do Deserto", 199, 0xFFE68A3D, style = "photo", collection = PHOTOS, keywords = "canion deserto foto montanha natureza"),
        Wallpaper("ft_orangotango", "Orangotango", 199, 0xFFC7E612, style = "photo", collection = PHOTOS, keywords = "animais animal floresta foto natureza orangotango"),
        Wallpaper("ft_ovelhas_no_penhasco", "Ovelhas no Penhasco", 199, 0xFFDDE645, style = "photo", collection = PHOTOS, keywords = "costa foto mar natureza ovelhas penhasco"),
        Wallpaper("ft_passarela_na_floresta", "Passarela na Floresta", 199, 0xFFC2E673, style = "photo", collection = PHOTOS, keywords = "floresta foto natureza neblina trilha"),
        Wallpaper("ft_pingentes_de_gelo", "Pingentes de Gelo", 199, 0xFFBED1F5, style = "photo", collection = PHOTOS, keywords = "casa foto gelo inverno natureza neve"),
        Wallpaper("ft_pinheiros_nevados", "Pinheiros Nevados", 199, 0xFFB2C3E6, style = "photo", collection = PHOTOS, keywords = "foto inverno natureza neve pinheiros"),
        Wallpaper("ft_plantacao_de_cha", "Plantação de Chá", 199, 0xFFBDE62E, style = "photo", collection = PHOTOS, keywords = "campo foto natureza plantacao verde"),
        Wallpaper("ft_porto_colorido", "Porto Colorido", 199, 0xFFE69954, style = "photo", collection = PHOTOS, keywords = "barcos cidade foto mar porto"),
        Wallpaper("ft_predios_infinitos", "Prédios Infinitos", 199, 0xFF76B0EC, style = "photo", collection = PHOTOS, keywords = "arquitetura cidade foto predios"),
        Wallpaper("ft_revoada_ao_por_do_sol", "Revoada ao Pôr do Sol", 199, 0xFFE8876F, style = "photo", collection = PHOTOS, keywords = "aves do foto lago natureza passaros por sol"),
        Wallpaper("ft_riacho_na_mata", "Riacho na Mata", 199, 0xFFD0E634, style = "photo", collection = PHOTOS, keywords = "floresta foto natureza pedras riacho rio"),
        Wallpaper("ft_rosa_vermelha", "Rosa Vermelha", 199, 0xFFE61235, style = "photo", collection = PHOTOS, keywords = "amor flor flores foto natureza rosa vermelha"),
        Wallpaper("ft_serras_em_camadas", "Serras em Camadas", 199, 0xFFFDB37F, style = "photo", collection = PHOTOS, keywords = "foto montanhas natureza neblina serra"),
        Wallpaper("ft_teia_com_orvalho", "Teia com Orvalho", 199, 0xFFE6D630, style = "photo", collection = PHOTOS, keywords = "foto manha natureza orvalho teia"),
        Wallpaper("ft_trilha_para_a_praia", "Trilha para a Praia", 199, 0xFF73B4E6, style = "photo", collection = PHOTOS, keywords = "foto natureza praia trilha tropical"),
        Wallpaper("ft_tucano", "Tucano", 199, 0xFFC5E612, style = "photo", collection = PHOTOS, keywords = "animais animal aves foto natureza tropical tucano"),
        Wallpaper("ft_urzes_nas_colinas", "Urzes nas Colinas", 199, 0xFFE6AD73, style = "photo", collection = PHOTOS, keywords = "colinas flores foto lago natureza"),
        Wallpaper("ft_vale_de_outono", "Vale de Outono", 199, 0xFFE6813B, style = "photo", collection = PHOTOS, keywords = "foto lago montanhas natureza outono vale"),
        Wallpaper("ft_varanda_sobre_o_mar", "Varanda sobre o Mar", 199, 0xFFE68F73, style = "photo", collection = PHOTOS, keywords = "cidade do foto mar por sol varanda"),
        Wallpaper("ft_zebras", "Zebras", 199, 0xFFE6AE73, style = "photo", collection = PHOTOS, keywords = "africa animais animal foto natureza savana zebra"),
        Wallpaper("ft_aguia", "Águia", 199, 0xFFB2C5E6, style = "photo", collection = PHOTOS, keywords = "aguia animais animal aves foto natureza"),
        Wallpaper("ft_beija_flor", "Beija-Flor", 249, 0xFFDCE64B, style = "photo", collection = PHOTOS, keywords = "animais animal aves beija flor foto natureza"),
        Wallpaper("ft_cachoeira", "Cachoeira", 249, 0xFFD6E629, style = "photo", collection = PHOTOS, keywords = "cachoeira foto montanhas natureza"),
        Wallpaper("ft_canal_ao_anoitecer", "Canal ao Anoitecer", 249, 0xFF739FE6, style = "photo", collection = PHOTOS, keywords = "canal cidade foto noite reflexo"),
        Wallpaper("ft_casa_de_pedra_na_montanha", "Casa de Pedra na Montanha", 249, 0xFFE68815, style = "photo", collection = PHOTOS, keywords = "casa foto montanhas natureza outono"),
        Wallpaper("ft_canion_vermelho", "Cânion Vermelho", 249, 0xFFE69E67, style = "photo", collection = PHOTOS, keywords = "canion deserto foto natureza pedras rio"),
        Wallpaper("ft_ceu_rosado_na_praia", "Céu Rosado na Praia", 249, 0xFFE69473, style = "photo", collection = PHOTOS, keywords = "do foto natureza por praia rosa sol"),
        Wallpaper("ft_elefantes", "Elefantes", 249, 0xFFE6B06D, style = "photo", collection = PHOTOS, keywords = "africa animais animal elefante foto natureza savana"),
        Wallpaper("ft_enseada_verde", "Enseada Verde", 249, 0xFFCBE64B, style = "photo", collection = PHOTOS, keywords = "costa foto mar natureza praia verde"),
        Wallpaper("ft_estacao_das_cerejeiras", "Estação das Cerejeiras", 249, 0xFFE6B366, style = "photo", collection = PHOTOS, keywords = "cerejeira estacao foto natureza primavera trem"),
        Wallpaper("ft_estrada_costeira", "Estrada Costeira", 249, 0xFFE6AC73, style = "photo", collection = PHOTOS, keywords = "costa estrada foto mar natureza ponte viagem"),
        Wallpaper("ft_estrada_dos_ciprestes", "Estrada dos Ciprestes", 249, 0xFFCCE656, style = "photo", collection = PHOTOS, keywords = "campo estrada foto italia natureza viagem"),
        Wallpaper("ft_guepardo", "Guepardo", 249, 0xFFE6CD73, style = "photo", collection = PHOTOS, keywords = "animais animal felino foto guepardo natureza"),
        Wallpaper("ft_jardim_japones", "Jardim Japonês", 249, 0xFFE1E63E, style = "photo", collection = PHOTOS, keywords = "cerejeira cidade foto japao jardim"),
        Wallpaper("ft_lago_alpino", "Lago Alpino", 249, 0xFF73A9E6, style = "photo", collection = PHOTOS, keywords = "foto lago montanhas natureza neve"),
        Wallpaper("ft_lago_da_cratera", "Lago da Cratera", 249, 0xFFE6DB56, style = "photo", collection = PHOTOS, keywords = "foto lago montanhas natureza vulcao"),
        Wallpaper("ft_lagoa_do_altiplano", "Lagoa do Altiplano", 249, 0xFF4C8EE6, style = "photo", collection = PHOTOS, keywords = "deserto foto lagoa montanhas natureza vulcao"),
        Wallpaper("ft_mesquitas_ao_entardecer", "Mesquitas ao Entardecer", 249, 0xFFE69B73, style = "photo", collection = PHOTOS, keywords = "cidade do foto mar por sol"),
        Wallpaper("ft_mirante_do_mediterraneo", "Mirante do Mediterrâneo", 249, 0xFFE6BE2D, style = "photo", collection = PHOTOS, keywords = "costa foto mar montanhas natureza"),
        Wallpaper("ft_onda_de_vidro", "Onda de Vidro", 249, 0xFF73A8E6, style = "photo", collection = PHOTOS, keywords = "foto mar natureza onda surf"),
        Wallpaper("ft_pavao", "Pavão", 249, 0xFFE6B856, style = "photo", collection = PHOTOS, keywords = "animais animal aves foto natureza pavao"),
        Wallpaper("ft_pinguins", "Pinguins", 249, 0xFF73A5E6, style = "photo", collection = PHOTOS, keywords = "animais animal foto gelo natureza pinguim"),
        Wallpaper("ft_pomar_em_flor", "Pomar em Flor", 249, 0xFFE6E449, style = "photo", collection = PHOTOS, keywords = "arvores cerejeira flores foto natureza primavera"),
        Wallpaper("ft_prado_alpino", "Prado Alpino", 249, 0xFFE6D52D, style = "photo", collection = PHOTOS, keywords = "cachoeira flores foto montanhas natureza"),
        Wallpaper("ft_praia_de_areia_negra", "Praia de Areia Negra", 249, 0xFFB2C5E6, style = "photo", collection = PHOTOS, keywords = "foto mar natureza penhasco praia"),
        Wallpaper("ft_ruela_na_chuva", "Ruela na Chuva", 249, 0xFFCFE647, style = "photo", collection = PHOTOS, keywords = "chuva cidade flores foto rua"),
        Wallpaper("ft_surfistas_ao_entardecer", "Surfistas ao Entardecer", 249, 0xFFF4B27A, style = "photo", collection = PHOTOS, keywords = "do foto mar natureza por praia sol surf"),
        Wallpaper("ft_tempestade_no_campo", "Tempestade no Campo", 249, 0xFF739DE6, style = "photo", collection = PHOTOS, keywords = "celeiro chuva fazenda foto natureza nuvens"),
        Wallpaper("ft_terras_coloridas", "Terras Coloridas", 249, 0xFFE68643, style = "photo", collection = PHOTOS, keywords = "colinas cores deserto foto natureza"),
        Wallpaper("ft_terracos_de_arroz", "Terraços de Arroz", 249, 0xFFE6CC12, style = "photo", collection = PHOTOS, keywords = "campo foto montanhas natureza neblina verde"),
        Wallpaper("ft_vale_do_rio", "Vale do Rio", 249, 0xFFE6DA4E, style = "photo", collection = PHOTOS, keywords = "foto montanhas natureza rio vale"),
        Wallpaper("ft_veleiro_no_azul", "Veleiro no Azul", 249, 0xFF419CE6, style = "photo", collection = PHOTOS, keywords = "azul barco foto mar natureza veleiro"),
        Wallpaper("ft_viaduto_na_serra", "Viaduto na Serra", 249, 0xFFDDE639, style = "photo", collection = PHOTOS, keywords = "foto montanhas natureza trem viaduto viagem"),
        Wallpaper("ft_vista_da_cupula", "Vista da Cúpula", 249, 0xFFE68240, style = "photo", collection = PHOTOS, keywords = "cidade foto italia telhados"),
        Wallpaper("ft_biblioteca_antiga", "Biblioteca Antiga", 299, 0xFFE68B3A, style = "photo", collection = PHOTOS, keywords = "biblioteca estudo foto livros natureza"),
        Wallpaper("ft_lago_turquesa", "Lago Turquesa", 299, 0xFF5FC6E6, style = "photo", collection = PHOTOS, keywords = "foto lago montanhas natureza pinheiros turquesa"),
        Wallpaper("ft_ondas_turquesa", "Ondas Turquesa", 299, 0xFF24D8E6, style = "photo", collection = PHOTOS, keywords = "aereo foto mar natureza ondas praia"),
        Wallpaper("ft_onca_pintada", "Onça-Pintada", 299, 0xFFB1E633, style = "photo", collection = PHOTOS, keywords = "animais animal felino foto natureza onca"),
        Wallpaper("ft_picos_da_patagonia", "Picos da Patagônia", 299, 0xFF73B3E6, style = "photo", collection = PHOTOS, keywords = "foto lago montanhas natureza neve"),
        Wallpaper("ft_trem_do_lago", "Trem do Lago", 299, 0xFF74B0E7, style = "photo", collection = PHOTOS, keywords = "foto lago montanhas natureza trem viagem"),
        Wallpaper("ft_vale_das_palmeiras", "Vale das Palmeiras", 299, 0xFFCFE643, style = "photo", collection = PHOTOS, keywords = "foto montanhas natureza neblina palmeiras vale"),
        // ---- Natureza 3D (11)
        Wallpaper("nt_aurora_artica", "Aurora Ártica", 0, 0xFF1CE6C2, plans = setOf("pro"), style = "render3d", collection = NATURE3D, keywords = "3d montanhas natureza noite"),
        Wallpaper("nt_por_do_sol_mar", "Pôr do Sol no Mar", 0, 0xFFEC9776, plans = setOf("go", "plus", "pro"), style = "render3d", collection = NATURE3D, keywords = "3d mar natureza"),
        Wallpaper("nt_dunas_douradas", "Dunas Douradas", 199, 0xFF669AE6, style = "render3d", collection = NATURE3D, keywords = "3d deserto natureza"),
        Wallpaper("nt_dunas_estrelas", "Dunas sob as Estrelas", 199, 0xFF7387E6, style = "render3d", collection = NATURE3D, keywords = "3d deserto espaco natureza noite"),
        Wallpaper("nt_deserto_vermelho", "Deserto Vermelho", 249, 0xFFEEA077, style = "render3d", collection = NATURE3D, keywords = "3d deserto natureza"),
        Wallpaper("nt_lago_espelho", "Lago Espelho", 249, 0xFF5B8AE6, style = "render3d", collection = NATURE3D, keywords = "3d montanhas natureza"),
        Wallpaper("nt_alpes_amanhecer", "Alpes ao Amanhecer", 299, 0xFF6487E6, style = "render3d", collection = NATURE3D, keywords = "3d montanhas natureza"),
        Wallpaper("nt_canion", "Cânion do Rio", 299, 0xFFF7CC7C, style = "render3d", collection = NATURE3D, keywords = "3d deserto montanhas natureza"),
        Wallpaper("nt_lago_esmeralda", "Lago Esmeralda", 299, 0xFF73ABE6, style = "render3d", collection = NATURE3D, keywords = "3d montanhas natureza"),
        Wallpaper("nt_fiorde", "Fiorde", 399, 0xFF6BA5E6, style = "render3d", collection = NATURE3D, keywords = "3d mar montanhas natureza"),
        Wallpaper("nt_inverno_anoitecer", "Inverno ao Anoitecer", 399, 0xFF7380E6, style = "render3d", collection = NATURE3D, keywords = "3d montanhas natureza noite"),
        // ---- Games 3D (11)
        Wallpaper("gm_cidade_cyberpunk", "Cidade Cyberpunk", 0, 0xFFE651E1, plans = setOf("plus", "pro"), style = "render3d", collection = GAMES3D, keywords = "3d cidades games jogos neon noite"),
        Wallpaper("gm_cristais_neon", "Cristais Neon", 0, 0xFF2B39E6, plans = setOf("pro"), style = "render3d", collection = GAMES3D, keywords = "3d games jogos neon noite"),
        Wallpaper("gm_mundo_de_lava", "Mundo de Lava", 0, 0xFFF5DC3D, plans = setOf("pro"), style = "render3d", collection = GAMES3D, keywords = "3d games jogos"),
        Wallpaper("gm_planeta_alienigena", "Planeta Alienígena", 0, 0xFFB773E6, plans = setOf("pro"), style = "render3d", collection = GAMES3D, keywords = "3d espaco games jogos"),
        Wallpaper("gm_estrada_montanha", "Estrada na Montanha", 249, 0xFF5D89E6, style = "render3d", collection = GAMES3D, keywords = "3d corrida games jogos montanhas"),
        Wallpaper("gm_mundo_low_poly", "Mundo Low-Poly", 249, 0xFF7381E6, style = "render3d", collection = GAMES3D, keywords = "3d games jogos montanhas natureza"),
        Wallpaper("gm_mundo_de_blocos", "Mundo de Blocos", 299, 0xFF4997E6, style = "render3d", collection = GAMES3D, keywords = "3d games jogos natureza"),
        Wallpaper("gm_por_do_sol_marte", "Pôr do Sol em Marte", 299, 0xFFE6B2CC, style = "render3d", collection = GAMES3D, keywords = "3d deserto espaco games jogos"),
        Wallpaper("gm_superficie_lunar", "Superfície Lunar", 299, 0xFF6AA0E6, style = "render3d", collection = GAMES3D, keywords = "3d espaco games jogos"),
        Wallpaper("gm_castelo_fantasia", "Castelo Fantasia", 399, 0xFF816EE6, style = "render3d", collection = GAMES3D, keywords = "3d games jogos montanhas"),
        Wallpaper("gm_estrada_synthwave", "Estrada Synthwave", 399, 0xFFE617C5, style = "render3d", collection = GAMES3D, keywords = "3d corrida games jogos neon"),
        // ---- Coleção clássica (17)
        Wallpaper("noite_estrelada", "Noite Estrelada", 0, 0xFFB2BDE6, style = "mountains", keywords = "montanhas natureza noite"),
        Wallpaper("nevoa_verde", "Névoa Verde", 0, 0xFF73E6BE, style = "mountains", keywords = "montanhas natureza"),
        Wallpaper("nebulosa_azul", "Nebulosa Azul", 99, 0xFF73C4E6, style = "nebula", keywords = "espaco estrelas nebulosa"),
        Wallpaper("nebulosa_rosa", "Nebulosa Rosa", 99, 0xFFE673B8, style = "nebula", keywords = "espaco estrelas nebulosa"),
        Wallpaper("seda_dourada", "Seda Dourada", 99, 0xFFE6AF43, style = "silk", keywords = "abstrato seda"),
        Wallpaper("aurora_lago", "Aurora no Lago", 149, 0xFF3FE6C0, style = "aurora", keywords = "aurora natureza noite"),
        Wallpaper("pro_eclipse", "Eclipse", 149, 0xFFE67812, style = "planet", keywords = "espaco noite planeta"),
        Wallpaper("galaxia_violeta", "Galáxia Violeta", 149, 0xFF9D4DE6, style = "spiral", keywords = "espaco galaxia"),
        Wallpaper("aurora_rosa", "Aurora Rosa", 199, 0xFFD262E6, style = "aurora", keywords = "aurora natureza noite"),
        Wallpaper("pro_nebulosa_esmeralda", "Nebulosa Esmeralda", 199, 0xFF73E6AF, style = "nebula", keywords = "espaco estrelas nebulosa"),
        Wallpaper("nebulosa_fenix", "Nebulosa Fênix", 199, 0xFFE6B473, style = "nebula", keywords = "espaco estrelas nebulosa"),
        Wallpaper("planeta_gelo", "Planeta de Gelo", 199, 0xFF73BDE6, style = "planet", keywords = "espaco planeta"),
        Wallpaper("portal_cosmico", "Portal Cósmico", 199, 0xFF3661E6, style = "spiral", keywords = "espaco galaxia"),
        Wallpaper("saturno_dourado", "Saturno Dourado", 199, 0xFFE69749, style = "planet", keywords = "espaco planeta"),
        Wallpaper("supernova", "Supernova", 199, 0xFFE6127C, style = "supernova", keywords = "espaco estrela"),
        Wallpaper("via_lactea", "Via Láctea", 199, 0xFF7399E6, style = "milkyway", keywords = "espaco estrelas noite"),
        Wallpaper("pro_planeta_rubi", "Planeta Rubi", 399, 0xFFE61E47, style = "planet", keywords = "espaco planeta"),
        // ---- Heróis & Vilões (12)
        Wallpaper("hv_fogo_azul", "Fogo Azul", 99, 0xFF1269E6, style = "inferno", collection = HEROES, keywords = "fogo herois"),
        Wallpaper("hv_impacto_azul", "Impacto Azul", 99, 0xFF126DE6, style = "comic", collection = HEROES, keywords = "herois quadrinhos"),
        Wallpaper("hv_rastro_azul", "Rastro Azul", 99, 0xFF73B7E6, style = "speed", collection = HEROES, keywords = "herois velocidade"),
        Wallpaper("hv_circuito_tatico", "Circuito Tático", 149, 0xFF5DD4E6, style = "circuit", collection = HEROES, keywords = "herois neon tecnologia"),
        Wallpaper("hv_escudo_dourado", "Escudo Dourado", 149, 0xFFE6C04F, style = "hexshield", collection = HEROES, keywords = "herois neon"),
        Wallpaper("hv_amanhecer_heroi", "Amanhecer do Herói", 199, 0xFFEE8C47, style = "dawncity", collection = HEROES, keywords = "cidade cidades herois"),
        Wallpaper("hv_fenda_dimensional", "Fenda Dimensional", 199, 0xFFA05BE6, style = "rift", collection = HEROES, keywords = "herois raio"),
        Wallpaper("hv_furia_tempestade", "Fúria da Tempestade", 199, 0xFF72B1E6, style = "storm", collection = HEROES, keywords = "herois raio tempestade"),
        Wallpaper("hv_impacto_sismico", "Impacto Sísmico", 199, 0xFFE68F4C, style = "shatter", collection = HEROES, keywords = "herois"),
        Wallpaper("hv_inferno_rubro", "Inferno Rubro", 199, 0xFFE65712, style = "inferno", collection = HEROES, keywords = "fogo herois"),
        Wallpaper("hv_nucleo_plasma", "Núcleo de Plasma", 199, 0xFF73CCE6, style = "plasma", collection = HEROES, keywords = "herois neon"),
        Wallpaper("hv_vortice_caos", "Vórtice do Caos", 199, 0xFF47E685, style = "vortex", collection = HEROES, keywords = "herois raio"),
        // ---- Mundos de Jogo (10)
        Wallpaper("jg_cupula_holografica", "Cúpula Holográfica", 199, 0xFFB2E6D8, style = "cyber", collection = GAMES, keywords = "cidade cidades games jogos neon"),
        Wallpaper("jg_turbo_ciano", "Turbo Ciano", 199, 0xFF43E6E3, style = "racing", collection = GAMES, keywords = "corrida estrada games jogos neon"),
        Wallpaper("jg_cervo_real", "Cervo Real", 249, 0xFFFED053, style = "hunt", collection = GAMES, keywords = "cervo floresta games jogos natureza"),
        Wallpaper("jg_coroa_celestial", "Coroa Celestial", 249, 0xFFE6C373, style = "fantasy", collection = GAMES, keywords = "castelo fantasia games jogos"),
        Wallpaper("jg_fortaleza_lunar", "Fortaleza Lunar", 249, 0xFFB2BDE6, style = "fantasy", collection = GAMES, keywords = "castelo fantasia games jogos"),
        Wallpaper("jg_floresta_boreal", "Floresta Boreal", 299, 0xFFFBE9C2, style = "hunt", collection = GAMES, keywords = "cervo floresta games jogos natureza"),
        Wallpaper("jg_lua_sangue", "Lua de Sangue", 299, 0xFFE61F12, style = "fantasy", collection = GAMES, keywords = "castelo fantasia games jogos noite"),
        Wallpaper("jg_por_do_sol_cibernetico", "Pôr do Sol Cibernético", 299, 0xFFE67673, style = "cyber", collection = GAMES, keywords = "cidade cidades games jogos neon"),
        Wallpaper("jg_retao_dourado", "Retão Dourado", 299, 0xFFE6C13B, style = "racing", collection = GAMES, keywords = "corrida estrada games jogos neon"),
        Wallpaper("jg_metropole_neon", "Metrópole Neon", 499, 0xFFC569E6, style = "cyber", collection = GAMES, keywords = "cidade cidades games jogos neon"),
        // ---- Premium 3D (24)
        Wallpaper("pm_colmeia_rubra", "Colmeia Rubra", 0, 0xFFE61265, plans = setOf("pro"), style = "hex3d", collection = PREMIUM, keywords = "3d abstrato colmeia neon premium"),
        Wallpaper("pm_esfera_cristal", "Esfera de Cristal", 0, 0xFFA157E6, plans = setOf("pro"), style = "glass", collection = PREMIUM, keywords = "3d abstrato esferas premium vidro"),
        Wallpaper("pm_cubo_ciano", "Cubo Ciano", 99, 0xFF12E6D4, style = "cubes", collection = PREMIUM, keywords = "3d abstrato cubos premium"),
        Wallpaper("pm_seda_aurora", "Seda Aurora", 99, 0xFF73E6C2, style = "liquidwave", collection = PREMIUM, keywords = "3d abstrato noite ondas premium"),
        Wallpaper("pm_colmeia_noturna", "Colmeia Noturna", 199, 0xFF5890E6, style = "hex3d", collection = PREMIUM, keywords = "3d abstrato colmeia neon noite premium"),
        Wallpaper("pm_cubo_rubro", "Cubo Rubro", 199, 0xFFE61222, style = "cubes", collection = PREMIUM, keywords = "3d abstrato cubos premium"),
        Wallpaper("pm_fumaca_fogo", "Fumaça de Fogo", 199, 0xFFE68B12, style = "silksmoke", collection = PREMIUM, keywords = "3d abstrato fumaca neon premium"),
        Wallpaper("pm_ondas_cosmicas", "Ondas Cósmicas", 199, 0xFF421EE6, style = "liquidwave", collection = PREMIUM, keywords = "3d abstrato ondas premium"),
        Wallpaper("pm_colmeia_violeta", "Colmeia Violeta", 249, 0xFFD965E6, style = "hex3d", collection = PREMIUM, keywords = "3d abstrato colmeia neon premium"),
        Wallpaper("pm_coracao_chuva", "Coração na Chuva", 249, 0xFFE61234, style = "drops", collection = PREMIUM, keywords = "3d abstrato gotas premium"),
        Wallpaper("pm_crepusculo_violeta", "Crepúsculo Violeta", 249, 0xFFE67389, style = "sunsea", collection = PREMIUM, keywords = "3d do mar natureza por premium sol"),
        Wallpaper("pm_facetas_ouro", "Facetas de Ouro", 249, 0xFFE6BC69, style = "facets", collection = PREMIUM, keywords = "3d abstrato premium"),
        Wallpaper("pm_fissura_dourada", "Fissura Dourada", 249, 0xFFE6A024, style = "shards3d", collection = PREMIUM, keywords = "3d abstrato lava premium"),
        Wallpaper("pm_fumaca_neon", "Fumaça Neon", 249, 0xFFE64C8C, style = "silksmoke", collection = PREMIUM, keywords = "3d abstrato fumaca neon premium"),
        Wallpaper("pm_fumaca_oceano", "Fumaça Oceano", 249, 0xFF12CEE6, style = "silksmoke", collection = PREMIUM, keywords = "3d abstrato fumaca mar neon premium"),
        Wallpaper("pm_mar_carmesim", "Mar Carmesim", 249, 0xFFE62F32, style = "sunsea", collection = PREMIUM, keywords = "3d do mar natureza por premium sol"),
        Wallpaper("pm_nucleo_lava", "Núcleo de Lava", 249, 0xFFE6A966, style = "shards3d", collection = PREMIUM, keywords = "3d abstrato lava premium"),
        Wallpaper("pm_orbe_noturno", "Orbe Noturno", 249, 0xFFB55CE6, style = "glass", collection = PREMIUM, keywords = "3d abstrato esferas noite premium vidro"),
        Wallpaper("pm_sorriso_chuva", "Sorriso na Chuva", 249, 0xFFE6A812, style = "drops", collection = PREMIUM, keywords = "3d abstrato gotas premium"),
        Wallpaper("pm_trio_orbes", "Trio de Orbes", 249, 0xFF9473E6, style = "glass", collection = PREMIUM, keywords = "3d abstrato esferas premium vidro"),
        Wallpaper("pm_prisma_molhado", "Prisma Molhado", 299, 0xFF2FE649, style = "drops", collection = PREMIUM, keywords = "3d abstrato gotas premium"),
        Wallpaper("pm_seda_iridescente", "Seda Iridescente", 299, 0xFF73E6D7, style = "liquidwave", collection = PREMIUM, keywords = "3d abstrato ondas premium"),
        Wallpaper("pm_sol_de_fogo", "Sol de Fogo", 399, 0xFFEC4913, style = "sunsea", collection = PREMIUM, keywords = "3d do mar natureza por premium sol"),
        Wallpaper("pm_colmeia_dourada", "Colmeia Dourada", 499, 0xFFE6A626, style = "hex3d", collection = PREMIUM, keywords = "3d abstrato colmeia neon premium"),
        // ---- Inspirações (28)
        Wallpaper("rf_chuva_na_cidade", "Chuva na Cidade", 0, 0xFFE6A96A, plans = setOf("plus", "pro"), style = "rainwindow", collection = INSPIRATIONS, keywords = "chuva cidade cidades"),
        Wallpaper("rf_terra_noturna", "Terra Noturna", 0, 0xFF58ACE6, plans = setOf("pro"), style = "earth", collection = INSPIRATIONS, keywords = "espaco noite planeta terra"),
        Wallpaper("rf_estrada_na_neblina", "Estrada na Neblina", 99, 0xFFB2D9E6, style = "aerialroad", collection = INSPIRATIONS, keywords = "aereo corrida estrada natureza"),
        Wallpaper("rf_sorriso_sombrio", "Sorriso Sombrio", 199, 0xFFE62E2F, style = "grin", collection = INSPIRATIONS, keywords = "misterioso sorriso"),
        Wallpaper("rf_bordo_molhado", "Bordô Molhado", 249, 0xFFE62612, style = "leaves", collection = INSPIRATIONS, keywords = "flores folhas natureza outono"),
        Wallpaper("rf_cubos_neon", "Cubos Neon", 249, 0xFFE673BC, style = "cubefield", collection = INSPIRATIONS, keywords = "abstrato cubos neon"),
        Wallpaper("rf_encapuzado_rubro", "Encapuzado Rubro", 249, 0xFFE6121B, style = "hood", collection = INSPIRATIONS, keywords = "misterioso"),
        Wallpaper("rf_fumaca_cosmica", "Fumaça Cósmica", 249, 0xFF4152E6, style = "smoke", collection = INSPIRATIONS, keywords = "abstrato fumaca"),
        Wallpaper("rf_gruta_do_por_do_sol", "Gruta do Pôr do Sol", 249, 0xFFEEB674, style = "seaarch", collection = INSPIRATIONS, keywords = "gruta mar natureza"),
        Wallpaper("rf_hibisco_rosa", "Hibisco Rosa", 249, 0xFFE6264E, style = "flower", collection = INSPIRATIONS, keywords = "flor flores natureza"),
        Wallpaper("rf_hora_azul_no_lago", "Hora Azul no Lago", 249, 0xFF73D1E6, style = "lake", collection = INSPIRATIONS, keywords = "lago natureza"),
        Wallpaper("rf_lago_rosado", "Lago Rosado", 249, 0xFF7773E6, style = "lake", collection = INSPIRATIONS, keywords = "lago natureza"),
        Wallpaper("rf_lobo_de_fogo", "Lobo de Fogo", 249, 0xFFE69623, style = "emblem", collection = INSPIRATIONS, keywords = "lobo misterioso neon"),
        Wallpaper("rf_mar_de_gelo", "Mar de Gelo", 249, 0xFFD06BE6, style = "icysea", collection = INSPIRATIONS, keywords = "gelo mar natureza"),
        Wallpaper("rf_nascer_orbital", "Nascer Orbital", 249, 0xFFE6CC73, style = "earth", collection = INSPIRATIONS, keywords = "espaco planeta terra"),
        Wallpaper("rf_olhar_felino", "Olhar Felino", 249, 0xFFB1E673, style = "eyes", collection = INSPIRATIONS, keywords = "misterioso olhos"),
        Wallpaper("rf_onda_de_tinta", "Onda de Tinta", 249, 0xFFF7CD21, style = "paint", collection = INSPIRATIONS, keywords = "abstrato marmore tinta"),
        Wallpaper("rf_pico_em_brasa", "Pico em Brasa", 249, 0xFFE65E1E, style = "peak", collection = INSPIRATIONS, keywords = "montanhas natureza pico"),
        Wallpaper("rf_folhas_outono", "Folhas de Outono", 299, 0xFFE66912, style = "leaves", collection = INSPIRATIONS, keywords = "flores folhas natureza outono"),
        Wallpaper("rf_noite_polar", "Noite Polar", 299, 0xFF738EE6, style = "icysea", collection = INSPIRATIONS, keywords = "gelo mar natureza noite"),
        Wallpaper("rf_aurora_no_pico", "Aurora no Pico", 399, 0xFF14E6AB, style = "peak", collection = INSPIRATIONS, keywords = "montanhas natureza noite pico"),
        Wallpaper("rf_baia_turquesa", "Baía Turquesa", 399, 0xFF42E6D1, style = "karst", collection = INSPIRATIONS, keywords = "ilha mar natureza"),
        Wallpaper("rf_fumaca_brasa", "Fumaça de Brasa", 399, 0xFFE67312, style = "smoke", collection = INSPIRATIONS, keywords = "abstrato fumaca"),
        Wallpaper("rf_hibisco_azul", "Hibisco Azul", 399, 0xFF165AE6, style = "flower", collection = INSPIRATIONS, keywords = "flor flores natureza"),
        Wallpaper("rf_marmore_dourado", "Mármore Dourado", 399, 0xFFE6AA1B, style = "paint", collection = INSPIRATIONS, keywords = "abstrato marmore tinta"),
        Wallpaper("rf_vidro_azul", "Vidro Azul", 399, 0xFF73B5E6, style = "rainwindow", collection = INSPIRATIONS, keywords = "chuva cidade cidades"),
        Wallpaper("rf_amanhecer_frio", "Amanhecer Frio", 499, 0xFF73A5E6, style = "peak", collection = INSPIRATIONS, keywords = "montanhas natureza pico"),
        Wallpaper("rf_luzes_do_planeta", "Luzes do Planeta", 499, 0xFF3A9EE6, style = "earth", collection = INSPIRATIONS, keywords = "espaco planeta terra"),
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
