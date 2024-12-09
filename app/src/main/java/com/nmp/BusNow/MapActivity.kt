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
import com.nmp.BusNow.classes.Recorrido
import com.nmp.BusNow.classes.Tandil
import java.io.IOException
import java.util.Locale

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
    private var latitude: Double = -1.0
    private var longitude: Double = -1.0
    private var address: String? = null
    private lateinit var lineaAnterior: String
    private val colectivos = arrayOf(
        "-",
        "🟡500",
        "🔴501",
        "⚪502",
        "🔵503",
        "🟢504",
        "🟤505"
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
        val adapter = ArrayAdapter(this,android.R.layout.simple_spinner_item,colectivos)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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
        getRecorridos()

        // Inicializa boton para informar que subio o bajo del colectivo
        btnSeguimiento = findViewById(R.id.seguimiento)
        btnSeguimiento.text = buildString { append("SUBÍ AL COLECTIVO") }
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
                if (position >= 1) { // Verifica que la opción seleccionada no sea la inicial
                    // Limpiar pantalla
                    mMap.clear()
                    colectivoMarkers.clear()
                    removeDirection()
                    // Dibuja la ruta seleccionada
                    val linea = colectivos[position].substring((colectivos[position].lastIndex)-2)
                    val colectivo = recorridos[linea.toLong()]
                    if (colectivo != null) {
                        mMap.addPolyline(colectivo.getRecorrido())
                        Log.i("print","Linea: ${linea}")
                        var cont = 1
                        for (latLng in colectivo.getParadas()) {
                            mMap.addCircle(
                                CircleOptions()
                                    .center(latLng)
                                    .radius(15.0) // Ajusta el radio según sea necesario
                                    .strokeColor(colectivo.getColor())
                                    .strokeWidth(2F)
                                    .fillColor(Color.WHITE) // Color del círculo
                            )
                            Log.i("print","Parada${cont}: ${latLng}")
                            cont++
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
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.option_1 -> {
                        // Acción para la opción 1
                        val intent = Intent(this,ChoiceActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    R.id.option_2 -> {
                        // Acción para la opción 2
                        val intent = Intent(this,TracingActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    R.id.option_3 -> {
                        // Acción para la opción 3
                        val intent = Intent(this,MapActivity::class.java)
                        startActivity(intent)
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
        database.child("recorridos2").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (recorridoSnapshot in snapshot.children) {
                        val recorrido = recorridoSnapshot.getValue(Recorrido::class.java)
                        recorrido?.let {
                            // Dibuja el recorrido en el mapa
                            val color: Int = when(it.color){
                                "Azul"-> Color.BLUE
                                "Amarillo"-> Color.YELLOW
                                "Blanco"-> Color.DKGRAY
                                "Rojo"-> Color.RED
                                "Verde"-> Color.GREEN
                                else-> -7650029 // Marrón
                            }
                            var latLng: LatLng
                            val listParadas: MutableList<LatLng> = mutableListOf()
                            for (parada in it.paradas) {
                                latLng = LatLng(parada[0], parada[1])
                                listParadas.add(latLng)
                            }
                            recorridos[it.linea] = Colectivo(drawRoute(it.puntos,color),listParadas,color)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("MainActivity", "loadPost:onCancelled", error.toException())
            }
        })
    }

    private fun drawRoute(puntos: List<List<Double>>, color: Int): PolylineOptions {
        val path = PolylineOptions()
            .width(8f)
            .color(color)

        // Agrega los puntos al Polyline
        for (punto in puntos) {
            val latLng = LatLng(punto[0], punto[1]) // [lat, lon]
            path.add(latLng)
        }

        return path
    }

    private fun trackBusLocations(linea: String) {
        // Elimina el listener anterior si existe
        ubicacionListener?.let { database.child("ubicaciones").child(lineaAnterior).removeEventListener(it) }

        // Limpiar los marcadores existentes si cambia la línea
        for ((busId, marker) in colectivoMarkers) {
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
                        val lon = busSnapshot.child("lon").getValue(Double::class.java) ?: 0.0
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
                                    .title("Colectivo $busId - Línea $linea")
                                    .icon(recorridos[linea.toLong()]?.getColor()
                                        ?.let { Funciones.createMarkerBitmap(it,this@MapActivity) }
                                        ?.let { BitmapDescriptorFactory.fromBitmap(it) })
                            )
                            busId?.let { colectivoMarkers[it] = marker!! }
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
    }
}