/**
 * Escolha de fotos, com queda suave no navegador.
 *
 * No aparelho isso abre a galeria ou a câmera de verdade. No navegador o
 * `expo-image-picker` vira um seletor de arquivo, e a câmera pode simplesmente
 * não existir (depende do navegador e de a página estar em HTTPS). Em vez de
 * quebrar a tela, devolvemos um aviso e a pessoa segue o fluxo sem a foto.
 *
 * O upload para o storage continua pendente nos dois casos — ver os `TODO`
 * em NovoAnuncio.tsx e PassoDaEntrega.tsx.
 */

import { Platform } from 'react-native';
import * as ImagePicker from 'expo-image-picker';

export interface ResultadoDeFoto {
  uris: string[];
  /** Preenchido quando não deu para usar câmera/galeria. */
  aviso?: string;
}

const AVISO_NAVEGADOR =
  'No navegador o app abre o seletor de arquivos em vez da câmera. Na versão instalada no celular isso usa a câmera de verdade.';

export async function escolherDaGaleria(quantidade: number): Promise<ResultadoDeFoto> {
  try {
    const permissao = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permissao.granted) {
      return { uris: [], aviso: 'Precisamos da sua permissão para acessar as fotos.' };
    }

    const resultado = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.Images,
      allowsMultipleSelection: true,
      selectionLimit: quantidade,
      quality: 0.7,
    });

    if (resultado.canceled) return { uris: [] };
    return { uris: resultado.assets.map((a) => a.uri) };
  } catch {
    return {
      uris: [],
      aviso:
        Platform.OS === 'web'
          ? `Este navegador não deixou abrir a galeria. ${AVISO_NAVEGADOR}`
          : 'Não conseguimos abrir suas fotos. Tente de novo.',
    };
  }
}

export async function tirarFotoAgora(): Promise<ResultadoDeFoto> {
  try {
    const permissao = await ImagePicker.requestCameraPermissionsAsync();
    if (!permissao.granted) {
      return { uris: [], aviso: 'Precisamos da câmera para registrar a foto.' };
    }

    const resultado = await ImagePicker.launchCameraAsync({ quality: 0.6 });
    if (resultado.canceled) return { uris: [] };

    const uri = resultado.assets[0]?.uri;
    return uri ? { uris: [uri] } : { uris: [] };
  } catch {
    return {
      uris: [],
      aviso:
        Platform.OS === 'web'
          ? `A câmera não está disponível nesta página. ${AVISO_NAVEGADOR} Você pode seguir sem a foto para ver o resto do fluxo.`
          : 'Não conseguimos abrir a câmera. Tente de novo.',
    };
  }
}
