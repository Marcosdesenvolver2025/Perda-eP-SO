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
 * Catálogo dos temas (298): temas novos em alta qualidade, exclusivos do Pro e as melhores coleções antigas.
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

    /** Assunto do tema, para mostrar os parecidos juntos em cada seção (ex.: os de fé lado a lado). */
    fun topicOf(w: Wallpaper): Int {
        val k = " " + w.keywords + " " + w.name.lowercase() + " "
        fun has(vararg t: String) = t.any { k.contains(" $it") }
        return when {
            w.collection == PLACES -> 0
            w.collection == ANIME -> 1
            w.collection == CARS -> 2
            has("fe ", "jesus", "deus", "cruz", "oracao", "oração", "biblia", "bíblia", "anjo", "igreja", "capela", "nossa senhora", "fe,") -> 4
            w.collection == NATURE -> 3
            has("futebol", "estadio", "estádio", "goleiro", "torcida") -> 5
            has("musica", "música", "dj ", "guitarra", "violao", "violão", "piano", "fones", "vinil", "bateria", "saxofone", "violino", "show") -> 6
            else -> 7
        }
    }

    val all: List<Wallpaper> = listOf(
        // ---- Novos da versão 3.2.0: realistas, em alta qualidade
        Wallpaper("hy_rosa_orvalho", "Rosa com Orvalho", 399, 0xFFFF4557, style = "ai", collection = NATURE, keywords = "rosa vermelha flor orvalho romantico"),
        Wallpaper("hy_cara_de_raiva", "Cara de Raiva", 99, 0xFFFF986A, style = "ai", collection = POPULAR, keywords = "cara raiva bravo olhos emoji fofo"),
        Wallpaper("hy_olhos_chorando", "Olhos Chorando", 99, 0xFFC09EFF, style = "ai", collection = POPULAR, keywords = "olhos chorando triste lagrimas fofo emoji"),
        Wallpaper("hy_noronha", "Fernando de Noronha", 0, 0xFFFFBA69, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "brasil praia ilha mar noronha"),
        Wallpaper("hy_jericoacoara", "Jericoacoara", 399, 0xFFFF9526, style = "ai", collection = PLACES, keywords = "brasil praia dunas por do sol ceara"),
        Wallpaper("hy_chapada", "Chapada Diamantina", 399, 0xFFFF6529, style = "ai", collection = PLACES, keywords = "brasil chapada cachoeira canion bahia"),
        Wallpaper("hy_bonito", "Rio Cristalino", 399, 0xFF4EE8FF, style = "ai", collection = PLACES, keywords = "brasil bonito rio cristalino peixes"),
        Wallpaper("hy_ouro_preto", "Ouro Preto", 399, 0xFF269AFF, style = "ai", collection = PLACES, keywords = "brasil ouro preto cidade historica igreja"),
        Wallpaper("hy_salvador", "Pelourinho", 399, 0xFFFFB85B, style = "ai", collection = PLACES, keywords = "brasil salvador bahia casas coloridas"),
        Wallpaper("hy_sao_paulo", "São Paulo à Noite", 399, 0xFFED80FF, style = "ai", collection = PLACES, keywords = "brasil sao paulo cidade predios noite luzes"),
        Wallpaper("hy_dubai", "Cidade no Deserto", 0, 0xFFFFA938, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "cidade futurista deserto arranha ceu noite"),
        Wallpaper("hy_hong_kong", "Porto Neon", 0, 0xFF4EB2FF, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "cidade neon porto asia noite predios"),
        Wallpaper("hy_londres", "Londres ao Entardecer", 399, 0xFFFF9E6A, style = "ai", collection = PLACES, keywords = "londres inglaterra ponte rio cidade"),
        Wallpaper("hy_seul", "Rua Neon de Seul", 399, 0xFFFFB926, style = "ai", collection = PLACES, keywords = "coreia seul rua neon noite cidade"),
        Wallpaper("hy_barcelona", "Barcelona", 99, 0xFFFFA771, style = "ai", collection = PLACES, keywords = "espanha barcelona cidade por do sol"),
        Wallpaper("hy_istambul", "Istambul", 399, 0xFFFFC169, style = "ai", collection = PLACES, keywords = "turquia istambul mesquita por do sol"),
        Wallpaper("hy_taj_mahal", "Taj Mahal", 0, 0xFFFF9F9E, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "india taj mahal amanhecer palacio"),
        Wallpaper("hy_muralha", "Muralha da China", 399, 0xFFFF8E49, style = "ai", collection = PLACES, keywords = "china muralha montanhas outono"),
        Wallpaper("hy_bali", "Arrozais de Bali", 399, 0xFFFFE963, style = "ai", collection = PLACES, keywords = "bali arroz terracos verde tropical"),
        Wallpaper("hy_hallstatt", "Vila nos Alpes", 0, 0xFFFFC69E, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "austria vila lago montanhas alpes"),
        Wallpaper("hy_dolomitas", "Dolomitas", 399, 0xFFFF828D, style = "ai", collection = PLACES, keywords = "italia dolomitas montanhas picos"),
        Wallpaper("hy_grand_canyon", "Grand Canyon", 399, 0xFFFFA039, style = "ai", collection = PLACES, keywords = "eua canion deserto por do sol"),
        Wallpaper("hy_lisboa", "Bonde de Lisboa", 399, 0xFFFFAD41, style = "ai", collection = PLACES, keywords = "portugal lisboa bonde rua amarelo"),
        Wallpaper("hy_amsterda", "Canais de Amsterdã", 399, 0xFFFFA140, style = "ai", collection = PLACES, keywords = "holanda amsterda canal bicicletas"),
        Wallpaper("hy_praga", "Praga", 399, 0xFFFFC568, style = "ai", collection = PLACES, keywords = "republica tcheca praga castelo ponte"),
        Wallpaper("hy_bambu", "Floresta de Bambu", 399, 0xFFFFF95C, style = "ai", collection = PLACES, keywords = "japao bambu floresta caminho verde"),
        Wallpaper("hy_hoi_an", "Lanternas de Hoi An", 0, 0xFFFF9126, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "vietna lanternas rio noite coloridas"),
        Wallpaper("hy_chefchaouen", "Cidade Azul", 399, 0xFF61B7FF, style = "ai", collection = PLACES, keywords = "marrocos cidade azul ruas"),
        Wallpaper("hy_bora_bora", "Bora Bora", 0, 0xFFFFC76A, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "ilha paraiso mar turquesa montanha"),
        Wallpaper("hy_patagonia", "Patagônia", 399, 0xFFFF7F6D, style = "ai", collection = PLACES, keywords = "patagonia montanhas lago torres"),
        Wallpaper("hy_lofoten", "Ilhas Lofoten", 0, 0xFF26FFB4, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "noruega ilhas aurora casas vermelhas"),
        Wallpaper("hy_terraco_sakura", "Terraço de Sakura", 399, 0xFF7DBFFF, style = "ai", collection = ANIME, keywords = "anime escola sakura telhado primavera"),
        Wallpaper("hy_chuva_anime", "Guarda-chuva na Chuva", 0, 0xFFFFA849, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "anime chuva rua guarda chuva noite"),
        Wallpaper("hy_janela_noite", "Cidade pela Janela", 399, 0xFFFFD99E, style = "ai", collection = ANIME, keywords = "anime cidade noite janela quarto"),
        Wallpaper("hy_estrelas_telhado", "Noite de Estrelas", 0, 0xFF8E9EFF, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "anime estrelas via lactea telhado casal"),
        Wallpaper("hy_festival_fogos", "Festival de Fogos", 0, 0xFFFFA776, plans = setOf("plus"), style = "ai", collection = ANIME, keywords = "anime fogos festival yukata verao"),
        Wallpaper("hy_praia_anime", "Praia de Anime", 399, 0xFFFFA06C, style = "ai", collection = ANIME, keywords = "anime praia por do sol mar verao"),
        Wallpaper("hy_biblioteca", "Biblioteca Mágica", 399, 0xFFFF972D, style = "ai", collection = ANIME, keywords = "fantasia biblioteca magica livros game"),
        Wallpaper("hy_torre_mago", "Torre do Mago", 0, 0xFF62B1FF, plans = setOf("plus"), style = "ai", collection = ANIME, keywords = "fantasia mago torre noite lua magia"),
        Wallpaper("hy_elfa", "Arqueira Élfica", 399, 0xFFFFE885, style = "ai", collection = ANIME, keywords = "fantasia elfa arqueira floresta game"),
        Wallpaper("hy_cavaleiro_dragao", "Cavaleiro e Dragão", 0, 0xFFFFB672, plans = setOf("plus"), style = "ai", collection = ANIME, keywords = "cavaleiro dragao fantasia batalha epico"),
        Wallpaper("hy_cyber_garota", "Garota Cyberpunk", 0, 0xFFFF6CD8, plans = setOf("plus"), style = "ai", collection = ANIME, keywords = "cyberpunk garota neon cidade anime"),
        Wallpaper("hy_arcade", "Fliperama Retrô", 299, 0xFFDA44FF, style = "ai", collection = ANIME, keywords = "retro fliperama arcade neon anos 80 games"),
        Wallpaper("hy_pixel_por_do_sol", "Cidade Pixel", 299, 0xFFFF515A, style = "ai", collection = ANIME, keywords = "pixel art cidade por do sol retro game"),
        Wallpaper("hy_gato_cafe", "Café dos Gatos", 299, 0xFFFF9B33, style = "ai", collection = ANIME, keywords = "anime gatos cafe fofo aconchegante"),
        Wallpaper("hy_duelo_chuva", "Duelo na Chuva", 299, 0xFF9EB3FF, style = "ai", collection = ANIME, keywords = "samurai duelo chuva espada anime"),
        Wallpaper("hy_cavaleiro_negro", "Cavaleiro Sombrio", 299, 0xFFCCD6E6, style = "ai", collection = ANIME, keywords = "cavaleiro armadura sombrio fantasia game"),
        Wallpaper("hy_castelo_aurora", "Castelo sob a Aurora", 0, 0xFF37FF9A, plans = setOf("plus"), style = "ai", collection = ANIME, keywords = "castelo aurora fantasia noite neve"),
        Wallpaper("hy_lanternas_ceu", "Lanternas no Céu", 0, 0xFFFFA558, plans = setOf("go"), style = "ai", collection = ANIME, keywords = "anime lanternas ceu noite lago"),
        Wallpaper("hy_dirigivel", "Cidade Steampunk", 299, 0xFFFFB548, style = "ai", collection = ANIME, keywords = "steampunk dirigivel cidade engrenagens"),
        Wallpaper("hy_mascara_raposa", "Máscara de Raposa", 0, 0xFFFF563A, plans = setOf("go"), style = "ai", collection = ANIME, keywords = "anime mascara raposa festival japao"),
        Wallpaper("hy_mundo_cubos", "Ilha de Aventura", 299, 0xFFFFD87B, style = "ai", collection = ANIME, keywords = "game ilha aventura fantasia mapa"),
        Wallpaper("hy_guerreiro_lobo", "Guerreiro e Lobo", 0, 0xFF86B7FF, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "guerreiro lobo neve fantasia"),
        Wallpaper("hy_nave", "Nave Espacial", 299, 0xFF40AEFF, style = "ai", collection = ANIME, keywords = "nave espacial ficcao planeta espaco"),
        Wallpaper("hy_anime_ponto_onibus", "Ponto de Ônibus", 299, 0xFFFFCD5D, style = "ai", collection = ANIME, keywords = "anime ponto onibus chuva verao"),
        Wallpaper("hy_dragao_oriental", "Dragão Oriental", 0, 0xFFFFCA78, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "dragao oriental chines nuvens ouro"),
        Wallpaper("hy_grau", "Grau ao Pôr do Sol", 0, 0xFFFFC05F, plans = setOf("pro"), style = "ai", collection = CARS, keywords = "moto grau empinando rua por do sol"),
        Wallpaper("hy_motocross", "Motocross", 299, 0xFFFF9D44, style = "ai", collection = CARS, keywords = "motocross moto salto terra trilha"),
        Wallpaper("hy_cafe_racer", "Moto Café Racer", 299, 0xFFFF9838, style = "ai", collection = CARS, keywords = "moto classica cafe racer garagem"),
        Wallpaper("hy_moto_chuva", "Moto na Chuva", 299, 0xFFFF5E3A, style = "ai", collection = CARS, keywords = "moto noite chuva neon rua"),
        Wallpaper("hy_superbike", "Superbike na Pista", 99, 0xFFFFC179, style = "ai", collection = CARS, keywords = "moto pista corrida velocidade"),
        Wallpaper("hy_caminhao", "Caminhão na Estrada", 299, 0xFFFF8335, style = "ai", collection = CARS, keywords = "caminhao estrada por do sol carreta"),
        Wallpaper("hy_caminhonete", "Caminhonete na Fazenda", 299, 0xFFFFA43B, style = "ai", collection = CARS, keywords = "agro caminhonete fazenda por do sol"),
        Wallpaper("hy_rally_neve", "Rally na Neve", 299, 0xFFFFD097, style = "ai", collection = CARS, keywords = "rally carro neve corrida"),
        Wallpaper("hy_buggy", "Buggy nas Dunas", 299, 0xFFFFA52B, style = "ai", collection = CARS, keywords = "buggy dunas praia aventura"),
        Wallpaper("hy_arrancada", "Arrancada", 299, 0xFFFF5547, style = "ai", collection = CARS, keywords = "carro arrancada fumaca pneu corrida"),
        Wallpaper("hy_conceito", "Carro do Futuro", 99, 0xFFFFAD5F, style = "ai", collection = CARS, keywords = "carro conceito futurista eletrico"),
        Wallpaper("hy_classico_cuba", "Rua dos Clássicos", 299, 0xFFFFBB6C, style = "ai", collection = CARS, keywords = "carro antigo rua colorida vintage"),
        Wallpaper("hy_trator", "Colheita ao Pôr do Sol", 299, 0xFFFF9726, style = "ai", collection = CARS, keywords = "agro trator colheita campo fazenda"),
        Wallpaper("hy_boiada", "Boiada", 299, 0xFFFF6F26, style = "ai", collection = CARS, keywords = "agro boiada gado estrada fazenda"),
        Wallpaper("hy_cavalo_crioulo", "Peão e Cavalo", 299, 0xFFFF9826, style = "ai", collection = CARS, keywords = "agro cavalo peao fazenda por do sol"),
        Wallpaper("hy_pantera", "Pantera Negra", 0, 0xFF95FF4E, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "pantera negra animal selva olhos"),
        Wallpaper("hy_tigre_branco", "Tigre Branco", 0, 0xFF9EC4FF, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "tigre branco animal neve"),
        Wallpaper("hy_leopardo_neve", "Leopardo-das-neves", 299, 0xFFFFD19E, style = "ai", collection = NATURE, keywords = "leopardo neve montanha animal"),
        Wallpaper("hy_urso_polar", "Urso Polar", 0, 0xFF26FFB3, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "urso polar aurora gelo animal"),
        Wallpaper("hy_elefante", "Elefantes ao Pôr do Sol", 299, 0xFFFF9226, style = "ai", collection = NATURE, keywords = "elefante savana africa por do sol"),
        Wallpaper("hy_girafas", "Girafas", 299, 0xFFFF9726, style = "ai", collection = NATURE, keywords = "girafa savana africa por do sol"),
        Wallpaper("hy_panda", "Panda", 299, 0xFFF9FF9B, style = "ai", collection = NATURE, keywords = "panda bambu fofo animal"),
        Wallpaper("hy_panda_vermelho", "Panda-vermelho", 0, 0xFFFF9926, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "panda vermelho fofo animal arvore"),
        Wallpaper("hy_raposa_neve", "Raposa na Neve", 0, 0xFFFFB76C, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "raposa neve inverno animal"),
        Wallpaper("hy_coruja", "Coruja", 299, 0xFF97CAFF, style = "ai", collection = NATURE, keywords = "coruja noite floresta olhos"),
        Wallpaper("hy_pavao", "Pavão", 299, 0xFFF7FF78, style = "ai", collection = NATURE, keywords = "pavao penas cores ave"),
        Wallpaper("hy_flamingos", "Flamingos", 299, 0xFFFFBA7E, style = "ai", collection = NATURE, keywords = "flamingo lago rosa por do sol aves"),
        Wallpaper("hy_tucano", "Tucano", 299, 0xFFB2FF27, style = "ai", collection = NATURE, keywords = "tucano brasil ave floresta"),
        Wallpaper("hy_golfinhos", "Golfinhos", 299, 0xFFFF9D30, style = "ai", collection = NATURE, keywords = "golfinhos mar por do sol saltando"),
        Wallpaper("hy_tartaruga", "Tartaruga Marinha", 299, 0xFF4CC3FF, style = "ai", collection = NATURE, keywords = "tartaruga marinha recife mar"),
        Wallpaper("hy_tubarao", "Tubarão-branco", 299, 0xFF26A4FF, style = "ai", collection = NATURE, keywords = "tubarao mar fundo oceano"),
        Wallpaper("hy_orca", "Orca", 299, 0xFFFFC27E, style = "ai", collection = NATURE, keywords = "orca baleia mar salto"),
        Wallpaper("hy_recife", "Recife de Coral", 299, 0xFF26F4FF, style = "ai", collection = NATURE, keywords = "coral recife peixes coloridos mar"),
        Wallpaper("hy_cervo", "Cervo na Neblina", 299, 0xFFFFC261, style = "ai", collection = NATURE, keywords = "cervo floresta neblina amanhecer"),
        Wallpaper("hy_urso_rio", "Urso no Rio", 299, 0xFFE0FF75, style = "ai", collection = NATURE, keywords = "urso rio salmao natureza"),
        Wallpaper("hy_cavalos_correndo", "Cavalos Selvagens", 0, 0xFFFF9126, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "cavalos correndo campo poeira"),
        Wallpaper("hy_husky", "Husky na Neve", 299, 0xFF4799FF, style = "ai", collection = NATURE, keywords = "husky cachorro neve olhos azuis"),
        Wallpaper("hy_gato_preto", "Gato Preto", 299, 0xFF26A7FF, style = "ai", collection = NATURE, keywords = "gato preto olhos verdes noite"),
        Wallpaper("hy_coelho", "Coelhinho", 99, 0xFFFFAB7E, style = "ai", collection = NATURE, keywords = "coelho fofo flores primavera"),
        Wallpaper("hy_borboletas", "Jardim de Borboletas", 299, 0xFFFFB24B, style = "ai", collection = NATURE, keywords = "borboletas flores jardim colorido"),
        Wallpaper("hy_tulipas", "Campo de Tulipas", 99, 0xFFFFA63D, style = "ai", collection = NATURE, keywords = "tulipas flores campo holanda"),
        Wallpaper("hy_outono", "Estrada de Outono", 299, 0xFFFF7226, style = "ai", collection = NATURE, keywords = "outono estrada arvores folhas"),
        Wallpaper("hy_pinheiros", "Floresta na Neblina", 299, 0xFFFFC766, style = "ai", collection = NATURE, keywords = "floresta pinheiros neblina raios sol"),
        Wallpaper("hy_cachoeira_musgo", "Cachoeira na Floresta", 299, 0xFFC7FF54, style = "ai", collection = NATURE, keywords = "cachoeira floresta musgo natureza"),
        Wallpaper("hy_cabana_aurora", "Cabana sob a Aurora", 0, 0xFFFFA84C, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "cabana aurora lago neve noite"),
        Wallpaper("hy_onda", "Tubo da Onda", 0, 0xFFFFD475, plans = setOf("plus"), style = "ai", collection = NATURE, keywords = "onda mar surf azul"),
        Wallpaper("hy_mar_nuvens", "Mar de Nuvens", 299, 0xFFFFB663, style = "ai", collection = NATURE, keywords = "montanha nuvens nascer do sol"),
        Wallpaper("hy_galaxia", "Galáxia Espiral", 299, 0xFF61A3FF, style = "ai", collection = NATURE, keywords = "galaxia espaco estrelas universo"),
        Wallpaper("hy_terra_espaco", "Terra Vista do Espaço", 199, 0xFF64B4FF, style = "ai", collection = NATURE, keywords = "terra planeta espaco nascer do sol"),
        Wallpaper("hy_lua_perto", "Lua de Perto", 199, 0xFFCCD6E6, style = "ai", collection = NATURE, keywords = "lua crateras espaco noite"),
        Wallpaper("hy_lotus", "Flor de Lótus", 99, 0xFFFF737A, style = "ai", collection = NATURE, keywords = "lotus flor lago zen"),
        Wallpaper("hy_jesus_ceu", "Jesus nas Nuvens", 0, 0xFFFFBB53, plans = setOf("plus"), style = "ai", collection = POPULAR, keywords = "fe jesus deus ceu luz religiao"),
        Wallpaper("hy_maos_oracao", "Mãos em Oração", 199, 0xFFFFA53F, style = "ai", collection = POPULAR, keywords = "fe oracao maos terco luz"),
        Wallpaper("hy_biblia", "Bíblia e Luz", 199, 0xFFFFA226, style = "ai", collection = POPULAR, keywords = "fe biblia luz deus"),
        Wallpaper("hy_nossa_senhora", "Manto de Estrelas", 0, 0xFF4A8FFF, plans = setOf("plus"), style = "ai", collection = POPULAR, keywords = "fe nossa senhora maria estrelas"),
        Wallpaper("hy_leao_juda", "Leão de Judá", 0, 0xFFFFB547, plans = setOf("plus"), style = "ai", collection = POPULAR, keywords = "fe leao juda coroa luz"),
        Wallpaper("hy_igreja_montanha", "Capela na Montanha", 199, 0xFFFFC64F, style = "ai", collection = POPULAR, keywords = "fe igreja capela montanha"),
        Wallpaper("hy_anjo", "Asas de Anjo", 0, 0xFFFFCE9E, plans = setOf("plus"), style = "ai", collection = POPULAR, keywords = "fe anjo asas luz ceu"),
        Wallpaper("hy_cruz_tempestade", "Luz na Tempestade", 199, 0xFFFFB063, style = "ai", collection = POPULAR, keywords = "fe cruz tempestade luz esperanca"),
        Wallpaper("hy_torcida", "Torcida Verde e Amarela", 199, 0xFFFFE239, style = "ai", collection = POPULAR, keywords = "futebol torcida brasil estadio bandeiras"),
        Wallpaper("hy_chute", "Chute ao Pôr do Sol", 199, 0xFFFF7B26, style = "ai", collection = POPULAR, keywords = "futebol jogador chute por do sol"),
        Wallpaper("hy_futebol_areia", "Futebol na Areia", 199, 0xFFFF9926, style = "ai", collection = POPULAR, keywords = "futebol praia areia por do sol"),
        Wallpaper("hy_varzea", "Futebol de Rua", 199, 0xFFFFAC4F, style = "ai", collection = POPULAR, keywords = "futebol rua comunidade por do sol"),
        Wallpaper("hy_goleiro", "Defesa do Goleiro", 99, 0xFF74FF58, style = "ai", collection = POPULAR, keywords = "futebol goleiro defesa chuva"),
        Wallpaper("hy_chuteira", "Chuteira no Gramado", 99, 0xFFCFFF4D, style = "ai", collection = POPULAR, keywords = "futebol chuteira gramado"),
        Wallpaper("hy_trofeu", "Taça Dourada", 199, 0xFFFFB84C, style = "ai", collection = POPULAR, keywords = "futebol taca trofeu campeao ouro"),
        Wallpaper("hy_estadio_ceu", "Estádio ao Entardecer", 199, 0xFFFFB433, style = "ai", collection = POPULAR, keywords = "futebol estadio por do sol vazio"),
        Wallpaper("hy_violao_fogueira", "Violão na Fogueira", 199, 0xFFFF9226, style = "ai", collection = POPULAR, keywords = "musica violao fogueira acampamento noite"),
        Wallpaper("hy_guitarra_palco", "Guitarra no Palco", 199, 0xFFFF8E73, style = "ai", collection = POPULAR, keywords = "musica guitarra palco show luzes"),
        Wallpaper("hy_dj", "Mesa de DJ", 199, 0xFF8659FF, style = "ai", collection = POPULAR, keywords = "musica dj balada mixer neon"),
        Wallpaper("hy_saxofone", "Jazz Noturno", 199, 0xFFFF9A2B, style = "ai", collection = POPULAR, keywords = "musica saxofone jazz bar noite"),
        Wallpaper("hy_bateria", "Bateria no Palco", 99, 0xFFFFC15D, style = "ai", collection = POPULAR, keywords = "musica bateria palco fumaca luz"),
        Wallpaper("hy_fita_cassete", "Fita Cassete", 99, 0xFFFFEA87, style = "ai", collection = POPULAR, keywords = "musica fita cassete retro anos 80"),
        Wallpaper("hy_violino_rosas", "Violino e Rosas", 199, 0xFFFF6326, style = "ai", collection = POPULAR, keywords = "musica violino rosas classico"),
        Wallpaper("hy_show_estadio", "Show no Estádio", 0, 0xFF9C5DFF, plans = setOf("go"), style = "ai", collection = POPULAR, keywords = "musica show estadio luzes multidao"),
        Wallpaper("hy_fones_vinil", "Fones e Vinil", 199, 0xFFFFB544, style = "ai", collection = POPULAR, keywords = "musica fones vinil discos aconchegante"),
        Wallpaper("hy_piano_lua", "Piano ao Luar", 199, 0xFFFFA142, style = "ai", collection = POPULAR, keywords = "musica piano lua noite janela"),
        Wallpaper("hy_rosa_negra", "Rosa Negra e Ouro", 199, 0xFFFFC25B, style = "ai", collection = POPULAR, keywords = "rosa negra ouro dark luxo"),
        Wallpaper("hy_caveira_rosas", "Caveira e Rosas", 99, 0xFFFF4830, style = "ai", collection = POPULAR, keywords = "caveira rosas dark"),
        Wallpaper("hy_mascara_samurai", "Máscara de Samurai", 199, 0xFFFF4935, style = "ai", collection = POPULAR, keywords = "samurai mascara dark vermelho"),
        Wallpaper("hy_lobo_pb", "Lobo Preto e Branco", 199, 0xFFCCD6E6, style = "ai", collection = POPULAR, keywords = "lobo preto branco olhar dark"),
        Wallpaper("hy_leao_dourado", "Leão de Ouro", 99, 0xFFFFC05F, style = "ai", collection = POPULAR, keywords = "leao ouro escultura luxo dark"),
        Wallpaper("hy_geometria", "Geometria Dourada", 99, 0xFFFFC87F, style = "ai", collection = POPULAR, keywords = "abstrato geometria dourado preto luxo"),
        Wallpaper("hy_fibra", "Fibra de Carbono", 99, 0xFFCCD6E6, style = "ai", collection = POPULAR, keywords = "fibra carbono textura dark"),
        Wallpaper("hy_baleia_ceu", "Baleia no Céu", 0, 0xFFFF9159, plans = setOf("go"), style = "ai", collection = POPULAR, keywords = "surreal baleia ceu nuvens"),
        Wallpaper("hy_escada_nuvens", "Escada para as Nuvens", 199, 0xFFFFB77A, style = "ai", collection = POPULAR, keywords = "surreal escada nuvens ceu"),
        Wallpaper("hy_lua_mar", "Barco sob a Lua", 199, 0xFFFFCB6A, style = "ai", collection = POPULAR, keywords = "surreal lua gigante mar barco"),
        Wallpaper("hy_porta_deserto", "Porta no Deserto", 199, 0xFFFFA551, style = "ai", collection = POPULAR, keywords = "surreal porta deserto mar"),
        Wallpaper("hy_planeta_casa", "Mundo Pequeno", 199, 0xFFFFBC7C, style = "ai", collection = POPULAR, keywords = "surreal planeta pequeno casa arvore"),
        Wallpaper("hy_coracao_rosa_preto", "Coração Rosa e Preto", 99, 0xFFFF6E93, style = "ai", collection = POPULAR, keywords = "coracao rosa preto amor aesthetic"),
        Wallpaper("hy_casal_praia", "Casal na Praia", 199, 0xFFFF8C28, style = "ai", collection = POPULAR, keywords = "amor casal praia por do sol"),
        Wallpaper("hy_maos_coracao", "Coração de Estrelinha", 99, 0xFFFF7326, style = "ai", collection = POPULAR, keywords = "amor coracao estrelinha maos noite"),
        Wallpaper("hy_ursinho", "Ursinho e Rosas", 99, 0xFFFF8248, style = "ai", collection = POPULAR, keywords = "amor ursinho rosas fofo"),
        Wallpaper("hy_gato_xicara", "Gatinho na Xícara", 199, 0xFFFFAF86, style = "ai", collection = POPULAR, keywords = "fofo gato xicara kawaii"),
        Wallpaper("hy_por_do_sol_pastel", "Mar Pastel", 99, 0xFFFFA19E, style = "ai", collection = POPULAR, keywords = "aesthetic mar pastel por do sol"),
        Wallpaper("hy_cromado", "Corações Cromados", 99, 0xFFC79EFF, style = "ai", collection = POPULAR, keywords = "y2k coracao cromado aesthetic"),
        Wallpaper("hy_borboletas_rosa", "Borboletas Rosa", 199, 0xFFFF9EA9, style = "ai", collection = POPULAR, keywords = "aesthetic borboletas rosa"),
        Wallpaper("hy_galaxia_rosa", "Galáxia Rosa", 199, 0xFFFF67AE, style = "ai", collection = POPULAR, keywords = "aesthetic galaxia rosa estrelas"),
        Wallpaper("hy_morango", "Morangos com Leite", 99, 0xFFFF9F9E, style = "ai", collection = POPULAR, keywords = "aesthetic morango leite fofo"),
        Wallpaper("hy_gradiente_aurora", "Gradiente Aurora", 99, 0xFF9967FF, style = "ai", collection = POPULAR, keywords = "gradiente minimalista aurora cores"),
        Wallpaper("hy_gradiente_sol", "Gradiente Pôr do Sol", 99, 0xFFFF4857, style = "ai", collection = POPULAR, keywords = "gradiente minimalista por do sol cores"),
        Wallpaper("hy_gradiente_oceano", "Gradiente Oceano", 99, 0xFF26E0FF, style = "ai", collection = POPULAR, keywords = "gradiente minimalista azul oceano"),
        Wallpaper("hy_gradiente_neon", "Gradiente Neon", 99, 0xFFDE45FF, style = "ai", collection = POPULAR, keywords = "gradiente neon minimalista"),
        Wallpaper("hy_bolhas_sabao", "Bolhas de Sabão", 99, 0xFFFFAE26, style = "ai", collection = POPULAR, keywords = "aesthetic bolhas sabao arco iris"),
        Wallpaper("hy_nuvem_coracao", "Céu de Algodão Doce", 199, 0xFF9CCEFF, style = "ai", collection = POPULAR, keywords = "aesthetic nuvem algodao doce ceu"),
        Wallpaper("hy_cachorro_flores", "Filhote nas Flores", 199, 0xFFFF98A8, style = "ai", collection = POPULAR, keywords = "fofo cachorro filhote flores"),
        Wallpaper("hy_pinguim", "Pinguinzinho", 199, 0xFF79C4FF, style = "ai", collection = POPULAR, keywords = "fofo pinguim neve"),
        Wallpaper("hy_gramado", "Natal em Gramado", 199, 0xFFFF9526, style = "ai", collection = PLACES, keywords = "brasil gramado natal luzes inverno"),
        Wallpaper("hy_paraty", "Paraty", 199, 0xFFFFD99A, style = "ai", collection = PLACES, keywords = "brasil paraty barcos casario mar"),
        Wallpaper("hy_petra", "Petra", 199, 0xFFFF7B5D, style = "ai", collection = PLACES, keywords = "jordania petra templo rocha"),
        Wallpaper("hy_angkor", "Templo na Selva", 199, 0xFFFFC494, style = "ai", collection = PLACES, keywords = "camboja templo selva amanhecer"),
        Wallpaper("hy_floripa", "Ponte de Floripa", 199, 0xFFFFA02A, style = "ai", collection = PLACES, keywords = "brasil florianopolis ponte noite mar"),
        Wallpaper("hy_ipe_amarelo", "Ipê Amarelo", 199, 0xFFFFC626, style = "ai", collection = NATURE, keywords = "brasil ipe amarelo arvore flores"),
        Wallpaper("hy_ipe_roxo", "Ipê Roxo", 199, 0xFFFF5B3C, style = "ai", collection = NATURE, keywords = "brasil ipe roxo arvore flores"),
        Wallpaper("hy_filhotes_leao", "Filhotes de Leão", 199, 0xFFFF9426, style = "ai", collection = NATURE, keywords = "leao filhotes fofo savana"),
        Wallpaper("hy_tempestade_mar", "Raios no Mar", 199, 0xFF7A5DFF, style = "ai", collection = NATURE, keywords = "tempestade raios mar noite"),
        Wallpaper("hy_arco_iris", "Vale do Arco-íris", 199, 0xFFFFEE8E, style = "ai", collection = NATURE, keywords = "arco iris vale montanhas chuva"),
        Wallpaper("hy_surfista", "Surfista na Onda", 199, 0xFFFF8831, style = "ai", collection = POPULAR, keywords = "surf surfista onda mar esporte"),
        Wallpaper("hy_skate", "Skate ao Pôr do Sol", 199, 0xFFFF982E, style = "ai", collection = POPULAR, keywords = "skate skatista rua por do sol esporte"),
        Wallpaper("hy_cafe_chuva", "Café e Chuva", 199, 0xFFFFB558, style = "ai", collection = POPULAR, keywords = "cafe chuva janela aconchegante"),
        Wallpaper("hy_livros_vela", "Leitura à Luz de Vela", 99, 0xFFFFA831, style = "ai", collection = POPULAR, keywords = "livros vela aconchegante noite"),
        Wallpaper("hy_olhos_lobo", "Olhos de Lobo", 199, 0xFF449BFF, style = "ai", collection = POPULAR, keywords = "lobo olhos brilhando noite dark"),
        Wallpaper("hy_aguia_dark", "Águia Sombria", 199, 0xFFFFAC53, style = "ai", collection = POPULAR, keywords = "aguia dark preto olhar"),
        Wallpaper("hy_anime_inverno", "Inverno de Anime", 0, 0xFF6695FF, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "anime neve inverno noite rua"),
        Wallpaper("hy_rio_sakura", "Rio de Cerejeiras", 199, 0xFFFFA58E, style = "ai", collection = ANIME, keywords = "anime sakura rio barco primavera"),
        // ---- Procedurais (antes eram Exclusivos Pro)
        Wallpaper("px_aquarela", "Aquarela", 99, 0xFFFFBF9E, style = "watercolor", collection = POPULAR, keywords = "aquarela arte lago montanhas pintura"),
        Wallpaper("px_baleia_ao_por_do_sol", "Baleia ao Pôr do Sol", 99, 0xFFFFC487, style = "fluke", collection = POPULAR, keywords = "baleia mar natureza oceano por sol"),
        Wallpaper("px_baloes_ao_amanhecer", "Balões ao Amanhecer", 99, 0xFF9EBBFF, style = "balloons", collection = POPULAR, keywords = "amanhecer baloes ceu nevoa vale"),
        Wallpaper("px_bolhas_iridescentes", "Bolhas Iridescentes", 99, 0xFF795AFF, style = "bubbles", collection = POPULAR, keywords = "arco bolhas iridescentes iris sabao"),
        Wallpaper("px_buraco_negro", "Buraco Negro", 99, 0xFFFF8C26, style = "blackhole", collection = POPULAR, keywords = "buraco espaco estrelas galaxia negro universo"),
        Wallpaper("px_cometa", "Cometa", 99, 0xFF466EFF, style = "comet", collection = POPULAR, keywords = "cometa deserto espaco estrelas noite"),
        Wallpaper("px_cordas_de_violao", "Cordas de Violão", 99, 0xFFFFB355, style = "guitar", collection = POPULAR, keywords = "acustico cordas madeira musica violao"),
        Wallpaper("px_dente_de_leao", "Dente-de-leão", 99, 0xFFFFC981, style = "dandelion", collection = POPULAR, keywords = "dente flor leao natureza por sol"),
        Wallpaper("px_disco_de_vinil", "Disco de Vinil", 99, 0xFFFF8E52, style = "vinyl", collection = POPULAR, keywords = "disco musica retro vinil"),
        Wallpaper("px_fitas_de_luz", "Fitas de Luz", 99, 0xFF8F5BFF, style = "ribbons", collection = POPULAR, keywords = "abstrato fitas luz neon"),
        Wallpaper("px_floco_de_neve", "Floco de Neve", 99, 0xFF267CFF, style = "snowflake", collection = POPULAR, keywords = "cristal floco gelo inverno neve"),
        Wallpaper("px_floresta_de_algas", "Floresta de Algas", 99, 0xFFFFE147, style = "kelp", collection = POPULAR, keywords = "algas floresta fundo mar oceano peixes"),
        Wallpaper("px_floresta_encantada", "Floresta Encantada", 99, 0xFF79FFD2, style = "mushrooms", collection = POPULAR, keywords = "cogumelos encantada floresta magica noite vagalumes"),
        Wallpaper("px_fogos_de_artificio", "Fogos de Artifício", 99, 0xFF785FFF, style = "fireworks", collection = POPULAR, keywords = "ano artificio cidade festa fogos noite novo"),
        Wallpaper("px_globo_de_plasma", "Globo de Plasma", 99, 0xFFCF44FF, style = "plasma", collection = POPULAR, keywords = "eletrico energia globo neon plasma raios"),
        Wallpaper("px_globo_espelhado", "Globo Espelhado", 99, 0xFFE36CFF, style = "discoball", collection = POPULAR, keywords = "balada disco espelhado espelho festa globo luzes"),
        Wallpaper("px_gota_de_mercurio", "Gota de Mercúrio", 99, 0xFF9555FF, style = "mercury", collection = POPULAR, keywords = "3d abstrato cromo gota liquido mercurio metal"),
        Wallpaper("px_lanternas_no_lago", "Lanternas no Lago", 99, 0xFFFF704E, style = "lanterns", collection = POPULAR, keywords = "festival lago lanternas luzes noite"),
        Wallpaper("px_mandala_de_luz", "Mandala de Luz", 99, 0xFFAB50FF, style = "mandala", collection = POPULAR, keywords = "geometria luz mandala neon"),
        Wallpaper("px_mar_bioluminescente", "Mar Bioluminescente", 99, 0xFF26BDFF, style = "biolum", collection = POPULAR, keywords = "azul bioluminescente brilho mar noite ondas praia"),
        Wallpaper("px_montanhas_em_nanquim", "Montanhas em Nanquim", 99, 0xFFCCD6E6, style = "inkwash", collection = POPULAR, keywords = "arte montanhas nanquim oriental pintura sol vermelho"),
        Wallpaper("px_papel_recortado", "Papel Recortado", 99, 0xFFB75CFF, style = "papercut", collection = POPULAR, keywords = "arte camadas montanhas papel por recortado sol"),
        Wallpaper("px_pena_de_pavao", "Pena de Pavão", 99, 0xFF61FF6B, style = "peacock", collection = POPULAR, keywords = "azul natureza pavao pena verde"),
        Wallpaper("px_show_ao_vivo", "Show ao Vivo", 99, 0xFFF190FF, style = "concert", collection = POPULAR, keywords = "festa luzes musica palco show vivo"),
        Wallpaper("px_teclas_de_piano", "Teclas de Piano", 99, 0xFFCCD6E6, style = "piano", collection = POPULAR, keywords = "classico musica piano teclado teclas"),
        Wallpaper("px_vitral", "Vitral", 99, 0xFFFF6047, style = "stainedglass", collection = POPULAR, keywords = "colorido igreja luz rosacea vidro vitral"),
        Wallpaper("px_vulcao_em_erupcao", "Vulcão em Erupção", 99, 0xFFFF6E3E, style = "volcano", collection = POPULAR, keywords = "3d erupcao fogo lava montanha natureza noite vulcao"),
        // ---- Lugares Famosos (16)
        Wallpaper("hx_toquio_chuva", "Beco de Tóquio", 99, 0xFFFFD15E, style = "ai", collection = PLACES, keywords = "beco chuva cidade japao lanternas neon noite rua toquio"),
        Wallpaper("hx_nova_york", "Nova York à Noite", 399, 0xFFFF9D2A, style = "ai", collection = PLACES, keywords = "cidade eua luzes manhattan noite nova predios york"),
        Wallpaper("hx_kyoto_torii", "Caminho dos Torii", 0, 0xFFFF6B26, plans = setOf("plus"), style = "ai", collection = PLACES, keywords = "caminho dos japao kyoto templo torii vermelho"),
        Wallpaper("hx_positano", "Costa Amalfitana", 0, 0xFFFFA343, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "amalfitana casas coloridas costa italia mar positano verao"),
        Wallpaper("hx_moraine", "Lago Moraine", 0, 0xFF9ECDFF, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "canada lago montanhas moraine natureza pinheiros turquesa"),
        Wallpaper("hx_lencois", "Lençóis ao Pôr do Sol", 0, 0xFFFFC689, plans = setOf("plus"), style = "ai", collection = PLACES, keywords = "brasil deserto dunas lagoas lencois maranhao por sol"),
        Wallpaper("hx_machu_picchu", "Machu Picchu", 0, 0xFFFFC654, plans = setOf("go"), style = "ai", collection = PLACES, keywords = "amanhecer inca machu montanhas nuvens peru picchu ruinas"),
        Wallpaper("hx_paris_amanhecer", "Paris ao Amanhecer", 0, 0xFFFFAF62, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "amanhecer cidade franca paris telhados torre viagem"),
        Wallpaper("hx_piramides", "Pirâmides sob as Estrelas", 0, 0xFF5A9AFF, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "deserto egito estrelas lactea noite piramides sob via"),
        Wallpaper("hx_veneza", "Veneza", 0, 0xFF26B2FF, plans = setOf("plus"), style = "ai", collection = PLACES, keywords = "agua canal cidade gondola italia noite veneza viagem"),
        Wallpaper("hx_islandia_aurora", "Aurora na Islândia", 0, 0xFF26FFA8, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "aurora boreal cachoeira estrelas islandia montanha noite"),
        Wallpaper("hx_iguacu", "Cataratas do Iguaçu", 0, 0xFFB7FF52, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "arco brasil cachoeira cataratas foz iguacu iris natureza"),
        Wallpaper("hx_maldivas", "Maldivas", 0, 0xFF26F2FF, plans = setOf("plus"), style = "ai", collection = PLACES, keywords = "bangalo ferias ilha maldivas mar paraiso praia turquesa"),
        Wallpaper("hx_fuji_cerejeiras", "Monte Fuji e Cerejeiras", 0, 0xFFFF908E, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "cerejeira cerejeiras flores fuji japao montanha monte pagode sakura"),
        Wallpaper("hx_rio_entardecer", "Rio ao Entardecer", 0, 0xFFFFA24A, plans = setOf("go"), style = "ai", collection = PLACES, keywords = "acucar brasil cidade entardecer janeiro mar pao por rio sol"),
        Wallpaper("hx_santorini", "Santorini", 0, 0xFFFFBE71, plans = setOf("pro"), style = "ai", collection = PLACES, keywords = "brancas casas grecia ilha mar por santorini sol viagem"),
        // ---- Anime & Games (14)
        Wallpaper("hx_trem_anime", "Trem ao Entardecer", 99, 0xFFFF883A, style = "ai", collection = ANIME, keywords = "anime desenho entardecer ferrovia por sol trem verao"),
        Wallpaper("hx_setup_gamer", "Setup Gamer", 399, 0xFF8854FF, style = "ai", collection = ANIME, keywords = "computador gamer jogos neon quarto rgb setup"),
        Wallpaper("hx_vila_pixel", "Vila Pixel", 399, 0xFFFF9026, style = "ai", collection = ANIME, keywords = "aconchegante art game noite pixel retro vagalumes vila"),
        Wallpaper("hx_astronauta_anime", "Garota Astronauta", 0, 0xFF888DFF, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "anime astronauta espaco estrelas garota planeta"),
        Wallpaper("hx_guerreira", "Guerreira de Fogo", 0, 0xFFFF7726, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "espada fantasia fogo game guerreira heroina rpg"),
        Wallpaper("hx_ilhas_flutuantes", "Ilhas Flutuantes", 0, 0xFFFFD088, plans = setOf("plus"), style = "ai", collection = ANIME, keywords = "cachoeira castelo fantasia flutuantes game ilhas"),
        Wallpaper("hx_ninja_lua", "Ninja na Lua Cheia", 0, 0xFFFFCC5E, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "anime cheia japao lua ninja noite telhado"),
        Wallpaper("hx_mecha", "Robô Gigante", 0, 0xFF6981FF, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "anime chuva cidade gigante mecha noite robo"),
        Wallpaper("hx_ceu_anime", "Céu de Anime", 0, 0xFFFFC03C, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "anime ceu desenho nuvens paisagem por sol"),
        Wallpaper("hx_cyberpunk", "Cidade Cyberpunk", 0, 0xFFF390FF, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "carros cidade cyberpunk futuro game neon voadores"),
        Wallpaper("hx_dragao", "Dragão nas Montanhas", 0, 0xFFFFB67E, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "dragao epico fantasia game montanhas nas voando"),
        Wallpaper("hx_quarto_lofi", "Quarto Lo-fi", 0, 0xFFFFC974, plans = setOf("plus"), style = "ai", collection = ANIME, keywords = "anime chuva fones gato lofi musica noite quarto"),
        Wallpaper("hx_raposa_espiritual", "Raposa Espiritual", 0, 0xFF89A4FF, plans = setOf("go"), style = "ai", collection = ANIME, keywords = "anime espirito espiritual floresta kitsune magica raposa"),
        Wallpaper("hx_samurai", "Samurai e Cerejeiras", 0, 0xFFFFA69E, plans = setOf("pro"), style = "ai", collection = ANIME, keywords = "anime cerejeira cerejeiras espada guerreiro japao lua samurai"),
        // ---- Carros & Motos (6)
        Wallpaper("hx_offroad", "Off-road na Lama", 199, 0xFFECFF82, style = "ai", collection = CARS, keywords = "4x4 aventura carro lama off offroad road trilha"),
        Wallpaper("hx_moto_estrada", "Moto na Estrada", 0, 0xFFFF8948, plans = setOf("go"), style = "ai", collection = CARS, keywords = "curva esportiva estrada mar moto por sol velocidade"),
        Wallpaper("hx_muscle_deserto", "Muscle Car no Deserto", 0, 0xFFFF7B26, plans = setOf("go"), style = "ai", collection = CARS, keywords = "car carro classico deserto estrada muscle por sol"),
        Wallpaper("hx_garagem", "Garagem dos Sonhos", 0, 0xFFFFA453, plans = setOf("pro"), style = "ai", collection = CARS, keywords = "carro dos garagem luxo luzes sonhos supercarro"),
        Wallpaper("hx_moto_cyber", "Moto Cyberpunk", 0, 0xFF6062FF, plans = setOf("pro"), style = "ai", collection = CARS, keywords = "cidade cyberpunk futurista moto neon noite"),
        Wallpaper("hx_hipercarro", "Hipercarro Neon", 0, 0xFFFF5626, plans = setOf("pro"), style = "ai", collection = CARS, keywords = "carro esportivo hipercarro neon tunel velocidade"),
        // ---- Natureza & Animais (20)
        Wallpaper("hx_beija_flor", "Beija-flor", 199, 0xFFE9FF3D, style = "ai", collection = NATURE, keywords = "beija colorido flor passaro"),
        Wallpaper("hx_girassois", "Campo de Girassóis", 199, 0xFFFF8F26, style = "ai", collection = NATURE, keywords = "campo flores girassois girassol por sol verao"),
        Wallpaper("hx_filhote", "Filhote de Cachorro", 199, 0xFFFFC753, style = "ai", collection = NATURE, keywords = "animal cachorro filhote flores fofo golden"),
        Wallpaper("hx_gatinho", "Gatinho", 199, 0xFFFF9626, style = "ai", collection = NATURE, keywords = "animal filhote fofo gatinho gato"),
        Wallpaper("hx_amazonia", "Rio Amazonas", 199, 0xFFFFC553, style = "ai", collection = NATURE, keywords = "amanhecer amazonas amazonia brasil floresta neblina rio"),
        Wallpaper("hx_aguia", "Águia", 0, 0xFF269BFF, plans = setOf("plus"), style = "ai", collection = NATURE, keywords = "aguia animal montanhas passaro voo"),
        Wallpaper("hx_borboleta_azul", "Borboleta Azul", 0, 0xFF2C87FF, plans = setOf("plus"), style = "ai", collection = NATURE, keywords = "azul borboleta flores morpho"),
        Wallpaper("hx_carpas", "Carpas Koi", 0, 0xFFFF7426, plans = setOf("plus"), style = "ai", collection = NATURE, keywords = "carpas japao koi lago lotus peixes"),
        Wallpaper("hx_araras", "Araras na Amazônia", 0, 0xFFF3FF92, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "amazonia arara araras brasil floresta passaro vermelho"),
        Wallpaper("hx_cavalo_praia", "Cavalo na Praia", 0, 0xFFFFA037, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "animal branco cavalo mar por praia sol"),
        Wallpaper("hx_cerejeira", "Cerejeira Solitária", 0, 0xFFFF9497, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "arvore cerejeira estrelas flores noite sakura solitaria"),
        Wallpaper("hx_jubarte", "Jubarte no Azul", 0, 0xFF26A8FF, plans = setOf("plus"), style = "ai", collection = NATURE, keywords = "azul baleia fundo jubarte mar oceano raios sol"),
        Wallpaper("hx_lua_gigante", "Lua Gigante", 0, 0xFFFFB555, plans = setOf("plus"), style = "ai", collection = NATURE, keywords = "cheia gigante lua montanha noite"),
        Wallpaper("hx_saturno", "Saturno", 0, 0xFFFFD290, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "aneis espaco planeta saturno"),
        Wallpaper("hx_tigre", "Tigre na Selva", 0, 0xFFFFD070, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "animal floresta olhar selva tigre"),
        Wallpaper("hx_astronauta", "Astronauta e a Terra", 0, 0xFFFF873E, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "astronauta espaco nascer planeta sol terra"),
        Wallpaper("hx_leao", "Leão ao Pôr do Sol", 0, 0xFFFF9A26, plans = setOf("pro"), style = "ai", collection = NATURE, keywords = "africa animal leao por rei savana sol"),
        Wallpaper("hx_lobo_lua", "Lobo e a Lua", 0, 0xFF4D9EFF, plans = setOf("plus"), style = "ai", collection = NATURE, keywords = "animal cheia lobo lua neve noite uivando"),
        Wallpaper("hx_onca", "Onça-Pintada", 0, 0xFFFFBD66, plans = setOf("plus"), style = "ai", collection = NATURE, keywords = "animal brasil jaguar onca pantanal pintada rio"),
        // ---- Populares (14)
        Wallpaper("hx_nuvens_rosa", "Nuvens de Algodão", 0, 0xFFFF92A5, style = "ai", collection = POPULAR, keywords = "aesthetic algodao ceu nuvens pastel rosa"),
        Wallpaper("hx_chuva_janela", "Chuva na Janela", 199, 0xFFFFA248, style = "ai", collection = POPULAR, keywords = "bokeh chuva cidade gotas janela noite"),
        Wallpaper("hx_pomba_paz", "Pomba da Paz", 199, 0xFFFFB954, style = "ai", collection = POPULAR, keywords = "espirito luz paz pomba religiao"),
        Wallpaper("hx_vidro_colorido", "Vidro Colorido", 99, 0xFFFFE23B, style = "ai", collection = POPULAR, keywords = "abstrato colorido formas gradiente moderno vidro"),
        Wallpaper("hx_bola_chuva", "Bola na Chuva", 199, 0xFFE0FF5D, style = "ai", collection = POPULAR, keywords = "bola chuva futebol gramado refletor"),
        Wallpaper("hx_cruz_amanhecer", "Cruz ao Amanhecer", 199, 0xFFFF9D31, style = "ai", collection = POPULAR, keywords = "amanhecer cruz deus luz religiao"),
        Wallpaper("hx_festival", "Festival na Praia", 199, 0xFFFF7E37, style = "ai", collection = POPULAR, keywords = "festival multidao musica por praia show sol"),
        Wallpaper("hx_rede_praia", "Rede na Praia", 99, 0xFFFFCF89, style = "ai", collection = POPULAR, keywords = "coqueiro ferias mar nordeste praia rede turquesa"),
        Wallpaper("hx_microfone", "Microfone no Palco", 0, 0xFFFFAF5A, plans = setOf("plus"), style = "ai", collection = POPULAR, keywords = "fumaca luzes microfone musica palco show"),
        Wallpaper("hx_estadio", "Estádio à Noite", 0, 0xFFD4FF26, plans = setOf("pro"), style = "ai", collection = POPULAR, keywords = "bola estadio futebol noite refletores"),
        Wallpaper("hx_marmore_ouro", "Mármore Negro e Ouro", 0, 0xFFFFC36F, plans = setOf("go"), style = "ai", collection = POPULAR, keywords = "dourado elegante luxo marmore negro ouro preto"),
        Wallpaper("hx_ondas_3d", "Ondas 3D", 0, 0xFFA578FF, plans = setOf("pro"), style = "ai", collection = POPULAR, keywords = "abstrato fluido iridescente ondas"),
        Wallpaper("hx_ouro_liquido", "Ouro Líquido", 0, 0xFFFFBB56, plans = setOf("plus"), style = "ai", collection = POPULAR, keywords = "abstrato dourado liquido luxo ouro"),
        Wallpaper("hx_vinil_cafe", "Vinil e Café", 0, 0xFFFFA044, plans = setOf("pro"), style = "ai", collection = POPULAR, keywords = "aconchegante cafe disco discos musica toca vinil"),
        // ---- Natureza 3D (4)
        Wallpaper("nt_alpes_ao_por_do_sol", "Alpes ao Pôr do Sol", 99, 0xFF8B65FF, style = "alpine", collection = NATURE3D, keywords = "3d alpes montanha natureza paisagem por sol"),
        Wallpaper("nt_aurora_artica", "Aurora Ártica", 99, 0xFF36FFE0, style = "render3d", collection = NATURE3D, keywords = "3d montanhas natureza noite"),
        Wallpaper("nt_fiorde_dourado", "Fiorde Dourado", 99, 0xFF969DFF, style = "alpine", collection = NATURE3D, keywords = "3d dourado fiorde montanha natureza paisagem"),
        Wallpaper("nt_via_lactea_montanhas", "Via Láctea nas Montanhas", 99, 0xFF5A78FF, style = "alpine", collection = NATURE3D, keywords = "3d lactea montanha montanhas nas natureza paisagem via"),
        // ---- Coleção clássica (3)
        Wallpaper("nevoa_verde", "Névoa Verde", 0, 0xFF9EFFDD, style = "mountains", keywords = "montanhas natureza"),
        Wallpaper("noite_estrelada", "Noite Estrelada", 0, 0xFF3B6BFF, style = "mountains", keywords = "montanhas natureza noite"),
        Wallpaper("cl_planeta_violeta", "Planeta Violeta", 99, 0xFF9E52FF, style = "planet", keywords = "espaco planeta universo violeta"),
        // ---- Heróis & Vilões (1)
        Wallpaper("hv_furia_tempestade", "Fúria da Tempestade", 99, 0xFF6CB7FF, style = "storm", collection = HEROES, keywords = "herois raio tempestade"),
        // ---- Premium 3D (6)
        Wallpaper("pm_seda_rubi", "Seda Rubi", 99, 0xFFFF456B, style = "liquidwave", collection = PREMIUM, keywords = "3d abstrato premium rubi seda"),
        Wallpaper("pm_aguas_vivas", "Águas-vivas", 99, 0xFF2680FF, style = "jellyfish", collection = PREMIUM, keywords = "agua aguas mar neon oceano viva vivas"),
        Wallpaper("pm_colmeia_neon", "Colmeia Neon", 99, 0xFFEF42FF, style = "hex3d", collection = PREMIUM, keywords = "3d abstrato colmeia neon premium"),
        Wallpaper("pm_mar_ao_luar", "Mar ao Luar", 99, 0xFF7E9CFF, style = "sunsea", collection = PREMIUM, keywords = "3d abstrato luar mar premium"),
        Wallpaper("pm_orbe_rubi", "Orbe Rubi", 99, 0xFFFF4C54, style = "glass", collection = PREMIUM, keywords = "3d abstrato orbe premium rubi"),
        Wallpaper("pm_sol_de_fogo", "Sol de Fogo", 99, 0xFFFF5626, style = "sunsea", collection = PREMIUM, keywords = "3d do mar natureza por premium sol"),
        // ---- Inspirações (9)
        Wallpaper("rf_coracao_apaixonado", "Coração Apaixonado", 99, 0xFFFF4565, style = "love_heart2", collection = INSPIRATIONS, keywords = "apaixonado coracao emoji fofo rosto"),
        Wallpaper("rf_coracao_partido_de_vidro", "Coração Partido de Vidro", 99, 0xFF3B72FF, style = "diamond_heart", collection = INSPIRATIONS, keywords = "coracao emoji fofo partido rosto vidro"),
        Wallpaper("rf_gelo_esmeralda", "Gelo Esmeralda", 99, 0xFF4AFFD9, style = "icysea", collection = INSPIRATIONS, keywords = "3d esmeralda gelo montanha natureza paisagem"),
        Wallpaper("rf_lago_lilas", "Lago Lilás", 99, 0xFFAF53FF, style = "lake", collection = INSPIRATIONS, keywords = "3d lago lilas montanha natureza paisagem"),
        Wallpaper("rf_olhos_apaixonados", "Olhos Apaixonados", 99, 0xFFFF3E7C, style = "eyes6", collection = INSPIRATIONS, keywords = "apaixonados emoji fofo olhos rosto"),
        Wallpaper("rf_terra_noturna", "Terra Noturna", 99, 0xFF4DAEFF, style = "earth", collection = INSPIRATIONS, keywords = "espaco noite planeta terra"),
        Wallpaper("rf_folhas_outono", "Folhas de Outono", 99, 0xFFFF9026, style = "leaves", collection = INSPIRATIONS, keywords = "flores folhas natureza outono"),
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
