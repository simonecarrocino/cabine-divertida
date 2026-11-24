# Cabine Divertida

App Android para tirar foto, colocar moldura, enviar pelo WhatsApp ou imprimir no tamanho escolhido. Tem também um **Modo Evento**, em que a organizadora define a moldura e o tamanho do papel uma vez e os convidados só tiram a foto.

## Como instalar no celular

1. Abra a página **Releases** deste repositório no celular Android.
2. Baixe o arquivo `cabine-divertida-v0.1.X.apk` da versão mais recente.
3. Toque no arquivo baixado. Se o Android pedir, permita "instalar apps desta fonte".

Cada alteração enviada para a branch `main` gera uma versão nova automaticamente (aba **Actions**). Para atualizar, é só instalar o APK novo por cima.

## Tecnologias

- Kotlin + Jetpack Compose (telas)
- CameraX (câmera)
- Framework de impressão do Android (tamanhos 10×15, 13×18, 15×21, 20×25, 20×30 e A4)
- Compartilhamento do Android para o WhatsApp
- GitHub Actions para gerar o APK

## Assinatura

O APK é assinado com a chave da Cabine Divertida, que **não fica no repositório**.
No GitHub ela vem de dois segredos (Settings › Secrets and variables › Actions):

- `KEYSTORE_BASE64`: o arquivo da chave em base64
- `KEYSTORE_PASSWORD`: a senha da chave

Guarde uma cópia do arquivo `cabine.keystore` em lugar seguro: sem ela não é possível publicar atualizações que instalem por cima do app.
