module.exports = function (api) {
  api.cache(true);
  return {
    // babel-preset-expo (v12, SDK 52) ja inclui o plugin do
    // react-native-reanimated automaticamente quando a lib esta instalada.
    presets: ['babel-preset-expo'],
  };
};
