package com.rbxnative.androidport;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Runs once to write, then again after force-stop to verify persistence. */
public final class StudioServicesTest extends Instrumentation {
    private String phase;
    private static final String SERVICE = "urn:rbx-native:android-service-test";
    private static final String USER = "account:雪";
    private static final String PASSWORD = "00ff8061007f";
    private static final String AUTHORITY = "com.rbxnative.androidport.servicefixtures";
    @Override public void onCreate(Bundle arguments) { phase = arguments.getString("phase", "write"); start(); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void write(File file, String text) throws Exception { Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8)); }
    private static String read(File file) throws Exception { return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8); }
    private static String expected() { return StudioServices.hex(USER.getBytes(StandardCharsets.UTF_8)) + ":" + PASSWORD; }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Activity activity = null;
        try {
            Intent launch = new Intent(getTargetContext(), ServiceTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = startActivitySync(launch);
            if (phase.equals("write")) {
                StudioServices.credential(activity, "write", SERVICE, USER, PASSWORD);
                require(expected().equals(StudioServices.credential(activity, "read", SERVICE, "", "")), "Keystore byte/username round trip");
                require(StudioServices.credential(activity, "read", SERVICE + "-other", "", "") == null, "Credentials leaked between services");
                for (Object value : activity.getSharedPreferences("studio-credentials-v1", 0).getAll().values()) {
                    String stored = value.toString();
                    require(!stored.contains(USER) && !stored.contains(PASSWORD), "Credential plaintext in preferences");
                }
                File fixtures = new File(activity.getFilesDir(), "service-fixtures");
                require(fixtures.isDirectory() || fixtures.mkdirs(), "Fixture directory");
                write(new File(fixtures, "seed.rbxl"), "original");
                write(new File(fixtures, "readonly.rbxl"), "protected");
                Uri document = DocumentsContract.buildDocumentUri(AUTHORITY, "root/seed.rbxl");
                File local = new File(StudioDocuments.localize(activity, document.toString(), false));
                require(read(local).equals("original"), "SAF read into ordinary Rust path");
                write(local, "edited-on-android");
                StudioDocuments.commit(activity, local.getPath());
                require(read(new File(fixtures, "seed.rbxl")).equals("edited-on-android"), "SAF writeback");
                activity.getSharedPreferences("service-test-state", 0).edit().putString("local", local.getPath()).commit();
                Uri tree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root");
                File project = new File(StudioDocuments.localize(activity, tree.toString(), false));
                File nested = new File(project, "scripts/new/place.rbxl");
                require(nested.getParentFile().mkdirs(), "Nested local project directory");
                write(nested, "project-save");
                StudioDocuments.commit(activity, nested.getPath());
                require(read(new File(fixtures, "scripts/new/place.rbxl")).equals("project-save"), "New document inside SAF project");
                Uri protectedUri = DocumentsContract.buildDocumentUri(AUTHORITY, "root/readonly.rbxl");
                File protectedLocal = new File(StudioDocuments.localize(activity, protectedUri.toString(), false));
                write(protectedLocal, "retained-unsaved-copy");
                boolean failed = false;
                try { StudioDocuments.commit(activity, protectedLocal.getPath()); } catch (Exception expectedFailure) { failed = true; }
                require(failed, "Read-only provider must report a save error");
                require(read(protectedLocal).equals("retained-unsaved-copy"), "Failed write must preserve local work");
                require(read(new File(fixtures, "readonly.rbxl")).equals("protected"), "Failed write must preserve provider file");
            } else {
                require(expected().equals(StudioServices.credential(activity, "read", SERVICE, "", "")), "Keystore did not survive force-stop");
                String path = activity.getSharedPreferences("service-test-state", 0).getString("local", null);
                require(path != null, "Document path mapping missing after force-stop");
                File local = new File(path);
                write(local, "saved-after-process-restart");
                StudioDocuments.commit(activity, local.getPath());
                require(read(new File(activity.getFilesDir(), "service-fixtures/seed.rbxl")).equals("saved-after-process-restart"), "SAF mapping did not survive process restart");
                StudioServices.credential(activity, "delete", SERVICE, "", "");
                require(StudioServices.credential(activity, "read", SERVICE, "", "") == null, "Credential delete");
            }
            result.putString("result", "PASS " + phase);
            if (activity != null) activity.finish();
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("test_error", error.toString());
            android.util.Log.e("StudioServicesTest", "Host service test failed", error);
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
