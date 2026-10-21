# Launcher Simples 3.0 - tema rosa (Android TV)

Leve, sem anúncios, sem recomendações e sem bibliotecas externas.

## O que tem
- **Play Store sempre presente**: é o primeiro favorito e não dá para ocultar.
- **Tocando agora (Spotify e outros)**: capa, música, artista, anterior/tocar/próxima e botão Spotify.
- **Fundo editável**: degradês prontos, imagem sua (pasta Download/Fotos da TV) ou foto aleatória da internet, com escurecimento ajustável.
- **Edição dos apps**: segure OK em um app para fixar nos favoritos, mover, ocultar ou abrir as informações dele.
- **Recentes**, **Limpar memória**, atalhos de Wi-Fi/Bluetooth/Configurações, relógio e clima (Open-Meteo, grátis).
- Botão **Menu** do controle (ou o botão "Personalizar") abre as opções.

## Novidades da 3.2
- Nova seção **Continue assistindo**: capa, título, barra de progresso e "faltam X min" do que você assistiu em Netflix, YouTube, Prime etc. Clique abre o app (volta direto ao player se a sessão ainda estiver ativa). Segure OK para remover um item ou limpar a lista.
- Funciona com o mesmo acesso do "Tocando agora" (passo 3 abaixo): o serviço registra em segundo plano o que cada app de vídeo informa ao Android. Músicas (Spotify etc.) ficam de fora.
- Limite: só aparece o que o app publicar como sessão de mídia, e o Android não permite abrir o vídeo exato; o app abre e você continua de onde indica a barra. Itens com 95% assistidos saem da lista.
- Ordem e visibilidade em Personalizar > Seções (padrão: logo abaixo do Spotify).

## Novidades da 3.1
- Barra do Spotify completa: tempo decorrido/total, barra para pular para qualquer trecho (use esquerda/direita no controle, 5 s por toque), embaralhar, anterior, tocar/pausar, próxima e repetir.
- Chave de assinatura fixa (app/debug.keystore): a partir daqui, versões novas instalam por cima com `install -r`, sem perder as configurações.
- Embaralhar/repetir/pular dependem do que o Spotify libera para outros apps; o que não for liberado aparece apagado ou avisa.

## Novidades da 3.0
- Visual de linhas, parecido com o Android TV, sem recomendações de filmes.
- Tema rosa (4 tons de destaque). Os ícones e banners dos apps NÃO mudam de cor.
- Seções (Spotify, Atalhos, Favoritos, Abertos recentemente, Todos os apps): Personalizar > Seções para subir, descer, ocultar ou mostrar.
- Atalhos: Wi-Fi, Bluetooth, Limpar memória, Limpar cache, Configurações.
- Tamanho dos cartões e "Todos os apps" em linha ou grade.
- Limite do Android: o launcher não consegue ler "continuar assistindo" (títulos de filmes/séries) da Netflix/YouTube, então "Abertos recentemente" mostra os últimos apps abertos.
- Limpar cache de todos os apps também é bloqueado para apps comuns; o botão explica o caminho (Armazenamento da TV ou `adb shell pm trim-caches 999G`).

## ATENÇÃO ao atualizar de uma versão anterior
Cada build do GitHub usa uma assinatura diferente, então desinstale a versão antiga antes:
```
adb -s IP:PORTA uninstall com.tvlauncher.simples
```

## 1. Gerar o APK
1. Crie um repositório no github.com (pode ser privado).
2. Add file > Upload files: arraste TODO o conteúdo da pasta, inclusive a pasta oculta `.github` e o `.gitignore`.
3. Aba Actions: o build "Compilar APK" roda sozinho (3 a 6 min). Se não rodar, use Run workflow.
4. Baixe `launcher-simples-apk` no fim da página da execução. Dentro está o `app-debug.apk`.

## 2. Instalar na TV (use a porta atual da depuração sem fio)
```
adb connect IP:PORTA
adb -s IP:PORTA install -r app-debug.apk
adb -s IP:PORTA shell cmd package set-home-activity com.tvlauncher.simples/.MainActivity
```
Aperte Home no controle. Se abrir o launcher antigo, use o tvtool.py para desativar o original (com.google.android.tvlauncher). Para voltar: `py tvtool.py restaurar --tv IP:PORTA --pacote com.google.android.tvlauncher`.

## 3. Ligar o Spotify no launcher (uma vez)
```
adb -s IP:PORTA shell cmd notification allow_listener com.tvlauncher.simples/com.tvlauncher.simples.NowPlayingService
```
Instale o Spotify pela Play Store (pacote com.spotify.tv.android), abra e toque uma música.

## 4. Fundo com a sua foto
```
adb -s IP:PORTA push minha-foto.jpg /sdcard/Download/
```
Depois: Personalizar > Fundo: escolher imagem da TV. Aceite o pedido de permissão que aparecer.

## Observações
- Se a Play Store tiver sido desativada pelo tvtool, ela não aparece; restaure o pacote com.android.vending.
- Restaurar tudo: Personalizar > Voltar tudo ao padrão.
