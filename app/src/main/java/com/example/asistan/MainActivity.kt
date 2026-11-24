package com.example.asistan

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = AiConfig(
            provider = AiProvider.DEEPSEEK,
            deepseekApiKey = "",
            chatgptApiKey = ""
        )
        val lim = Lim(config)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                lim.sendMessage("Merhaba")
            }
            result.onSuccess { reply ->
                // Yanıtı burada işleyin
            }.onFailure { error ->
                // Hatayı burada işleyin
            }
        }
    }
}
