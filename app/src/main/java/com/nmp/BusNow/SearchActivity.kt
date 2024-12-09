package com.nmp.BusNow

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nmp.BusNow.classes.OnItemClickListener
import com.nmp.BusNow.classes.rvAdapter
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.nmp.BusNow.classes.Funciones
import com.nmp.BusNow.classes.rvViewHolder

class SearchActivity : AppCompatActivity() {

    private lateinit var back: ImageButton
    private lateinit var btnHome: CardView
    private lateinit var btnWork: CardView
    private lateinit var btnMore: CardView
    private lateinit var rvRecientes: RecyclerView
    private lateinit var rvSugerencias: RecyclerView
    private lateinit var editTextSearch: EditText
    private lateinit var placesClient: PlacesClient
    private lateinit var suggestionsContainer: CardView
    private lateinit var cardContainer: ConstraintLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_search)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initComponents()
        initListeners()
    }

    private fun initComponents() {
        // Inicializa el EditText y muestra el teclado automáticamente
        editTextSearch = findViewById(R.id.search)
        editTextSearch.requestFocus() // Enfoca el EditText
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(editTextSearch, InputMethodManager.SHOW_IMPLICIT)

        // Inicializa el boton de volver hacia atras
        back = findViewById(R.id.back)

        // Inicializa el contenedor de las tarjetas de atajos y recientes
        cardContainer = findViewById(R.id.cardContainer)

        // Inicializa botones de atajos de busqueda
        btnMore = findViewById(R.id.more)
        btnWork = findViewById(R.id.work)
        btnHome = findViewById(R.id.home)

        // Inicializa lista de busquedas recientes
        rvRecientes = findViewById(R.id.rvRecientes)
        val recientes = listOf("Pellegrini 157", "Monarca", "UNICEN", "Belgrano 321", "Olavarria", "Santamarina 500", "San Lorenzo 455", "España 266", "Ferreteria", "Grido")
        rvRecientes.adapter = rvAdapter(recientes, R.layout.item_recientes, object : OnItemClickListener {
            override fun onItemClickListener(item: String) {
                // Hacer algo al clickear el elemento
            }}) {view -> rvViewHolder(view) }
        rvRecientes.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        // Inicializa Google Places
        if (!Places.isInitialized()) { Places.initialize(applicationContext, getString(R.string.google_maps_key)) }
        placesClient = Places.createClient(this)

        // Inicializa el contenedor de sugerencias
        suggestionsContainer = findViewById(R.id.suggestionsContainer)
        rvSugerencias = findViewById(R.id.rvSugerencias)
    }

    private fun initListeners() {
        backListener()
        buttonsShortcutListener()
        searchListener()
    }

    private fun searchListener() {
        val context = this
        Funciones.searchListener(editTextSearch,suggestionsContainer,cardContainer,placesClient,
            rvSugerencias,context,object : OnItemClickListener {
                override fun onItemClickListener(item: String) {
                    val latLng = Funciones.getLatLngSuggestion(item)
                    val intent = Intent(this@SearchActivity, MapActivity::class.java).apply {
                        putExtra("latitude", latLng.latitude)
                        putExtra("longitude", latLng.longitude)
                        putExtra("address", item)
                    }
                    startActivity(intent)
                }}) {
            true
        }
    }

    private fun backListener() {
        back.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun buttonsShortcutListener() {
        btnMore.setOnClickListener {
            Log.i("print", "boton: \"Más\"")
        }
        btnWork.setOnClickListener {
            Log.i("print", "boton: \"Trabajo\"")
        }
        btnHome.setOnClickListener {
            Log.i("print", "boton: \"Casa\"")
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Captura el evento de toque y desabilita el teclado que fue enfocado al principio
        if (ev.action == MotionEvent.ACTION_DOWN) {
            // Obtén la vista que fue tocada
            val v = currentFocus
            if (v is EditText) {
                // Crea un rectángulo que contenga las coordenadas de la vista enfocada
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                // Si el toque no está dentro del rectángulo del EditText, oculta el teclado
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        // Procesa el toque normalmente
        return super.dispatchTouchEvent(ev)
    }
}