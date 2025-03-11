package com.nmp.BusNow

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.nmp.BusNow.MapActivity.Companion.recorridos
import com.nmp.BusNow.classes.Funciones
import com.nmp.BusNow.classes.Tandil
import org.json.JSONArray
import org.json.JSONObject

class PredictActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        var busLocation: LatLng = LatLng(0.0, 0.0)
    }

    private var linea: Long = -1L
    private var busId: String? = null
    private var parada: LatLng = LatLng(0.0,0.0)
    private var paradas : MutableList<String> = mutableListOf(" ")
    private var paradasLatlng : List<LatLng> = listOf()
    private var paradasMarkers = mutableListOf<Marker>()
    private lateinit var mMap: GoogleMap
    private lateinit var back: ImageButton
    private lateinit var spinner: Spinner
    private lateinit var predictText: TextView
    private lateinit var spinnerHint: TextView
    private lateinit var btnCalcular: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_predict)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Recibe los datos del colectivo pasados desde el intent
        linea = intent.getLongExtra("linea", -1L)
        busId = intent.getStringExtra("bus_id")

        initComponents()
        initListeners()
    }

    private fun initComponents(){
        // Inicializa el Spinner de opciones de colectivos
        spinner = findViewById(R.id.spinner)
        if (linea != -1L) {
            paradasLatlng = recorridos[linea]!!.getParadas()
            for (latLng in paradasLatlng) {
                paradas.add(Funciones.getDireccion(latLng,this))
            }
        }
        val adapter = ArrayAdapter(this,android.R.layout.simple_spinner_item, paradas)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(0, false) // Evita que dispare el evento onItemSelected al inicio

        // Obtiene el SupportMapFragment y pide notificación cuando el mapa esté listo para ser usado.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Inicializa el boton de volver hacia atras
        back = findViewById(R.id.back)

        // Inicializa texto de prediccion
        predictText = findViewById(R.id.predict)

        // Inicializa el spinner hint
        spinnerHint = findViewById(R.id.spinner_hint)

        // Inicializa boton para solicitar prediccion
        btnCalcular = findViewById(R.id.calcular)
    }

    private fun initListeners(){
        spinnerListener()
        backListener()
        calcularListener()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setLatLngBoundsForCameraTarget(Tandil.bounds())
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(Tandil.center(), 15f))
        val colectivo = recorridos[linea]
        if (colectivo != null) {
            mMap.addPolyline(colectivo.getRecorrido())
            var i = 1
            for (latLng in colectivo.getParadas()) {
                mMap.addCircle(
                    CircleOptions()
                        .center(latLng)
                        .radius(15.0)
                        .strokeColor(colectivo.getColor())
                        .strokeWidth(2F)
                        .fillColor(Color.WHITE)
                )
                // Agregar un Marker invisible sobre el círculo
                val marker = mMap.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title(paradas[i]) // Dirección de la parada
                        .icon(BitmapDescriptorFactory.fromBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))) // Icono transparente
                )
                marker?.let { paradasMarkers.add(it) } // Guardar el marcador invisible

                i += 1
            }
        }
        // Agregar listener para detectar clics en los marcadores
        setupMarkersClickListener()

        getUbicacionActual()
    }

    @SuppressLint("PotentialBehaviorOverride")
    private fun setupMarkersClickListener() {
        mMap.setOnMarkerClickListener { marker ->
            marker.showInfoWindow()
            val index = paradasMarkers.indexOf(marker)
            spinner.setSelection(index+1)
            true
        }
    }

    private fun spinnerListener() {
        // Maneja las selecciones del Spinner
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                // Limpiar pantalla
                predictText.text = ""
                if (position >= 1) { // Verifica que la opción seleccionada no sea la inicial
                    parada = paradasLatlng[position-1]
                    getUbicacionActual()
                    spinnerHint.visibility = View.GONE

                    // Obtener el marcador correspondiente
                    val marker = paradasMarkers[position - 1]
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, 15f)) // Ajustar el zoom si es necesario
                    marker.showInfoWindow() // Muestra la info del marcador
                } else {
                    spinnerHint.visibility = View.VISIBLE
                    parada = LatLng(0.0,0.0)
                    busLocation = LatLng(0.0,0.0)

                    // Buscar si hay un marcador con ventana abierta y cerrarla
                    for (marker in paradasMarkers) {
                        if (marker.isInfoWindowShown) {
                            marker.hideInfoWindow()
                        }
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                // Acción por defecto cuando no se selecciona nada
            }
        }
    }

    private fun backListener() {
        back.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun calcularListener() {
        btnCalcular.setOnClickListener {
            if ((linea != -1L) && (busId != null) && (parada != LatLng(0.0,0.0)) && (busLocation != LatLng(0.0,0.0))) {
                btnCalcular.setText("CALCULANDO...")
                btnCalcular.setBackgroundColor(Color.RED)
                predictRoute(parada, 0)
            } else {
                Toast.makeText(this, "No fue seleccionada ninguna parada", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getUbicacionActual() {
        val url = "http://10.0.2.2:8000/get_ubicacion_actual"

        // Crear el objeto JSON para enviar
        val jsonBody = JSONObject()
        jsonBody.put("linea", linea.toInt())
        jsonBody.put("id", busId)

        Log.i("print",jsonBody.toString())

        val request = JsonObjectRequest(
            Request.Method.POST, url, jsonBody,
            { response ->
                try {
                    if (response.has("error")) {
                        val errorMessage = response.getString("error")
                        Log.e("print", "Error del servidor: $errorMessage")
                    } else if (response.has("ubicacion")) {
                        val ubicacion = response.getJSONArray("ubicacion")
                        val lat = ubicacion.getDouble(0)
                        val lng = ubicacion.getDouble(1)

                        busLocation = LatLng(lat,lng)
                        mMap.addMarker(
                            MarkerOptions()
                                .position(busLocation)
                                .title("Colectivo $busId - Línea $linea")
                                .icon(recorridos[linea]!!.getColor()
                                    .let { Funciones.createMarkerBitmap(it,this@PredictActivity) }
                                    .let { BitmapDescriptorFactory.fromBitmap(it) })
                        )

                        Log.i("print", "Coordenadas recibidas: ($lat, $lng)")
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

        // Agregar la solicitud a la cola
        val queue = Volley.newRequestQueue(this)
        queue.add(request)
    }

    private fun predictRoute(parada: LatLng, llamado: Int) {
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
                            val coordinatesArray = response.getJSONArray("prediction")
                            var tiempo = response.getInt("tiempo")
                            val coordinatesList = mutableListOf<LatLng>()

                            for (i in 0 until coordinatesArray.length()) {
                                val coord = coordinatesArray.getJSONArray(i)
                                val lat = coord.getDouble(0)
                                val lng = coord.getDouble(1)
                                val latLng = LatLng(lat, lng)
                                coordinatesList.add(latLng)

                            }

                            if (tiempo != -1) {
                                if (llamado == 1) {
                                    tiempo -= 1
                                } else if (llamado == 2) {
                                    tiempo += 1
                                }
                                predictText.setText("$tiempo mins")
                                btnCalcular.setText(R.string.calcular_tiempo)
                                btnCalcular.setBackgroundColor(Color.GREEN)
                            } else {
                                val i = recorridos[linea]!!.getParadas().indexOf(parada)
                                var parada_nueva: LatLng?
                                if (llamado == 0){
                                    parada_nueva = recorridos[linea]!!.getParadas().getOrNull(i+1)
                                } else {
                                    parada_nueva = recorridos[linea]!!.getParadas().getOrNull(i-2)
                                }
                                if (parada_nueva != null) {
                                    predictRoute(parada_nueva,llamado+1)
                                } else if (llamado == 0) {
                                    parada_nueva = recorridos[linea]!!.getParadas().getOrNull(i-1)
                                    if (parada_nueva != null) {
                                        predictRoute(parada_nueva,2)
                                    }
                                } else {
                                    btnCalcular.setText(R.string.calcular_tiempo)
                                    btnCalcular.setBackgroundColor(Color.GREEN)
                                    Toast.makeText(this, "El colectivo esta demasiado lejos de la parada, la prediccion no es precisa", Toast.LENGTH_SHORT).show()
                                }
                            }

                            Log.i("print", "Coordenadas recibidas: $coordinatesList")
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
            btnCalcular.setText(R.string.calcular_tiempo)
            btnCalcular.setBackgroundColor(Color.GREEN)
            Toast.makeText(this, "El colectivo esta demasiado lejos de la parada, la prediccion no es precisa", Toast.LENGTH_SHORT).show()
        }
    }

}