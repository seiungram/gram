package org.telegram.messenger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.location.Location;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.util.Consumer;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.util.List;

public class OsmdroidMapsProvider implements IMapsProvider {

    @Override
    public void initializeMaps(Context context) {
        Configuration.getInstance().setUserAgentValue(context.getPackageName());
    }

    @Override
    public IMapView onCreateMapView(Context context) {
        return new OsmMapView(context);
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLng(LatLng latLng) {
        return new OsmCameraUpdate(latLng.latitude, latLng.longitude, -1);
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngZoom(LatLng latLng, float zoom) {
        return new OsmCameraUpdate(latLng.latitude, latLng.longitude, zoom);
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngBounds(ILatLngBounds bounds, int padding) {
        LatLng center = bounds.getCenter();
        return new OsmCameraUpdate(center.latitude, center.longitude, 15);
    }

    @Override
    public ILatLngBoundsBuilder onCreateLatLngBoundsBuilder() {
        return new OsmLatLngBoundsBuilder();
    }

    @Override
    public IMapStyleOptions loadRawResourceStyle(Context context, int resId) {
        return new IMapStyleOptions() {};
    }

    @Override
    public String getMapsAppPackageName() {
        return "org.microg.vending"; // fallback or dummy
    }

    @Override
    public int getInstallMapsString() {
        return R.string.OK;
    }

    @Override
    public IMarkerOptions onCreateMarkerOptions() {
        return new OsmMarkerOptions();
    }

    @Override
    public ICircleOptions onCreateCircleOptions() {
        return new OsmCircleOptions();
    }

    // --- Sub-classes implementing interfaces ---

    public static class OsmCameraUpdate implements ICameraUpdate {
        final double lat, lon;
        final float zoom;
        public OsmCameraUpdate(double lat, double lon, float zoom) {
            this.lat = lat;
            this.lon = lon;
            this.zoom = zoom;
        }
    }

    public static class OsmLatLngBoundsBuilder implements ILatLngBoundsBuilder {
        private double minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;

        @Override
        public ILatLngBoundsBuilder include(LatLng latLng) {
            minLat = Math.min(minLat, latLng.latitude);
            maxLat = Math.max(maxLat, latLng.latitude);
            minLon = Math.min(minLon, latLng.longitude);
            maxLon = Math.max(maxLon, latLng.longitude);
            return this;
        }

        @Override
        public ILatLngBounds build() {
            return () -> new LatLng((minLat + maxLat) / 2, (minLon + maxLon) / 2);
        }
    }

    public static class OsmMarkerOptions implements IMarkerOptions {
        LatLng position;
        Bitmap iconBitmap;
        String title;
        String snippet;

        @Override public IMarkerOptions position(LatLng latLng) { this.position = latLng; return this; }
        @Override public IMarkerOptions icon(Bitmap bitmap) { this.iconBitmap = bitmap; return this; }
        @Override public IMarkerOptions icon(int resId) { return this; }
        @Override public IMarkerOptions anchor(float lat, float lng) { return this; }
        @Override public IMarkerOptions title(String title) { this.title = title; return this; }
        @Override public IMarkerOptions snippet(String snippet) { this.snippet = snippet; return this; }
        @Override public IMarkerOptions flat(boolean flat) { return this; }
    }

    public static class OsmCircleOptions implements ICircleOptions {
        LatLng center;
        double radius;
        int strokeColor, fillColor, strokeWidth;

        @Override public ICircleOptions center(LatLng latLng) { this.center = latLng; return this; }
        @Override public ICircleOptions radius(double radius) { this.radius = radius; return this; }
        @Override public ICircleOptions strokeColor(int color) { this.strokeColor = color; return this; }
        @Override public ICircleOptions fillColor(int color) { this.fillColor = color; return this; }
        @Override public ICircleOptions strokePattern(List<PatternItem> patternItems) { return this; }
        @Override public ICircleOptions strokeWidth(int width) { this.strokeWidth = width; return this; }
    }

    public static class OsmMapView implements IMapView {
        private final MapView mapView;
        public OsmMapView(Context context) {
            mapView = new MapView(context);
            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.setMultiTouchControls(true);
        }

        @Override public View getView() { return mapView; }
        @Override public void getMapAsync(Consumer<IMap> callback) { callback.accept(new OsmMapImpl(mapView)); }
        @Override public void onResume() { mapView.onResume(); }
        @Override public void onPause() { mapView.onPause(); }
        @Override public void onCreate(android.os.Bundle savedInstance) {}
        @Override public void onDestroy() { mapView.onDetach(); }
        @Override public void onLowMemory() {}
        @Override public void setOnDispatchTouchEventInterceptor(ITouchInterceptor interceptor) {}
        @Override public void setOnInterceptTouchEventInterceptor(ITouchInterceptor interceptor) {}
        @Override public void setOnLayoutListener(Runnable callback) {}
    }

    public static class OsmMapImpl implements IMap {
        private final MapView mapView;
        public OsmMapImpl(MapView mapView) { this.mapView = mapView; }

        @Override public void setMapType(int mapType) {}
        @Override public float getMaxZoomLevel() { return (float) mapView.getMaxZoomLevel(); }
        @Override public float getMinZoomLevel() { return (float) mapView.getMinZoomLevel(); }
        @Override public void setMyLocationEnabled(boolean enabled) {}
        @Override public IUISettings getUiSettings() {
            return new IUISettings() {
                @Override public void setMyLocationButtonEnabled(boolean enabled) {}
                @Override public void setZoomControlsEnabled(boolean enabled) {}
                @Override public void setCompassEnabled(boolean enabled) {}
            };
        }
        @Override public void setOnCameraMoveStartedListener(OnCameraMoveStartedListener listener) {}
        @Override public void setOnCameraIdleListener(Runnable callback) {}
        @Override public CameraPosition getCameraPosition() {
            GeoPoint p = (GeoPoint) mapView.getMapCenter();
            return new CameraPosition(new LatLng(p.getLatitude(), p.getLongitude()), (float) mapView.getZoomLevelDouble());
        }
        @Override public void setOnMapLoadedCallback(Runnable callback) { if (callback != null) callback.run(); }
        @Override public IProjection getProjection() {
            return latLng -> {
                Point pt = new Point();
                mapView.getProjection().toPixels(new GeoPoint(latLng.latitude, latLng.longitude), pt);
                return pt;
            };
        }
        @Override public void setPadding(int left, int top, int right, int bottom) {}
        @Override public void setMapStyle(IMapStyleOptions style) {}

        @Override public IMarker addMarker(IMarkerOptions markerOptions) {
            OsmMarkerOptions options = (OsmMarkerOptions) markerOptions;
            Marker marker = new Marker(mapView);
            if (options.position != null) {
                marker.setPosition(new GeoPoint(options.position.latitude, options.position.longitude));
            }
            if (options.iconBitmap != null) {
                marker.setIcon(new android.graphics.drawable.BitmapDrawable(mapView.getResources(), options.iconBitmap));
            }
            mapView.getOverlays().add(marker);
            mapView.invalidate();
            return new IMarker() {
                Object tag;
                @Override public Object getTag() { return tag; }
                @Override public void setTag(Object tag) { this.tag = tag; }
                @Override public LatLng getPosition() {
                    GeoPoint gp = marker.getPosition();
                    return new LatLng(gp.getLatitude(), gp.getLongitude());
                }
                @Override public void setPosition(LatLng latLng) {
                    marker.setPosition(new GeoPoint(latLng.latitude, latLng.longitude));
                    mapView.invalidate();
                }
                @Override public void setRotation(int rotation) { marker.setRotation(rotation); }
                @Override public void setIcon(Bitmap bitmap) {
                    marker.setIcon(new android.graphics.drawable.BitmapDrawable(mapView.getResources(), bitmap));
                    mapView.invalidate();
                }
                @Override public void setIcon(int resId) {
                    marker.setIcon(mapView.getContext().getDrawable(resId));
                    mapView.invalidate();
                }
                @Override public void remove() {
                    mapView.getOverlays().remove(marker);
                    mapView.invalidate();
                }
            };
        }

        @Override public ICircle addCircle(ICircleOptions circleOptions) {
            OsmCircleOptions opt = (OsmCircleOptions) circleOptions;
            List<GeoPoint> pts = Polygon.pointsAsCircle(new GeoPoint(opt.center.latitude, opt.center.longitude), opt.radius);
            Polygon polygon = new Polygon(mapView);
            polygon.setPoints(pts);
            polygon.getFillPaint().setColor(opt.fillColor);
            polygon.getOutlinePaint().setColor(opt.strokeColor);
            polygon.getOutlinePaint().setStrokeWidth(opt.strokeWidth);
            mapView.getOverlays().add(polygon);
            mapView.invalidate();
            return new ICircle() {
                @Override public void setStrokeColor(int color) { polygon.getOutlinePaint().setColor(color); }
                @Override public void setFillColor(int color) { polygon.getFillPaint().setColor(color); }
                @Override public void setRadius(double radius) { opt.radius = radius; }
                @Override public double getRadius() { return opt.radius; }
                @Override public void setCenter(LatLng latLng) {
                    polygon.setPoints(Polygon.pointsAsCircle(new GeoPoint(latLng.latitude, latLng.longitude), opt.radius));
                    mapView.invalidate();
                }
                @Override public void remove() {
                    mapView.getOverlays().remove(polygon);
                    mapView.invalidate();
                }
            };
        }

        @Override public void setOnMyLocationChangeListener(Consumer<Location> callback) {}
        @Override public void setOnMarkerClickListener(OnMarkerClickListener markerClickListener) {}
        @Override public void setOnCameraMoveListener(Runnable callback) {}
        @Override public void animateCamera(ICameraUpdate update) { moveCamera(update); }
        @Override public void animateCamera(ICameraUpdate update, ICancelableCallback callback) { moveCamera(update); if (callback != null) callback.onFinish(); }
        @Override public void animateCamera(ICameraUpdate update, int duration, ICancelableCallback callback) { moveCamera(update); if (callback != null) callback.onFinish(); }

        @Override public void moveCamera(ICameraUpdate update) {
            if (update instanceof OsmCameraUpdate) {
                OsmCameraUpdate u = (OsmCameraUpdate) update;
                if (u.zoom >= 0) {
                    mapView.getController().setZoom((double) u.zoom);
                }
                mapView.getController().setCenter(new GeoPoint(u.lat, u.lon));
            }
        }
    }
}
