package br.com.cabinedivertida

/**
 * Prompts prontos para pedir uma moldura a uma IA de imagem (Gemini e outras),
 * com as medidas e as áreas livres de cada formato.
 */
object FramePrompts {

    private class Spec(
        val formatName: String,
        val size: String,
        val ratio: String,
        val windowX: String,
        val windowY: String,
        val windowSize: String,
        val borders: String,
        val reserved: String,
        val badgeArea: String,
        val badgeMax: String,
    )

    private fun spec(f: OutputFormat): Spec = when (f) {
        OutputFormat.REELS -> Spec(
            formatName = "Reels do Instagram",
            size = "1080 x 1920 pixels",
            ratio = "9:16, vertical",
            windowX = "de 100 a 980 px",
            windowY = "de 180 a 1250 px",
            windowSize = "cerca de 880 x 1070 px",
            borders = "Laterais: cerca de 100 px de cada lado. Topo: cerca de 180 px. Base: faixa decorativa mais larga, de 1250 até 1920 px (cerca de 670 px).",
            reserved = "- Topo: de 0 a 270 px (barra de status e título do Reels). Só decoração aqui.\n" +
                "- Base: de 1250 a 1920 px (legenda e nome da conta). Só decoração aqui: flores, folhagens, enfeites.\n" +
                "- Lateral direita: os últimos 120 px (botões de curtir, comentar e compartilhar). Nada importante ali.",
            badgeArea = "centralizado na horizontal, entre 1050 e 1250 px na vertical, sobreposto à parte de baixo da janela da foto, logo acima da faixa decorativa",
            badgeMax = "no máximo 420 px de largura e 220 px de altura",
        )
        OutputFormat.STORY -> Spec(
            formatName = "Story do Instagram",
            size = "1080 x 1920 pixels",
            ratio = "9:16, vertical",
            windowX = "de 100 a 980 px",
            windowY = "de 180 a 1420 px",
            windowSize = "cerca de 880 x 1240 px",
            borders = "Laterais: cerca de 100 px de cada lado. Topo: cerca de 180 px. Base: faixa decorativa de 1420 até 1920 px (cerca de 500 px).",
            reserved = "- Topo: de 0 a 250 px (barra de progresso e nome da conta). Só decoração aqui.\n" +
                "- Base: de 1600 a 1920 px (campo de responder e enviar mensagem). Só decoração aqui.",
            badgeArea = "centralizado na horizontal, entre 1420 e 1600 px na vertical, logo abaixo da janela da foto",
            badgeMax = "no máximo 600 px de largura e 180 px de altura",
        )
        OutputFormat.POST -> Spec(
            formatName = "post do feed do Instagram",
            size = "1080 x 1350 pixels",
            ratio = "4:5, vertical",
            windowX = "de 90 a 990 px",
            windowY = "de 90 a 1050 px",
            windowSize = "cerca de 900 x 960 px",
            borders = "Laterais: cerca de 90 px de cada lado. Topo: cerca de 90 px. Base: faixa mais larga para o nome do evento, de 1050 até 1350 px (cerca de 300 px).",
            reserved = "- Na grade do perfil a imagem aparece cortada em 3:4, perdendo cerca de 35 px de cada lado. Não coloque texto nem detalhes importantes a menos de 70 px das bordas laterais.",
            badgeArea = "centralizado na faixa de baixo, entre 1080 e 1320 px na vertical",
            badgeMax = "no máximo 600 px de largura e 230 px de altura",
        )
        OutputFormat.QUADRADO -> Spec(
            formatName = "post quadrado do Instagram",
            size = "1080 x 1080 pixels",
            ratio = "1:1, quadrado",
            windowX = "de 90 a 990 px",
            windowY = "de 90 a 800 px",
            windowSize = "cerca de 900 x 710 px",
            borders = "Laterais: cerca de 90 px de cada lado. Topo: cerca de 90 px. Base: faixa para o nome do evento, de 800 até 1080 px (cerca de 280 px).",
            reserved = "- Na grade do perfil a imagem aparece cortada em 3:4, perdendo cerca de 135 px de cada lado. Mantenha o nome do evento e os detalhes importantes entre 150 e 930 px na horizontal.",
            badgeArea = "centralizado na faixa de baixo, entre 820 e 1050 px na vertical",
            badgeMax = "no máximo 600 px de largura e 220 px de altura",
        )
        OutputFormat.F10X15 -> Spec(
            formatName = "foto impressa 10x15 cm",
            size = "1200 x 1800 pixels (300 dpi)",
            ratio = "2:3, vertical",
            windowX = "de 100 a 1100 px",
            windowY = "de 100 a 1400 px",
            windowSize = "cerca de 1000 x 1300 px",
            borders = "Laterais: cerca de 100 px de cada lado. Topo: cerca de 100 px. Base: faixa para o nome do evento, de 1400 até 1800 px (cerca de 400 px).",
            reserved = "- Impressão sem borda pode cortar 2 a 3 mm de cada lado. Não coloque texto nem detalhes importantes a menos de 60 px das bordas.",
            badgeArea = "centralizado na faixa de baixo, entre 1440 e 1760 px na vertical",
            badgeMax = "no máximo 700 px de largura e 300 px de altura",
        )
    }

    fun build(format: OutputFormat, eventName: String, theme: String, colors: String): String {
        val s = spec(format)
        val name = eventName.trim().ifEmpty { "[NOME DO EVENTO]" }
        val tema = theme.trim().ifEmpty { "[TEMA, ex.: Natal artesanal com flores de feltro, pinhas, galhos de pinheiro, azevinho e estrelas douradas]" }
        val cores = colors.trim().ifEmpty { "[CORES, ex.: vermelho bordô, verde-pinheiro, dourado e creme]" }
        return """
Crie uma moldura decorativa para fotos de evento, no formato ${s.formatName}.

FORMATO
- Proporção ${s.ratio}, ${s.size}.
- A imagem é uma moldura: o centro será substituído por uma foto de pessoas.

JANELA DA FOTO (área central)
- Um retângulo central preenchido com CINZA LISO E UNIFORME, cor exata #C8C8C8, sem textura, sem gradiente, sem sombra, sem brilho e sem neve ou enfeites por dentro.
- Posição aproximada: ${s.windowX} na horizontal e ${s.windowY} na vertical (${s.windowSize}).
- A borda entre o cinza e a moldura deve ser nítida e bem definida.
- Enfeites podem invadir levemente os cantos da janela, mas no máximo 60 px para dentro, e nunca no centro (é onde ficam os rostos).

LARGURA DA MOLDURA
- ${s.borders}

ÁREAS RESERVADAS (não colocar texto nem logo nelas)
${s.reserved}

NOME DO EVENTO
- Um selo ou faixa com o texto "$name", ${s.badgeArea}.
- Tamanho do selo: ${s.badgeMax}.
- Texto grande, legível, em letras arredondadas e amigáveis.

ESTILO
- Tema: $tema.
- Cores: $cores.
- Visual de papelaria artesanal, com textura de papel e tecido na moldura (não na janela cinza).
- Sem pessoas, sem marca d'água e sem texto além do nome do evento.
""".trim()
    }
}
