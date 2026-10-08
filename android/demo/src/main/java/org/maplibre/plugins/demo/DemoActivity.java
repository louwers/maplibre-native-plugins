package org.maplibre.plugins.demo;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.reflect.InvocationTargetException;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;

/** Registers the plugin under development and shows its demo style. */
public final class DemoActivity extends Activity {
  // tools/bin/plugin run-android waits for these log messages.
  private static final String TAG = "MapLibrePluginDemo";

  private FrameLayout root;
  private MapView mapView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    root = new FrameLayout(this);
    setContentView(root);
    setTitle(BuildConfig.PLUGIN_NAME);

    MapLibre.getInstance(this);
    try {
      Class.forName(BuildConfig.PLUGIN_CLASS).getMethod("register").invoke(null);
      Log.i(TAG, "Registered " + BuildConfig.PLUGIN_CLASS);
    } catch (InvocationTargetException exception) {
      showError("Plugin registration failed", exception.getCause());
      return;
    } catch (ReflectiveOperationException exception) {
      showError("Plugin registration class not found", exception);
      return;
    }

    mapView = new MapView(this);
    root.addView(mapView);
    mapView.onCreate(savedInstanceState);
    mapView.addOnDidFailLoadingMapListener(message -> showError("Map failed to load: " + message, null));
    mapView.addOnDidBecomeIdleListener(() -> Log.i(TAG, "Map idle"));
    mapView.getMapAsync(map -> {
      if (BuildConfig.HAS_CAMERA) {
        map.setCameraPosition(new CameraPosition.Builder()
            .target(new LatLng(BuildConfig.LATITUDE, BuildConfig.LONGITUDE))
            .zoom(BuildConfig.ZOOM)
            .build());
      }
      map.setStyle(new Style.Builder().fromUri(BuildConfig.STYLE_URI),
          style -> Log.i(TAG, "Style loaded: " + BuildConfig.STYLE_URI));
    });
  }

  private void showError(String message, Throwable cause) {
    Log.e(TAG, message, cause);
    TextView text = new TextView(this);
    text.setText(cause == null ? message : message + "\n\n" + cause);
    text.setTextColor(Color.RED);
    text.setGravity(Gravity.CENTER);
    text.setPadding(48, 48, 48, 48);
    root.addView(text, new FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
  }

  @Override protected void onStart() { super.onStart(); if (mapView != null) mapView.onStart(); }
  @Override protected void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
  @Override protected void onPause() { if (mapView != null) mapView.onPause(); super.onPause(); }
  @Override protected void onStop() { if (mapView != null) mapView.onStop(); super.onStop(); }
  @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }
  @Override protected void onDestroy() { if (mapView != null) mapView.onDestroy(); super.onDestroy(); }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    if (mapView != null) mapView.onSaveInstanceState(outState);
  }
}
