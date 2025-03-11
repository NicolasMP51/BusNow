package com.nmp.BusNow

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.nmp.BusNow.classes.OnItemClickListener
import com.nmp.BusNow.classes.rvAdapter
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.nmp.BusNow.ChoiceActivity.Companion.rutaSeleccionada
import com.nmp.BusNow.MapActivity.Companion.recorridos
import com.nmp.BusNow.PredictActivity.Companion.busLocation
import com.nmp.BusNow.classes.Funciones
import com.nmp.BusNow.classes.rvViewHolder
import org.json.JSONArray
import org.json.JSONObject

class TracingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var back: ImageButton
    private lateinit var switchNotifications: SwitchCompat
    private lateinit var rvParadas: RecyclerView
    private lateinit var btnSeguimiento: TextView
    private lateinit var cantParadas: TextView
    private lateinit var textTime: TextView
    private lateinit var rutaSel: TextView
    private var colectivo: String = ""
    private lateinit var origin: LatLng
    private lateinit var destination: LatLng

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_tracing)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Recibe las coordenadas origen y destino pasadas desde el intent
        val originLat = intent.getDoubleExtra("originLat", -1.0)
        val originLong = intent.getDoubleExtra("originLong", -1.0)
        val destLat = intent.getDoubleExtra("destLat", -1.0)
        val destLong = intent.getDoubleExtra("destLong", -1.0)
        if (originLat != -1.0 && originLong != -1.0 && destLat != -1.0 && destLong != -1.0 ) {
            origin = LatLng(originLat,originLong)
            destination = LatLng(destLat,destLong)
        }

        initComponents()
        initListeners()
    }

    private fun initComponents() {
        // Obtiene el SupportMapFragment y pide notificación cuando el mapa esté listo para ser usado.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Inicializa el boton de volver hacia atras
        back = findViewById(R.id.back)

        //Inicializa el boton de activar y desactivar notificaciones
        switchNotifications = findViewById(R.id.switch_notifications)

        // Inicializa el cartel de ruta seleccionada
        rutaSel = findViewById(R.id.ruta_seleccionada)
        colectivo = ChoiceActivity.rutaSeleccionada.getLinea()
        rutaSel.text = if(Funciones.isNotBus(colectivo)) colectivo else "Línea $colectivo"

        // Inicializa los textos de tiempo y cantidad de paradas
        cantParadas = findViewById(R.id.cantParadas)
        textTime = findViewById(R.id.textTime)
        if (rutaSeleccionada.getLinea() != "Caminando") {
            cantParadas.text = buildString { append("Bajar en ${rutaSeleccionada.getParadas().size - 1} paradas") }
            textTime.text = "- mins"
        }
        else {
            cantParadas.text = ""
            textTime.text = rutaSeleccionada.getDuration()
            val imgBus = findViewById<ImageView>(R.id.img_bus)
            imgBus.setImageResource(R.drawable.walk)

        }

        // Inicializa lista de paradas siguientes
        rvParadas = findViewById(R.id.rvParadas)

        // Inicializa boton para informar que subio o bajo del colectivo
        btnSeguimiento = findViewById(R.id.seguimiento)
        btnSeguimiento.text = buildString { append("SUBÍ AL COLECTIVO") }

    }

    private fun initListeners() {
        backListener()
        notificationsListener()
        seguimientoListener()
        rvSetup()
    }
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        if (rutaSeleccionada.getLinea() != "Caminando") {
            Funciones.drawRoute(mMap, origin, destination, recorridos[rutaSeleccionada.getLinea().toLong()]!!.getRecorrido())
            getBusCercano(rutaSeleccionada.getParadas().first(),1,rutaSeleccionada.getLinea().toLong())
        }
        else {
            Funciones.drawRoute(mMap, origin, destination, rutaSeleccionada.getPoints())
        }
    }

    private fun backListener() {
        back.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun notificationsListener() {
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Activado
                Log.i("print","Activado")
            } else {
                // Desactivado
                Log.i("print","Desactivado")
            }
        }
    }

    private fun seguimientoListener() {
        if(!Funciones.isNotBus(colectivo)) {
            Funciones.seguimientoListener(btnSeguimiento, this)
            Funciones.setSelectedBus(colectivo.toLong(),btnSeguimiento)
        } else Funciones.setSelectedBus(0L,btnSeguimiento)
    }

    private fun rvSetup() {
        val paradas: MutableList<String> = mutableListOf()
        for (latLng in rutaSeleccionada.getParadas()) {
            paradas.add(Funciones.getDireccion(latLng,this))
        }
        rvParadas.adapter = rvAdapter(paradas, R.layout.item_paradas, object : OnItemClickListener {
            override fun onItemClickListener(item: String) {
                // Hacer algo al clickear el elemento
            }}) {view -> rvViewHolder(view) }
        rvParadas.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
    }

    private fun getBusCercano(parada: LatLng, llamado: Int, linea: Long) {
        if (llamado <= 2) {
            Log.i("print", "${llamado+1} llamado")

            val url = "http://10.0.2.2:8000/get_bus_cerca"

            val index = Funciones.encontrarSegmentoMasCercano(parada, recorridos[linea]!!.getRecorrido().points)
            val vector1 = Funciones.getVector(index, linea)
            val vector2 = Funciones.getVector(index - 1, linea)
            val vector3 = Funciones.getVector(index + 1, linea)

            // Crear el objeto JSON para enviar
            val jsonBody = JSONObject()
            jsonBody.put("linea", linea)
            jsonBody.put("lat", parada.latitude)
            jsonBody.put("lng", parada.longitude)
            jsonBody.put("vector1", vector1)
            jsonBody.put("vector2", vector2)
            jsonBody.put("vector3", vector3)

            Log.i("print", jsonBody.toString())

            val request = JsonObjectRequest(
                Request.Method.POST, url, jsonBody,
                { response ->
                    try {
                        if (response.has("error")) {
                            val errorMessage = response.getString("error")
                            Log.e("print", "Error del servidor: $errorMessage")
                        } else if (response.has("bus_mas_cercano")) {
                            val bus = response.getJSONObject("bus_mas_cercano")
                            val id = bus.getString("id")
                            val lat = bus.getDouble("lat")
                            val lng = bus.getDouble("lng")
                            var tiempo = bus.getInt("tiempo")

                            if (tiempo != -1) {
                                if (llamado == 1) {
                                    tiempo -= 1
                                } else if (llamado == 2) {
                                    tiempo += 1
                                }
                                val busLocation = LatLng(lat,lng)
                                mMap.addMarker(
                                    MarkerOptions()
                                        .position(busLocation)
                                        .title("Colectivo $id - Línea $linea")
                                        .icon(recorridos[linea]!!.getColor()
                                            .let { Funciones.createMarkerBitmap(it,this@TracingActivity) }
                                            .let { BitmapDescriptorFactory.fromBitmap(it) })
                                )
                                predictRoute(rutaSeleccionada.getParadas().last(),1,linea,id)
                            } else {
                                val i = recorridos[linea]!!.getParadas().indexOf(parada)
                                var parada_nueva: LatLng?
                                if (llamado == 0){
                                    parada_nueva = recorridos[linea]!!.getParadas().getOrNull(i+1)
                                } else {
                                    parada_nueva = recorridos[linea]!!.getParadas().getOrNull(i-2)
                                }
                                if (parada_nueva != null) {
                                    getBusCercano(parada_nueva,llamado+1,linea)
                                } else if (llamado == 0) {
                                    parada_nueva = recorridos[linea]!!.getParadas().getOrNull(i-1)
                                    if (parada_nueva != null) {
                                        getBusCercano(parada_nueva,2,linea)
                                    }
                                } else {
                                    Toast.makeText(this, "El colectivo esta demasiado lejos de la parada, la prediccion no es precisa", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Log.e("print", "Respuesta inesperada del servidor")
                        }

                    } catch (e: Exception) {
                        Log.e("print", "Error procesando la respuesta: ${e.message}")
                    }
                },
                { error ->
                    Log.e("print", "Error: ${error.message}")
                }
            )

            // AUMENTAR TIMEOUT
            request.retryPolicy = DefaultRetryPolicy(
                60000, // 60 segundos de timeout
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES, // Reintentos
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT // Multiplicador de espera
            )

            // Agregar la solicitud a la cola
            val queue = Volley.newRequestQueue(this)
            queue.add(request)
        } else {
            Toast.makeText(this, "El colectivo esta demasiado lejos de la parada, la prediccion no es precisa", Toast.LENGTH_SHORT).show()
        }
    }

    private fun predictRoute(parada: LatLng, llamado: Int, linea: Long, busId: String) {
        if (llamado <= 2) {
            Log.i("print", "${llamado+1} llamado")

            val url = "http://10.0.2.2:8000/predict"

            val index = Funciones.encontrarSegmentoMasCercano(parada, recorridos[linea]!!.getRecorrido().points)
            val vector1 = Funciones.getVector(index, linea)
            val vector2 = Funciones.getVector(index - 1, linea)
            val vector3 = Funciones.getVector(index + 1, linea)

            // Crear el objeto JSON para enviar
            val jsonBody = JSONObject()
            jsonBody.put("linea", linea)
            jsonBody.put("id", busId)
            jsonBody.put("parada_lat", parada.latitude)
            jsonBody.put("parada_lng", parada.longitude)
            jsonBody.put("vector1", vector1)
            jsonBody.put("vector2", vector2)
            jsonBody.put("vector3", vector3)

            Log.i("print", jsonBody.toString())

            val request = JsonObjectRequest(
                Request.Method.POST, url, jsonBody,
                { response ->
                    try {
                        if (response.has("error")) {
                            val errorMessage = response.getString("error")
                            Log.e("print", "Error del servidor: $errorMessage")
                        } else if (response.has("prediction") && response.has("tiempo")) {
                            var tiempo = response.getInt("tiempo")

                            if (tiempo != -1) {
                                if (llamado == 1) {
                                    tiempo -= 1
                                } else if (llamado == 2) {
                                    tiempo += 1
                                }
                                textTime.setText("$tiempo mins")
                            } else {
                                val i = recorridos[linea]!!.getParadas().indexOf(parada)
                                var parada_nueva: LatLng?
                                if (llamado == 0){
                                    parada_nueva = recorridos[linea]!!.getParadas().getOrNull(i+1)
                                } else {
                                    parada_nueva = recorridos[linea]!!.getParadas().getOrNull(i-2)
                                }
                                if (parada_nueva != null) {
                                    predictRoute(parada_nueva,llamado+1, linea, busId)
                                } else if (llamado == 0) {
                                    parada_nueva = recorridos[linea]!!.getParadas().getOrNull(i-1)
                                    if (parada_nueva != null) {
                                        predictRoute(parada_nueva,2, linea, busId)
                                    }
                                } else {
                                    Toast.makeText(this, "El colectivo esta demasiado lejos de la parada, la prediccion no es precisa", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Log.e("print", "Respuesta inesperada del servidor")
                        }

                    } catch (e: Exception) {
                        Log.e("print", "Error procesando la respuesta: ${e.message}")
                    }
                },
                { error ->
                    Log.e("print", "Error: ${error.message}")
                }
            )

            // AUMENTAR TIMEOUT
            request.retryPolicy = DefaultRetryPolicy(
                60000, // 60 segundos de timeout
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES, // Reintentos
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT // Multiplicador de espera
            )

            // Agregar la solicitud a la cola
            val queue = Volley.newRequestQueue(this)
            queue.add(request)
        } else {
            Toast.makeText(this, "El colectivo esta demasiado lejos de la parada, la prediccion no es precisa", Toast.LENGTH_SHORT).show()
        }
    }
}