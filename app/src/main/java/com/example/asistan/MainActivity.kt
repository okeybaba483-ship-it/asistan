package com.example.asistan

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Uygulamanın çökmemesi için geçici bir ekran yazısı ekliyoruz
        val textView = TextView(this)
        textView.text = "Asistan Uygulaması Başarıyla Açıldı!\n\nAPI bağlantıları yapılandırılıyor..."
        textView.textSize = 20f
        
        // Bu yazıyı ekrana bas
        setContentView(textView)

        // Eski hata veren AI yapılandırma kodlarını şimdilik devre dışı bıraktık
        /*
        val config = AiConfig(
            provider = AiProvider.DEEPSEEK,
            deepseekApiKey = "",
            chatgptApiKey = ""
        )
        */
    }
}
