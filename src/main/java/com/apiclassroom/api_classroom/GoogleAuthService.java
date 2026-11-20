package com.apiclassroom.api_classroom;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.classroom.Classroom;
import com.google.api.services.classroom.ClassroomScopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Maneja el inicio de sesión con Google y guarda un token por usuario (clave = correo)
 * en la carpeta "tokens". Así varios estudiantes pueden usar la misma app y nadie
 * tiene que borrar carpetas.
 */
@Service
public class GoogleAuthService {

    public record ResultadoLogin(String email, boolean necesitaConsentimiento) {
    }

    private static final String APP_NAME = "ApiClassroom";

    private static final List<String> SCOPES = List.of(
            "openid",
            "https://www.googleapis.com/auth/userinfo.email",
            ClassroomScopes.CLASSROOM_COURSES_READONLY,
            ClassroomScopes.CLASSROOM_COURSEWORK_ME_READONLY,
            CalendarScopes.CALENDAR_EVENTS);

    private final JsonFactory json = GsonFactory.getDefaultInstance();
    private final String redirectUri;
    private final String tokensDir;

    private HttpTransport transport;
    private GoogleAuthorizationCodeFlow flow;

    public GoogleAuthService(@Value("${app.base-url}") String baseUrl,
                             @Value("${app.tokens-dir}") String tokensDir) {
        this.redirectUri = baseUrl.replaceAll("/+$", "") + "/oauth2/callback";
        this.tokensDir = tokensDir;
    }

    /** Se construye la primera vez que se necesita (así la app arranca aunque falte credentials.json). */
    private synchronized GoogleAuthorizationCodeFlow flow() {
        if (flow == null) {
            try (InputStream in = GoogleAuthService.class.getResourceAsStream("/credentials.json")) {
                if (in == null) {
                    throw new IllegalStateException(
                            "No se encontró credentials.json en src/main/resources (debe ser un cliente OAuth de tipo 'Aplicación web').");
                }
                GoogleClientSecrets secrets = GoogleClientSecrets.load(json,
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                transport = GoogleNetHttpTransport.newTrustedTransport();
                flow = new GoogleAuthorizationCodeFlow.Builder(transport, json, secrets, SCOPES)
                        .setDataStoreFactory(new FileDataStoreFactory(new File(tokensDir)))
                        .setAccessType("offline")
                        .build();
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo iniciar la autenticación con Google: " + e.getMessage(), e);
            }
        }
        return flow;
    }

    /** URL de Google a la que se manda al usuario para que acepte los permisos. */
    public String urlDeLogin(String state, boolean forzarConsentimiento) {
        GoogleAuthorizationCodeRequestUrl url = flow().newAuthorizationUrl();
        url.setRedirectUri(redirectUri);
        url.setState(state);
        if (forzarConsentimiento) {
            url.setApprovalPrompt("force");
        }
        return url.build();
    }

    /** Cambia el "code" que devuelve Google por tokens y los guarda bajo el correo del usuario. */
    public ResultadoLogin procesarCallback(String code) throws IOException {
        GoogleAuthorizationCodeFlow f = flow();
        GoogleTokenResponse tokens = f.newTokenRequest(code).setRedirectUri(redirectUri).execute();
        if (tokens.getIdToken() == null) {
            throw new IllegalStateException("Google no devolvió el correo del usuario (faltan los scopes openid/email).");
        }
        String email = tokens.parseIdToken().getPayload().getEmail().toLowerCase();

        if (tokens.getRefreshToken() == null) {
            // Google solo entrega refresh token la primera vez que el usuario acepta.
            Credential existente = f.loadCredential(email);
            if (existente != null && existente.getRefreshToken() != null) {
                tokens.setRefreshToken(existente.getRefreshToken());
            } else {
                return new ResultadoLogin(email, true);
            }
        }
        f.createAndStoreCredential(tokens, email);
        return new ResultadoLogin(email, false);
    }

    public boolean estaConectado(String userId) {
        try {
            Credential c = flow().loadCredential(userId);
            return c != null && c.getRefreshToken() != null;
        } catch (IOException e) {
            return false;
        }
    }

    public Credential credencial(String userId) throws IOException {
        Credential c = flow().loadCredential(userId);
        if (c == null) {
            throw new IllegalStateException("El usuario " + userId + " no ha iniciado sesión.");
        }
        return c;
    }

    public Classroom classroom(String userId) throws IOException {
        return new Classroom.Builder(transport(), json, credencial(userId))
                .setApplicationName(APP_NAME).build();
    }

    public Calendar calendar(String userId) throws IOException {
        return new Calendar.Builder(transport(), json, credencial(userId))
                .setApplicationName(APP_NAME).build();
    }

    /** Todos los usuarios que ya iniciaron sesión alguna vez. */
    public Set<String> usuarios() {
        try {
            return flow().getCredentialDataStore().keySet();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer la carpeta de tokens: " + e.getMessage(), e);
        }
    }

    public void desconectar(String userId) throws IOException {
        flow().getCredentialDataStore().delete(userId);
    }

    private HttpTransport transport() {
        flow(); // asegura que transport ya existe
        return transport;
    }
}
