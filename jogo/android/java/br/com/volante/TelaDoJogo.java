package br.com.volante;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * O aplicativo inteiro é uma tela só com um WebView, e o jogo roda dentro dele.
 *
 * O detalhe que decide se isto funciona ou não: o jogo usa módulos ES
 * (`import`), e o navegador RECUSA módulo carregado de `file://`. Então em vez
 * de apontar o WebView para `file:///android_asset/`, servimos os arquivos nós
 * mesmos, por baixo de um endereço `https://` de mentira, interceptando cada
 * pedido em {@link Almoxarifado}. Com uma origem https de verdade os módulos
 * carregam e o `localStorage` (onde fica o progresso) funciona direito.
 */
public class TelaDoJogo extends Activity {

  /** Não existe no ar: só este aplicativo responde por ele. */
  private static final String MAQUINA = "volante.local";
  private static final String RAIZ = "https://" + MAQUINA + "/";

  /** O fundo dos menus, para a primeira fração de segundo não ser branca. */
  private static final int COR_DE_FUNDO = Color.rgb(0x0e, 0x1d, 0x33);

  private WebView tela;

  @SuppressLint("SetJavaScriptEnabled")
  @Override
  protected void onCreate(Bundle estadoAnterior) {
    super.onCreate(estadoAnterior);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    tela = new WebView(this);
    tela.setBackgroundColor(COR_DE_FUNDO);

    WebSettings ajustes = tela.getSettings();
    ajustes.setJavaScriptEnabled(true);
    ajustes.setDomStorageEnabled(true);
    // O som é sintetizado na hora pelo WebAudio; sem isto ele nunca sai.
    ajustes.setMediaPlaybackRequiresUserGesture(false);
    // Nada de zoom: o dedo aqui é para girar o volante.
    ajustes.setSupportZoom(false);
    ajustes.setBuiltInZoomControls(false);
    ajustes.setDisplayZoomControls(false);
    // O jogo não lê arquivo nem vai à rede: fechamos as duas portas.
    ajustes.setAllowFileAccess(false);
    ajustes.setAllowContentAccess(false);
    ajustes.setCacheMode(WebSettings.LOAD_NO_CACHE);

    tela.setWebViewClient(new Almoxarifado(getAssets()));
    tela.setWebChromeClient(new WebChromeClient() {
      @Override
      public boolean onConsoleMessage(ConsoleMessage recado) {
        // Deixa o erro de JavaScript aparecer no logcat. É o que torna
        // possível alguém relatar um problema com alguma pista.
        android.util.Log.d("Volante", recado.message()
            + " (" + recado.sourceId() + ":" + recado.lineNumber() + ")");
        return true;
      }
    });

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      WebView.setWebContentsDebuggingEnabled(true);
    }

    setContentView(tela);
    tela.loadUrl(RAIZ + "index.html");
  }

  @Override
  public void onWindowFocusChanged(boolean comFoco) {
    super.onWindowFocusChanged(comFoco);
    if (comFoco) esconderBarras();
  }

  /** Tela cheia de verdade: sem barra de status e sem barra de navegação. */
  private void esconderBarras() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return;
    getWindow().getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (tela != null) tela.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (tela != null) tela.onResume();
    esconderBarras();
  }

  /**
   * O botão voltar pausa o jogo em vez de fechar o aplicativo. Só sai quando
   * já se está num menu — que é onde a pessoa espera que voltar signifique sair.
   */
  @Override
  public boolean onKeyDown(int codigo, KeyEvent evento) {
    if (codigo != KeyEvent.KEYCODE_BACK || tela == null) {
      return super.onKeyDown(codigo, evento);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      // Classe anônima, não lambda: o dexador usado na compilação não entende
      // `invokedynamic`, que é como o Java 8 implementa lambda.
      tela.evaluateJavascript(
          "(function(){try{"
              + "var j=window.VOLANTE;"
              + "if(j&&j.estado==='correndo'){j.pausar();return 'pausou';}"
              + "}catch(e){}return 'sair';})()",
          new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String valor) {
              if (valor == null || valor.contains("sair")) finish();
            }
          });
      return true;
    }
    finish();
    return true;
  }

  @Override
  protected void onDestroy() {
    if (tela != null) {
      tela.destroy();
      tela = null;
    }
    super.onDestroy();
  }

  // -------------------------------------------------------------------------

  /**
   * Entrega os arquivos do jogo, que moram em `assets/jogo/`, como se viessem
   * de um servidor.
   *
   * O tipo de conteúdo importa mais do que parece: o navegador recusa um módulo
   * ES que não chegue com tipo de JavaScript, e recusa calado. Um `.js`
   * entregue como `text/plain` derruba o jogo inteiro sem dizer por quê.
   */
  private static final class Almoxarifado extends WebViewClient {

    private static final String PASTA = "jogo";
    private final AssetManager assets;

    Almoxarifado(AssetManager assets) {
      this.assets = assets;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView tela, WebResourceRequest pedido) {
      Uri endereco = pedido.getUrl();
      if (endereco == null || !MAQUINA.equals(endereco.getHost())) return null;

      String caminho = endereco.getPath();
      if (caminho == null || caminho.isEmpty() || "/".equals(caminho)) caminho = "/index.html";
      // Sem "..": um caminho montado para fugir da pasta não sai dela.
      if (caminho.contains("..")) return naoEncontrado(caminho);

      String arquivo = PASTA + caminho;
      try {
        InputStream conteudo = assets.open(arquivo);
        Map<String, String> cabecalhos = new HashMap<>();
        cabecalhos.put("Cache-Control", "no-cache");
        return new WebResourceResponse(tipoDe(arquivo), "utf-8", 200, "OK", cabecalhos, conteudo);
      } catch (IOException naoTem) {
        return naoEncontrado(arquivo);
      }
    }

    private static WebResourceResponse naoEncontrado(String arquivo) {
      byte[] recado = ("nao achei " + arquivo).getBytes(Charset.forName("UTF-8"));
      return new WebResourceResponse("text/plain", "utf-8", 404, "Nao encontrado",
          new HashMap<String, String>(), new ByteArrayInputStream(recado));
    }

    private static String tipoDe(String arquivo) {
      String nome = arquivo.toLowerCase();
      if (nome.endsWith(".html")) return "text/html";
      if (nome.endsWith(".js") || nome.endsWith(".mjs")) return "text/javascript";
      if (nome.endsWith(".css")) return "text/css";
      if (nome.endsWith(".json") || nome.endsWith(".webmanifest")) return "application/json";
      if (nome.endsWith(".svg")) return "image/svg+xml";
      if (nome.endsWith(".png")) return "image/png";
      if (nome.endsWith(".jpg") || nome.endsWith(".jpeg")) return "image/jpeg";
      if (nome.endsWith(".woff2")) return "font/woff2";
      return "application/octet-stream";
    }
  }
}
