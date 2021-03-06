package com.disarm.surakshit.collectgis.Model;

import com.mapbox.mapboxsdk.geometry.LatLng;

import java.util.List;

/**
 * Created by bishakh on 6/28/18.
 */

public class KmlObject {
    private int zoom;
    private String tileName;
    private int type; // point / polygon
    private List<LatLng> points;
    private String message;
    private String source;

    public static final int KMLOBJECT_TYPE_POLYGON = 0;
    public static final int KMLOBJECT_TYPE_MARKER = 1;

    public KmlObject() {
    }

    public KmlObject(int zoom, int type, List<LatLng> points, String message, String source, String tileName) {
        this.zoom = zoom;
        this.type = type;
        this.points = points;
        this.message = message;
        this.source = source;
        this.tileName = tileName;
    }

    @Override
    public String toString() {
        return tileName + ":" + message;
    }

    public int getZoom() {
        return zoom;
    }

    public String getTileName() {
        return tileName;
    }

    public void setTileName(String tileName) {
        this.tileName = tileName;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setZoom(int zoom) {
        this.zoom = zoom;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public List<LatLng> getPoints() {
        return points;
    }

    public void setPoints(List<LatLng> points) {
        this.points = points;
    }
}
