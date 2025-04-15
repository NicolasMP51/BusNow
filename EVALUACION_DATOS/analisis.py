import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import os
import json

# Configuración de estilos para los gráficos
sns.set(style="whitegrid")

# Ruta donde están almacenados los archivos
ruta_archivos = "datos"

# Crear un DataFrame vacío para almacenar todos los datos
datos_completos = pd.DataFrame()

# Leer todos los archivos en la carpeta
for archivo in os.listdir(ruta_archivos):
    if '-' in archivo:  # Verificamos que el nombre del archivo siga el formato esperado "linea-id"
        linea, colectivo_id = archivo.split('-')
        colectivo_id = colectivo_id.split('.')[0]  # Para eliminar la extensión del archivo
        filepath = os.path.join(ruta_archivos, archivo)
        
        # Leer archivo línea por línea
        with open(filepath, 'r') as f:
            registros = []
            for line in f:
                try:
                    # Convertir cada línea en JSON y agregar la línea y colectivo al registro
                    data = json.loads(line.strip())
                    data['linea'] = int(linea)
                    data['id'] = int(colectivo_id)
                    registros.append(data)
                except json.JSONDecodeError:
                    print(f"Error al leer línea en archivo {archivo}: {line}")
            
            # Convertir los registros a un DataFrame
            df = pd.json_normalize(registros)
            datos_completos = pd.concat([datos_completos, df], ignore_index=True)

# Convertir tiempos a formato legible (asumimos que están en milisegundos UNIX)
datos_completos['tiempo_request'] = pd.to_datetime(datos_completos['tiempo_request'], unit='ms') - pd.Timedelta(hours=3) # Ajustarlo a Argentina
datos_completos['tiempo_colectivo'] = pd.to_datetime(datos_completos['tiempo_colectivo'], unit='ms') - pd.Timedelta(hours=3) # Ajustarlo a Argentina

# Gráfico de la distribución por hora del día
datos_completos['hora'] = datos_completos['tiempo_colectivo'].dt.hour
plt.figure(figsize=(10, 6))
sns.countplot(x='hora', data=datos_completos, palette='viridis')
plt.title("Distribución de registros por hora del día")
plt.xlabel("Hora del día")
plt.ylabel("Cantidad de registros")
plt.show()

# Gráfico de la distribución por día de la semana
datos_completos['dia_semana'] = datos_completos['tiempo_colectivo'].dt.dayofweek
plt.figure(figsize=(10, 6))
sns.countplot(x='dia_semana', data=datos_completos, palette='viridis')
plt.title("Distribución de registros por día de la semana")
plt.xlabel("Día de la semana (0 = Lunes, 6 = Domingo)")
plt.ylabel("Cantidad de registros")
plt.show()

# Gráfico de cantidad de datos por línea
plt.figure(figsize=(10, 6))
sns.countplot(x='linea', data=datos_completos, palette='Blues')
plt.title("Cantidad de datos por línea de colectivo")
plt.xlabel("Línea de colectivo")
plt.ylabel("Cantidad de registros")
plt.show()

# Gráfico de cantidad de datos por colectivo (linea, id)
plt.figure(figsize=(12, 8))
sns.countplot(x='id', hue='linea', data=datos_completos, palette='Set2')
plt.title("Cantidad de datos por colectivo")
plt.xlabel("ID del colectivo")
plt.ylabel("Cantidad de registros")
plt.legend(title="Línea de colectivo")
plt.show()

# Gráfico de valores nulos por columna
valores_nulos = datos_completos.isnull().sum()
plt.figure(figsize=(10, 6))
valores_nulos.plot(kind='bar', color='darkred')
plt.title("Cantidad de valores nulos por columna")
plt.xlabel("Columnas")
plt.ylabel("Cantidad de valores nulos")
plt.show()

# Si hay registros duplicados, generamos un gráfico
duplicados = datos_completos.duplicated(subset=['linea', 'id', 'tiempo_request', 'tiempo_colectivo', 'posicion.lat', 'posicion.lng'])
registros_duplicados = datos_completos[duplicados]
if duplicados.sum() > 0:
    plt.figure(figsize=(10, 6))
    sns.countplot(x='linea', data=registros_duplicados, palette='coolwarm')
    plt.title("Distribución de registros duplicados por línea")
    plt.xlabel("Línea de colectivo")
    plt.ylabel("Cantidad de registros duplicados")
    plt.show()
else:
    print("No se encontraron registros duplicados.")

# Histograma de las velocidades
plt.figure(figsize=(10, 6))
sns.histplot(datos_completos['velocidad'], bins=50, color='green', kde=True)
plt.title("Distribución de velocidades de los colectivos")
plt.xlabel("Velocidad (km/h)")
plt.ylabel("Frecuencia")
plt.show()