package com.disarm.surakshit.collectgis;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.disarm.surakshit.collectgis.Util.Constants;
import com.disarm.surakshit.collectgis.Util.ConversionUtil;
import com.disarm.surakshit.collectgis.Util.UploadJobService;
import com.disarm.surakshit.collectgis.location.LocationState;
import com.disarm.surakshit.collectgis.location.MLocation;
import com.firebase.jobdispatcher.Constraint;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineListener;
import com.mapbox.android.core.location.LocationEnginePriority;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.mapboxsdk.annotations.BasePointCollection;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.Polygon;
import com.mapbox.mapboxsdk.annotations.PolygonOptions;
import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerOptions;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.mapboxsdk.plugins.locationlayer.OnLocationLayerClickListener;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode;

import org.osmdroid.bonuspack.kml.KmlDocument;
import org.osmdroid.bonuspack.kml.KmlPlacemark;
import org.osmdroid.views.overlay.FolderOverlay;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity implements LocationEngineListener {
    private MapView mapView;
    private FloatingActionButton addButton;
    private FloatingActionButton cancelButton;
    private FloatingActionButton undoButton;
    private int addFlag;
    private CoordinatorLayout mainLayout;
    private ArrayList<Marker> markerList;
    private ArrayList<LatLng> polygonPoints;
    private BasePointCollection currentPolygon;
    private String description;
    private String phoneNumber;
    private Double currentZoom;
    private HashMap<String, Boolean> allPlottedKml;
    private org.osmdroid.views.MapView mMapView;
    private List<PolygonOptions> allPolygons = new ArrayList<>();
    private List<MarkerOptions> allMarkers = new ArrayList<>();
    private List<PolylineOptions> allPolyLines = new ArrayList<>();
    private FirebaseJobDispatcher dispatcher;
    private Boolean isJobInitialized;
    private LocationLayerPlugin locationLayerPlugin;
    private LocationEngine locationEngine;

    private static final int BUTTON_ADD = 0;
    private static final int BUTTON_DRAW = 1;
    private static final int BUTTON_DONE = 2;

    public static final int LOCATION_PERMISSION = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        mapView.onCreate(savedInstanceState);
        mapInit();
        setButton();
        isGPSEnabled();
        showWorkingData();
        //schedule upload jobService
        dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(this));
        isJobInitialized = false;
        scheduleJobService();
    }

    private void isGPSEnabled() {
        if (!LocationState.with(this).locationServicesEnabled()) {
            enableGPS();
        }
        MLocation.subscribe(this);
    }

    private void scheduleJobService() {
        if (isJobInitialized)
            return;
        Job uploadJob = dispatcher.newJobBuilder()
                .setService(UploadJobService.class)
                .setRecurring(true)
                .setLifetime(Lifetime.UNTIL_NEXT_BOOT)
                .setConstraints(Constraint.ON_ANY_NETWORK)
                .setTrigger(Trigger.executionWindow(0, 60))
                .setReplaceCurrent(true)
                .setTag("Upload_Job")
                .setRetryStrategy(RetryStrategy.DEFAULT_EXPONENTIAL).build();
        dispatcher.mustSchedule(uploadJob);
        isJobInitialized = true;
    }

    private void mapInit() {
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                mapboxMap.setCameraPosition(new CameraPosition.Builder()
                        .target(new LatLng(23.5477, 87.2931))
                        .zoom(currentZoom)
                        .build());
                mapboxMap.setMaxZoomPreference(18);
                mapboxMap.setMinZoomPreference(14);
                locationEngine = new LocationEngineProvider(MainActivity.this).obtainBestLocationEngineAvailable();
                locationEngine.setPriority(LocationEnginePriority.HIGH_ACCURACY);
                locationEngine.setFastestInterval(1000);
                locationEngine.addLocationEngineListener(MainActivity.this);
                locationEngine.activate();
                int[] padding;
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                    padding = new int[]{0, 750, 0, 0};
                } else {
                    padding = new int[]{0, 250, 0, 0};
                }
                LocationLayerOptions options = LocationLayerOptions.builder(MainActivity.this)
                        .padding(padding)
                        .build();
                locationLayerPlugin = new LocationLayerPlugin(mapView, mapboxMap, locationEngine, options);
                locationLayerPlugin.addOnLocationClickListener(new OnLocationLayerClickListener() {
                    @Override
                    public void onLocationLayerClick() {
                        Snackbar.make(mainLayout, "My Location", Snackbar.LENGTH_SHORT).show();
                    }
                });
                locationLayerPlugin.setCameraMode(CameraMode.NONE);
                locationLayerPlugin.setRenderMode(RenderMode.COMPASS);
                getLifecycle().addObserver(locationLayerPlugin);
            }
        });
    }

    private void setButton() {
        addFlag = 0;
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(final MapboxMap mapboxMap) {
                mapboxMap.addOnMapClickListener(new MapboxMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(@NonNull LatLng point) {
                        if (addFlag == BUTTON_DRAW) {
                            //add marker in map
                            MarkerOptions markerOptions = new MarkerOptions().position(point);
                            polygonPoints.add(point);
                            markerList.add(mapboxMap.addMarker(markerOptions));
                        }
                    }
                });
            }
        });
        undoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (addFlag == BUTTON_DRAW) {
                    if (polygonPoints.size() != 0 && markerList.size() != 0) {
                        final Marker marker = markerList.get(markerList.size() - 1);
                        markerList.remove(marker);
                        LatLng latLng = polygonPoints.get(polygonPoints.size() - 1);
                        polygonPoints.remove(latLng);
                        mapView.getMapAsync(new OnMapReadyCallback() {
                            @Override
                            public void onMapReady(MapboxMap mapboxMap) {
                                mapboxMap.removeMarker(marker);
                            }
                        });
                    } else {
                        Snackbar.make(mainLayout, "No Marker to remove", Snackbar.LENGTH_SHORT).show();
                    }
                } else if (addFlag == BUTTON_DONE) {
                    addButton.setImageResource(R.drawable.ic_draw_white_24dp);
                    addFlag = BUTTON_DRAW;
                    //remove current polygon
                    mapView.getMapAsync(new OnMapReadyCallback() {
                        @Override
                        public void onMapReady(MapboxMap mapboxMap) {
                            if (currentPolygon instanceof Polygon)
                                mapboxMap.removePolygon((Polygon) currentPolygon);
                            else if (currentPolygon instanceof Polyline)
                                mapboxMap.removePolyline((Polyline) currentPolygon);
                        }
                    });
                }
            }
        });
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doneButtonHelper();
            }
        });
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //for adding marker
                if (addFlag == BUTTON_ADD) {
                    addButton.setImageResource(R.drawable.ic_draw_white_24dp);
                    cancelButton.setVisibility(View.VISIBLE);
                    undoButton.setVisibility(View.VISIBLE);
                    addFlag = BUTTON_DRAW;
                }
                //for drawing
                else if (addFlag == BUTTON_DRAW) {
                    if (polygonPoints.size() == 0) {
                        Snackbar.make(mainLayout, "No Marker to draw", Snackbar.LENGTH_SHORT).show();
                        return;
                    }
                    addButton.setImageResource(R.drawable.ic_done_white_24dp);
                    addFlag = BUTTON_DONE;
                    drawButtonHelper();
                }
                //for saving
                else if (addFlag == BUTTON_DONE) {
                    createDialog();
                }
            }
        });
    }

    private void createDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_place, null);
        final EditText placeEdit = dialogView.findViewById(R.id.dialog_place_edit);
        Button submitButton = dialogView.findViewById(R.id.dialog_place_submit);
        Button cancelButton = dialogView.findViewById(R.id.dialog_place_cancel);
        description = "";
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView).setCancelable(false);
        final AlertDialog dialog = builder.create();
        dialog.show();
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                description = placeEdit.getText().toString();
                if (description.equals("")) {
                    placeEdit.setError("Please provide the name");
                    return;
                }
                KmlDocument kml = new KmlDocument();
                setCurrentZoom();
                String latLng = getLocation(MainActivity.this);
                String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                String file_name = "TXT_50_data_" +
                        phoneNumber +
                        "_defaultMcs_"
                        + latLng
                        + "_" + timeStamp + ".kml";
                if (polygonPoints.size() == 1) {
                    org.osmdroid.views.overlay.Marker marker = new org.osmdroid.views.overlay.Marker(mMapView);
                    marker.setSnippet(description);
                    marker.setPosition(ConversionUtil.getGeoPoint(polygonPoints.get(0)));
                    KmlPlacemark placemark = new KmlPlacemark(marker);
                    kml.mKmlRoot.add(placemark);
                    kml.mKmlRoot.setExtendedData("Media Type", "TXT");
                    kml.mKmlRoot.setExtendedData("Group Type", "data");
                    kml.mKmlRoot.setExtendedData("Time Stamp", timeStamp);
                    kml.mKmlRoot.setExtendedData("Source", phoneNumber);
                    kml.mKmlRoot.setExtendedData("Destination", "defaultMcs");
                    kml.mKmlRoot.setExtendedData("Lat Long", latLng);
                    kml.mKmlRoot.setExtendedData("Group ID", "1");
                    kml.mKmlRoot.setExtendedData("Priority", "50");
                    kml.mKmlRoot.setExtendedData("Zoom", currentZoom.toString());
                    kml.mKmlRoot.setExtendedData("KML Type", "Point");
                } else if (polygonPoints.size() > 1) {
                    org.osmdroid.views.overlay.Polygon polygon = new org.osmdroid.views.overlay.Polygon();
                    polygon.setPoints(ConversionUtil.getGeoPointList(polygonPoints));
                    polygon.setSnippet(description);
                    kml.mKmlRoot.setExtendedData("Media Type", "TXT");
                    kml.mKmlRoot.setExtendedData("Group Type", "data");
                    kml.mKmlRoot.setExtendedData("Time Stamp", timeStamp);
                    kml.mKmlRoot.setExtendedData("Source", phoneNumber);
                    kml.mKmlRoot.setExtendedData("Destination", "defaultMcs");
                    kml.mKmlRoot.setExtendedData("Lat Long", latLng);
                    kml.mKmlRoot.setExtendedData("Group ID", "1");
                    kml.mKmlRoot.setExtendedData("Priority", "50");
                    kml.mKmlRoot.setExtendedData("Zoom", currentZoom.toString());
                    kml.mKmlRoot.setExtendedData("KML Type", "Polygon");
                    kml.mKmlRoot.addOverlay(polygon, kml);
                }
                File file = Environment.getExternalStoragePublicDirectory(Constants.CMS_WORKING + file_name);
                File tmpFile = Environment.getExternalStoragePublicDirectory(Constants.CMS_TEMP_KML + file_name);
                kml.saveAsKML(file);
                kml.saveAsKML(tmpFile);
                showWorkingData();
                doneButtonHelper();
                dialog.dismiss();
            }
        });
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doneButtonHelper();
                dialog.dismiss();
            }
        });
    }

    private void setCurrentZoom() {
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                currentZoom = mapboxMap.getCameraPosition().zoom;
            }
        });
    }

    private void drawButtonHelper() {
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                if (polygonPoints.size() > 2) {
                    PolygonOptions polygonOptions = new PolygonOptions().addAll(polygonPoints)
                            .add(polygonPoints.get(0))
                            .alpha((float) 0.5)
                            .fillColor(R.color.transparent);
                    currentPolygon = mapboxMap.addPolygon(polygonOptions);
                } else {
                    if (polygonPoints.size() == 2) {
                        PolylineOptions polylineOptions = new PolylineOptions().addAll(polygonPoints)
                                .color(R.color.black).width(3);
                        currentPolygon = mapboxMap.addPolyline(polylineOptions);
                    }
                }
            }
        });
    }

    private void doneButtonHelper() {
        addButton.setImageResource(R.drawable.ic_add_white_24dp);
        cancelButton.setVisibility(View.GONE);
        undoButton.setVisibility(View.GONE);
        //remove all markers and polygons
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                for (Marker marker : markerList)
                    mapboxMap.removeMarker(marker);
                if (currentPolygon instanceof Polygon)
                    mapboxMap.removePolygon((Polygon) currentPolygon);
                else if (currentPolygon instanceof Polyline)
                    mapboxMap.removePolyline((Polyline) currentPolygon);
                polygonPoints.clear();
                markerList.clear();
            }
        });
        addFlag = BUTTON_ADD;
    }

    private void showWorkingData() {

        File working = Environment.getExternalStoragePublicDirectory(Constants.CMS_WORKING);
        File[] allFiles = working.listFiles();
        for (File file : allFiles) {
            if (allPlottedKml.containsKey(file.getName()))
                continue;
            allPlottedKml.put(file.getName(), true);
            KmlDocument kml = new KmlDocument();
            if (file.getName().contains("kml")) {
                Log.d("working data", "filename:" + file.getName());
                kml.parseKMLFile(file);
                final FolderOverlay kmlOverlay = (FolderOverlay) kml.mKmlRoot.buildOverlay(mMapView, null, null, kml);
                Log.d("working data", "kml overlay size:" + kmlOverlay.getItems().size());
                for (int i = 0; i < kmlOverlay.getItems().size(); i++) {
                    if (kmlOverlay.getItems().get(i) instanceof org.osmdroid.views.overlay.Polygon) {
                        List<LatLng> polyPoints = ConversionUtil.getLatLngList(((org.osmdroid.views.overlay.Polygon) kmlOverlay.getItems().get(i)).getPoints());
                        String snippet = ((org.osmdroid.views.overlay.Polygon) kmlOverlay.getItems().get(i)).getSnippet();
                        if (polyPoints.size() > 3) {
                            allPolygons.add(new PolygonOptions().addAll(polyPoints)
                                    .alpha((float) 0.5)
                                    .fillColor(R.color.transparent));
                        } else if (polyPoints.size() == 3) {
                            allPolyLines.add(new PolylineOptions().add(polyPoints.get(0))
                                    .add(polyPoints.get(1))
                                    .color(R.color.black)
                                    .width(3));
                        }
                    } else if (kmlOverlay.getItems().get(i) instanceof org.osmdroid.views.overlay.Marker) {
                        LatLng point = ConversionUtil.getLatLng(((org.osmdroid.views.overlay.Marker) kmlOverlay.getItems().get(i)).getPosition());
                        String snippet = ((org.osmdroid.views.overlay.Marker) kmlOverlay.getItems().get(i)).getSnippet();
                        allMarkers.add(new MarkerOptions().position(point).setSnippet(snippet));
                    }
                }
            }
        }
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                if (!allPolygons.isEmpty())
                    mapboxMap.addPolygons(allPolygons);
                if (!allPolyLines.isEmpty())
                    mapboxMap.addPolylines(allPolyLines);
                if (!allMarkers.isEmpty())
                    mapboxMap.addMarkers(allMarkers);
                allPolygons.clear();
                allPolyLines.clear();
                allMarkers.clear();
            }
        });
    }

    private void init() {
        mapView = findViewById(R.id.mapView);
        mainLayout = findViewById(R.id.main_layout);
        addButton = findViewById(R.id.main_add_fab);
        cancelButton = findViewById(R.id.main_cancel_fab);
        undoButton = findViewById(R.id.main_undo_fab);
        markerList = new ArrayList<>();
        polygonPoints = new ArrayList<>();
        allPlottedKml = new HashMap<>();
        allPolygons = new ArrayList<>();
        allMarkers = new ArrayList<>();
        allPolyLines = new ArrayList<>();
        currentPolygon = null;
        currentZoom = 15.0;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        phoneNumber = preferences.getString(Constants.PHONE_NO, "");
        mMapView = new org.osmdroid.views.MapView(MainActivity.this);
    }

    public void enableGPS() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder
                .setMessage(R.string.gps_msg)
                .setCancelable(false)
                .setTitle("Turn on Location")
                .setPositiveButton(R.string.enable_gps,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int id) {
                                Intent callGPSSettingIntent = new Intent(
                                        Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivityForResult(callGPSSettingIntent, LOCATION_PERMISSION);
                            }
                        });
        alertDialogBuilder.setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        finish();
                    }
                });
        AlertDialog alert = alertDialogBuilder.create();
        alert.show();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == LOCATION_PERMISSION && resultCode == 0) {
            String provider = Settings.Secure.getString(getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            if (provider != null) {
                switch (provider.length()) {
                    case 0:
                        //GPS still not enabled..
                        Toast.makeText(this, "Please enable GPS!!!", Toast.LENGTH_SHORT).show();
                        break;
                    default:
                        MLocation.subscribe(this);
                        if (locationEngine.isConnected())
                            locationEngine.activate();
                        Toast.makeText(this, "GPS is enabled.", Toast.LENGTH_LONG).show();
                        break;
                }
            }
        } else {
            //the user did not enable his GPS
            enableGPS();
        }
    }

    public String getLocation(Context context) {
        Location l = MLocation.getLocation(context);
        Location location = locationEngine.getLastLocation();
        String lat_long = null;
        if (l != null) {
            double lat = l.getLatitude();
            double lon = l.getLongitude();
            boolean hasLatLon = (lat != 0.0d) || (lon != 0.0d);
            if (hasLatLon) {
                Log.v("lat_lon", String.valueOf(l.getLatitude() + "_" + l.getLongitude()));
                lat_long = String.valueOf(l.getLatitude() + "_" + l.getLongitude());
            }
        }
        return lat_long;
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
        if (locationEngine != null) {
            locationEngine.requestLocationUpdates();
            locationEngine.addLocationEngineListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
        if (locationEngine != null) {
            locationEngine.removeLocationEngineListener(this);
            locationEngine.removeLocationUpdates();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        MLocation.unsubscribe(this);
        if (locationEngine != null) {
            locationEngine.deactivate();
        }
    }

    @Override
    public void onConnected() {
        locationEngine.requestLocationUpdates();
    }

    @Override
    public void onLocationChanged(final Location location) {
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                mapboxMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(location.getLatitude(), location.getLongitude()), mapboxMap.getCameraPosition().zoom));
            }
        });
    }
}
