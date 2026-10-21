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
 * Catálogo dos temas (165): coleção clássica, Heróis & Vilões, Mundos de Jogo e Premium 3D.
 * Os repetidos e os mais fracos saíram. Exclusivos dos planos têm "plans" e não se compram.
 */
object Wallpapers {
    /** Coleção "Heróis & Vilões": clima de super-herói, sem personagens de terceiros. */
    const val HEROES = "herois"

    /** Coleção "Mundos de Jogo": caça e sobrevivência, ciberpunk, fantasia medieval e corrida urbana. */
    const val GAMES = "jogos"

    /** Coleção "Premium 3D": colmeias neon, facetas, lava, metal líquido, esferas de vidro, gotas, cubos e mar ao pôr do sol. */
    const val PREMIUM = "premium"

    val all: List<Wallpaper> = listOf(
        // ---- Coleção clássica (38)
        Wallpaper("nevoa_verde", "Névoa Verde", 0, 0xFF3DDC84, style = "mountains"),
        Wallpaper("noite_estrelada", "Noite Estrelada", 0, 0xFF7AA7FF, style = "mountains"),
        Wallpaper("pro_eclipse", "Eclipse", 0, 0xFFFF9A3D, plans = setOf("pro"), style = "planet"),
        Wallpaper("pro_aurora_suprema", "Aurora Suprema", 0, 0xFF5CFFC4, plans = setOf("pro"), style = "aurora"),
        Wallpaper("pro_nebulosa_esmeralda", "Nebulosa Esmeralda", 0, 0xFF2CF08A, plans = setOf("pro"), style = "nebula"),
        Wallpaper("pro_synthwave_noir", "Synthwave Noir", 0, 0xFFFF3F6E, plans = setOf("pro"), style = "synthwave"),
        Wallpaper("pro_cidade_dourada", "Cidade Dourada", 0, 0xFFFFC24D, plans = setOf("pro"), style = "city"),
        Wallpaper("pro_seda_obsidiana", "Seda Obsidiana", 0, 0xFF7FA0FF, plans = setOf("pro"), style = "silk"),
        Wallpaper("pro_planeta_rubi", "Planeta Rubi", 0, 0xFFFF4D6D, plans = setOf("pro"), style = "planet"),
        Wallpaper("pro_cume_aurora", "Cume da Aurora", 0, 0xFF5CFFC4, plans = setOf("pro"), style = "mountains"),
        Wallpaper("luzes_cidade", "Luzes da Cidade", 50, 0xFFFFC24D, style = "bokeh"),
        Wallpaper("cristal_ametista", "Cristal Ametista", 50, 0xFFA56BFF, style = "lowpoly"),
        Wallpaper("serra_rosa", "Serra Rosa", 99, 0xFFFF8FB1, style = "mountains"),
        Wallpaper("dourado_entardecer", "Entardecer Dourado", 99, 0xFFFFB547, style = "mountains"),
        Wallpaper("dunas_ambar", "Dunas Âmbar", 99, 0xFFFFA94D, style = "dunes"),
        Wallpaper("dunas_lunares", "Dunas Lunares", 99, 0xFF9DB4FF, style = "dunes"),
        Wallpaper("floresta_neblina", "Floresta na Neblina", 99, 0xFF59C08A, style = "forest"),
        Wallpaper("seda_grafite", "Seda Grafite", 99, 0xFFC9CED6, style = "silk"),
        Wallpaper("seda_dourada", "Seda Dourada", 199, 0xFFFFC857, style = "silk"),
        Wallpaper("mar_dourado", "Mar Dourado", 199, 0xFFFFB347, style = "ocean"),
        Wallpaper("mar_turquesa", "Mar Turquesa", 199, 0xFF2ED8C3, style = "ocean"),
        Wallpaper("montanhas_lilas", "Montanhas Lilás", 199, 0xFFC08BFF, style = "mountains"),
        Wallpaper("lua_cheia", "Lua Cheia", 199, 0xFFC7D3FF, style = "moon"),
        Wallpaper("nebulosa_rosa", "Nebulosa Rosa", 299, 0xFFFF5FC8, style = "nebula"),
        Wallpaper("nebulosa_azul", "Nebulosa Azul", 299, 0xFF3FA9FF, style = "nebula"),
        Wallpaper("aurora_boreal", "Aurora Boreal", 299, 0xFF2CF0A0, style = "aurora"),
        Wallpaper("synthwave", "Synthwave", 299, 0xFFFF3FD0, style = "synthwave"),
        Wallpaper("galaxia_violeta", "Galáxia Violeta", 399, 0xFFB56BFF, style = "spiral"),
        Wallpaper("aurora_lago", "Aurora no Lago", 399, 0xFF46F2B4, style = "aurora"),
        Wallpaper("lua_mar", "Lua sobre o Mar", 399, 0xFFAFC6FF, style = "moon"),
        Wallpaper("nebulosa_fenix", "Nebulosa Fênix", 499, 0xFFFF7A3D, style = "nebula"),
        Wallpaper("planeta_gelo", "Planeta de Gelo", 499, 0xFF7FE3FF, style = "planet"),
        Wallpaper("aurora_rosa", "Aurora Rosa", 499, 0xFFFF6BD6, style = "aurora"),
        Wallpaper("metropole", "Metrópole 2099", 499, 0xFF2FF3FF, style = "city"),
        Wallpaper("via_lactea", "Via Láctea", 499, 0xFFE7C6FF, style = "milkyway"),
        Wallpaper("saturno_dourado", "Saturno Dourado", 499, 0xFFFFD27A, style = "planet"),
        Wallpaper("portal_cosmico", "Portal Cósmico", 599, 0xFF6BE4FF, style = "spiral"),
        Wallpaper("supernova", "Supernova", 599, 0xFFFFB05A, style = "supernova"),
        // ---- Heróis & Vilões (23)
        Wallpaper("hv_fumaca_sombria", "Fumaça Sombria", 199, 0xFFA56BFF, style = "toxic", collection = HEROES),
        Wallpaper("hv_impacto_azul", "Impacto Azul", 299, 0xFF3FB8FF, style = "comic", collection = HEROES),
        Wallpaper("hv_rastro_azul", "Rastro Azul", 299, 0xFF4DC8FF, style = "speed", collection = HEROES),
        Wallpaper("hv_fogo_azul", "Fogo Azul", 299, 0xFF4DB8FF, style = "inferno", collection = HEROES),
        Wallpaper("hv_nevoa_toxica", "Névoa Tóxica", 299, 0xFF7CFF3F, style = "toxic", collection = HEROES),
        Wallpaper("hv_explosao_pop", "Explosão Pop", 399, 0xFFFFD23F, style = "comic", collection = HEROES),
        Wallpaper("hv_velocidade_rubra", "Velocidade Rubra", 399, 0xFFFF4D3D, style = "speed", collection = HEROES),
        Wallpaper("hv_escudo_dourado", "Escudo Dourado", 399, 0xFFFFC857, style = "hexshield", collection = HEROES),
        Wallpaper("hv_circuito_tatico", "Circuito Tático", 399, 0xFF3FE0FF, style = "circuit", collection = HEROES),
        Wallpaper("hv_tempestade_eletrica", "Tempestade Elétrica", 499, 0xFF6FB6FF, style = "storm", collection = HEROES),
        Wallpaper("hv_portal_rubro", "Portal Rubro", 499, 0xFFFF4D5E, style = "rift", collection = HEROES),
        Wallpaper("hv_inferno_rubro", "Inferno Rubro", 499, 0xFFFF6A2A, style = "inferno", collection = HEROES),
        Wallpaper("hv_impacto_sismico", "Impacto Sísmico", 499, 0xFFFF7A2A, style = "shatter", collection = HEROES),
        Wallpaper("hv_nucleo_plasma", "Núcleo de Plasma", 499, 0xFF6BE4FF, style = "plasma", collection = HEROES),
        Wallpaper("hv_orbe_sombrio", "Orbe Sombrio", 499, 0xFFC77DFF, style = "plasma", collection = HEROES),
        Wallpaper("hv_cidade_alerta", "Cidade em Alerta", 499, 0xFFFF4D3D, style = "dawncity", collection = HEROES),
        Wallpaper("hv_vortice_caos", "Vórtice do Caos", 499, 0xFF5CFF8A, style = "vortex", collection = HEROES),
        Wallpaper("hv_eclipse_rubro", "Eclipse Rubro", 499, 0xFFFF3B3B, style = "planet", collection = HEROES),
        Wallpaper("hv_furia_tempestade", "Fúria da Tempestade", 599, 0xFF7FD4FF, style = "storm", collection = HEROES),
        Wallpaper("hv_trovoada_violeta", "Trovoada Violeta", 599, 0xFFB57BFF, style = "storm", collection = HEROES),
        Wallpaper("hv_escudo_energia", "Escudo de Energia", 599, 0xFF3FE0FF, style = "hexshield", collection = HEROES),
        Wallpaper("hv_fenda_dimensional", "Fenda Dimensional", 599, 0xFFC77DFF, style = "rift", collection = HEROES),
        Wallpaper("hv_amanhecer_heroi", "Amanhecer do Herói", 599, 0xFFFFB347, style = "dawncity", collection = HEROES),
        // ---- Mundos de Jogo (24)
        Wallpaper("jg_bosque_nevoa", "Bosque da Névoa", 0, 0xFFC8E8B0, plans = setOf("pro"), style = "hunt", collection = GAMES),
        Wallpaper("jg_uivo_noite", "Uivo da Noite", 0, 0xFF8AB0FF, plans = setOf("pro"), style = "hunt", collection = GAMES),
        Wallpaper("jg_cervo_real", "Cervo Real", 0, 0xFFFFB050, plans = setOf("pro"), style = "hunt", collection = GAMES),
        Wallpaper("jg_matriz_verde", "Matriz Verde", 0, 0xFF3AFF6A, plans = setOf("pro"), style = "cyber", collection = GAMES),
        Wallpaper("jg_cupula_holografica", "Cúpula Holográfica", 0, 0xFF7AFFE8, plans = setOf("pro"), style = "cyber", collection = GAMES),
        Wallpaper("jg_fortaleza_lunar", "Fortaleza Lunar", 0, 0xFFE0E8FF, plans = setOf("pro"), style = "fantasy", collection = GAMES),
        Wallpaper("jg_coroa_celestial", "Coroa Celestial", 0, 0xFFFFF0A0, plans = setOf("pro"), style = "fantasy", collection = GAMES),
        Wallpaper("jg_turbo_ciano", "Turbo Ciano", 0, 0xFF3FFFF0, plans = setOf("pro"), style = "racing", collection = GAMES),
        Wallpaper("jg_estrada_violeta", "Estrada Violeta", 0, 0xFFB07AFF, plans = setOf("pro"), style = "racing", collection = GAMES),
        Wallpaper("jg_pista_gelada", "Pista Gelada", 0, 0xFFC8F0FF, plans = setOf("pro"), style = "racing", collection = GAMES),
        Wallpaper("jg_floresta_boreal", "Floresta Boreal", 999, 0xFF9FD0FF, style = "hunt", collection = GAMES),
        Wallpaper("jg_distrito_holograma", "Distrito Holograma", 999, 0xFF2FE8FF, style = "cyber", collection = GAMES),
        Wallpaper("jg_por_do_sol_cibernetico", "Pôr do Sol Cibernético", 999, 0xFFFF9A3A, style = "cyber", collection = GAMES),
        Wallpaper("jg_lua_sangue", "Lua de Sangue", 999, 0xFFFF3A3A, style = "fantasy", collection = GAMES),
        Wallpaper("jg_reino_rosa", "Reino Rosa", 999, 0xFFFFA0C8, style = "fantasy", collection = GAMES),
        Wallpaper("jg_retao_dourado", "Retão Dourado", 999, 0xFFFFD23F, style = "racing", collection = GAMES),
        Wallpaper("jg_lua_cacadora", "Lua Caçadora", 1499, 0xFFA8C8FF, style = "hunt", collection = GAMES),
        Wallpaper("jg_torres_douradas", "Torres Douradas", 1499, 0xFFFFD23F, style = "cyber", collection = GAMES),
        Wallpaper("jg_bastiao_dourado", "Bastião Dourado", 1499, 0xFFFFD050, style = "fantasy", collection = GAMES),
        Wallpaper("jg_asfalto_neon", "Asfalto Neon", 1499, 0xFFFF4FD8, style = "racing", collection = GAMES),
        Wallpaper("jg_rodovia_por_do_sol", "Rodovia ao Pôr do Sol", 1499, 0xFFFFA04A, style = "racing", collection = GAMES),
        Wallpaper("jg_amanhecer_mata", "Amanhecer na Mata", 2000, 0xFFFFC07A, style = "hunt", collection = GAMES),
        Wallpaper("jg_metropole_neon", "Metrópole Neon", 2000, 0xFFFF4FD8, style = "cyber", collection = GAMES),
        Wallpaper("jg_castelo_estrelas", "Castelo das Estrelas", 2000, 0xFFFFE08A, style = "fantasy", collection = GAMES),
        // ---- Premium 3D (novos) (80)
        Wallpaper("pm_colmeia_rubra", "Colmeia Rubra", 0, 0xFFFF2A5A, plans = setOf("pro"), style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_colmeia_ciano", "Colmeia Ciano", 0, 0xFF1AE8FF, plans = setOf("go"), style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_colmeia_violeta", "Colmeia Violeta", 0, 0xFFA04AFF, plans = setOf("plus", "pro"), style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_facetas_ouro", "Facetas de Ouro", 0, 0xFFFFB02A, plans = setOf("pro"), style = "facets", collection = PREMIUM),
        Wallpaper("pm_facetas_ametista", "Facetas Ametista", 0, 0xFFB05AFF, plans = setOf("pro"), style = "facets", collection = PREMIUM),
        Wallpaper("pm_nucleo_lava", "Núcleo de Lava", 0, 0xFFFF6A1A, plans = setOf("pro"), style = "shards3d", collection = PREMIUM),
        Wallpaper("pm_fissura_azul", "Fissura Azul", 0, 0xFF2A8AFF, plans = setOf("pro"), style = "shards3d", collection = PREMIUM),
        Wallpaper("pm_linhas_ouro", "Linhas de Ouro", 0, 0xFFFFB050, plans = setOf("pro"), style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_seda_azul", "Seda Azul", 0, 0xFF3A7AFF, plans = setOf("plus", "pro"), style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_seda_iridescente", "Seda Iridescente", 0, 0xFFFF6AD5, plans = setOf("pro"), style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_ondas_cosmicas", "Ondas Cósmicas", 0, 0xFF7A5AFF, plans = setOf("plus"), style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_esfera_cristal", "Esfera de Cristal", 0, 0xFFA8C0FF, plans = setOf("pro"), style = "glass", collection = PREMIUM),
        Wallpaper("pm_trio_orbes", "Trio de Orbes", 0, 0xFF7AFFE8, plans = setOf("pro"), style = "glass", collection = PREMIUM),
        Wallpaper("pm_orbe_noturno", "Orbe Noturno", 0, 0xFF5A7AFF, plans = setOf("plus"), style = "glass", collection = PREMIUM),
        Wallpaper("pm_sorriso_chuva", "Sorriso na Chuva", 0, 0xFFFFB81A, plans = setOf("pro"), style = "drops", collection = PREMIUM),
        Wallpaper("pm_coracao_chuva", "Coração na Chuva", 0, 0xFFFF2A4A, plans = setOf("go", "plus"), style = "drops", collection = PREMIUM),
        Wallpaper("pm_sorriso_limao", "Sorriso Limão", 0, 0xFFA8FF2A, plans = setOf("go"), style = "drops", collection = PREMIUM),
        Wallpaper("pm_cubo_azul", "Cubo Azul", 0, 0xFF1AA0FF, plans = setOf("go", "pro"), style = "cubes", collection = PREMIUM),
        Wallpaper("pm_cubo_violeta", "Cubo Violeta", 0, 0xFFA04AFF, plans = setOf("plus"), style = "cubes", collection = PREMIUM),
        Wallpaper("pm_mar_carmesim", "Mar Carmesim", 0, 0xFFFF3A4A, plans = setOf("pro"), style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_crepusculo_violeta", "Crepúsculo Violeta", 0, 0xFFC04A8A, plans = setOf("pro"), style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_hora_azul", "Hora Azul", 0, 0xFF5A8AFF, plans = setOf("go", "plus"), style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_fumaca_neon", "Fumaça Neon", 0, 0xFFFF3A8A, plans = setOf("plus", "pro"), style = "silksmoke", collection = PREMIUM),
        Wallpaper("pm_fumaca_oceano", "Fumaça Oceano", 0, 0xFF2AD0FF, plans = setOf("go"), style = "silksmoke", collection = PREMIUM),
        Wallpaper("pm_fumaca_aurora", "Fumaça Aurora", 0, 0xFF2AFFB0, plans = setOf("plus"), style = "silksmoke", collection = PREMIUM),
        Wallpaper("pm_gotas_douradas", "Gotas Douradas", 99, 0xFFFFC24A, style = "drops", collection = PREMIUM),
        Wallpaper("pm_cubo_ciano", "Cubo Ciano", 99, 0xFF2AFFE8, style = "cubes", collection = PREMIUM),
        Wallpaper("pm_aneis_cobre", "Anéis de Cobre", 99, 0xFFFF8A4A, style = "rings", collection = PREMIUM),
        Wallpaper("pm_aneis_violeta", "Anéis Violeta", 99, 0xFFA04AFF, style = "rings", collection = PREMIUM),
        Wallpaper("pm_aneis_rose", "Anéis Rosé", 99, 0xFFFF9AB0, style = "rings", collection = PREMIUM),
        Wallpaper("pm_facetas_cobre", "Facetas de Cobre", 199, 0xFFFF7A3A, style = "facets", collection = PREMIUM),
        Wallpaper("pm_ondas_rubras", "Ondas Rubras", 199, 0xFFFF2A3A, style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_seda_aurora", "Seda Aurora", 199, 0xFF2AFFD0, style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_gotas_rubras", "Gotas Rubras", 199, 0xFFFF3A3A, style = "drops", collection = PREMIUM),
        Wallpaper("pm_cubo_branco", "Cubo Branco", 199, 0xFFE8F0FF, style = "cubes", collection = PREMIUM),
        Wallpaper("pm_aneis_azuis", "Anéis Azuis", 199, 0xFF3A8AFF, style = "rings", collection = PREMIUM),
        Wallpaper("pm_colmeia_glacial", "Colmeia Glacial", 299, 0xFFCFEFFF, style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_facetas_esmeralda", "Facetas Esmeralda", 299, 0xFF2AFF8A, style = "facets", collection = PREMIUM),
        Wallpaper("pm_fissura_toxica", "Fissura Tóxica", 299, 0xFF7AFF2A, style = "shards3d", collection = PREMIUM),
        Wallpaper("pm_ondas_esmeralda", "Ondas Esmeralda", 299, 0xFF2AFF8A, style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_gotas_azuis", "Gotas Azuis", 299, 0xFF3A8AFF, style = "drops", collection = PREMIUM),
        Wallpaper("pm_cubo_laranja", "Cubo Laranja", 299, 0xFFFF7A1A, style = "cubes", collection = PREMIUM),
        Wallpaper("pm_aneis_prata", "Anéis de Prata", 299, 0xFFDCE4EE, style = "rings", collection = PREMIUM),
        Wallpaper("pm_fumaca_esmeralda", "Fumaça Esmeralda", 299, 0xFF2AFF7A, style = "silksmoke", collection = PREMIUM),
        Wallpaper("pm_colmeia_esmeralda", "Colmeia Esmeralda", 399, 0xFF2AFF7A, style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_fissura_carmesim", "Fissura Carmesim", 399, 0xFFFF2A3A, style = "shards3d", collection = PREMIUM),
        Wallpaper("pm_ondas_prata", "Ondas de Prata", 399, 0xFFDCE4EE, style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_moeda_dourada", "Moeda na Chuva", 399, 0xFFFFC23A, style = "drops", collection = PREMIUM),
        Wallpaper("pm_cubo_esmeralda", "Cubo Esmeralda", 399, 0xFF2AFF7A, style = "cubes", collection = PREMIUM),
        Wallpaper("pm_entardecer_laranja", "Entardecer Laranja", 399, 0xFFFF7A2A, style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_mar_de_vinho", "Mar de Vinho", 399, 0xFFC02A5A, style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_fumaca_dourada", "Fumaça Dourada", 399, 0xFFFFD07A, style = "silksmoke", collection = PREMIUM),
        Wallpaper("pm_colmeia_solar", "Colmeia Solar", 499, 0xFFFF5A1A, style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_facetas_prata", "Facetas de Prata", 499, 0xFFDCE4EE, style = "facets", collection = PREMIUM),
        Wallpaper("pm_ouro_rose", "Ouro Rosé", 499, 0xFFFF9A8A, style = "facets", collection = PREMIUM),
        Wallpaper("pm_orbe_glacial", "Orbe Glacial", 499, 0xFFC8F0FF, style = "glass", collection = PREMIUM),
        Wallpaper("pm_cubo_rubro", "Cubo Rubro", 499, 0xFFFF2A3A, style = "cubes", collection = PREMIUM),
        Wallpaper("pm_mare_turquesa", "Maré Turquesa", 499, 0xFF2AD0C8, style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_aneis_ouro", "Anéis de Ouro", 499, 0xFFFFC24A, style = "rings", collection = PREMIUM),
        Wallpaper("pm_fumaca_violeta", "Fumaça Violeta", 499, 0xFFA04AFF, style = "silksmoke", collection = PREMIUM),
        Wallpaper("pm_colmeia_noturna", "Colmeia Noturna", 599, 0xFF3A7AFF, style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_orbe_rosa", "Orbe Rosa", 599, 0xFFFF6AB0, style = "glass", collection = PREMIUM),
        Wallpaper("pm_orbe_solar", "Orbe Solar", 599, 0xFFFF8A2A, style = "glass", collection = PREMIUM),
        Wallpaper("pm_por_do_sol_dourado", "Pôr do Sol Dourado", 599, 0xFFFFB03A, style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_ceu_rosa", "Céu Rosa", 599, 0xFFFF8AB0, style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_fumaca_fogo", "Fumaça de Fogo", 599, 0xFFFF6A1A, style = "silksmoke", collection = PREMIUM),
        Wallpaper("pm_colmeia_arco_iris", "Colmeia Arco-íris", 799, 0xFF2AFFB0, style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_facetas_rubi", "Facetas Rubi", 799, 0xFFFF2A4A, style = "facets", collection = PREMIUM),
        Wallpaper("pm_fissura_dourada", "Fissura Dourada", 799, 0xFFFFC24A, style = "shards3d", collection = PREMIUM),
        Wallpaper("pm_ondas_rose", "Ondas Rosé", 799, 0xFFFF7A9A, style = "liquidwave", collection = PREMIUM),
        Wallpaper("pm_sorriso_rosa", "Sorriso Rosa", 799, 0xFFFF5A9A, style = "drops", collection = PREMIUM),
        Wallpaper("pm_orbe_esmeralda", "Orbe Esmeralda", 999, 0xFF2AFF8A, style = "glass", collection = PREMIUM),
        Wallpaper("pm_prisma_molhado", "Prisma Molhado", 999, 0xFFFFE82A, style = "drops", collection = PREMIUM),
        Wallpaper("pm_cubo_dourado", "Cubo Dourado", 999, 0xFFFFB02A, style = "cubes", collection = PREMIUM),
        Wallpaper("pm_ultimo_raio", "Último Raio", 999, 0xFFFFD07A, style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_facetas_neon", "Facetas Neon", 1499, 0xFF2AFFE8, style = "facets", collection = PREMIUM),
        Wallpaper("pm_fissura_violeta", "Fissura Violeta", 1499, 0xFFB05AFF, style = "shards3d", collection = PREMIUM),
        Wallpaper("pm_sol_de_fogo", "Sol de Fogo", 1499, 0xFFFF4A1A, style = "sunsea", collection = PREMIUM),
        Wallpaper("pm_colmeia_dourada", "Colmeia Dourada", 2000, 0xFFFFB02A, style = "hex3d", collection = PREMIUM),
        Wallpaper("pm_orbe_ambar", "Orbe Âmbar", 2000, 0xFFFFB03A, style = "glass", collection = PREMIUM),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String?): Wallpaper? = id?.let { byId[it] }

    /** Faixas de preço vendidas na Play (um produto por faixa). */
    val tierProducts: List<String> get() = all.filter { !it.free && !it.pro }.map { it.productId }.distinct()

    /** Papel padrão do navegador (grátis). */
    const val DEFAULT_BROWSER = "noite_estrelada"
}
