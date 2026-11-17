# FotoAuto — correção automática de fotos (Android 10+)

Toda foto nova tirada com a câmera (pasta DCIM) é corrigida sozinha, em segundo plano:
rotação EXIF, balanço de branco, contraste (níveis automáticos), brilho (gama) e leve realce de cor.
A original é mantida; a corrigida é salva em **Imagens/FotoAuto** com o sufixo `_corrigida`.

## Como instalar
1. No GitHub: aba **Actions** → "Build FotoAuto APK" → última execução → baixe o artefato **FotoAuto-apk**.
2. Descompacte e envie `app-debug.apk` para o celular; abra e permita "instalar apps desconhecidos".
3. Abra o FotoAuto → **Ativar correção automática** → permita acesso a **todas** as fotos e notificações.
4. Tire fotos normalmente. Uma notificação fixa indica que está ativo (exigência do Android).

Dica: em alguns aparelhos (Xiaomi, Samsung), desative a otimização de bateria do FotoAuto para ele não ser fechado.
Volta a funcionar sozinho após reiniciar o celular.
