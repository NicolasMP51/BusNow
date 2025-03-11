package com.nmp.BusNow

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Color
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.nmp.BusNow.classes.Colectivo
import com.nmp.BusNow.classes.Funciones
import com.nmp.BusNow.classes.Tandil
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import android.app.AlertDialog
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.auth.FirebaseAuth
import com.nmp.BusNow.classes.ColorSpinnerAdapter
import org.json.JSONArray
import org.json.JSONObject

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        val recorridos = mutableMapOf<Long, Colectivo>()
    }

    private lateinit var mMap: GoogleMap
    private lateinit var database: DatabaseReference
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var colectivoMarkers: MutableMap<String, Marker> = mutableMapOf()
    private var ubicacionListener: ValueEventListener? = null
    private lateinit var currentLatLng: LatLng
    private lateinit var selectedLocation: LatLng
    private lateinit var currentAddress: String
    private lateinit var selectedAddress: String
    private lateinit var indications: LinearLayoutCompat
    private lateinit var spinner: Spinner
    private var spinnerActive: Boolean = false
    private lateinit var options: ImageButton
    private lateinit var search: LinearLayoutCompat
    private lateinit var searchText: TextView
    private lateinit var remove: ImageButton
    private lateinit var btnSeguimiento: TextView
    private lateinit var btnPredict: TextView
    private var btnPredictActive: Boolean = false
    private var latitude: Double = -1.0
    private var longitude: Double = -1.0
    private var address: String? = null
    private lateinit var lineaAnterior: String
    private lateinit var placesClient: PlacesClient
    private val colectivos = listOf(
        Pair("-", Color.WHITE),
        Pair("500", Color.YELLOW),
        Pair("501", Color.RED),
        Pair("502", Color.WHITE),
        Pair("503", Color.BLUE),
        Pair("504", Color.GREEN),
        Pair("505", Color.rgb(139,69,19)) // Marrón
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_map)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Solicita permisos y obtiene la ubicación si están otorgados
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkLocationPermission()

        // Recibe las coordenadas y dirección pasadas desde el intent
        latitude = intent.getDoubleExtra("latitude", -1.0)
        longitude = intent.getDoubleExtra("longitude", -1.0)
        address = intent.getStringExtra("address")

        initComponents()
        initListeners()
    }

    private fun initComponents() {
        // Obtiene el SupportMapFragment y pide notificación cuando el mapa esté listo para ser usado.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Inicializa el Spinner de opciones de colectivos
        spinner = findViewById(R.id.spinner)
        val adapter = ColorSpinnerAdapter(this, colectivos)
        spinner.adapter = adapter

        // Inicializa el boton de opciones
        options = findViewById(R.id.options)

        // Inicializa boton de busqueda
        search = findViewById(R.id.search)
        searchText = findViewById(R.id.search_text)

        // Inicializa boton de remove
        remove = findViewById(R.id.remove)

        // Inicializa boton de indicaciones
        indications = findViewById(R.id.indications)

        // Inicializa Firebase Database
        database = FirebaseDatabase.getInstance().reference

        // Obtiene todos los datos de los recorridos (inicializa el object "recorridos")
        getRecorridos()

        // Inicializa boton para informar que subio o bajo del colectivo
        btnSeguimiento = findViewById(R.id.seguimiento)
        btnSeguimiento.text = buildString { append("SUBÍ AL COLECTIVO") }

        // Inicializa boton para ir a la interfaz de prediccion
        btnPredict = findViewById(R.id.predict)

        // Inicializa Google Places
        if (!Places.isInitialized()) { Places.initialize(applicationContext, getString(R.string.google_maps_key)) }
        placesClient = Places.createClient(this)
    }

    private fun initListeners() {
        spinnerListener()
        optionsListener()
        searchListener()
        removeListener()
        indicationsListener()
        Funciones.seguimientoListener(btnSeguimiento, this)
    }

    private fun removeListener() {
        remove.setOnClickListener {
            removeDirection()
            updateMapCurrentLoc()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Define el area que cubra Tandil

        mMap.setLatLngBoundsForCameraTarget(Tandil.bounds())

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    currentLatLng = LatLng(location.latitude, location.longitude)
                    currentAddress = "Tu ubicación actual"
                } else {
                    currentLatLng = Tandil.center()
                    currentAddress = "Centro de Tandil"
                }

                // Si se pasaron coordenadas válidas, centrar el mapa y agregar un pin
                if (latitude != -1.0 && longitude != -1.0) {
                    selectedLocation = LatLng(latitude, longitude)
                    selectedAddress = address.toString()
                    addDirection()
                    updateMapSelectedLoc()
                } else {
                    // Si no se pasan coordenadas, mostrar la ubicación predeterminada
                    updateMapCurrentLoc()
                }
            }
        } catch (e: SecurityException) {
            // Manejar la excepción de permisos si es necesario
        }

        mMap.setOnMapClickListener { latLng ->
            selectedLocation = latLng
            spinnerActive = false
            spinner.setSelection(0)
            selectedAddress = Funciones.getDireccion(latLng,this)

            mMap.clear()  // Limpia cualquier marcador existente
            mMap.addMarker(MarkerOptions().position(selectedLocation).title(selectedAddress))
            addDirection()
        }
    }

    private fun addDirection(){
        searchText.text = selectedAddress
        searchText.setTextColor(ContextCompat.getColor(this, R.color.black))
        remove.visibility = View.VISIBLE
        indications.visibility = View.VISIBLE
    }

    private fun removeDirection(){
        searchText.text = ContextCompat.getString(this, R.string.busca_aqui)
        searchText.setTextColor(ContextCompat.getColor(this, R.color.gray))
        remove.visibility = View.GONE
        indications.visibility = View.GONE
    }

    private fun spinnerListener() {
        // Maneja las selecciones del Spinner
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>,view: View?,position: Int,id: Long) {
                btnPredict.visibility = View.GONE
                btnPredictActive = false
                if (position >= 1) { // Verifica que la opción seleccionada no sea la inicial
                    // Limpiar pantalla
                    mMap.clear()
                    colectivoMarkers.clear()
                    removeDirection()
                    // Dibuja la ruta seleccionada
                    val linea = colectivos[position].first
                    val colectivo = recorridos[linea.toLong()]
                    if (colectivo != null) {
                        mMap.addPolyline(colectivo.getRecorrido())
                        for (latLng in colectivo.getParadas()) {
                            mMap.addCircle(
                                CircleOptions()
                                    .center(latLng)
                                    .radius(15.0) // Ajusta el radio según sea necesario
                                    .strokeColor(colectivo.getColor())
                                    .strokeWidth(2F)
                                    .fillColor(Color.WHITE) // Color del círculo
                            )
                        }
                    }
                    spinnerActive = true
                    trackBusLocations(linea)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(Tandil.center(), 14f))
                    Funciones.setSelectedBus(linea.toLong(), btnSeguimiento)
                } else {
                    if(!Funciones.isSegActive())
                        Funciones.setSelectedBus(0L, btnSeguimiento)
                    if (spinnerActive) {
                        spinnerActive = false
                        updateMapCurrentLoc()
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                // Acción por defecto cuando no se selecciona nada
            }
        }
    }

    private fun optionsListener() {
        options.setOnClickListener {
            val popupMenu = PopupMenu(this, options)
            popupMenu.menuInflater.inflate(R.menu.options_menu, popupMenu.menu)

            val auth = FirebaseAuth.getInstance()
            val usuario = auth.currentUser

            // Modificar dinámicamente el texto de la opción 1
            val opt1 = popupMenu.menu.findItem(R.id.option_1)
            opt1.title = if (usuario != null) "Cerrar sesión" else "Iniciar sesión"

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.option_1 -> {
                        // Acción para la opción 1
                        if (usuario != null) {
                            // Hay un usuario logueado
                            auth.signOut()
                            Toast.makeText(this, "Sesión cerrada: ${usuario.email}", Toast.LENGTH_SHORT).show()
                        } else {
                            // No hay sesión activa
                            mostrarLoginDialog()
                        }
                        true
                    }
                    R.id.option_2 -> {
                        // Acción para la opción 2
                        if (usuario != null) {
                            // Hay un usuario logueado
                            mostrarFavoritosDialog()
                        } else {
                            // No hay sesión activa
                            Toast.makeText(this, "Debes iniciar sesion para guardar preferencias", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }
    }

    private fun searchListener() {
        search.setOnClickListener {
            val intent = Intent(this,SearchActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Log.i("print", "Permisos: OK")
            } else {
                Log.i("print", "Permisos: MAL")
            }
        }
    }

    private fun updateMapSelectedLoc() {
        mMap.clear()  // Limpia cualquier marcador existente
        mMap.addMarker(MarkerOptions().position(selectedLocation).title(selectedAddress))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(selectedLocation, 15f))
    }

    private fun updateMapCurrentLoc() {
        mMap.clear()  // Limpia cualquier marcador existente
        mMap.addCircle(
            CircleOptions()
                .center(currentLatLng)
                .radius(20.0) // Ajusta el radio según sea necesario
                .strokeColor(Color.WHITE)
                .strokeWidth(2F)
                .fillColor(Color.BLUE) // Color del círculo
        )
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
    }

    private fun indicationsListener() {
        indications.setOnClickListener {
            if (::currentLatLng.isInitialized && ::selectedLocation.isInitialized) {
                val intent = Intent(this,ChoiceActivity::class.java).apply {
                    putExtra("originLat", currentLatLng.latitude)
                    putExtra("originLong", currentLatLng.longitude)
                    putExtra("originAddress", currentAddress)
                    putExtra("destLat", selectedLocation.latitude)
                    putExtra("destLong", selectedLocation.longitude)
                    putExtra("destAddress", selectedAddress)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Ubicaciones no disponibles", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getRecorridos() {
        val url = "http://10.0.2.2:8000/get_recorridos"

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                // Manejo de la respuesta del servidor
                if (response.has("error")) {
                    val errorMessage = response.getString("error")
                    Log.e("print", "Error del servidor: $errorMessage")
                } else if (response.has("recorridos")) {
                    val recorridosArray = response.getJSONArray("recorridos")
                    for (i in 0 until recorridosArray.length()) {
                        val recorrido = recorridosArray.getJSONObject(i)
                        val linea = recorrido.getLong("linea")
                        val color = recorrido.getString("color")
                        val paradas = recorrido.getJSONArray("paradas")
                        val puntos = recorrido.getJSONArray("puntos")

                        // Procesar los datos del recorrido
                        val colorInt: Int = when (color) {
                            "Azul" -> Color.BLUE
                            "Amarillo" -> Color.YELLOW
                            "Blanco" -> Color.DKGRAY
                            "Rojo" -> Color.RED
                            "Verde" -> Color.GREEN
                            else -> -7650029  // Marrón
                        }

                        val listParadas: MutableList<LatLng> = mutableListOf()
                        for (j in 0 until paradas.length()) {
                            val parada = paradas.getJSONArray(j)
                            val lat = parada.getDouble(0)
                            val lng = parada.getDouble(1)
                            listParadas.add(LatLng(lat, lng))
                        }

                        val listPuntos: MutableList<LatLng> = mutableListOf()
                        for (j in 0 until puntos.length()) {
                            val punto = puntos.getJSONArray(j)
                            val lat = punto.getDouble(0)
                            val lng = punto.getDouble(1)
                            listPuntos.add(LatLng(lat, lng))
                        }

                        // Guardar los datos procesados
                        recorridos[linea] = Colectivo(drawRoute(listPuntos, colorInt), listParadas, colorInt)

                        Log.i("print", "Recorridos cargados correctamente")
                    }
                } else {
                    Log.e("print", "Respuesta inesperada del servidor")
                }
            },
            { error ->
                Log.e("MainActivity", "Error en la solicitud: ${error.message}")
            }
        )

        // Agregar la solicitud a la cola
        val queue = Volley.newRequestQueue(this)
        queue.add(request)
    }

    private fun drawRoute(puntos: MutableList<LatLng>, color: Int): PolylineOptions {
        val path = PolylineOptions()
            .width(8f)
            .color(color)

        // Agrega los puntos al Polyline
        for (punto in puntos) {
            path.add(punto)
        }

        return path
    }

    private fun trackBusLocations(linea: String) {
        // Elimina el listener anterior si existe
        ubicacionListener?.let { database.child("ubicaciones").child(lineaAnterior).removeEventListener(it) }

        // Limpiar los marcadores existentes si cambia la línea
        for (marker in colectivoMarkers.values) {
            marker.remove() // Remueve el marcador del mapa
        }
        colectivoMarkers.clear() // Limpiar el mapa de marcadores antiguos

        // Actualizar la línea seleccionada
        lineaAnterior = linea

        // Crear un nuevo ValueEventListener para escuchar los cambios de ubicación de todos los colectivos de la línea
        ubicacionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Recorremos cada colectivo en la línea
                    for (busSnapshot in snapshot.children) {
                        val busId = busSnapshot.key // ID del colectivo (e.g. "bus1", "bus2")
                        val lat = busSnapshot.child("lat").getValue(Double::class.java) ?: 0.0
                        val lon = busSnapshot.child("lng").getValue(Double::class.java) ?: 0.0
                        val newLocation = LatLng(lat, lon)

                        // Verifica si ya existe un marcador para este colectivo
                        if (colectivoMarkers.containsKey(busId)) {
                            // Actualiza la posición del marcador
                            colectivoMarkers[busId]?.position = newLocation
                        } else {
                            // Crea un nuevo marcador para este colectivo
                            val marker = mMap.addMarker(
                                MarkerOptions()
                                    .position(newLocation)
                                    .title("Línea $linea")
                                    .snippet("ID: $busId")
                                    .icon(recorridos[linea.toLong()]?.getColor()
                                        ?.let { Funciones.createMarkerBitmap(it,this@MapActivity) }
                                        ?.let { BitmapDescriptorFactory.fromBitmap(it) })
                            )
                            busId?.let { colectivoMarkers[it] = marker!! }

                            // Asociar el ID del colectivo al marcador
                            marker?.tag = busId
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("MainActivity", "loadBusLocations:onCancelled", error.toException())
            }
        }

        // Añadir el listener a la referencia de la base de datos
        database.child("ubicaciones").child(linea).addValueEventListener(ubicacionListener!!)

        // Agregar listener para detectar clics en los marcadores
        setupMarkerClickListener(linea.toLong())
    }

    @SuppressLint("PotentialBehaviorOverride")
    private fun setupMarkerClickListener(linea: Long) {
        mMap.setOnMarkerClickListener { marker ->
            if (btnPredictActive) {
                marker.hideInfoWindow() // Oculta la info si ya está abierta
                btnPredict.visibility = View.GONE
                btnPredictActive = false
            } else {
                marker.showInfoWindow() // La muestra si estaba cerrada
                val busId = marker.tag as? String
                if (busId != null) {
                    val intent = Intent(this,PredictActivity::class.java).apply {
                        putExtra("linea", linea)
                        putExtra("bus_id", busId)
                    }
                    btnPredict.visibility = View.VISIBLE
                    btnPredictActive = true
                    btnPredict.setOnClickListener {
                        startActivity(intent)
                    }
                }
            }
            true
        }
    }

    private fun mostrarLoginDialog() {
        val auth = FirebaseAuth.getInstance()

        // Inflar el diseño del diálogo
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_login, null)

        // Obtener referencias a los campos de entrada
        val emailEditText = dialogView.findViewById<EditText>(R.id.editTextEmail)
        val passwordEditText = dialogView.findViewById<EditText>(R.id.editTextPassword)

        // Crear el diálogo
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Iniciar sesión")
            .setPositiveButton("Iniciar sesión") { _, _ ->
                val email = emailEditText.text.toString().trim()
                val password = passwordEditText.text.toString().trim()

                if (email.isNotEmpty() && password.isNotEmpty()) {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Registrarse") { _, _ ->
                val email = emailEditText.text.toString().trim()
                val password = passwordEditText.text.toString().trim()

                if (email.isNotEmpty() && password.isNotEmpty()) {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                }
            }
            .create()

        dialog.show()
    }

    @SuppressLint("InflateParams")
    private fun mostrarFavoritosDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_favoritos, null)
        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Guardar Direcciones Favoritas")

        val alertDialog = builder.create()

        val casaEditText = dialogView.findViewById<EditText>(R.id.casaEditText)
        val trabajoEditText = dialogView.findViewById<EditText>(R.id.trabajoEditText)
        val listaDirecciones = dialogView.findViewById<LinearLayout>(R.id.listaDirecciones)
        val agregarDireccionBtn = dialogView.findViewById<Button>(R.id.agregarDireccionBtn)
        val aplicarBtn = dialogView.findViewById<Button>(R.id.aplicarBtn)
        val cancelarBtn = dialogView.findViewById<Button>(R.id.cancelarBtn)

        val direccionesExtras = mutableListOf<View>()

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

                        for (key in favoritos.keys()) {
                            val favorito = favoritos.getJSONObject(key)
                            val direccion = favorito.getString("direccion")

                            when (key) {
                                "Casa" -> casaEditText.setText(direccion)
                                "Trabajo" -> trabajoEditText.setText(direccion)
                                else -> agregarDireccionDinamica(listaDirecciones, direccionesExtras, key, direccion)
                            }
                        }

                        Log.i("print", "Respuesta del servidor: ${favoritos}")
                        alertDialog.show()
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

        agregarDireccionBtn.setOnClickListener {
            agregarDireccionDinamica(listaDirecciones, direccionesExtras, "", "")
        }

        aplicarBtn.setOnClickListener {
            val favoritosList = mutableListOf<Map<String, Any>>()
            val direccionesPendientes = mutableListOf<Pair<String, String>>() // Lista de direcciones a procesar

            // Agregar direcciones a la lista de pendientes
            if (casaEditText.text.isNotEmpty()) {
                direccionesPendientes.add(Pair("Casa", casaEditText.text.toString()))
            }

            if (trabajoEditText.text.isNotEmpty()) {
                direccionesPendientes.add(Pair("Trabajo", trabajoEditText.text.toString()))
            }

            for (view in direccionesExtras) {
                val nombre = view.findViewById<EditText>(R.id.nombreDireccionEditText).text.toString()
                val direccion = view.findViewById<EditText>(R.id.direccionEditText).text.toString()

                if (nombre.isNotEmpty() && direccion.isNotEmpty()) {
                    direccionesPendientes.add(Pair(nombre, direccion))
                }
            }

            // Verificar si hay direcciones a procesar
            if (direccionesPendientes.isEmpty()) {
                alertDialog.dismiss()
            } else {

                var procesadas = 0 // Contador de direcciones procesadas
                var error = false

                // Obtener coordenadas para cada dirección
                for ((nombre, direccion) in direccionesPendientes) {
                    getLatLng(direccion) { latLng ->
                        val favorito = mapOf(
                            "nombre" to nombre,
                            "direccion" to direccion,
                            "lat" to (latLng?.latitude ?: 0.0),
                            "lng" to (latLng?.longitude ?: 0.0)
                        )
                        favoritosList.add(favorito)

                        if (latLng == null) {
                           error = true
                        }

                        procesadas++ // Incrementar contador

                        // Si todas las direcciones han sido procesadas, guardar en el servidor
                        if (procesadas == direccionesPendientes.size) {
                            if (error) {
                                Toast.makeText(this, "No ingresó una dirección valida (calle y altura)", Toast.LENGTH_SHORT).show()
                            } else {
                                guardarFavoritosEnServidor(userId, favoritosList)
                            }
                            alertDialog.dismiss()
                        }
                    }
                }
            }
        }


        cancelarBtn.setOnClickListener {
            alertDialog.dismiss()
        }

        Volley.newRequestQueue(this).add(request)
    }

    private fun agregarDireccionDinamica(listaDirecciones: LinearLayout, direccionesExtras: MutableList<View>, nombre: String, direccion: String) {
        val nuevaDireccionView = LayoutInflater.from(listaDirecciones.context).inflate(R.layout.item_direccion, null)
        val nombreEditText = nuevaDireccionView.findViewById<EditText>(R.id.nombreDireccionEditText)
        val direccionEditText = nuevaDireccionView.findViewById<EditText>(R.id.direccionEditText)
        val eliminarBtn = nuevaDireccionView.findViewById<ImageButton>(R.id.eliminarDireccionBtn) // Botón de eliminar

        nombreEditText.setText(nombre)
        direccionEditText.setText(direccion)

        eliminarBtn.setOnClickListener {
            listaDirecciones.removeView(nuevaDireccionView)
            direccionesExtras.remove(nuevaDireccionView)
        }

        listaDirecciones.addView(nuevaDireccionView)
        direccionesExtras.add(nuevaDireccionView)
    }


    private fun guardarFavoritosEnServidor(userId: String, favoritosList: List<Map<String, Any>>) {
        val url = "http://10.0.2.2:8000/guardar_favoritos"
        val jsonBody = JSONObject().apply {
            put("user_id", userId)
            put("lista_favoritos", JSONArray(favoritosList))
        }

        val request = JsonObjectRequest(Request.Method.POST, url, jsonBody,
            { response ->
                Toast.makeText(this, "Favoritos guardados!", Toast.LENGTH_SHORT).show()
            },
            { error ->
                Toast.makeText(this, "Error guardando favoritos: ${error.message}", Toast.LENGTH_SHORT).show()
            })

        val queue = Volley.newRequestQueue(this)
        queue.add(request)
    }

    private fun getLatLng(query: String, callback: (LatLng?) -> Unit) {
        val token = AutocompleteSessionToken.newInstance()

        val request = FindAutocompletePredictionsRequest.builder()
            .setLocationBias(Tandil.locationBias())  // Limitar la búsqueda a Tandil
            .setSessionToken(token)
            .setQuery(query)
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                if (response.autocompletePredictions.isNotEmpty()) {
                    val prediction = response.autocompletePredictions[0]
                    val placeId = prediction.placeId
                    val placeFields = listOf(com.google.android.libraries.places.api.model.Place.Field.LAT_LNG)

                    val placeRequest = FetchPlaceRequest.builder(placeId, placeFields).build()
                    placesClient.fetchPlace(placeRequest)
                        .addOnSuccessListener { placeResponse ->
                            val latLng = placeResponse.place.latLng
                            if (latLng != null && Tandil.isWithinBounds(latLng)) {
                                Log.i("print", "La ubicación de ${query} es: ${latLng}")
                                callback(latLng)
                            } else {
                                Log.e("print", "La ubicación de ${query} no fue encontrada")
                                callback(null) // Si la ubicación no está dentro de Tandil
                            }
                        }
                        .addOnFailureListener {
                            Log.e("print", "Hubo un error en la busqueda de ${query}")
                            callback(null) // Si hay un error en la búsqueda
                        }
                } else {
                    Log.e("print", "No hubo predicciones para la direccion: ${query}")
                    callback(null) // Si no hay predicciones
                }
            }
            .addOnFailureListener {
                Log.e("print", "Hubo un error en la prediccion de la direccion: ${query}")
                callback(null) // Si hay un error en la predicción
            }
    }

}