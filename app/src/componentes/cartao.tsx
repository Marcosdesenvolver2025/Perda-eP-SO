/**
 * Formulário de cartão de crédito.
 *
 * Os dados ficam só neste componente e vão direto para a pagar.me na hora de
 * tokenizar (ver `src/pagamento/cartao.ts`). Nada disso é enviado para a
 * nossa API nem guardado no aparelho.
 */

import React, { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';

import { cores, espaco, fonte, raio } from '../tema';
import { reais } from '../util/formato';
import { Campo } from './base';
import {
  apenasDigitos,
  formatarNumero,
  formatarValidade,
  type DadosDoCartao,
} from '../pagamento/cartao';

export function FormularioDeCartao({
  valor,
  aoMudar,
  total,
  parcelas,
  aoMudarParcelas,
}: {
  valor: DadosDoCartao | null;
  aoMudar: (dados: DadosDoCartao) => void;
  total: number;
  parcelas: number;
  aoMudarParcelas: (n: number) => void;
}) {
  const [numero, setNumero] = useState(valor?.numero ?? '');
  const [titular, setTitular] = useState(valor?.titular ?? '');
  const [validade, setValidade] = useState(
    valor ? `${valor.validadeMes}/${valor.validadeAno}` : '',
  );
  const [cvv, setCvv] = useState(valor?.cvv ?? '');

  function propagar(campos: Partial<DadosDoCartao>) {
    const [mes = '', ano = ''] = validade.split('/');
    aoMudar({
      numero,
      titular,
      validadeMes: mes,
      validadeAno: ano,
      cvv,
      ...campos,
    });
  }

  // no máximo 12x, e nunca com parcela abaixo de R$ 5,00
  const maxParcelas = Math.max(1, Math.min(12, Math.floor(total / 500)));
  const opcoes = Array.from({ length: maxParcelas }, (_, i) => i + 1);

  return (
    <View>
      <Campo
        rotulo="número do cartão"
        valor={formatarNumero(numero)}
        aoMudar={(v) => {
          const limpo = apenasDigitos(v).slice(0, 16);
          setNumero(limpo);
          propagar({ numero: limpo });
        }}
        dica="0000 0000 0000 0000"
        teclado="numeric"
      />
      <Campo
        rotulo="nome impresso no cartão"
        valor={titular}
        aoMudar={(v) => {
          setTitular(v);
          propagar({ titular: v });
        }}
        dica="como está no cartão"
      />

      <View style={{ flexDirection: 'row', gap: espaco.md }}>
        <View style={{ flex: 1 }}>
          <Campo
            rotulo="validade"
            valor={formatarValidade(validade)}
            aoMudar={(v) => {
              const formatado = formatarValidade(v);
              setValidade(formatado);
              const [mes = '', ano = ''] = formatado.split('/');
              propagar({ validadeMes: mes, validadeAno: ano });
            }}
            dica="MM/AA"
            teclado="numeric"
          />
        </View>
        <View style={{ flex: 1 }}>
          <Campo
            rotulo="cvv"
            valor={cvv}
            aoMudar={(v) => {
              const limpo = apenasDigitos(v).slice(0, 4);
              setCvv(limpo);
              propagar({ cvv: limpo });
            }}
            dica="123"
            teclado="numeric"
            maxLength={4}
          />
        </View>
      </View>

      <Text style={[fonte.rotulo, { marginBottom: espaco.sm }]}>parcelas</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: espaco.sm }}>
        {opcoes.map((n) => (
          <Pressable
            key={n}
            onPress={() => aoMudarParcelas(n)}
            accessibilityRole="button"
            accessibilityState={{ selected: parcelas === n }}
            style={{
              paddingHorizontal: espaco.lg,
              paddingVertical: espaco.md,
              borderRadius: raio.md,
              borderWidth: 1.5,
              borderColor: parcelas === n ? cores.verde : cores.borda,
              backgroundColor: parcelas === n ? cores.verdeClaro : cores.branco,
            }}
          >
            <Text style={{ fontWeight: '700', fontSize: 13, color: cores.texto }}>
              {n}x {reais(Math.floor(total / n))}
            </Text>
            <Text style={fonte.pequeno}>{n === 1 ? 'à vista' : 'sem juros'}</Text>
          </Pressable>
        ))}
      </ScrollView>

      <Text style={[fonte.pequeno, { marginTop: espaco.md }]}>
        seus dados de cartão vão direto para a pagar.me. a gente não guarda nada disso.
      </Text>
    </View>
  );
}
