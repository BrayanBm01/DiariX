package com.example.totaldiaria

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.adapter.PapeleraAdapter
import com.example.totaldiaria.database.PapeleraRepository
import com.example.totaldiaria.navigation.BottomNavigator

class PapeleraActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_papelera)

        window.statusBarColor =
            ContextCompat.getColor(this, android.R.color.white)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        val recycler =
            findViewById<RecyclerView>(R.id.recyclerPapelera)

        val papeleraRepository =
            PapeleraRepository(this)

        recycler.layoutManager =
            LinearLayoutManager(this)

        recycler.adapter =
            PapeleraAdapter(
                papeleraRepository.obtenerPapelera()
            )

        BottomNavigator.configurar(this, R.id.nav_papelera)
    }
}
