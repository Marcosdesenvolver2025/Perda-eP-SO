# MusiBox

Player de músicas locais, central de downloads (pelo menu Compartilhar) e cofre privado criptografado, com login Google e sincronização no Firebase.

## Como o projeto está organizado

```
app/src/main/java/com/musibox/app/
├── core/        utilidades (formatação, permissões, internet, chaves de música)
├── data/        Room (banco local + migrations), DataStore (configurações), repositórios
├── media/       Media3/ExoPlayer, MediaSessionService (segundo plano, notificação, tela bloqueada)
├── download/    motor yt-dlp + ffmpeg, WorkManager, notificações de download
├── vault/       criptografia (Tink + Android Keystore), PIN, biometria, bloqueio, backup
├── storage/     gravação na galeria (MediaStore), espaço usado, limpeza de cache
├── sync/        login Google (Credential Manager + Firebase Auth) e sincronização Firestore
├── share/       ShareActivity: recebe links e mídias pelo Compartilhar
└── ui/          telas em Jetpack Compose (Material 3)
```

## Firebase (projeto do google-services.json)

1. **Authentication → Método de login → Google**: ativar.
2. **Firestore Database**: criar o banco.
3. **Regras do Firestore**: copiar o conteúdo de `firestore.rules` e publicar.
4. **Configurações do projeto → app Android → Impressões digitais**: adicionar o SHA-1 e o SHA-256 da chave que assina o APK
   (`keystore/musibox.jks`):
   - SHA-1: `0B:C5:01:F9:E2:2E:20:F5:A0:26:3F:62:57:6D:AD:8A:AF:05:45:F6`
   - SHA-256: `57:E0:4A:79:08:1D:EE:C1:B4:3A:38:CD:1E:5A:71:0A:2B:F1:F1:4D:88:6F:8E:92:5B:F7:89:D1:DC:E3:37:4F`

O `applicationId` é lido de `gradle.properties` (`musibox.applicationId`) e precisa ser igual ao `package_name`
do `app/google-services.json`. O Client ID usado no login é o do tipo **Web** (client_type 3) do mesmo arquivo,
lido automaticamente na compilação.

## Compilar

```
./gradlew assembleRelease
```

Os APKs ficam em `app/build/outputs/apk/release/` (um por arquitetura; celulares atuais usam `arm64-v8a`).
O GitHub Actions (`.github/workflows/build.yml`) compila, roda os testes unitários e testa as regras do Firestore
no emulador a cada envio para `main`.

## Chave de assinatura

A chave (`keystore/musibox.jks`) **não fica no repositório**. O GitHub Actions gera APKs sem assinatura e a
assinatura é feita fora do CI com a chave do dono. Guarde a chave em local seguro: atualizações do app precisam
ser assinadas com a mesma chave.

O `app/google-services.json` é a configuração pública do Firebase (a mesma que vai dentro do APK). Recomenda-se
restringir a chave de API no Google Cloud Console a apps Android com este package e SHA-1.
