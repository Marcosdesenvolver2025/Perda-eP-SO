package com.musibox.app.themes

import androidx.compose.ui.graphics.Color

/**
 * Papel de parede do MusiBox (imagens originais em assets/wallpapers).
 * price em centavos: 0 = grátis; pro = exclusivo do MusiBox Pró.
 */
data class Wallpaper(
    val id: String,
    val name: String,
    val price: Int,
    val accentArgb: Long,
    val pro: Boolean = false,
    val style: String = "",
    /** Coleção especial (ex.: "herois" = Heróis & Vilões); vazio = coleção principal. */
    val collection: String = "",
) {
    val accent: Color get() = Color(accentArgb)
    val free: Boolean get() = price == 0 && !pro
    /** Produto da Google Play da faixa de preço (ex.: "papel_099" = R$ 0,99). */
    val productId: String get() = "papel_" + price.toString().padStart(3, '0')
    val assetUri: String get() = "file:///android_asset/wallpapers/$id.webp"

    /** Preço da tabela (o preço real vem da Play Store quando disponível). */
    val priceLabel: String
        get() = when {
            pro -> "Pró"
            price == 0 -> "Grátis"
            else -> "R$ " + "%d,%02d".format(price / 100, price % 100)
        }
}

/** Gerado a partir do catálogo dos papéis de parede (110 itens: 60 da coleção principal + 50 de Heróis & Vilões). */
object Wallpapers {
    /** Coleção "Heróis & Vilões": temas originais com clima de super-herói (sem personagens de terceiros). */
    const val HEROES = "herois"

    val all: List<Wallpaper> = listOf(
        Wallpaper("nevoa_verde", "Névoa Verde", 0, 0xFF3DDC84, pro = false, style = "mountains"),
        Wallpaper("noite_estrelada", "Noite Estrelada", 0, 0xFF7AA7FF, pro = false, style = "mountains"),
        Wallpaper("degrade_aurora", "Degradê Aurora", 20, 0xFF7C5CFF, pro = false, style = "gradient"),
        Wallpaper("degrade_oceano", "Degradê Oceano", 20, 0xFF2EC4F1, pro = false, style = "gradient"),
        Wallpaper("degrade_por_do_sol", "Degradê Pôr do Sol", 20, 0xFFFF8A3D, pro = false, style = "gradient"),
        Wallpaper("degrade_menta", "Degradê Menta", 20, 0xFF26D07C, pro = false, style = "gradient"),
        Wallpaper("relevo_grafite", "Relevo Grafite", 20, 0xFFB0B8C4, pro = false, style = "topo"),
        Wallpaper("relevo_menta", "Relevo Menta", 20, 0xFF1FDF67, pro = false, style = "topo"),
        Wallpaper("linhas_violeta", "Linhas Violeta", 20, 0xFFB57BFF, pro = false, style = "flowlines"),
        Wallpaper("linhas_ciano", "Linhas Ciano", 20, 0xFF3FE0FF, pro = false, style = "flowlines"),
        Wallpaper("malha_neon", "Malha Neon", 50, 0xFFFF4FD8, pro = false, style = "blobs"),
        Wallpaper("malha_tropical", "Malha Tropical", 50, 0xFFFFB020, pro = false, style = "blobs"),
        Wallpaper("malha_glacial", "Malha Glacial", 50, 0xFF7FD4FF, pro = false, style = "blobs"),
        Wallpaper("malha_cereja", "Malha Cereja", 50, 0xFFFF5A7A, pro = false, style = "blobs"),
        Wallpaper("luzes_cidade", "Luzes da Cidade", 50, 0xFFFFC24D, pro = false, style = "bokeh"),
        Wallpaper("luzes_azuis", "Luzes Azuis", 50, 0xFF4DB8FF, pro = false, style = "bokeh"),
        Wallpaper("cristal_ametista", "Cristal Ametista", 50, 0xFFA56BFF, pro = false, style = "lowpoly"),
        Wallpaper("cristal_esmeralda", "Cristal Esmeralda", 50, 0xFF2BE38B, pro = false, style = "lowpoly"),
        Wallpaper("serra_rosa", "Serra Rosa", 99, 0xFFFF8FB1, pro = false, style = "mountains"),
        Wallpaper("vale_azul", "Vale Azul", 99, 0xFF6FB6FF, pro = false, style = "mountains"),
        Wallpaper("dourado_entardecer", "Entardecer Dourado", 99, 0xFFFFB547, pro = false, style = "mountains"),
        Wallpaper("dunas_ambar", "Dunas Âmbar", 99, 0xFFFFA94D, pro = false, style = "dunes"),
        Wallpaper("dunas_lunares", "Dunas Lunares", 99, 0xFF9DB4FF, pro = false, style = "dunes"),
        Wallpaper("floresta_neblina", "Floresta na Neblina", 99, 0xFF59C08A, pro = false, style = "forest"),
        Wallpaper("floresta_noturna", "Floresta Noturna", 99, 0xFF5E7BFF, pro = false, style = "forest"),
        Wallpaper("seda_grafite", "Seda Grafite", 99, 0xFFC9CED6, pro = false, style = "silk"),
        Wallpaper("seda_dourada", "Seda Dourada", 199, 0xFFFFC857, pro = false, style = "silk"),
        Wallpaper("seda_rose", "Seda Rosé", 199, 0xFFFF8FAB, pro = false, style = "silk"),
        Wallpaper("mar_dourado", "Mar Dourado", 199, 0xFFFFB347, pro = false, style = "ocean"),
        Wallpaper("mar_turquesa", "Mar Turquesa", 199, 0xFF2ED8C3, pro = false, style = "ocean"),
        Wallpaper("picos_nevados", "Picos Nevados", 199, 0xFF8EC9FF, pro = false, style = "mountains"),
        Wallpaper("montanhas_lilas", "Montanhas Lilás", 199, 0xFFC08BFF, pro = false, style = "mountains"),
        Wallpaper("lua_cheia", "Lua Cheia", 199, 0xFFC7D3FF, pro = false, style = "moon"),
        Wallpaper("nebulosa_rosa", "Nebulosa Rosa", 299, 0xFFFF5FC8, pro = false, style = "nebula"),
        Wallpaper("nebulosa_azul", "Nebulosa Azul", 299, 0xFF3FA9FF, pro = false, style = "nebula"),
        Wallpaper("aurora_boreal", "Aurora Boreal", 299, 0xFF2CF0A0, pro = false, style = "aurora"),
        Wallpaper("cidade_neon", "Cidade Neon", 299, 0xFFFF4FD8, pro = false, style = "city"),
        Wallpaper("planeta_aneis", "Planeta dos Anéis", 299, 0xFFFFC27A, pro = false, style = "planet"),
        Wallpaper("synthwave", "Synthwave", 299, 0xFFFF3FD0, pro = false, style = "synthwave"),
        Wallpaper("galaxia_violeta", "Galáxia Violeta", 499, 0xFFB56BFF, pro = false, style = "spiral"),
        Wallpaper("aurora_lago", "Aurora no Lago", 499, 0xFF46F2B4, pro = false, style = "aurora"),
        Wallpaper("synthwave_ciano", "Synthwave Ciano", 499, 0xFF2FF3FF, pro = false, style = "synthwave"),
        Wallpaper("cume_estrelado", "Cume Estrelado", 499, 0xFF8FA8FF, pro = false, style = "mountains"),
        Wallpaper("lua_mar", "Lua sobre o Mar", 499, 0xFFAFC6FF, pro = false, style = "moon"),
        Wallpaper("nebulosa_fenix", "Nebulosa Fênix", 799, 0xFFFF7A3D, pro = false, style = "nebula"),
        Wallpaper("planeta_gelo", "Planeta de Gelo", 799, 0xFF7FE3FF, pro = false, style = "planet"),
        Wallpaper("aurora_rosa", "Aurora Rosa", 799, 0xFFFF6BD6, pro = false, style = "aurora"),
        Wallpaper("metropole", "Metrópole 2099", 799, 0xFF2FF3FF, pro = false, style = "city"),
        Wallpaper("via_lactea", "Via Láctea", 999, 0xFFE7C6FF, pro = false, style = "milkyway"),
        Wallpaper("saturno_dourado", "Saturno Dourado", 999, 0xFFFFD27A, pro = false, style = "planet"),
        Wallpaper("portal_cosmico", "Portal Cósmico", 1499, 0xFF6BE4FF, pro = false, style = "spiral"),
        Wallpaper("supernova", "Supernova", 2000, 0xFFFFB05A, pro = false, style = "supernova"),
        Wallpaper("pro_eclipse", "Eclipse", 0, 0xFFFF9A3D, pro = true, style = "planet"),
        Wallpaper("pro_aurora_suprema", "Aurora Suprema", 0, 0xFF5CFFC4, pro = true, style = "aurora"),
        Wallpaper("pro_nebulosa_esmeralda", "Nebulosa Esmeralda", 0, 0xFF2CF08A, pro = true, style = "nebula"),
        Wallpaper("pro_synthwave_noir", "Synthwave Noir", 0, 0xFFFF3F6E, pro = true, style = "synthwave"),
        Wallpaper("pro_cidade_dourada", "Cidade Dourada", 0, 0xFFFFC24D, pro = true, style = "city"),
        Wallpaper("pro_seda_obsidiana", "Seda Obsidiana", 0, 0xFF7FA0FF, pro = true, style = "silk"),
        Wallpaper("pro_planeta_rubi", "Planeta Rubi", 0, 0xFFFF4D6D, pro = true, style = "planet"),
        Wallpaper("pro_cume_aurora", "Cume da Aurora", 0, 0xFF5CFFC4, pro = true, style = "mountains"),
        // ---- Heróis & Vilões (50)
        Wallpaper("hv_furia_tempestade", "Fúria da Tempestade", 2000, 0xFF7FD4FF, style = "storm", collection = HEROES),
        Wallpaper("hv_fenda_dimensional", "Fenda Dimensional", 2000, 0xFFC77DFF, style = "rift", collection = HEROES),
        Wallpaper("hv_trovoada_violeta", "Trovoada Violeta", 1499, 0xFFB57BFF, style = "storm", collection = HEROES),
        Wallpaper("hv_escudo_energia", "Escudo de Energia", 1499, 0xFF3FE0FF, style = "hexshield", collection = HEROES),
        Wallpaper("hv_amanhecer_heroi", "Amanhecer do Herói", 1499, 0xFFFFB347, style = "dawncity", collection = HEROES),
        Wallpaper("hv_portal_rubro", "Portal Rubro", 999, 0xFFFF4D5E, style = "rift", collection = HEROES),
        Wallpaper("hv_inferno_rubro", "Inferno Rubro", 999, 0xFFFF6A2A, style = "inferno", collection = HEROES),
        Wallpaper("hv_impacto_sismico", "Impacto Sísmico", 999, 0xFFFF7A2A, style = "shatter", collection = HEROES),
        Wallpaper("hv_nucleo_plasma", "Núcleo de Plasma", 999, 0xFF6BE4FF, style = "plasma", collection = HEROES),
        Wallpaper("hv_vortice_caos", "Vórtice do Caos", 999, 0xFF5CFF8A, style = "vortex", collection = HEROES),
        Wallpaper("hv_tempestade_eletrica", "Tempestade Elétrica", 799, 0xFF6FB6FF, style = "storm", collection = HEROES),
        Wallpaper("hv_rasgo_esmeralda", "Rasgo Esmeralda", 799, 0xFF3DFF9A, style = "rift", collection = HEROES),
        Wallpaper("hv_fenda_celeste", "Fenda Celeste", 799, 0xFF7FE3FF, style = "rift", collection = HEROES),
        Wallpaper("hv_orbe_sombrio", "Orbe Sombrio", 799, 0xFFC77DFF, style = "plasma", collection = HEROES),
        Wallpaper("hv_cidade_alerta", "Cidade em Alerta", 799, 0xFFFF4D3D, style = "dawncity", collection = HEROES),
        Wallpaper("hv_olho_tormenta", "Olho da Tormenta", 799, 0xFFB57BFF, style = "vortex", collection = HEROES),
        Wallpaper("hv_eclipse_rubro", "Eclipse Rubro", 799, 0xFFFF3B3B, style = "planet", collection = HEROES),
        Wallpaper("hv_raio_escarlate", "Raio Escarlate", 499, 0xFFFF4D5E, style = "storm", collection = HEROES),
        Wallpaper("hv_explosao_pop", "Explosão Pop", 499, 0xFFFFD23F, style = "comic", collection = HEROES),
        Wallpaper("hv_velocidade_rubra", "Velocidade Rubra", 499, 0xFFFF4D3D, style = "speed", collection = HEROES),
        Wallpaper("hv_escudo_dourado", "Escudo Dourado", 499, 0xFFFFC857, style = "hexshield", collection = HEROES),
        Wallpaper("hv_barreira_rubra", "Barreira Rubra", 499, 0xFFFF4D5E, style = "hexshield", collection = HEROES),
        Wallpaper("hv_chama_esmeralda", "Chama Esmeralda", 499, 0xFF3DFF7A, style = "inferno", collection = HEROES),
        Wallpaper("hv_terra_partida_azul", "Terra Partida Azul", 499, 0xFF4DC8FF, style = "shatter", collection = HEROES),
        Wallpaper("hv_esfera_solar", "Esfera Solar", 499, 0xFFFFC24D, style = "plasma", collection = HEROES),
        Wallpaper("hv_circuito_tatico", "Circuito Tático", 499, 0xFF3FE0FF, style = "circuit", collection = HEROES),
        Wallpaper("hv_vigilia_noturna", "Vigília Noturna", 499, 0xFF7AA7FF, style = "dawncity", collection = HEROES),
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
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String?): Wallpaper? = id?.let { byId[it] }

    /** Faixas de preço vendidas na Play (um produto por faixa). */
    val tierProducts: List<String> get() = all.filter { !it.free && !it.pro }.map { it.productId }.distinct()

    /** Papel padrão do navegador (grátis). */
    const val DEFAULT_BROWSER = "noite_estrelada"
}
