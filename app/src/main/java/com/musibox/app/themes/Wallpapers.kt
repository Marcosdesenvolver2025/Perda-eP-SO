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
 * Catálogo dos temas (185): coleção principal (60), Heróis & Vilões (50) e Mundos de Jogo (75:
 * 50 à venda e 25 só nos planos). Preços: antigos de R$ 0,50 a R$ 5,99; novos de R$ 4,99 a R$ 20.
 */
object Wallpapers {
    /** Coleção "Heróis & Vilões": clima de super-herói, sem personagens de terceiros. */
    const val HEROES = "herois"

    /** Coleção "Mundos de Jogo": caça e sobrevivência, ciberpunk, fantasia medieval e corrida urbana. */
    const val GAMES = "jogos"

    val all: List<Wallpaper> = listOf(
        // ---- Coleção principal (60)
        Wallpaper("nevoa_verde", "Névoa Verde", 0, 0xFF3DDC84, style = "mountains"),
        Wallpaper("noite_estrelada", "Noite Estrelada", 0, 0xFF7AA7FF, style = "mountains"),
        Wallpaper("degrade_aurora", "Degradê Aurora", 50, 0xFF7C5CFF, style = "gradient"),
        Wallpaper("degrade_oceano", "Degradê Oceano", 50, 0xFF2EC4F1, style = "gradient"),
        Wallpaper("degrade_por_do_sol", "Degradê Pôr do Sol", 50, 0xFFFF8A3D, style = "gradient"),
        Wallpaper("degrade_menta", "Degradê Menta", 50, 0xFF26D07C, style = "gradient"),
        Wallpaper("relevo_grafite", "Relevo Grafite", 50, 0xFFB0B8C4, style = "topo"),
        Wallpaper("relevo_menta", "Relevo Menta", 50, 0xFF1FDF67, style = "topo"),
        Wallpaper("linhas_violeta", "Linhas Violeta", 50, 0xFFB57BFF, style = "flowlines"),
        Wallpaper("linhas_ciano", "Linhas Ciano", 50, 0xFF3FE0FF, style = "flowlines"),
        Wallpaper("malha_neon", "Malha Neon", 50, 0xFFFF4FD8, style = "blobs"),
        Wallpaper("malha_tropical", "Malha Tropical", 50, 0xFFFFB020, style = "blobs"),
        Wallpaper("malha_glacial", "Malha Glacial", 50, 0xFF7FD4FF, style = "blobs"),
        Wallpaper("malha_cereja", "Malha Cereja", 50, 0xFFFF5A7A, style = "blobs"),
        Wallpaper("luzes_cidade", "Luzes da Cidade", 50, 0xFFFFC24D, style = "bokeh"),
        Wallpaper("luzes_azuis", "Luzes Azuis", 50, 0xFF4DB8FF, style = "bokeh"),
        Wallpaper("cristal_ametista", "Cristal Ametista", 50, 0xFFA56BFF, style = "lowpoly"),
        Wallpaper("cristal_esmeralda", "Cristal Esmeralda", 50, 0xFF2BE38B, style = "lowpoly"),
        Wallpaper("serra_rosa", "Serra Rosa", 99, 0xFFFF8FB1, style = "mountains"),
        Wallpaper("vale_azul", "Vale Azul", 99, 0xFF6FB6FF, style = "mountains"),
        Wallpaper("dourado_entardecer", "Entardecer Dourado", 99, 0xFFFFB547, style = "mountains"),
        Wallpaper("dunas_ambar", "Dunas Âmbar", 99, 0xFFFFA94D, style = "dunes"),
        Wallpaper("dunas_lunares", "Dunas Lunares", 99, 0xFF9DB4FF, style = "dunes"),
        Wallpaper("floresta_neblina", "Floresta na Neblina", 99, 0xFF59C08A, style = "forest"),
        Wallpaper("floresta_noturna", "Floresta Noturna", 99, 0xFF5E7BFF, style = "forest"),
        Wallpaper("seda_grafite", "Seda Grafite", 99, 0xFFC9CED6, style = "silk"),
        Wallpaper("seda_dourada", "Seda Dourada", 199, 0xFFFFC857, style = "silk"),
        Wallpaper("seda_rose", "Seda Rosé", 199, 0xFFFF8FAB, style = "silk"),
        Wallpaper("mar_dourado", "Mar Dourado", 199, 0xFFFFB347, style = "ocean"),
        Wallpaper("mar_turquesa", "Mar Turquesa", 199, 0xFF2ED8C3, style = "ocean"),
        Wallpaper("picos_nevados", "Picos Nevados", 199, 0xFF8EC9FF, style = "mountains"),
        Wallpaper("montanhas_lilas", "Montanhas Lilás", 199, 0xFFC08BFF, style = "mountains"),
        Wallpaper("lua_cheia", "Lua Cheia", 199, 0xFFC7D3FF, style = "moon"),
        Wallpaper("nebulosa_rosa", "Nebulosa Rosa", 299, 0xFFFF5FC8, style = "nebula"),
        Wallpaper("nebulosa_azul", "Nebulosa Azul", 299, 0xFF3FA9FF, style = "nebula"),
        Wallpaper("aurora_boreal", "Aurora Boreal", 299, 0xFF2CF0A0, style = "aurora"),
        Wallpaper("cidade_neon", "Cidade Neon", 299, 0xFFFF4FD8, style = "city"),
        Wallpaper("planeta_aneis", "Planeta dos Anéis", 299, 0xFFFFC27A, style = "planet"),
        Wallpaper("synthwave", "Synthwave", 299, 0xFFFF3FD0, style = "synthwave"),
        Wallpaper("galaxia_violeta", "Galáxia Violeta", 399, 0xFFB56BFF, style = "spiral"),
        Wallpaper("aurora_lago", "Aurora no Lago", 399, 0xFF46F2B4, style = "aurora"),
        Wallpaper("synthwave_ciano", "Synthwave Ciano", 399, 0xFF2FF3FF, style = "synthwave"),
        Wallpaper("cume_estrelado", "Cume Estrelado", 399, 0xFF8FA8FF, style = "mountains"),
        Wallpaper("lua_mar", "Lua sobre o Mar", 399, 0xFFAFC6FF, style = "moon"),
        Wallpaper("nebulosa_fenix", "Nebulosa Fênix", 499, 0xFFFF7A3D, style = "nebula"),
        Wallpaper("planeta_gelo", "Planeta de Gelo", 499, 0xFF7FE3FF, style = "planet"),
        Wallpaper("aurora_rosa", "Aurora Rosa", 499, 0xFFFF6BD6, style = "aurora"),
        Wallpaper("metropole", "Metrópole 2099", 499, 0xFF2FF3FF, style = "city"),
        Wallpaper("via_lactea", "Via Láctea", 499, 0xFFE7C6FF, style = "milkyway"),
        Wallpaper("saturno_dourado", "Saturno Dourado", 499, 0xFFFFD27A, style = "planet"),
        Wallpaper("portal_cosmico", "Portal Cósmico", 599, 0xFF6BE4FF, style = "spiral"),
        Wallpaper("supernova", "Supernova", 599, 0xFFFFB05A, style = "supernova"),
        Wallpaper("pro_eclipse", "Eclipse", 0, 0xFFFF9A3D, plans = setOf("pro"), style = "planet"),
        Wallpaper("pro_aurora_suprema", "Aurora Suprema", 0, 0xFF5CFFC4, plans = setOf("pro"), style = "aurora"),
        Wallpaper("pro_nebulosa_esmeralda", "Nebulosa Esmeralda", 0, 0xFF2CF08A, plans = setOf("pro"), style = "nebula"),
        Wallpaper("pro_synthwave_noir", "Synthwave Noir", 0, 0xFFFF3F6E, plans = setOf("pro"), style = "synthwave"),
        Wallpaper("pro_cidade_dourada", "Cidade Dourada", 0, 0xFFFFC24D, plans = setOf("pro"), style = "city"),
        Wallpaper("pro_seda_obsidiana", "Seda Obsidiana", 0, 0xFF7FA0FF, plans = setOf("pro"), style = "silk"),
        Wallpaper("pro_planeta_rubi", "Planeta Rubi", 0, 0xFFFF4D6D, plans = setOf("pro"), style = "planet"),
        Wallpaper("pro_cume_aurora", "Cume da Aurora", 0, 0xFF5CFFC4, plans = setOf("pro"), style = "mountains"),
        // ---- Heróis & Vilões (50)
        Wallpaper("hv_furia_tempestade", "Fúria da Tempestade", 599, 0xFF7FD4FF, style = "storm", collection = HEROES),
        Wallpaper("hv_fenda_dimensional", "Fenda Dimensional", 599, 0xFFC77DFF, style = "rift", collection = HEROES),
        Wallpaper("hv_trovoada_violeta", "Trovoada Violeta", 599, 0xFFB57BFF, style = "storm", collection = HEROES),
        Wallpaper("hv_escudo_energia", "Escudo de Energia", 599, 0xFF3FE0FF, style = "hexshield", collection = HEROES),
        Wallpaper("hv_amanhecer_heroi", "Amanhecer do Herói", 599, 0xFFFFB347, style = "dawncity", collection = HEROES),
        Wallpaper("hv_portal_rubro", "Portal Rubro", 499, 0xFFFF4D5E, style = "rift", collection = HEROES),
        Wallpaper("hv_inferno_rubro", "Inferno Rubro", 499, 0xFFFF6A2A, style = "inferno", collection = HEROES),
        Wallpaper("hv_impacto_sismico", "Impacto Sísmico", 499, 0xFFFF7A2A, style = "shatter", collection = HEROES),
        Wallpaper("hv_nucleo_plasma", "Núcleo de Plasma", 499, 0xFF6BE4FF, style = "plasma", collection = HEROES),
        Wallpaper("hv_vortice_caos", "Vórtice do Caos", 499, 0xFF5CFF8A, style = "vortex", collection = HEROES),
        Wallpaper("hv_tempestade_eletrica", "Tempestade Elétrica", 499, 0xFF6FB6FF, style = "storm", collection = HEROES),
        Wallpaper("hv_rasgo_esmeralda", "Rasgo Esmeralda", 499, 0xFF3DFF9A, style = "rift", collection = HEROES),
        Wallpaper("hv_fenda_celeste", "Fenda Celeste", 499, 0xFF7FE3FF, style = "rift", collection = HEROES),
        Wallpaper("hv_orbe_sombrio", "Orbe Sombrio", 499, 0xFFC77DFF, style = "plasma", collection = HEROES),
        Wallpaper("hv_cidade_alerta", "Cidade em Alerta", 499, 0xFFFF4D3D, style = "dawncity", collection = HEROES),
        Wallpaper("hv_olho_tormenta", "Olho da Tormenta", 499, 0xFFB57BFF, style = "vortex", collection = HEROES),
        Wallpaper("hv_eclipse_rubro", "Eclipse Rubro", 499, 0xFFFF3B3B, style = "planet", collection = HEROES),
        Wallpaper("hv_raio_escarlate", "Raio Escarlate", 399, 0xFFFF4D5E, style = "storm", collection = HEROES),
        Wallpaper("hv_explosao_pop", "Explosão Pop", 399, 0xFFFFD23F, style = "comic", collection = HEROES),
        Wallpaper("hv_velocidade_rubra", "Velocidade Rubra", 399, 0xFFFF4D3D, style = "speed", collection = HEROES),
        Wallpaper("hv_escudo_dourado", "Escudo Dourado", 399, 0xFFFFC857, style = "hexshield", collection = HEROES),
        Wallpaper("hv_barreira_rubra", "Barreira Rubra", 399, 0xFFFF4D5E, style = "hexshield", collection = HEROES),
        Wallpaper("hv_chama_esmeralda", "Chama Esmeralda", 399, 0xFF3DFF7A, style = "inferno", collection = HEROES),
        Wallpaper("hv_terra_partida_azul", "Terra Partida Azul", 399, 0xFF4DC8FF, style = "shatter", collection = HEROES),
        Wallpaper("hv_esfera_solar", "Esfera Solar", 399, 0xFFFFC24D, style = "plasma", collection = HEROES),
        Wallpaper("hv_circuito_tatico", "Circuito Tático", 399, 0xFF3FE0FF, style = "circuit", collection = HEROES),
        Wallpaper("hv_vigilia_noturna", "Vigília Noturna", 399, 0xFF7AA7FF, style = "dawncity", collection = HEROES),
        Wallpaper("hv_relampago_dourado", "Relâmpago Dourado", 299, 0xFFFFC857, style = "storm", collection = HEROES),
        Wallpaper("hv_impacto_azul", "Impacto Azul", 299, 0xFF3FB8FF, style = "comic", collection = HEROES),
        Wallpaper("hv_golpe_vilao", "Golpe do Vilão", 299, 0xFFA8FF3F, style = "comic", collection = HEROES),
        Wallpaper("hv_rastro_azul", "Rastro Azul", 299, 0xFF4DC8FF, style = "speed", collection = HEROES),
        Wallpaper("hv_domo_esmeralda", "Domo Esmeralda", 299, 0xFF3DFF9A, style = "hexshield", collection = HEROES),
        Wallpaper("hv_fogo_azul", "Fogo Azul", 299, 0xFF4DB8FF, style = "inferno", collection = HEROES),
        Wallpaper("hv_nevoa_toxica", "Névoa Tóxica", 299, 0xFF7CFF3F, style = "toxic", collection = HEROES),
        Wallpaper("hv_rachadura_esmeralda", "Rachadura Esmeralda", 299, 0xFF3DFF9A, style = "shatter", collection = HEROES),
        Wallpaper("hv_orbe_carmesim", "Orbe Carmesim", 299, 0xFFFF4D6D, style = "plasma", collection = HEROES),
        Wallpaper("hv_tempestade_esmeralda", "Tempestade Esmeralda", 199, 0xFF3DFF9A, style = "storm", collection = HEROES),
        Wallpaper("hv_estouro_rosa", "Estouro Rosa", 199, 0xFFFF5FC8, style = "comic", collection = HEROES),
        Wallpaper("hv_supersonico_dourado", "Supersônico Dourado", 199, 0xFFFFC24D, style = "speed", collection = HEROES),
        Wallpaper("hv_fogo_violeta", "Fogo Violeta", 199, 0xFFC77DFF, style = "inferno", collection = HEROES),
        Wallpaper("hv_fumaca_sombria", "Fumaça Sombria", 199, 0xFFA56BFF, style = "toxic", collection = HEROES),
        Wallpaper("hv_solo_dourado", "Solo Dourado", 199, 0xFFFFC857, style = "shatter", collection = HEROES),
        Wallpaper("hv_visor_rubro", "Visor Rubro", 199, 0xFFFF4D5E, style = "circuit", collection = HEROES),
        Wallpaper("hv_quadrinho_laranja", "Quadrinho Laranja", 99, 0xFFFF9A3D, style = "comic", collection = HEROES),
        Wallpaper("hv_corrida_esmeralda", "Corrida Esmeralda", 99, 0xFF3DFF9A, style = "speed", collection = HEROES),
        Wallpaper("hv_veneno_carmesim", "Veneno Carmesim", 99, 0xFFFF3F5E, style = "toxic", collection = HEROES),
        Wallpaper("hv_fratura_violeta", "Fratura Violeta", 99, 0xFFB57BFF, style = "shatter", collection = HEROES),
        Wallpaper("hv_matriz_dourada", "Matriz Dourada", 99, 0xFFFFC857, style = "circuit", collection = HEROES),
        Wallpaper("hv_miasma_acido", "Miasma Ácido", 50, 0xFFE8FF3F, style = "toxic", collection = HEROES),
        Wallpaper("hv_sistema_esmeralda", "Sistema Esmeralda", 50, 0xFF3DFF9A, style = "circuit", collection = HEROES),
        // ---- Mundos de Jogo: à venda (50)
        Wallpaper("jg_amanhecer_mata", "Amanhecer na Mata", 2000, 0xFFFFC07A, style = "hunt", collection = GAMES),
        Wallpaper("jg_metropole_neon", "Metrópole Neon", 2000, 0xFFFF4FD8, style = "cyber", collection = GAMES),
        Wallpaper("jg_castelo_estrelas", "Castelo das Estrelas", 2000, 0xFFFFE08A, style = "fantasy", collection = GAMES),
        Wallpaper("jg_lua_cacadora", "Lua Caçadora", 1499, 0xFFA8C8FF, style = "hunt", collection = GAMES),
        Wallpaper("jg_torres_douradas", "Torres Douradas", 1499, 0xFFFFD23F, style = "cyber", collection = GAMES),
        Wallpaper("jg_bastiao_dourado", "Bastião Dourado", 1499, 0xFFFFD050, style = "fantasy", collection = GAMES),
        Wallpaper("jg_asfalto_neon", "Asfalto Neon", 1499, 0xFFFF4FD8, style = "racing", collection = GAMES),
        Wallpaper("jg_rodovia_por_do_sol", "Rodovia ao Pôr do Sol", 1499, 0xFFFFA04A, style = "racing", collection = GAMES),
        Wallpaper("jg_floresta_boreal", "Floresta Boreal", 999, 0xFF9FD0FF, style = "hunt", collection = GAMES),
        Wallpaper("jg_mata_esmeralda", "Mata Esmeralda", 999, 0xFF5CE89A, style = "hunt", collection = GAMES),
        Wallpaper("jg_distrito_holograma", "Distrito Holograma", 999, 0xFF2FE8FF, style = "cyber", collection = GAMES),
        Wallpaper("jg_por_do_sol_cibernetico", "Pôr do Sol Cibernético", 999, 0xFFFF9A3A, style = "cyber", collection = GAMES),
        Wallpaper("jg_lua_sangue", "Lua de Sangue", 999, 0xFFFF3A3A, style = "fantasy", collection = GAMES),
        Wallpaper("jg_torres_ocaso", "Torres do Ocaso", 999, 0xFFFFB04A, style = "fantasy", collection = GAMES),
        Wallpaper("jg_reino_rosa", "Reino Rosa", 999, 0xFFFFA0C8, style = "fantasy", collection = GAMES),
        Wallpaper("jg_retao_dourado", "Retão Dourado", 999, 0xFFFFD23F, style = "racing", collection = GAMES),
        Wallpaper("jg_inverno_selvagem", "Inverno Selvagem", 799, 0xFFFFC0D8, style = "hunt", collection = GAMES),
        Wallpaper("jg_clareira_dourada", "Clareira Dourada", 799, 0xFFFFD27A, style = "hunt", collection = GAMES),
        Wallpaper("jg_raios_meio_dia", "Raios do Meio-dia", 799, 0xFFE8F07A, style = "hunt", collection = GAMES),
        Wallpaper("jg_chuva_neon", "Chuva de Neon", 799, 0xFFFF3FA0, style = "cyber", collection = GAMES),
        Wallpaper("jg_cidade_ciano", "Cidade Ciano", 799, 0xFF3FFFF0, style = "cyber", collection = GAMES),
        Wallpaper("jg_reino_flutuante", "Reino Flutuante", 799, 0xFFC04AD8, style = "fantasy", collection = GAMES),
        Wallpaper("jg_ceu_arcano", "Céu Arcano", 799, 0xFF9A6AFF, style = "fantasy", collection = GAMES),
        Wallpaper("jg_noite_corrida", "Noite de Corrida", 799, 0xFFFF6A3A, style = "racing", collection = GAMES),
        Wallpaper("jg_alvorada_pista", "Alvorada na Pista", 799, 0xFFFF8AB0, style = "racing", collection = GAMES),
        Wallpaper("jg_linha_chegada", "Linha de Chegada", 799, 0xFFFF5A2A, style = "racing", collection = GAMES),
        Wallpaper("jg_trilha_cacador", "Trilha do Caçador", 599, 0xFFFF9A4A, style = "hunt", collection = GAMES),
        Wallpaper("jg_outono_rubro", "Outono Rubro", 599, 0xFFFF5A4A, style = "hunt", collection = GAMES),
        Wallpaper("jg_aurora_floresta", "Aurora na Floresta", 599, 0xFFE8A0FF, style = "hunt", collection = GAMES),
        Wallpaper("jg_setor_77", "Setor 77", 599, 0xFFFFB03A, style = "cyber", collection = GAMES),
        Wallpaper("jg_noite_carmesim", "Noite Carmesim", 599, 0xFFFF2A3F, style = "cyber", collection = GAMES),
        Wallpaper("jg_ceu_sintetico", "Céu Sintético", 599, 0xFFB07AFF, style = "cyber", collection = GAMES),
        Wallpaper("jg_ruinas_celestes", "Ruínas Celestes", 599, 0xFF5AE8FF, style = "fantasy", collection = GAMES),
        Wallpaper("jg_ilhas_crepusculo", "Ilhas do Crepúsculo", 599, 0xFFFF9A5A, style = "fantasy", collection = GAMES),
        Wallpaper("jg_santuario_esmeralda", "Santuário Esmeralda", 599, 0xFF3AFF9A, style = "fantasy", collection = GAMES),
        Wallpaper("jg_chuva_pista", "Chuva na Pista", 599, 0xFF7AB8FF, style = "racing", collection = GAMES),
        Wallpaper("jg_circuito_rubro", "Circuito Rubro", 599, 0xFFFF2A3F, style = "racing", collection = GAMES),
        Wallpaper("jg_viaduto_neon", "Viaduto Neon", 599, 0xFF3F8AFF, style = "racing", collection = GAMES),
        Wallpaper("jg_pantano_sombrio", "Pântano Sombrio", 499, 0xFFC8D85A, style = "hunt", collection = GAMES),
        Wallpaper("jg_crepusculo_selvagem", "Crepúsculo Selvagem", 499, 0xFFFF8A6A, style = "hunt", collection = GAMES),
        Wallpaper("jg_nuvem_dados", "Nuvem de Dados", 499, 0xFF5AB8FF, style = "cyber", collection = GAMES),
        Wallpaper("jg_mercado_noturno", "Mercado Noturno", 499, 0xFFFF7A3A, style = "cyber", collection = GAMES),
        Wallpaper("jg_avenida_laser", "Avenida Laser", 499, 0xFFFF3FD0, style = "cyber", collection = GAMES),
        Wallpaper("jg_arranha_ceus_rubros", "Arranha-céus Rubros", 499, 0xFFFF5A5A, style = "cyber", collection = GAMES),
        Wallpaper("jg_cidadela_glacial", "Cidadela Glacial", 499, 0xFFB8E8FF, style = "fantasy", collection = GAMES),
        Wallpaper("jg_vale_cristais", "Vale dos Cristais", 499, 0xFFFF7AF0, style = "fantasy", collection = GAMES),
        Wallpaper("jg_ruinas_aurora", "Ruínas da Aurora", 499, 0xFF5CFFC4, style = "fantasy", collection = GAMES),
        Wallpaper("jg_avenida_molhada", "Avenida Molhada", 499, 0xFFFFB03A, style = "racing", collection = GAMES),
        Wallpaper("jg_pista_esmeralda", "Pista Esmeralda", 499, 0xFF3AFF6A, style = "racing", collection = GAMES),
        Wallpaper("jg_contra_relogio", "Contra o Relógio", 499, 0xFFFFE03A, style = "racing", collection = GAMES),
        // ---- Mundos de Jogo: só nos planos (25 = 16 Pro + 6 Plus + 3 Go)
        Wallpaper("jg_bosque_nevoa", "Bosque da Névoa", 0, 0xFFC8E8B0, plans = setOf("plus", "pro"), style = "hunt", collection = GAMES),
        Wallpaper("jg_uivo_noite", "Uivo da Noite", 0, 0xFF8AB0FF, plans = setOf("pro"), style = "hunt", collection = GAMES),
        Wallpaper("jg_cervo_real", "Cervo Real", 0, 0xFFFFB050, plans = setOf("pro"), style = "hunt", collection = GAMES),
        Wallpaper("jg_floresta_ancestral", "Floresta Ancestral", 0, 0xFFE8C080, plans = setOf("pro"), style = "hunt", collection = GAMES),
        Wallpaper("jg_matriz_verde", "Matriz Verde", 0, 0xFF3AFF6A, plans = setOf("plus", "pro"), style = "cyber", collection = GAMES),
        Wallpaper("jg_cupula_holografica", "Cúpula Holográfica", 0, 0xFF7AFFE8, plans = setOf("pro"), style = "cyber", collection = GAMES),
        Wallpaper("jg_neon_gelado", "Neon Gelado", 0, 0xFFC8F0FF, plans = setOf("pro"), style = "cyber", collection = GAMES),
        Wallpaper("jg_horizonte_2199", "Horizonte 2199", 0, 0xFFFF6AD5, plans = setOf("pro"), style = "cyber", collection = GAMES),
        Wallpaper("jg_abismo_cosmico", "Abismo Cósmico", 0, 0xFF7A5AFF, plans = setOf("pro"), style = "fantasy", collection = GAMES),
        Wallpaper("jg_fortaleza_lunar", "Fortaleza Lunar", 0, 0xFFE0E8FF, plans = setOf("pro"), style = "fantasy", collection = GAMES),
        Wallpaper("jg_trono_nebular", "Trono Nebular", 0, 0xFFFF6A9A, plans = setOf("pro"), style = "fantasy", collection = GAMES),
        Wallpaper("jg_coroa_celestial", "Coroa Celestial", 0, 0xFFFFF0A0, plans = setOf("pro"), style = "fantasy", collection = GAMES),
        Wallpaper("jg_turbo_ciano", "Turbo Ciano", 0, 0xFF3FFFF0, plans = setOf("go", "pro"), style = "racing", collection = GAMES),
        Wallpaper("jg_estrada_violeta", "Estrada Violeta", 0, 0xFFB07AFF, plans = setOf("plus", "pro"), style = "racing", collection = GAMES),
        Wallpaper("jg_velocidade_maxima", "Velocidade Máxima", 0, 0xFFFF3FD0, plans = setOf("pro"), style = "racing", collection = GAMES),
        Wallpaper("jg_pista_gelada", "Pista Gelada", 0, 0xFFC8F0FF, plans = setOf("pro"), style = "racing", collection = GAMES),
        Wallpaper("jg_neblina_azul", "Neblina Azul", 0, 0xFF7AB8FF, plans = setOf("go", "plus"), style = "hunt", collection = GAMES),
        Wallpaper("jg_mata_fantasma", "Mata Fantasma", 0, 0xFFC8D0DC, plans = setOf("plus"), style = "hunt", collection = GAMES),
        Wallpaper("jg_distrito_violeta", "Distrito Violeta", 0, 0xFFD04AFF, plans = setOf("plus"), style = "cyber", collection = GAMES),
        Wallpaper("jg_arquipelago_astral", "Arquipélago Astral", 0, 0xFF8AD0FF, plans = setOf("plus"), style = "fantasy", collection = GAMES),
        Wallpaper("jg_ilhas_esquecidas", "Ilhas Esquecidas", 0, 0xFFA8B8C8, plans = setOf("plus"), style = "fantasy", collection = GAMES),
        Wallpaper("jg_curva_meia_noite", "Curva da Meia-Noite", 0, 0xFF5A8AFF, plans = setOf("go", "plus"), style = "racing", collection = GAMES),
        Wallpaper("jg_taiga_gelada", "Taiga Gelada", 0, 0xFF7FE0E0, plans = setOf("go"), style = "hunt", collection = GAMES),
        Wallpaper("jg_blecaute_neon", "Blecaute Neon", 0, 0xFF3FE0FF, plans = setOf("go"), style = "cyber", collection = GAMES),
        Wallpaper("jg_rota_noturna", "Rota Noturna", 0, 0xFF8AB0FF, plans = setOf("go"), style = "racing", collection = GAMES),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String?): Wallpaper? = id?.let { byId[it] }

    /** Faixas de preço vendidas na Play (um produto por faixa). */
    val tierProducts: List<String> get() = all.filter { !it.free && !it.pro }.map { it.productId }.distinct()

    /** Papel padrão do navegador (grátis). */
    const val DEFAULT_BROWSER = "noite_estrelada"
}
