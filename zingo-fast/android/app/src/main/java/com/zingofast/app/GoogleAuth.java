package com.zingofast.app;

import android.os.CancellationSignal;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import java.util.concurrent.Executor;

/** Google sign-in with the system account picker (Credential Manager). The ID token goes to Firebase Auth in the page. */
final class GoogleAuth {
    interface Done { void result(String idToken, String error); }

    private static final String TYPE = "com.google.android.libraries.identity.googleid.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL";

    private GoogleAuth() { }

    static void signIn(MainActivity act, String webClientId, Done done) {
        if (webClientId == null || !webClientId.matches("^[\\w.-]+\\.apps\\.googleusercontent\\.com$")) { done.result(null, "not_configured"); return; }
        act.runOnUiThread(() -> {
            try {
                CredentialManager cm = CredentialManager.create(act);
                GetSignInWithGoogleOption opt = new GetSignInWithGoogleOption.Builder(webClientId).build();
                GetCredentialRequest req = new GetCredentialRequest.Builder().addCredentialOption(opt).build();
                Executor main = r -> act.runOnUiThread(r);
                cm.getCredentialAsync(act, req, new CancellationSignal(), main,
                        new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                            @Override
                            public void onResult(GetCredentialResponse r) {
                                Credential c = r.getCredential();
                                if (c instanceof CustomCredential && TYPE.equals(c.getType())) {
                                    try {
                                        GoogleIdTokenCredential g = GoogleIdTokenCredential.createFrom(((CustomCredential) c).getData());
                                        done.result(g.getIdToken(), null);
                                    } catch (Exception e) { done.result(null, "bad_credential"); }
                                } else done.result(null, "bad_credential");
                            }

                            @Override
                            public void onError(GetCredentialException e) {
                                done.result(null, e instanceof GetCredentialCancellationException ? "cancelled" : String.valueOf(e.getMessage()));
                            }
                        });
            } catch (Throwable e) { done.result(null, String.valueOf(e.getMessage())); }
        });
    }
}
