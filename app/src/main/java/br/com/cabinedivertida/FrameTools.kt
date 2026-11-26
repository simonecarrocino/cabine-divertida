package br.com.cabinedivertida

import android.graphics.Bitmap

/** Ajustes automáticos nas artes de moldura enviadas pela usuária. */
object FrameTools {

    /** true se a imagem já tem partes transparentes (PNG com o meio vazado). */
    private fun hasTransparency(b: Bitmap): Boolean {
        if (!b.hasAlpha()) return false
        val stepX = maxOf(1, b.width / 64)
        val stepY = maxOf(1, b.height / 64)
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                if ((b.getPixel(x, y) ushr 24) < 250) return true
                x += stepX
            }
            y += stepY
        }
        return false
    }

    private fun isWindowGray(c: Int): Boolean {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        return mx - mn < 16 && mn > 165 && mx < 232
    }

    /**
     * Se a arte não tem transparência mas tem a janela em cinza liso (como os prompts pedem),
     * deixa essa janela transparente. Começa no centro e vai preenchendo o cinza ligado a ele.
     * Se não achar uma janela cinza grande, devolve a imagem como veio.
     */
    fun autoTransparentWindow(src: Bitmap): Bitmap {
        if (hasTransparency(src)) return src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        // Procura um ponto cinza perto do centro para começar
        var seed = -1
        val cx = w / 2
        val cy = h / 2
        loop@ for (r in 0..minOf(w, h) / 4 step 8) {
            for ((dx, dy) in listOf(0 to 0, r to 0, -r to 0, 0 to r, 0 to -r, r to r, -r to -r, r to -r, -r to r)) {
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until w && y in 0 until h && isWindowGray(px[y * w + x])) {
                    seed = y * w + x
                    break@loop
                }
            }
        }
        if (seed < 0) return src

        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        queue[tail++] = seed
        visited[seed] = true
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            if (x > 0) { val n = i - 1; if (!visited[n] && isWindowGray(px[n])) { visited[n] = true; queue[tail++] = n } }
            if (x < w - 1) { val n = i + 1; if (!visited[n] && isWindowGray(px[n])) { visited[n] = true; queue[tail++] = n } }
            if (y > 0) { val n = i - w; if (!visited[n] && isWindowGray(px[n])) { visited[n] = true; queue[tail++] = n } }
            if (y < h - 1) { val n = i + w; if (!visited[n] && isWindowGray(px[n])) { visited[n] = true; queue[tail++] = n } }
        }
        // Janela pequena demais: não é a área da foto, deixa como está
        if (tail < w * h / 20) return src

        for (k in 0 until tail) px[queue[k]] = 0
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }
}
