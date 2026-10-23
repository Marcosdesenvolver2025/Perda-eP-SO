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
) {
    val accent: Color get() = Color(accentArgb)

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
 * Catálogo dos temas (300): clássica, Heróis & Vilões, Mundos de Jogo, Premium 3D e Inspirações.
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

    val all: List<Wallpaper> = listOf(
        // ---- Coleção clássica (38)
        Wallpaper("pro_aurora_suprema", "Aurora Suprema", 0, 0xFF5CFFC4, plans = setOf("pro"), style = "aurora"),
        Wallpaper("pro_cume_aurora", "Cume da Aurora", 0, 0xFF5CFFC4, plans = setOf("pro"), style = "mountains"),
        Wallpaper("noite_estrelada", "Noite Estrelada", 0, 0xFF7AA7FF, style = "mountains"),
        Wallpaper("nevoa_verde", "Névoa Verde", 0, 0xFF3DDC84, style = "mountains"),
        Wallpaper("pro_planeta_rubi", "Planeta Rubi", 0, 0xFFFF4D6D, plans = setOf("pro"), style = "planet"),
        Wallpaper("cristal_ametista", "Cristal Ametista", 99, 0xFFA56BFF, style = "lowpoly"),
        Wallpaper("dunas_lunares", "Dunas Lunares", 99, 0xFF9DB4FF, style = "dunes"),
        Wallpaper("dunas_ambar", "Dunas Âmbar", 99, 0xFFFFA94D, style = "dunes"),
        Wallpaper("dourado_entardecer", "Entardecer Dourado", 99, 0xFFFFB547, style = "mountains"),
        Wallpaper("floresta_neblina", "Floresta na Neblina", 99, 0xFF59C08A, style = "forest"),
        Wallpaper("luzes_cidade", "Luzes da Cidade", 99, 0xFFFFC24D, style = "bokeh"),
        Wallpaper("seda_grafite", "Seda Grafite", 99, 0xFFC9CED6, style = "silk"),
        Wallpaper("serra_rosa", "Serra Rosa", 99, 0xFFFF8FB1, style = "mountains"),
        Wallpaper("lua_cheia", "Lua Cheia", 99, 0xFFC7D3FF, style = "moon"),
        Wallpaper("mar_dourado", "Mar Dourado", 99, 0xFFFFB347, style = "ocean"),
        Wallpaper("mar_turquesa", "Mar Turquesa", 99, 0xFF2ED8C3, style = "ocean"),
        Wallpaper("montanhas_lilas", "Montanhas Lilás", 99, 0xFFC08BFF, style = "mountains"),
        Wallpaper("seda_dourada", "Seda Dourada", 99, 0xFFFFC857, style = "silk"),
        Wallpaper("aurora_boreal", "Aurora Boreal", 99, 0xFF2CF0A0, style = "aurora"),
        Wallpaper("nebulosa_azul", "Nebulosa Azul", 99, 0xFF3FA9FF, style = "nebula"),
        Wallpaper("nebulosa_rosa", "Nebulosa Rosa", 99, 0xFFFF5FC8, style = "nebula"),
        Wallpaper("pro_seda_obsidiana", "Seda Obsidiana", 99, 0xFF7FA0FF, style = "silk"),
        Wallpaper("synthwave", "Synthwave", 99, 0xFFFF3FD0, style = "synthwave"),
        Wallpaper("aurora_lago", "Aurora no Lago", 149, 0xFF46F2B4, style = "aurora"),
        Wallpaper("pro_eclipse", "Eclipse", 149, 0xFFFF9A3D, style = "planet"),
        Wallpaper("galaxia_violeta", "Galáxia Violeta", 149, 0xFFB56BFF, style = "spiral"),
        Wallpaper("lua_mar", "Lua sobre o Mar", 149, 0xFFAFC6FF, style = "moon"),
        Wallpaper("pro_synthwave_noir", "Synthwave Noir", 149, 0xFFFF3F6E, style = "synthwave"),
        Wallpaper("aurora_rosa", "Aurora Rosa", 199, 0xFFFF6BD6, style = "aurora"),
        Wallpaper("pro_cidade_dourada", "Cidade Dourada", 199, 0xFFFFC24D, style = "city"),
        Wallpaper("metropole", "Metrópole 2099", 199, 0xFF2FF3FF, style = "city"),
        Wallpaper("nebulosa_fenix", "Nebulosa Fênix", 199, 0xFFFF7A3D, style = "nebula"),
        Wallpaper("planeta_gelo", "Planeta de Gelo", 199, 0xFF7FE3FF, style = "planet"),
        Wallpaper("saturno_dourado", "Saturno Dourado", 199, 0xFFFFD27A, style = "planet"),
        Wallpaper("via_lactea", "Via Láctea", 199, 0xFFE7C6FF, style = "milkyway"),
        Wallpaper("pro_nebulosa_esmeralda", "Nebulosa Esmeralda", 199, 0xFF2CF08A, style = "nebula"),
        Wallpaper("portal_cosmico", "Portal Cósmico", 199, 0xFF6BE4FF, style = "spiral"),
        Wallpaper("supernova", "Supernova", 199, 0xFFFFB05A, style = "supernova"),
        // ---- Heróis & Vilões (23)
        Wallpaper("hv_fumaca_sombria", "Fumaça Sombria", 99, 0xFFA56BFF, style = "toxic", collection = HEROES),
        Wallpaper("hv_fogo_azul", "Fogo Azul", 99, 0xFF4DB8FF, style = "inferno", collection = HEROES),
        Wallpaper("hv_impacto_azul", "Impacto Azul", 99, 0xFF3FB8FF, style = "comic", collection = HEROES),
        Wallpaper("hv_nevoa_toxica", "Névoa Tóxica", 99, 0xFF7CFF3F, style = "toxic", collection = HEROES),
        Wallpaper("hv_rastro_azul", "Rastro Azul", 99, 0xFF4DC8FF, style = "speed", collection = HEROES),
        Wallpaper("hv_circuito_tatico", "Circuito Tático", 149, 0xFF3FE0FF, style = "circuit", collection = HEROES),
        Wallpaper("hv_escudo_dourado", "Escudo Dourado", 149, 0xFFFFC857, style = "hexshield", collection = HEROES),
        Wallpaper("hv_explosao_pop", "Explosão Pop", 149, 0xFFFFD23F, style = "comic", collection = HEROES),
        Wallpaper("hv_velocidade_rubra", "Velocidade Rubra", 149, 0xFFFF4D3D, style = "speed", collection = HEROES),
        Wallpaper("hv_cidade_alerta", "Cidade em Alerta", 199, 0xFFFF4D3D, style = "dawncity", collection = HEROES),
        Wallpaper("hv_eclipse_rubro", "Eclipse Rubro", 199, 0xFFFF3B3B, style = "planet", collection = HEROES),
        Wallpaper("hv_impacto_sismico", "Impacto Sísmico", 199, 0xFFFF7A2A, style = "shatter", collection = HEROES),
        Wallpaper("hv_inferno_rubro", "Inferno Rubro", 199, 0xFFFF6A2A, style = "inferno", collection = HEROES),
        Wallpaper("hv_nucleo_plasma", "Núcleo de Plasma", 199, 0xFF6BE4FF, style = "plasma", collection = HEROES),
        Wallpaper("hv_orbe_sombrio", "Orbe Sombrio", 199, 0xFFC77DFF, style = "plasma", collection = HEROES),
        Wallpaper("hv_portal_rubro", "Portal Rubro", 199, 0xFFFF4D5E, style = "rift", collection = HEROES),
        Wallpaper("hv_tempestade_eletrica", "Tempestade Elétrica", 199, 0xFF6FB6FF, style = "storm", collection = HEROES),
        Wallpaper("hv_vortice_caos", "Vórtice do Caos", 199, 0xFF5CFF8A, style = "vortex", collection = HEROES),
        Wallpaper("hv_amanhecer_heroi", "Amanhecer do Herói", 199, 0xFFFFB347, style = "dawncity", collection = HEROES),
        Wallpaper("hv_escudo_energia", "Escudo de Energia", 199, 0xFF3FE0FF, style = "hexshield", collection = HEROES),
        Wallpaper("hv_fenda_dimensional", "Fenda Dimensional", 199, 0xFFC77DFF, style = "rift", collection = HEROES),
        Wallpaper("hv_furia_tempestade", "Fúria da Tempestade", 199, 0xFF7FD4FF, style = "storm", collection = HEROES),
        Wallpaper("hv_trovoada_violeta", "Trovoada Violeta", 199, 0xFFB57BFF, style = "storm", collection = HEROES),
        // ---- Mundos de Jogo (24)
        Wallpaper("jg_bosque_nevoa", "Bosque da Névoa", 0, 0xFFC8E8B0, plans = setOf("pro"), style = "hunt", collection = GAMES),
        Wallpaper("jg_cervo_real", "Cervo Real", 0, 0xFFFFB050, plans = setOf("pro"), style = "hunt", collection = GAMES),
        Wallpaper("jg_coroa_celestial", "Coroa Celestial", 0, 0xFFFFF0A0, plans = setOf("pro"), style = "fantasy", collection = GAMES),
        Wallpaper("jg_fortaleza_lunar", "Fortaleza Lunar", 0, 0xFFE0E8FF, plans = setOf("pro"), style = "fantasy", collection = GAMES),
        Wallpaper("jg_uivo_noite", "Uivo da Noite", 0, 0xFF8AB0FF, plans = setOf("pro"), style = "hunt", collection = GAMES),
        Wallpaper("jg_matriz_verde", "Matriz Verde", 199, 0xFF3AFF6A, style = "cyber", collection = GAMES),
        Wallpaper("jg_cupula_holografica", "Cúpula Holográfica", 199, 0xFF7AFFE8, style = "cyber", collection = GAMES),
        Wallpaper("jg_estrada_violeta", "Estrada Violeta", 199, 0xFFB07AFF, style = "racing", collection = GAMES),
        Wallpaper("jg_pista_gelada", "Pista Gelada", 199, 0xFFC8F0FF, style = "racing", collection = GAMES),
        Wallpaper("jg_turbo_ciano", "Turbo Ciano", 199, 0xFF3FFFF0, style = "racing", collection = GAMES),
        Wallpaper("jg_distrito_holograma", "Distrito Holograma", 299, 0xFF2FE8FF, style = "cyber", collection = GAMES),
        Wallpaper("jg_floresta_boreal", "Floresta Boreal", 299, 0xFF9FD0FF, style = "hunt", collection = GAMES),
        Wallpaper("jg_lua_sangue", "Lua de Sangue", 299, 0xFFFF3A3A, style = "fantasy", collection = GAMES),
        Wallpaper("jg_por_do_sol_cibernetico", "Pôr do Sol Cibernético", 299, 0xFFFF9A3A, style = "cyber", collection = GAMES),
        Wallpaper("jg_reino_rosa", "Reino Rosa", 299, 0xFFFFA0C8, style = "fantasy", collection = GAMES),
        Wallpaper("jg_retao_dourado", "Retão Dourado", 299, 0xFFFFD23F, style = "racing", collection = GAMES),
        Wallpaper("jg_asfalto_neon", "Asfalto Neon", 399, 0xFFFF4FD8, style = "racing", collection = GAMES),
        Wallpaper("jg_bastiao_dourado", "Bastião Dourado", 399, 0xFFFFD050, style = "fantasy", collection = GAMES),
        Wallpaper("jg_lua_cacadora", "Lua Caçadora", 399, 0xFFA8C8FF, style = "hunt", collection = GAMES),
        Wallpaper("jg_rodovia_por_do_sol", "Rodovia ao Pôr do Sol", 399, 0xFFFFA04A, style = "racing", collection = GAMES),
        Wallpaper("jg_torres_douradas", "Torres Douradas", 399, 0xFFFFD23F, style = "cyber", collection = GAMES),
        Wallpaper("jg_amanhecer_mata", "Amanhecer na Mata", 499, 0xFFFFC07A, style = "hunt", collection = GAMES),
        Wallpaper("jg_castelo_estrelas", "Castelo das Estrelas", 499, 0xFFFFE08A, style = "fantasy", collection = GAMES),
        Wallpaper("jg_metropole_neon", "Metrópole Neon", 499, 0xFFFF4FD8, style = "cyber", collection = GAMES),
        // ---- Premium 3D (80)
        Wallpaper("pm_colmeia_rubra", "Colmeia Rubra", 0, 0xFFFF2A5A, plans = setOf("plus", "pro"), style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_colmeia_violeta", "Colmeia Violeta", 0, 0xFFA04AFF, plans = setOf("pro"), style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_coracao_chuva", "Coração na Chuva", 0, 0xFFFF2A4A, plans = setOf("pro"), style = "drops", collection = PREMIUM),
        Wallpaper("pm_crepusculo_violeta", "Crepúsculo Violeta", 0, 0xFFC04A8A, plans = setOf("pro"), style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_cubo_azul", "Cubo Azul", 0, 0xFF1AA0FF, plans = setOf("pro"), style = "cubes", collection = PREMIUM),
        Wallpaper("pm_esfera_cristal", "Esfera de Cristal", 0, 0xFFA8C0FF, plans = setOf("plus", "pro"), style = "glass", collection = PREMIUM),
        Wallpaper("pm_facetas_ouro", "Facetas de Ouro", 0, 0xFFFFB02A, plans = setOf("pro"), style = "facets", collection = PREMIUM),
        Wallpaper("pm_fumaca_neon", "Fumaça Neon", 0, 0xFFFF3A8A, plans = setOf("pro"), style = "silksmoke", collection = PREMIUM),
        Wallpaper("pm_fumaca_oceano", "Fumaça Oceano", 0, 0xFF2AD0FF, plans = setOf("pro"), style = "silksmoke", collection = PREMIUM),
        Wallpaper("pm_hora_azul", "Hora Azul", 0, 0xFF5A8AFF, plans = setOf("pro"), style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_linhas_ouro", "Linhas de Ouro", 0, 0xFFFFB050, plans = setOf("pro"), style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_mar_carmesim", "Mar Carmesim", 0, 0xFFFF3A4A, plans = setOf("plus", "pro"), style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_nucleo_lava", "Núcleo de Lava", 0, 0xFFFF6A1A, plans = setOf("pro"), style = "shards3d", collection = PREMIUM),
        Wallpaper("pm_orbe_noturno", "Orbe Noturno", 0, 0xFF5A7AFF, plans = setOf("pro"), style = "glass", collection = PREMIUM),
        Wallpaper("pm_seda_azul", "Seda Azul", 0, 0xFF3A7AFF, plans = setOf("pro"), style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_sorriso_chuva", "Sorriso na Chuva", 0, 0xFFFFB81A, plans = setOf("go", "pro"), style = "drops", collection = PREMIUM),
        Wallpaper("pm_trio_orbes", "Trio de Orbes", 0, 0xFF7AFFE8, plans = setOf("pro"), style = "glass", collection = PREMIUM),
        Wallpaper("pm_aneis_cobre", "Anéis de Cobre", 99, 0xFFFF8A4A, style = "rings", collection = PREMIUM),
        Wallpaper("pm_aneis_rose", "Anéis Rosé", 99, 0xFFFF9AB0, style = "rings", collection = PREMIUM),
        Wallpaper("pm_aneis_violeta", "Anéis Violeta", 99, 0xFFA04AFF, style = "rings", collection = PREMIUM),
        Wallpaper("pm_cubo_ciano", "Cubo Ciano", 99, 0xFF2AFFE8, style = "cubes", collection = PREMIUM),
        Wallpaper("pm_gotas_douradas", "Gotas Douradas", 99, 0xFFFFC24A, style = "drops", collection = PREMIUM),
        Wallpaper("pm_aneis_azuis", "Anéis Azuis", 99, 0xFF3A8AFF, style = "rings", collection = PREMIUM),
        Wallpaper("pm_cubo_branco", "Cubo Branco", 99, 0xFFE8F0FF, style = "cubes", collection = PREMIUM),
        Wallpaper("pm_facetas_cobre", "Facetas de Cobre", 99, 0xFFFF7A3A, style = "facets", collection = PREMIUM),
        Wallpaper("pm_gotas_rubras", "Gotas Rubras", 99, 0xFFFF3A3A, style = "drops", collection = PREMIUM),
        Wallpaper("pm_ondas_rubras", "Ondas Rubras", 99, 0xFFFF2A3A, style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_seda_aurora", "Seda Aurora", 99, 0xFF2AFFD0, style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_aneis_prata", "Anéis de Prata", 99, 0xFFDCE4EE, style = "rings", collection = PREMIUM),
        Wallpaper("pm_colmeia_glacial", "Colmeia Glacial", 99, 0xFFCFEFFF, style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_cubo_laranja", "Cubo Laranja", 99, 0xFFFF7A1A, style = "cubes", collection = PREMIUM),
        Wallpaper("pm_facetas_esmeralda", "Facetas Esmeralda", 99, 0xFF2AFF8A, style = "facets", collection = PREMIUM),
        Wallpaper("pm_fissura_toxica", "Fissura Tóxica", 99, 0xFF7AFF2A, style = "shards3d", collection = PREMIUM),
        Wallpaper("pm_fumaca_esmeralda", "Fumaça Esmeralda", 99, 0xFF2AFF7A, style = "silksmoke", collection = PREMIUM),
        Wallpaper("pm_gotas_azuis", "Gotas Azuis", 99, 0xFF3A8AFF, style = "drops", collection = PREMIUM),
        Wallpaper("pm_ondas_esmeralda", "Ondas Esmeralda", 99, 0xFF2AFF8A, style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_colmeia_esmeralda", "Colmeia Esmeralda", 149, 0xFF2AFF7A, style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_cubo_esmeralda", "Cubo Esmeralda", 149, 0xFF2AFF7A, style = "cubes", collection = PREMIUM),
        Wallpaper("pm_entardecer_laranja", "Entardecer Laranja", 149, 0xFFFF7A2A, style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_fissura_carmesim", "Fissura Carmesim", 149, 0xFFFF2A3A, style = "shards3d", collection = PREMIUM),
        Wallpaper("pm_fumaca_dourada", "Fumaça Dourada", 149, 0xFFFFD07A, style = "silksmoke", collection = PREMIUM),
        Wallpaper("pm_mar_de_vinho", "Mar de Vinho", 149, 0xFFC02A5A, style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_moeda_dourada", "Moeda na Chuva", 149, 0xFFFFC23A, style = "drops", collection = PREMIUM),
        Wallpaper("pm_ondas_prata", "Ondas de Prata", 149, 0xFFDCE4EE, style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_aneis_ouro", "Anéis de Ouro", 199, 0xFFFFC24A, style = "rings", collection = PREMIUM),
        Wallpaper("pm_colmeia_solar", "Colmeia Solar", 199, 0xFFFF5A1A, style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_cubo_rubro", "Cubo Rubro", 199, 0xFFFF2A3A, style = "cubes", collection = PREMIUM),
        Wallpaper("pm_facetas_prata", "Facetas de Prata", 199, 0xFFDCE4EE, style = "facets", collection = PREMIUM),
        Wallpaper("pm_fumaca_aurora", "Fumaça Aurora", 199, 0xFF2AFFB0, style = "silksmoke", collection = PREMIUM),
        Wallpaper("pm_fumaca_violeta", "Fumaça Violeta", 199, 0xFFA04AFF, style = "silksmoke", collection = PREMIUM),
        Wallpaper("pm_mare_turquesa", "Maré Turquesa", 199, 0xFF2AD0C8, style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_orbe_glacial", "Orbe Glacial", 199, 0xFFC8F0FF, style = "glass", collection = PREMIUM),
        Wallpaper("pm_ouro_rose", "Ouro Rosé", 199, 0xFFFF9A8A, style = "facets", collection = PREMIUM),
        Wallpaper("pm_colmeia_noturna", "Colmeia Noturna", 199, 0xFF3A7AFF, style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_cubo_violeta", "Cubo Violeta", 199, 0xFFA04AFF, style = "cubes", collection = PREMIUM),
        Wallpaper("pm_ceu_rosa", "Céu Rosa", 199, 0xFFFF8AB0, style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_fumaca_fogo", "Fumaça de Fogo", 199, 0xFFFF6A1A, style = "silksmoke", collection = PREMIUM),
        Wallpaper("pm_ondas_cosmicas", "Ondas Cósmicas", 199, 0xFF7A5AFF, style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_orbe_rosa", "Orbe Rosa", 199, 0xFFFF6AB0, style = "glass", collection = PREMIUM),
        Wallpaper("pm_orbe_solar", "Orbe Solar", 199, 0xFFFF8A2A, style = "glass", collection = PREMIUM),
        Wallpaper("pm_por_do_sol_dourado", "Pôr do Sol Dourado", 199, 0xFFFFB03A, style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_sorriso_limao", "Sorriso Limão", 199, 0xFFA8FF2A, style = "drops", collection = PREMIUM),
        Wallpaper("pm_colmeia_arco_iris", "Colmeia Arco-íris", 249, 0xFF2AFFB0, style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_facetas_ametista", "Facetas Ametista", 249, 0xFFB05AFF, style = "facets", collection = PREMIUM),
        Wallpaper("pm_facetas_rubi", "Facetas Rubi", 249, 0xFFFF2A4A, style = "facets", collection = PREMIUM),
        Wallpaper("pm_fissura_azul", "Fissura Azul", 249, 0xFF2A8AFF, style = "shards3d", collection = PREMIUM),
        Wallpaper("pm_fissura_dourada", "Fissura Dourada", 249, 0xFFFFC24A, style = "shards3d", collection = PREMIUM),
        Wallpaper("pm_ondas_rose", "Ondas Rosé", 249, 0xFFFF7A9A, style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_sorriso_rosa", "Sorriso Rosa", 249, 0xFFFF5A9A, style = "drops", collection = PREMIUM),
        Wallpaper("pm_colmeia_ciano", "Colmeia Ciano", 299, 0xFF1AE8FF, style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_cubo_dourado", "Cubo Dourado", 299, 0xFFFFB02A, style = "cubes", collection = PREMIUM),
        Wallpaper("pm_orbe_esmeralda", "Orbe Esmeralda", 299, 0xFF2AFF8A, style = "glass", collection = PREMIUM),
        Wallpaper("pm_prisma_molhado", "Prisma Molhado", 299, 0xFFFFE82A, style = "drops", collection = PREMIUM),
        Wallpaper("pm_seda_iridescente", "Seda Iridescente", 299, 0xFFFF6AD5, style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_ultimo_raio", "Último Raio", 299, 0xFFFFD07A, style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_facetas_neon", "Facetas Neon", 399, 0xFF2AFFE8, style = "facets", collection = PREMIUM),
        Wallpaper("pm_fissura_violeta", "Fissura Violeta", 399, 0xFFB05AFF, style = "shards3d", collection = PREMIUM),
        Wallpaper("pm_sol_de_fogo", "Sol de Fogo", 399, 0xFFFF4A1A, style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_colmeia_dourada", "Colmeia Dourada", 499, 0xFFFFB02A, style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_orbe_ambar", "Orbe Âmbar", 499, 0xFFFFB03A, style = "glass", collection = PREMIUM),
        // ---- Inspirações (135)
        Wallpaper("rf_baia_esmeralda", "Baía Esmeralda", 0, 0xFF2AB0A0, plans = setOf("pro"), style = "karst", collection = INSPIRATIONS),
        Wallpaper("rf_bordo_molhado", "Bordô Molhado", 0, 0xFFFF5A3A, plans = setOf("plus", "pro"), style = "leaves", collection = INSPIRATIONS),
        Wallpaper("rf_chuva_na_cidade", "Chuva na Cidade", 0, 0xFFFFB03A, plans = setOf("plus", "pro"), style = "rainwindow", collection = INSPIRATIONS),
        Wallpaper("rf_cubos_neon", "Cubos Neon", 0, 0xFFFF6AB0, plans = setOf("pro"), style = "cubefield", collection = INSPIRATIONS),
        Wallpaper("rf_eclipse_lilas", "Eclipse Lilás", 0, 0xFFA8A0FF, plans = setOf("pro"), style = "circles", collection = INSPIRATIONS),
        Wallpaper("rf_encapuzado_rubro", "Encapuzado Rubro", 0, 0xFFFF2A3A, plans = setOf("go", "pro"), style = "hood", collection = INSPIRATIONS),
        Wallpaper("rf_esferas_jade", "Esferas de Jade", 0, 0xFF3AFFC8, plans = setOf("pro"), style = "circles", collection = INSPIRATIONS),
        Wallpaper("rf_folhas_turquesa", "Folhas Turquesa", 0, 0xFF3AE8FF, plans = setOf("go", "pro"), style = "leaves", collection = INSPIRATIONS),
        Wallpaper("rf_fumaca_cosmica", "Fumaça Cósmica", 0, 0xFF7A2AFF, plans = setOf("plus", "pro"), style = "smoke", collection = INSPIRATIONS),
        Wallpaper("rf_gruta_do_por_do_sol", "Gruta do Pôr do Sol", 0, 0xFFFFC07A, plans = setOf("pro"), style = "seaarch", collection = INSPIRATIONS),
        Wallpaper("rf_hibisco_rosa", "Hibisco Rosa", 0, 0xFFFF3A7A, plans = setOf("go", "plus", "pro"), style = "flower", collection = INSPIRATIONS),
        Wallpaper("rf_lago_rosado", "Lago Rosado", 0, 0xFFFFB0A0, plans = setOf("go", "pro"), style = "lake", collection = INSPIRATIONS),
        Wallpaper("rf_lobo_de_fogo", "Lobo de Fogo", 0, 0xFFFF7A1A, plans = setOf("plus", "pro"), style = "emblem", collection = INSPIRATIONS),
        Wallpaper("rf_mar_de_gelo", "Mar de Gelo", 0, 0xFFC06AD8, plans = setOf("pro"), style = "icysea", collection = INSPIRATIONS),
        Wallpaper("rf_nascer_orbital", "Nascer Orbital", 0, 0xFFFFC07A, plans = setOf("pro"), style = "earth", collection = INSPIRATIONS),
        Wallpaper("rf_onda_de_tinta", "Onda de Tinta", 0, 0xFF3AD8FF, plans = setOf("pro"), style = "paint", collection = INSPIRATIONS),
        Wallpaper("rf_pico_em_brasa", "Pico em Brasa", 0, 0xFFFF7A3A, plans = setOf("pro"), style = "peak", collection = INSPIRATIONS),
        Wallpaper("rf_terra_noturna", "Terra Noturna", 0, 0xFF5AB0FF, plans = setOf("plus", "pro"), style = "earth", collection = INSPIRATIONS),
        Wallpaper("rf_cubos_grafite", "Cubos Grafite", 99, 0xFFB0B8C8, style = "cubefield", collection = INSPIRATIONS),
        Wallpaper("rf_esferas_rubras", "Esferas Rubras", 99, 0xFFFF3A3A, style = "circles", collection = INSPIRATIONS),
        Wallpaper("rf_lobo_glacial", "Lobo Glacial", 99, 0xFF3AE8FF, style = "emblem", collection = INSPIRATIONS),
        Wallpaper("rf_olhos_azuis", "Olhos Azuis", 99, 0xFF3AB0FF, style = "eyes", collection = INSPIRATIONS),
        Wallpaper("rf_olhos_rubros", "Olhos Rubros", 99, 0xFFFF3A3A, style = "eyes", collection = INSPIRATIONS),
        Wallpaper("rf_sorriso_dourado", "Sorriso Dourado", 99, 0xFFFFD23A, style = "grin", collection = INSPIRATIONS),
        Wallpaper("rf_sorriso_toxico", "Sorriso Tóxico", 99, 0xFF7AFF2A, style = "grin", collection = INSPIRATIONS),
        Wallpaper("rf_cubos_violeta", "Cubos Violeta", 99, 0xFFB07AFF, style = "cubefield", collection = INSPIRATIONS),
        Wallpaper("rf_esferas_esmeralda", "Esferas Esmeralda", 99, 0xFF3AFF7A, style = "circles", collection = INSPIRATIONS),
        Wallpaper("rf_folhas_violeta", "Folhas Violeta", 99, 0xFFB07AFF, style = "leaves", collection = INSPIRATIONS),
        Wallpaper("rf_lobo_violeta", "Lobo Violeta", 99, 0xFFB04AFF, style = "emblem", collection = INSPIRATIONS),
        Wallpaper("rf_olhar_sonolento", "Olhar Sonolento", 99, 0xFFC8C8FF, style = "eyes", collection = INSPIRATIONS),
        Wallpaper("rf_sorriso_violeta", "Sorriso Violeta", 99, 0xFFB04AFF, style = "grin", collection = INSPIRATIONS),
        Wallpaper("rf_tinta_menta", "Tinta Menta", 99, 0xFF3AFFB0, style = "paint", collection = INSPIRATIONS),
        Wallpaper("rf_estrada_na_neblina", "Estrada na Neblina", 99, 0xFFA8C0C8, style = "aerialroad", collection = INSPIRATIONS),
        Wallpaper("rf_folhas_menta", "Folhas Menta", 99, 0xFF8AFFD0, style = "leaves", collection = INSPIRATIONS),
        Wallpaper("rf_lago_de_prata", "Lago de Prata", 99, 0xFFD0D8E0, style = "lake", collection = INSPIRATIONS),
        Wallpaper("rf_lobo_esmeralda", "Lobo Esmeralda", 99, 0xFF3AFF7A, style = "emblem", collection = INSPIRATIONS),
        Wallpaper("rf_sorriso_gelido", "Sorriso Gélido", 99, 0xFF3AE8FF, style = "grin", collection = INSPIRATIONS),
        Wallpaper("rf_tinta_doce", "Tinta Doce", 99, 0xFFFF8AD0, style = "paint", collection = INSPIRATIONS),
        Wallpaper("rf_cubos_esmeralda", "Cubos Esmeralda", 199, 0xFF3AFF8A, style = "cubefield", collection = INSPIRATIONS),
        Wallpaper("rf_cubos_oceano", "Cubos Oceano", 199, 0xFF3AB0FF, style = "cubefield", collection = INSPIRATIONS),
        Wallpaper("rf_encapuzado_ciano", "Encapuzado Ciano", 199, 0xFF3AFFF0, style = "hood", collection = INSPIRATIONS),
        Wallpaper("rf_encapuzado_dourado", "Encapuzado Dourado", 199, 0xFFFFC23A, style = "hood", collection = INSPIRATIONS),
        Wallpaper("rf_encapuzado_verde", "Encapuzado Verde", 199, 0xFF3AFF6A, style = "hood", collection = INSPIRATIONS),
        Wallpaper("rf_gelo_rubro", "Gelo Rubro", 199, 0xFFFF5A5A, style = "icysea", collection = INSPIRATIONS),
        Wallpaper("rf_gruta_carmesim", "Gruta Carmesim", 199, 0xFFE02A5A, style = "seaarch", collection = INSPIRATIONS),
        Wallpaper("rf_lago_carmesim", "Lago Carmesim", 199, 0xFFFF4A4A, style = "lake", collection = INSPIRATIONS),
        Wallpaper("rf_lago_menta", "Lago Menta", 199, 0xFF7AFFD0, style = "lake", collection = INSPIRATIONS),
        Wallpaper("rf_olhos_curiosos", "Olhos Curiosos", 199, 0xFFF4F4F0, style = "eyes", collection = INSPIRATIONS),
        Wallpaper("rf_baia_noturna", "Baía Noturna", 199, 0xFF5A8AFF, style = "karst", collection = INSPIRATIONS),
        Wallpaper("rf_baia_violeta", "Baía Violeta", 199, 0xFFB07AFF, style = "karst", collection = INSPIRATIONS),
        Wallpaper("rf_cubos_dourados", "Cubos Dourados", 199, 0xFFFFC23A, style = "cubefield", collection = INSPIRATIONS),
        Wallpaper("rf_cubos_por_do_sol", "Cubos Pôr do Sol", 199, 0xFFFFB03A, style = "cubefield", collection = INSPIRATIONS),
        Wallpaper("rf_cubos_de_fogo", "Cubos de Fogo", 199, 0xFFFF5A1A, style = "cubefield", collection = INSPIRATIONS),
        Wallpaper("rf_encapuzado_azul", "Encapuzado Azul", 199, 0xFF2A8AFF, style = "hood", collection = INSPIRATIONS),
        Wallpaper("rf_encapuzado_violeta", "Encapuzado Violeta", 199, 0xFFB04AFF, style = "hood", collection = INSPIRATIONS),
        Wallpaper("rf_esferas_oceano", "Esferas Oceano", 199, 0xFF3AB0FF, style = "circles", collection = INSPIRATIONS),
        Wallpaper("rf_esferas_rose", "Esferas Rosé", 199, 0xFFFFA0C0, style = "circles", collection = INSPIRATIONS),
        Wallpaper("rf_esferas_violeta", "Esferas Violeta", 199, 0xFFB05AFF, style = "circles", collection = INSPIRATIONS),
        Wallpaper("rf_estrada_carmesim", "Estrada Carmesim", 199, 0xFFFF3A3A, style = "aerialroad", collection = INSPIRATIONS),
        Wallpaper("rf_estrada_de_verao", "Estrada de Verão", 199, 0xFF3AFF7A, style = "aerialroad", collection = INSPIRATIONS),
        Wallpaper("rf_floresta_de_olhos", "Floresta de Olhos", 199, 0xFFFFE07A, style = "eyes", collection = INSPIRATIONS),
        Wallpaper("rf_fumaca_arco_iris", "Fumaça Arco-íris", 199, 0xFFFFE03A, style = "smoke", collection = INSPIRATIONS),
        Wallpaper("rf_fumaca_esmeralda", "Fumaça Esmeralda", 199, 0xFF2AFF7A, style = "smoke", collection = INSPIRATIONS),
        Wallpaper("rf_garoa_ciano", "Garoa Ciano", 199, 0xFF3AFFF0, style = "rainwindow", collection = INSPIRATIONS),
        Wallpaper("rf_gelo_dourado", "Gelo Dourado", 199, 0xFFFFC07A, style = "icysea", collection = INSPIRATIONS),
        Wallpaper("rf_gelo_rosado", "Gelo Rosado", 199, 0xFFFFB0C8, style = "icysea", collection = INSPIRATIONS),
        Wallpaper("rf_gelo_turquesa", "Gelo Turquesa", 199, 0xFF3AE8D8, style = "icysea", collection = INSPIRATIONS),
        Wallpaper("rf_gruta_azul", "Gruta Azul", 199, 0xFF7AB0FF, style = "seaarch", collection = INSPIRATIONS),
        Wallpaper("rf_gruta_rubra", "Gruta Rubra", 199, 0xFFFF4A3A, style = "seaarch", collection = INSPIRATIONS),
        Wallpaper("rf_gruta_violeta", "Gruta Violeta", 199, 0xFFB07AFF, style = "seaarch", collection = INSPIRATIONS),
        Wallpaper("rf_lago_violeta", "Lago Violeta", 199, 0xFFB07AFF, style = "lake", collection = INSPIRATIONS),
        Wallpaper("rf_manha_de_neblina", "Manhã de Neblina", 199, 0xFFC8D8E0, style = "lake", collection = INSPIRATIONS),
        Wallpaper("rf_mundo_verde", "Mundo Verde", 199, 0xFF3AFF8A, style = "earth", collection = INSPIRATIONS),
        Wallpaper("rf_pico_na_tempestade", "Pico na Tempestade", 199, 0xFFC0C8D8, style = "peak", collection = INSPIRATIONS),
        Wallpaper("rf_sorriso_sombrio", "Sorriso Sombrio", 199, 0xFFFF1A1A, style = "grin", collection = INSPIRATIONS),
        Wallpaper("rf_tinta_oceano", "Tinta Oceano", 199, 0xFF2A8AFF, style = "paint", collection = INSPIRATIONS),
        Wallpaper("rf_tinta_por_do_sol", "Tinta Pôr do Sol", 199, 0xFFFF7A3A, style = "paint", collection = INSPIRATIONS),
        Wallpaper("rf_tinta_rubi", "Tinta Rubi", 199, 0xFFFF2A4A, style = "paint", collection = INSPIRATIONS),
        Wallpaper("rf_baia_dourada", "Baía Dourada", 249, 0xFFFFC23A, style = "karst", collection = INSPIRATIONS),
        Wallpaper("rf_baia_na_neblina", "Baía na Neblina", 249, 0xFFA8C0C8, style = "karst", collection = INSPIRATIONS),
        Wallpaper("rf_chuva_esmeralda", "Chuva Esmeralda", 249, 0xFF3AFF8A, style = "rainwindow", collection = INSPIRATIONS),
        Wallpaper("rf_chuva_violeta", "Chuva Violeta", 249, 0xFFA04AFF, style = "rainwindow", collection = INSPIRATIONS),
        Wallpaper("rf_esferas_douradas", "Esferas Douradas", 249, 0xFFFFC23A, style = "circles", collection = INSPIRATIONS),
        Wallpaper("rf_estrada_dourada", "Estrada Dourada", 249, 0xFFFFC23A, style = "aerialroad", collection = INSPIRATIONS),
        Wallpaper("rf_folhas_esmeralda", "Folhas Esmeralda", 249, 0xFF3AFF7A, style = "leaves", collection = INSPIRATIONS),
        Wallpaper("rf_folhas_sombrias", "Folhas Sombrias", 249, 0xFF5A8AA0, style = "leaves", collection = INSPIRATIONS),
        Wallpaper("rf_fumaca_crepusculo", "Fumaça Crepúsculo", 249, 0xFFFF7AB0, style = "smoke", collection = INSPIRATIONS),
        Wallpaper("rf_fumaca_lagoa", "Fumaça Lagoa", 249, 0xFF2AE8C8, style = "smoke", collection = INSPIRATIONS),
        Wallpaper("rf_fumaca_sombria", "Fumaça Sombria", 249, 0xFFFF2A3A, style = "smoke", collection = INSPIRATIONS),
        Wallpaper("rf_gruta_laranja", "Gruta Laranja", 249, 0xFFFF8A2A, style = "seaarch", collection = INSPIRATIONS),
        Wallpaper("rf_gruta_rosa", "Gruta Rosa", 249, 0xFFFF9AC0, style = "seaarch", collection = INSPIRATIONS),
        Wallpaper("rf_hibisco_branco", "Hibisco Branco", 249, 0xFFF4F0FF, style = "flower", collection = INSPIRATIONS),
        Wallpaper("rf_hibisco_coral", "Hibisco Coral", 249, 0xFFFF7A5A, style = "flower", collection = INSPIRATIONS),
        Wallpaper("rf_hibisco_laranja", "Hibisco Laranja", 249, 0xFFFF8A1A, style = "flower", collection = INSPIRATIONS),
        Wallpaper("rf_hora_azul_no_lago", "Hora Azul no Lago", 249, 0xFF6AB0D0, style = "lake", collection = INSPIRATIONS),
        Wallpaper("rf_lago_dourado", "Lago Dourado", 249, 0xFFFFC23A, style = "lake", collection = INSPIRATIONS),
        Wallpaper("rf_lobo_rubro", "Lobo Rubro", 249, 0xFFFF2A3A, style = "emblem", collection = INSPIRATIONS),
        Wallpaper("rf_olhar_felino", "Olhar Felino", 249, 0xFF7AFF3A, style = "eyes", collection = INSPIRATIONS),
        Wallpaper("rf_pico_carmesim", "Pico Carmesim", 249, 0xFFFF3A3A, style = "peak", collection = INSPIRATIONS),
        Wallpaper("rf_pico_violeta", "Pico Violeta", 249, 0xFFB07AFF, style = "peak", collection = INSPIRATIONS),
        Wallpaper("rf_tinta_aurora", "Tinta Aurora", 249, 0xFF3AFFC0, style = "paint", collection = INSPIRATIONS),
        Wallpaper("rf_artico_azul", "Ártico Azul", 249, 0xFF7AC8FF, style = "icysea", collection = INSPIRATIONS),
        Wallpaper("rf_baia_ao_entardecer", "Baía ao Entardecer", 299, 0xFFFFA06A, style = "karst", collection = INSPIRATIONS),
        Wallpaper("rf_chuva_dourada", "Chuva Dourada", 299, 0xFFFFD23A, style = "rainwindow", collection = INSPIRATIONS),
        Wallpaper("rf_chuva_rosa", "Chuva Rosa", 299, 0xFFFF5AB0, style = "rainwindow", collection = INSPIRATIONS),
        Wallpaper("rf_estrada_de_inverno", "Estrada de Inverno", 299, 0xFFD0E8FF, style = "aerialroad", collection = INSPIRATIONS),
        Wallpaper("rf_estrada_de_outono", "Estrada de Outono", 299, 0xFFFF8A2A, style = "aerialroad", collection = INSPIRATIONS),
        Wallpaper("rf_folhas_douradas", "Folhas Douradas", 299, 0xFFFFC23A, style = "leaves", collection = INSPIRATIONS),
        Wallpaper("rf_folhas_outono", "Folhas de Outono", 299, 0xFFFF8A2A, style = "leaves", collection = INSPIRATIONS),
        Wallpaper("rf_fumaca_glacial", "Fumaça Glacial", 299, 0xFFBFEFFF, style = "smoke", collection = INSPIRATIONS),
        Wallpaper("rf_fumaca_rose_ouro", "Fumaça Rosé e Ouro", 299, 0xFFFFB0A0, style = "smoke", collection = INSPIRATIONS),
        Wallpaper("rf_gruta_turquesa", "Gruta Turquesa", 299, 0xFF3AE8D8, style = "seaarch", collection = INSPIRATIONS),
        Wallpaper("rf_hibisco_dourado", "Hibisco Dourado", 299, 0xFFFFC23A, style = "flower", collection = INSPIRATIONS),
        Wallpaper("rf_hibisco_rubro", "Hibisco Rubro", 299, 0xFFFF2A2A, style = "flower", collection = INSPIRATIONS),
        Wallpaper("rf_lago_noturno", "Lago Noturno", 299, 0xFFFF9A5A, style = "lake", collection = INSPIRATIONS),
        Wallpaper("rf_mundo_gelado", "Mundo Gelado", 299, 0xFFBFEFFF, style = "earth", collection = INSPIRATIONS),
        Wallpaper("rf_marmore_negro", "Mármore Negro", 299, 0xFFD0D4DC, style = "paint", collection = INSPIRATIONS),
        Wallpaper("rf_noite_polar", "Noite Polar", 299, 0xFF5A7AFF, style = "icysea", collection = INSPIRATIONS),
        Wallpaper("rf_pico_dourado", "Pico Dourado", 299, 0xFFFFC23A, style = "peak", collection = INSPIRATIONS),
        Wallpaper("rf_pico_ao_luar", "Pico ao Luar", 299, 0xFF8AB0FF, style = "peak", collection = INSPIRATIONS),
        Wallpaper("rf_tinta_noturna", "Tinta Noturna", 299, 0xFFFF3A8A, style = "paint", collection = INSPIRATIONS),
        Wallpaper("rf_vidro_negro", "Vidro Negro", 299, 0xFFC8D0E0, style = "circles", collection = INSPIRATIONS),
        Wallpaper("rf_alpenglow", "Alpenglow", 399, 0xFFFF8AB0, style = "peak", collection = INSPIRATIONS),
        Wallpaper("rf_aurora_no_pico", "Aurora no Pico", 399, 0xFF3AFFC0, style = "peak", collection = INSPIRATIONS),
        Wallpaper("rf_baia_turquesa", "Baía Turquesa", 399, 0xFF3AF0E0, style = "karst", collection = INSPIRATIONS),
        Wallpaper("rf_bordo_rubro", "Bordô Rubro", 399, 0xFFFF2A3A, style = "leaves", collection = INSPIRATIONS),
        Wallpaper("rf_fumaca_brasa", "Fumaça de Brasa", 399, 0xFFFF6A1A, style = "smoke", collection = INSPIRATIONS),
        Wallpaper("rf_hibisco_azul", "Hibisco Azul", 399, 0xFF3A8AFF, style = "flower", collection = INSPIRATIONS),
        Wallpaper("rf_hibisco_violeta", "Hibisco Violeta", 399, 0xFFB05AFF, style = "flower", collection = INSPIRATIONS),
        Wallpaper("rf_mundo_oceano", "Mundo Oceano", 399, 0xFF2AD0FF, style = "earth", collection = INSPIRATIONS),
        Wallpaper("rf_marmore_dourado", "Mármore Dourado", 399, 0xFFFFC23A, style = "paint", collection = INSPIRATIONS),
        Wallpaper("rf_vidro_azul", "Vidro Azul", 399, 0xFF3A8AFF, style = "rainwindow", collection = INSPIRATIONS),
        Wallpaper("rf_amanhecer_frio", "Amanhecer Frio", 499, 0xFF9AD0FF, style = "peak", collection = INSPIRATIONS),
        Wallpaper("rf_luzes_do_planeta", "Luzes do Planeta", 499, 0xFFFFB04A, style = "earth", collection = INSPIRATIONS),
        Wallpaper("rf_temporal_noturno", "Temporal Noturno", 499, 0xFFFF3A3A, style = "rainwindow", collection = INSPIRATIONS),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String?): Wallpaper? = id?.let { byId[it] }

    /** "R$ 1,99" a partir de centavos. */
    fun priceText(cents: Int): String = "R$ " + "%d,%02d".format(cents / 100, cents % 100)

    /** Faixas de preço vendidas na Play (um produto por faixa). */
    val tierProducts: List<String> get() = all.filter { !it.free && !it.pro }.map { it.productId }.distinct()

    /** Papel padrão do navegador (grátis). */
    const val DEFAULT_BROWSER = "noite_estrelada"
}
