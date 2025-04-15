import numpy as np
from datetime import datetime
from geopy.distance import geodesic
from firebase_admin.db import Reference

def elegir_modelo(linea: int):
    from SERVER.main import modelos, scalers
    if linea == 500:
        model = modelos[0]
        scaler = scalers[0]
    elif linea == 501:
        model = modelos[1]
        scaler = scalers[1]
    elif linea == 502:
        model = modelos[2]
        scaler = scalers[2]
    elif linea == 503:
        model = modelos[3]
        scaler = scalers[3]
    elif linea == 504:
        model = modelos[4]
        scaler = scalers[4]
    elif linea == 505:
        model = modelos[5]
        scaler = scalers[5]
    return model, scaler

def predecir(modelo, X_inicial, scaler, parada_obj, vectores):
    tiempo = -1
    perimetro = 200
    while (tiempo == -1) and (perimetro <= 400):
        predicciones, tiempo = predecir_n_pasos(modelo, X_inicial, scaler, parada_obj, vectores, perimetro)
        perimetro += 100
    return predicciones, tiempo

def predecir_n_pasos(modelo, X_inicial, scaler, parada_obj, vectores, perimetro):
    predicciones = []
    entrada_actual = X_inicial.copy()
    encontro = False

    for i in range(60):
        reg_ant = entrada_actual[0][-1]  # Último registro en la ventana
        reg_ant = scaler.inverse_transform([reg_ant])[0]
        dia_ant, hora_ant = reg_ant[3], reg_ant[5]

        prediccion = modelo.predict(entrada_actual)  # Predice el siguiente paso

        pred_ext = np.append(prediccion[0], [0,0,0,0])
        prediccion = scaler.inverse_transform([pred_ext])

        hora_pred = hora_ant + (1/60)
        prediccion[0][3] = dia_ant
        prediccion[0][4] = 60
        prediccion[0][5] = hora_pred
        prediccion[0][6] = 0

        pred = scaler.transform(prediccion)[0]
        predicciones.append(pred)  # Guarda la predicción

        # Actualiza la ventana de entrada
        entrada_actual = np.append(entrada_actual[:, 1:, :], [[pred]], axis=1)

        tiempo = i + 1

        if encontro_objetivo(prediccion[0][0], prediccion[0][1], reg_ant[0], reg_ant[1], parada_obj, vectores, perimetro):
            print(f"Tiempo estimado a la parada: {tiempo} minutos")
            encontro = True
            break

    # Desnormaliza predicciones
    predicciones = np.array(predicciones)
    predicciones_desnormalizadas = scaler.inverse_transform(predicciones)
    if not encontro:
       tiempo = -1
    return predicciones_desnormalizadas, tiempo

def encontro_objetivo(x_pred, y_pred, x_ant, y_ant, parada_obj, vectores, perimetro):
    umbral_direccion = np.cos(np.pi / 3) # Coseno del ángulo máximo permitido para la dirección (default 60°)

    # Verificar si la predicción está dentro del radio de la parada
    distancia = geodesic((x_pred,y_pred), parada_obj).meters
    if distancia < perimetro:
        # Calcular la dirección del movimiento y compararla con el tramo
        dir_mov = np.array([x_pred - x_ant, y_pred - y_ant])
        dir_mov = dir_mov / np.linalg.norm(dir_mov) if np.linalg.norm(dir_mov) != 0 else np.array([0, 0])

        for vector in vectores:
            vector_norm = np.array(vector) / np.linalg.norm(vector)
            cos_angulo = np.dot(dir_mov, vector_norm)  # Producto punto

            if cos_angulo > umbral_direccion:  # Similaridad de dirección
                return True
    return False

def update_last10(ref,bus_id,current_location):
    from SERVER.main import last_records

    # Comprobar si ha pasado al menos 1 minuto desde la última actualización de last10
    bus_ref = ref.child(bus_id)
    current_time = datetime.now()
    current_time_decimal = current_time.hour + (current_time.minute / 60.0)

    last_record = last_records.get(bus_id)

    if last_record is None:
        # Si no tenemos un registro en memoria, lo obtenemos de Firebase (solo la primera vez)
        last10 = list(bus_ref.child("last10").get() or [])
        if last10:
            last_record = last10[-1]  # Último elemento de la lista
            last_records[bus_id] = last_record  # Guardamos en memoria

    if last_record:
        last_time = last_record["tiempo"]
        last_lat = last_record["lat"]
        last_lng = last_record["lng"]
        tiempo_dif = current_time.timestamp() - last_time

        if tiempo_dif >= 60.0:  # Si ha pasado 1 minuto

            # Calcular distancia en metros
            last_location = (last_lat, last_lng)
            distance = geodesic(current_location, last_location).kilometers
            velocidad = round((distance / tiempo_dif) * 3600, 2)

            # Crear el nuevo registro
            nuevo_registro = {
                "lat": current_location[0],
                "lng": current_location[1],
                "velocidad": velocidad,
                "dia_semana": current_time.weekday(),
                "tiempo_dif": tiempo_dif,
                "tiempo": current_time.timestamp(),
                "hora": current_time_decimal,
                "inicio_recorrido": 0
            }

            # Mantener solo los últimos 10 registros
            last10 = list(bus_ref.child("last10").get() or [])
            last10.append(nuevo_registro)
            if len(last10) > 10:
                last10.pop(0)  # Eliminar el más antiguo

            # Guardar la lista actualizada en Firebase
            bus_ref.update({"last10": last10})

            # Actualizar el registro en memoria
            last_records[bus_id] = nuevo_registro
    else:
        # Si last10 está vacío, agregar el primer elemento
        nuevo_registro = {
            "lat": current_location[0], 
            "lng": current_location[1],
            "velocidad": 20,  # No hay referencia anterior para calcular velocidad
            "dia_semana": current_time.weekday(),
            "tiempo_dif": 60,  # Primer registro, no hay referencia de tiempo
            "tiempo": current_time.timestamp(),
            "hora": current_time_decimal,
            "inicio_recorrido": 0
        }

        bus_ref.update({"last10": [nuevo_registro]})  # Crear la lista con el primer elemento

        # Actualizar el registro en memoria
        last_records[bus_id] = nuevo_registro

def remove_inactive_buses(ref: Reference):
    lineas = ref.get()
    
    if lineas:
        for linea, buses in lineas.items():
            if buses:
                now = datetime.now().timestamp()
                timeout_seconds = 3600  # 3600 seg = 1 hora

                for bus_id, bus_data in buses.items():
                    last_update = bus_data.get("time")
                    if last_update and (now - last_update) > timeout_seconds:
                        ref.child(f"{linea}/{bus_id}").delete()  # Eliminar el bus si lleva más de 10 min sin actualizarse