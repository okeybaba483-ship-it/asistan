package com.example.asistan

import android.app.Activity
import android.os.Bundle

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = AiConfig(
            provider = AiProvider.DEEPSEEK,
            deepseekApiKey = "",
            chatgptApiKey = ""
        )
        val lim = Lim(config)

        Thread {
            // İsteği arka plan iş parçacığında çalıştırın
        }.start()
    }
}
