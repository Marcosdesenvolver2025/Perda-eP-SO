# MusiBox — regras do R8 (encolhe o app sem renomear classes, para logs legíveis)
-dontobfuscate
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable

# Tink (criptografia do cofre)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# Login Google (Credential Manager)
-keep class androidx.credentials.playservices.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

# Firestore: classes de dados lidas por reflexão (o app usa mapas, mas por segurança)
-keepclassmembers class com.musibox.app.** { <init>(...); }

# Room / WorkManager
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
