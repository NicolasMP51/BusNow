from fastapi import FastAPI, BackgroundTasks
import tensorflow as tf
import pandas as pd
import numpy as np
import joblib
from pydantic import BaseModel
from SERVER.utils import elegir_modelo, predecir, update_last10, remove_inactive_buses
from firebase_admin import credentials, db, initialize_app
from geopy.distance import geodesic
from datetime import datetime
from typing import List

# Cargar los modelos LSTM
modelo_500 = tf.keras.models.load_model('modelo_500.h5',custom_objects={'mse': tf.keras.losses.MeanSquaredError(),'mae': tf.keras.metrics.MeanAbsoluteError()})
modelo_501 = tf.keras.models.load_model('modelo_501.h5',custom_objects={'mse': tf.keras.losses.MeanSquaredError(),'mae': tf.keras.metrics.MeanAbsoluteError()})
modelo_502 = tf.keras.models.load_model('modelo_502.h5',custom_objects={'mse': tf.keras.losses.MeanSquaredError(),'mae': tf.keras.metrics.MeanAbsoluteError()})
modelo_503 = tf.keras.models.load_model('modelo_503.h5',custom_objects={'mse': tf.keras.losses.MeanSquaredError(),'mae': tf.keras.metrics.MeanAbsoluteError()})
modelo_504 = tf.keras.models.load_model('modelo_504.h5',custom_objects={'mse': tf.keras.losses.MeanSquaredError(),'mae': tf.keras.metrics.MeanAbsoluteError()})
modelo_505 = tf.keras.models.load_model('modelo_505.h5',custom_objects={'mse': tf.keras.losses.MeanSquaredError(),'mae': tf.keras.metrics.MeanAbsoluteError()})
modelos = [modelo_500, modelo_501, modelo_502, modelo_503, modelo_504, modelo_505]

# Cargar los scalers para desnormalizar
scaler_500 = joblib.load('scaler_500.pkl')
scaler_501 = joblib.load('scaler_501.pkl')
scaler_502 = joblib.load('scaler_502.pkl')
scaler_503 = joblib.load('scaler_503.pkl')
scaler_504 = joblib.load('scaler_504.pkl')
scaler_505 = joblib.load('scaler_505.pkl')
scalers = [scaler_500, scaler_501, scaler_502, scaler_503, scaler_504, scaler_505]

# Inicializar Firebase Admin
cred = credentials.Certificate("RUTA_ARCH_KEYS")
initialize_app(cred, {"databaseURL": "https://colectivosapp-94939-default-rtdb.firebaseio.com"})

app = FastAPI()

class PredictionRequest(BaseModel):
    linea: int
    id: str
    parada_lat: float
    parada_lng: float
    vector1: tuple[float,float]
    vector2: tuple[float,float]
    vector3: tuple[float,float]

@app.post("/predict")
async def predict_route(request: PredictionRequest):
    model, scaler = elegir_modelo(request.linea)
    ref = db.reference(f"ubicaciones/{request.linea}/{request.id}/last10")
    ventana_base = ref.get()

    if ventana_base and len(ventana_base)==10:
        ventana_base = pd.DataFrame(ventana_base)
        ventana_base = ventana_base.rename(columns={'lat': 'posicion.lat', 'lng': 'posicion.lng'})
        ventana_base = scaler.transform(ventana_base[['posicion.lat','posicion.lng','velocidad','dia_semana','tiempo_dif','hora','inicio_recorrido']])
        ventana_base = np.expand_dims(ventana_base, axis=0)
        prediction, tiempo = predecir(model,ventana_base,scaler,[request.parada_lat, request.parada_lng], [request.vector1, request.vector2, request.vector3])
        latlng = prediction[:, :2].tolist()
        return {"prediction": latlng, "tiempo": tiempo}
    return {"error": "No se encontraron 10 locaciones previas"}

@app.get("/get_recorridos")
async def get_recorridos(background_tasks: BackgroundTasks):
    ref = db.reference("recorridos")
    recorridos_data = ref.get()

    # LLamada encubierta para realizar la limpieza de buses inactivos en segundo plano. Descomentar luego de terminar pruebas !!!!
    # background_tasks.add_task(remove_inactive_buses, db.reference("ubicaciones"))
    
    if recorridos_data:
        recorridos = []
        for recorrido in recorridos_data:
            linea = recorrido.get("linea", 0)
            color = recorrido.get("color", "Desconocido")
            paradas = recorrido.get("paradas", [])
            puntos = recorrido.get("puntos", [])
            
            # Armar el recorrido y devolverlo
            recorrido = {
                "linea": linea,
                "color": color,
                "paradas": paradas,
                "puntos": puntos
            }
            recorridos.append(recorrido)
        return {"recorridos": recorridos}
    return {"error": "No se encontraron recorridos"}

class LocationData(BaseModel):
    linea: int
    lat: float
    long: float

# Diccionario para rastrear la última actualización de last10 de cada bus
last_records = {}

@app.post("/update_bus_location")
async def update_bus_location(location_data: LocationData):
    # Obtener los datos de la ubicación
    linea = location_data.linea
    lat = location_data.lat
    long = location_data.long
    current_location = (lat, long)

    min_distance = float("inf")
    nearby_bus_id = None

    ref = db.reference(f"ubicaciones/{linea}")
    buses = ref.get()

    if buses:
        for bus_id, bus_data in buses.items():
            bus_lat = bus_data.get("lat")
            bus_lon = bus_data.get("lng")
            if bus_lat is None or bus_lon is None:
                continue

            bus_location = (bus_lat, bus_lon)
            distance = geodesic(current_location, bus_location).meters

            if distance < min_distance:
                min_distance = distance
                nearby_bus_id = bus_id

    if nearby_bus_id and min_distance < 100:
        # Actualizar ubicación del bus más cercano
        ref.child(nearby_bus_id).update({"lat": lat, "lng": long, "time": datetime.now().timestamp()})
        selected_bus_id = nearby_bus_id
    else:
        # Crear un nuevo bus si no hay ninguno cercano
        new_bus_id = f"bus{len(buses) + 1}" if buses else "bus1"
        ref.child(new_bus_id).set({"lat": lat, "lng": long, "time": datetime.now().timestamp()})
        selected_bus_id = new_bus_id

    update_last10(ref,selected_bus_id,current_location)

    return {"status": "Location updated", "bus_id": selected_bus_id}

class UbiRequest(BaseModel):
    linea: int
    id: str

@app.post("/get_ubicacion_actual")
async def get_ubicacion_actual(request: UbiRequest):
    ref = db.reference(f"ubicaciones/{request.linea}/{request.id}")
    lat_actual = ref.child("lat").get()
    lng_actual = ref.child("lng").get()

    if lat_actual and lng_actual:
        return {"ubicacion": [lat_actual, lng_actual]}
    return {"error": f"No se encontro la ubicacion actual del {request.id} linea {request.linea}"}

class DireccionFavorita(BaseModel):
    nombre: str
    direccion: str
    lat: float 
    lng: float

class Favoritos(BaseModel):
    user_id: str
    lista_favoritos: List[DireccionFavorita]

@app.post("/guardar_favoritos")
async def guardar_favoritos(favoritos: Favoritos):
    ref = db.reference(f"usuarios/{favoritos.user_id}/favoritos")
    
    
    # Convertir lista de favoritos en un diccionario clave-valor
    favoritos_dict = {
        fav.nombre: {
            "direccion": fav.direccion, 
            "lat": fav.lat, 
            "lng": fav.lng
        } 
        for fav in favoritos.lista_favoritos
    }

    ref.set(favoritos_dict)
    return {"mensaje": "Lugar favorito guardado correctamente"}

@app.get("/get_favoritos/{user_id}")
async def get_favoritos(user_id: str):
    ref = db.reference(f"usuarios/{user_id}/favoritos")
    favoritos = ref.get()
    if favoritos:
        return {"favoritos": favoritos} 
    return {"error": "No hay favoritos guardados"}

class Busqueda(BaseModel):
    user_id: str
    direccion: str
    lat: float
    lng: float

@app.post("/guardar_busqueda")
async def guardar_busqueda(busqueda: Busqueda):
    ref = db.reference(f"usuarios/{busqueda.user_id}/busquedas_recientes")
    busquedas = ref.get() or []

    nuevo_registro = {
        "direccion": busqueda.direccion,
        "lat": busqueda.lat,
        "lng": busqueda.lng
    }

    # Verificar si ya existe en la lista (comparando "direccion")
    if any(b["direccion"] == busqueda.direccion for b in busquedas):
        return {"mensaje": "La búsqueda no fue guardada, ya se encontraba en la lista"}

    busquedas.insert(0, nuevo_registro)  # Agregar nueva búsqueda
    if len(busquedas) > 10:  # Mantener solo 10
        busquedas.pop()

    ref.set(busquedas)
    return {"mensaje": "Búsqueda guardada correctamente"}

@app.get("/get_busquedas/{user_id}")
async def get_busquedas(user_id: str):
    ref = db.reference(f"usuarios/{user_id}/busquedas_recientes")
    busquedas = ref.get()
    if busquedas:
        return {"busquedas": busquedas}
    return {"error": "No hay búsquedas recientes"}

class BusCercaRequest(BaseModel):
    linea: int
    lat: float
    lng: float
    vector1: tuple[float,float]
    vector2: tuple[float,float]
    vector3: tuple[float,float]

@app.post("/get_bus_cerca")
async def get_ubicacion_actual(request: BusCercaRequest):
    ref = db.reference(f"ubicaciones/{request.linea}")
    model, scaler = elegir_modelo(request.linea)
    buses = ref.get()  # Obtener todos los buses de la línea
    if not buses:
        return {"error": f"No se encontraron colectivos para la línea {request.linea}"}

    bus_mas_cercano = None
    tiempo_minimo = float("inf")

    for bus_id, datos in buses.items():
        lat_actual = datos.get("lat")
        lng_actual = datos.get("lng")
        ventana_base = datos.get("last10")

        if ventana_base and len(ventana_base)==10:
            ventana_base = pd.DataFrame(ventana_base)
            ventana_base = ventana_base.rename(columns={'lat': 'posicion.lat', 'lng': 'posicion.lng'})
            ventana_base = scaler.transform(ventana_base[['posicion.lat','posicion.lng','velocidad','dia_semana','tiempo_dif','hora','inicio_recorrido']])
            ventana_base = np.expand_dims(ventana_base, axis=0)
            prediction, tiempo = predecir(model,ventana_base,scaler,[request.lat, request.lng], [request.vector1, request.vector2, request.vector3])
            if tiempo == -1:
                tiempo = 1000
        else:
            return {"error": "No se encontraron 10 locaciones previas"}

        if tiempo < tiempo_minimo:
            tiempo_minimo = tiempo
            if (tiempo == 1000):
                tiempo = -1
            bus_mas_cercano = {"id": bus_id, "lat": lat_actual, "lng": lng_actual, "tiempo": tiempo}

    if bus_mas_cercano:
        return {"bus_mas_cercano": bus_mas_cercano}
    else:
        return {"error": f"No se pudo determinar el colectivo más cercano en la línea {request.linea}"}