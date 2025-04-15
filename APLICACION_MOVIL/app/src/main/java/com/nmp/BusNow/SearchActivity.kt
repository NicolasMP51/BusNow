package com.nmp.BusNow

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.maps.model.LatLng
import com.nmp.BusNow.classes.OnItemClickListener
import com.nmp.BusNow.classes.rvAdapter
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.auth.FirebaseAuth
import com.nmp.BusNow.classes.Funciones
import com.nmp.BusNow.classes.rvViewHolder
import org.json.JSONObject

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
        getBusquedas()

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
                    guardarBusqueda(item, latLng)
                    goToMapActivity(item, latLng)
                }}) {
            true
        }
    }

    private fun goToMapActivity(direccion: String, latLng: LatLng) {
        val intent = Intent(this@SearchActivity, MapActivity::class.java).apply {
            putExtra("latitude", latLng.latitude)
            putExtra("longitude", latLng.longitude)
            putExtra("address", direccion)
        }
        startActivity(intent)
    }

    private fun backListener() {
        back.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
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

    private fun guardarBusqueda(direccion: String, latLng: LatLng) {
        val url = "http://10.0.2.2:8000/guardar_busqueda"
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val jsonBody = JSONObject().apply {
            put("user_id", userId)
            put("direccion", direccion)
            put("lat", latLng.latitude)
            put("lng", latLng.longitude)
        }

        val request = JsonObjectRequest(
            Request.Method.POST, url, jsonBody,
            { response ->
                Log.i("print", "Respuesta: ${response.getString("mensaje")}")
            },
            { error ->
                Log.e("print", "Error: ${error.message}")
            })

        Volley.newRequestQueue(this).add(request)
    }

    private fun getBusquedas() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val url = "http://10.0.2.2:8000/get_busquedas/$userId"
        val busquedasListString = mutableListOf<String>()
        val busquedasListLatLng = mutableListOf<LatLng>()

        val request = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                try {
                    if (response.has("error")) {
                        val errorMessage = response.getString("error")
                        Log.e("print", "Error del servidor: $errorMessage")
                    } else if (response.has("busquedas")) {
                        val busquedasArray = response.getJSONArray("busquedas")

                        for (i in 0 until busquedasArray.length()) {
                            val s = busquedasArray.getJSONObject(i).getString("direccion")
                            val lat = busquedasArray.getJSONObject(i).getDouble("lat")
                            val lng = busquedasArray.getJSONObject(i).getDouble("lng")
                            busquedasListString.add(s)
                            busquedasListLatLng.add(LatLng(lat,lng))
                        }

                        rvRecientes.adapter = rvAdapter(busquedasListString, R.layout.item_recientes, object : OnItemClickListener {
                            override fun onItemClickListener(item: String) {
                                // Al clickear el elemento
                                val i = busquedasListString.indexOf(item)
                                val latLng = busquedasListLatLng[i]
                                goToMapActivity(item, latLng)
                            }}) {view -> rvViewHolder(view) }
                        rvRecientes.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)


                        Log.i("print", "Búsquedas recientes: $busquedasListString")
                    } else {
                        Log.e("print", "Respuesta inesperada del servidor")
                    }

                } catch (e: Exception) {
                    Log.e("print", "Error procesando la respuesta: ${e.message}")
                }
            },
            { error ->
                Log.e("print", "Error: ${error.message}")
            })

        Volley.newRequestQueue(this).add(request)
    }

    private fun buttonsShortcutListener() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_more, null)
        val listView = dialogView.findViewById<ListView>(R.id.listViewMore) // Lista para direcciones extra
        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Más Direcciones Favoritas")

        val alertDialog = builder.create()

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val url = "http://10.0.2.2:8000/get_favoritos/$userId"

        val request = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                try {
                    if (response.has("error")) {
                        val errorMessage = response.getString("error")
                        Log.e("print", "Error del servidor: $errorMessage")
                    } else if (response.has("favoritos")) {
                        val favoritos = response.getJSONObject("favoritos")
                        val direccionesExtras = mutableListOf<Pair<String, LatLng>>() // Lista para botón "Más"

                        // Configurar botones de Casa y Trabajo
                        configurarBoton(btnHome, favoritos,"Casa")
                        configurarBoton(btnWork, favoritos,"Trabajo")

                        // Agregar direcciones extra a la lista
                        for (key in favoritos.keys()) {
                            if (key != "Casa" && key != "Trabajo") {
                                val favorito = favoritos.getJSONObject(key)
                                val direccion = favorito.getString("direccion")
                                val lat = favorito.getDouble("lat")
                                val lng = favorito.getDouble("lng")

                                direccionesExtras.add(Pair("$key - $direccion", LatLng(lat, lng)))
                            }
                        }

                        // Configurar el botón "Más"
                        if (direccionesExtras.isNotEmpty()) {
                            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, direccionesExtras.map { it.first })
                            listView.adapter = adapter
                            listView.setOnItemClickListener { _, _, position, _ ->
                                val (direccion, latLng) = direccionesExtras[position]
                                goToMapActivity(direccion.split(" - ")[1], latLng)
                                alertDialog.dismiss()
                            }
                            btnMore.setOnClickListener { alertDialog.show() }
                        } else {
                            btnMore.setOnClickListener {
                                Toast.makeText(this, "No hay direcciones adicionales", Toast.LENGTH_SHORT).show()
                            }
                        }

                        Log.i("print", "Respuesta del servidor: ${favoritos}")
                    } else {
                        Log.e("print", "Respuesta inesperada del servidor")
                    }
                } catch (e: Exception) {
                    Log.e("print", "Error procesando la respuesta: ${e.message}")
                }
            },
            { error ->
                Log.e("print", "Error: ${error.message}")
            })

        Volley.newRequestQueue(this).add(request)
    }

    private fun configurarBoton(boton: CardView, favoritos: JSONObject, nombre: String) {
        if (favoritos.has(nombre)) {
            val favorito = favoritos.getJSONObject(nombre)
            val direccion = favorito.getString("direccion")
            val lat = favorito.getDouble("lat")
            val lng = favorito.getDouble("lng")

            boton.setOnClickListener {
                goToMapActivity(direccion, LatLng(lat, lng))
            }
        } else {
            boton.setOnClickListener {
                Toast.makeText(this, "Dirección no definida", Toast.LENGTH_SHORT).show()
            }
        }
    }

}