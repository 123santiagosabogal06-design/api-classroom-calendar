# API para la organización y seguimiento de actividades académicas

Servicio en Java (Spring Boot) que, con la autorización del estudiante, lee sus cursos y tareas de **Google Classroom** y crea, actualiza o elimina los eventos correspondientes en su **Google Calendar**, con recordatorios, sin duplicar y de forma automática cada 30 minutos.

Proyecto de Práctica Profesional, Ingeniería de Sistemas, Escuela Tecnológica Instituto Técnico Central. Autor: Angel Santiago Sabogal Rincon.

> Es un prototipo académico. Funciona mientras la aplicación esté encendida en el computador donde se ejecuta.

## Qué hace

- Inicio de sesión con Google (OAuth 2.0, flujo de aplicación web). La aplicación nunca ve la contraseña.
- Un token por usuario, guardado bajo su correo en la carpeta `tokens/`.
- Lee cursos activos, tareas con fecha límite y estado de entrega desde Classroom.
- Crea un evento por tarea (30 minutos antes de la entrega) con enlace, recordatorio por correo y aviso.
- No duplica: cada evento lleva una clave privada y se compara antes de crear.
- Actualiza si cambia el título o la hora, y elimina el evento si la tarea fue entregada o ya no existe.
- Sincronización automática cada 30 minutos para todos los usuarios guardados, o manual con el botón de la página.
- Arquitectura por fuentes y destinos: una segunda fuente simulada demuestra que se pueden sumar plataformas.

## Requisitos

- Java 21 o superior.
- Maven (incluido como `mvnw` / `mvnw.cmd`) o Apache NetBeans.
- Un proyecto en Google Cloud con las APIs **Google Classroom API** y **Google Calendar API** habilitadas.
- Un cliente OAuth de tipo **Aplicación web** con esta URI de redireccionamiento autorizada:
  `http://localhost:8080/oauth2/callback`
- Mientras la aplicación esté en modo de prueba en Google Cloud, las cuentas que vayan a iniciar sesión deben estar agregadas como usuarios de prueba.

## Credenciales (no incluidas en este repositorio)

Por seguridad, **no se incluyen** `credentials.json` ni la carpeta `tokens/`: dan acceso a la aplicación y a los datos de quienes la autorizaron.

1. En Google Cloud, crea el cliente OAuth de tipo *Aplicación web* y descarga su JSON.
2. Guárdalo como `src/main/resources/credentials.json`.
3. La carpeta `tokens/` se crea sola cuando alguien inicia sesión.

Ambos archivos están en `.gitignore`.

## Ejecución

```
./mvnw spring-boot:run
```

En Windows: `mvnw.cmd spring-boot:run`. También se puede ejecutar desde NetBeans con clic derecho sobre el proyecto y *Run*.

Abre `http://localhost:8080`, pulsa *Iniciar sesión con Google* y luego *Sincronizar ahora*.

Para exponerla a otros dispositivos durante una prueba se puede usar un túnel (por ejemplo ngrok). En ese caso hay que registrar `https://<tu-dominio>/oauth2/callback` en Google Cloud y ejecutar con:

```
java -jar target/<archivo>.jar --app.base-url=https://<tu-dominio>
```

## Configuración (`src/main/resources/application.properties`)

| Propiedad | Para qué sirve |
|---|---|
| `app.base-url` | Dirección pública de la aplicación (por defecto `http://localhost:8080`) |
| `app.tokens-dir` | Carpeta donde se guardan los tokens |
| `app.timezone` | Zona horaria (por defecto `America/Bogota`) |
| `app.sync.interval` / `app.sync.initial-delay` | Frecuencia de la sincronización automática (formato ISO-8601, por ejemplo `PT30M`) |
| `app.reminder.email-minutes` / `app.reminder.popup-minutes` | Minutos antes de la entrega para el correo y el aviso |
| `app.fuente-simulada.activa` | Activa la fuente simulada de ejemplo (`true` / `false`) |
| `server.forward-headers-strategy` | Poner `framework` cuando se usa un túnel https |

## Estructura

Paquete `com.apiclassroom.api_classroom`:

| Clase | Función |
|---|---|
| `ApiClassroomApplication` | Arranque de la aplicación |
| `GoogleAuthService` | Inicio de sesión con Google y almacenamiento de tokens por usuario |
| `AuthController` | Rutas `/login`, `/oauth2/callback` y `/logout` |
| `ApiController` | Rutas `/api/me`, `/api/sync`, `/api/disconnect` y `/api/ping` |
| `Tarea` | Modelo neutral de una tarea |
| `TareaSource` | Interfaz que toda fuente de tareas debe cumplir |
| `ClassroomSource` | Fuente real: Google Classroom |
| `FuenteSimulada` | Segunda fuente, simulada, para demostrar extensibilidad |
| `CalendarSink` | Destino: crea, actualiza y elimina eventos en Google Calendar |
| `SyncService` | Reúne las tareas de todas las fuentes y las pasa a `CalendarSink` |
| `Sincronizador` | Ejecuta la sincronización automática |
| `ResumenSync` | Reporte de cada sincronización |

Para agregar otra plataforma basta con crear una clase `@Component` que implemente `TareaSource`; no hace falta modificar `SyncService` ni `CalendarSink`.

## Permisos que solicita a Google

| Permiso | Uso |
|---|---|
| `openid`, `userinfo.email` | Identificar al usuario y guardar su token bajo su correo |
| `classroom.courses.readonly` | Leer sus cursos (solo lectura) |
| `classroom.coursework.me.readonly` | Leer sus tareas y su estado de entrega (solo lectura) |
| `calendar.events` | Crear, actualizar y borrar los eventos de tareas |

## Limitaciones conocidas

- Solo corre mientras la aplicación esté encendida en el equipo del autor.
- En modo de prueba de Google Cloud, los permisos guardados vencen a los 7 días y solo entran cuentas registradas como usuarios de prueba.
- Los tokens se guardan sin cifrar en archivos locales.
- La fuente adicional es simulada; no hay una integración real con otra plataforma.
- Pendiente: avisar en la página cuando un permiso vence, revocar el acceso en Google al desconectar y cifrar los tokens.
