// Páginas pagas embutidas no código da função.
//
// Fica VAZIO no repositório de propósito: o caminho normal é o Netlify
// construir a partir do repositório, quando a pasta conteudo/ viaja junto e
// este mapa não é usado.
//
// `tools/empacotar.mjs` reescreve este arquivo ao montar o pacote pronto para
// arrastar no Netlify — nessa publicação não há etapa de build, então as
// páginas pagas precisam ir dentro do próprio código da função. Elas
// continuam saindo só pela função `pagina`, depois da conferência do crachá.
export const PAGINAS_EMBUTIDAS = {};
