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
        
        // Lim sınıfı da eksik olduğu için bunu şimdilik devre dışı bırakıyorum
        // val lim = Lim(config) 

        Thread {
            // İsteği arka plan iş parçacığında çalıştırın
        }.start()
    }
}
