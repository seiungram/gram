package org.telegram.messenger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.DashPathEffect;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.util.Consumer;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class OsmdroidMapsProvider implements IMapsProvider {

    @Override
    public void initializeMaps(Context context) {
        if (context == null) {
            return;
        }

        Configuration.getInstance().setUserAgentValue(context.getPackageName());

        File basePath = new File(context.getCacheDir(), "osmdroid");
        File tileCache = new File(basePath, "tiles");
        if (!basePath.exists()) {
            basePath.mkdirs();
        }
        if (!tileCache.exists()) {
            tileCache.mkdirs();
        }
        Configuration.getInstance().setOsmdroidBasePath(basePath);
        Configuration.getInstance().setOsmdroidTileCache(tileCache);
    }

    @Override
    public IMapView onCreateMapView(Context context) {
        initializeMaps(context.getApplicationContext());
        return new OsmMapView(context);
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLng(LatLng latLng) {
        return new OsmCameraUpdate(latLng.latitude, latLng.longitude, -1, null, 0);
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngZoom(LatLng latLng, float zoom) {
        return new OsmCameraUpdate(latLng.latitude, latLng.longitude, zoom, null, 0);
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngBounds(ILatLngBounds bounds, int padding) {
        if (bounds instanceof OsmLatLngBounds) {
            OsmLatLngBounds osmBounds = (OsmLatLngBounds) bounds;
            LatLng center = osmBounds.getCenter();
            return new OsmCameraUpdate(
                    center.latitude, center.longitude, -1, osmBounds, Math.max(0, padding));
        }
        LatLng center = bounds.getCenter();
        return new OsmCameraUpdate(center.latitude, center.longitude, -1, null, Math.max(0, padding));
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
        return null;
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

    public static final class OsmCameraUpdate implements ICameraUpdate {
        final double latitude;
        final double longitude;
        final float zoom;
        final OsmLatLngBounds bounds;
        final int padding;

        OsmCameraUpdate(
                double latitude,
                double longitude,
                float zoom,
                OsmLatLngBounds bounds,
                int padding) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.zoom = zoom;
            this.bounds = bounds;
            this.padding = padding;
        }
    }

    public static final class OsmLatLngBoundsBuilder implements ILatLngBoundsBuilder {
        private double minLatitude = 90;
        private double maxLatitude = -90;
        private double minLongitude = 180;
        private double maxLongitude = -180;
        private boolean hasPoints;

        @Override
        public ILatLngBoundsBuilder include(LatLng latLng) {
            if (latLng == null) {
                return this;
            }
            minLatitude = Math.min(minLatitude, latLng.latitude);
            maxLatitude = Math.max(maxLatitude, latLng.latitude);
            minLongitude = Math.min(minLongitude, latLng.longitude);
            maxLongitude = Math.max(maxLongitude, latLng.longitude);
            hasPoints = true;
            return this;
        }

        @Override
        public ILatLngBounds build() {
            if (!hasPoints) {
                return new OsmLatLngBounds(new LatLng(0, 0), 0, 0, 0, 0);
            }
            return new OsmLatLngBounds(
                    new LatLng(
                            (minLatitude + maxLatitude) / 2,
                            (minLongitude + maxLongitude) / 2),
                    minLatitude,
                    maxLatitude,
                    minLongitude,
                    maxLongitude);
        }
    }

    public static final class OsmLatLngBounds implements ILatLngBounds {
        private final LatLng center;
        final double minLatitude;
        final double maxLatitude;
        final double minLongitude;
        final double maxLongitude;

        OsmLatLngBounds(
                LatLng center,
                double minLatitude,
                double maxLatitude,
                double minLongitude,
                double maxLongitude) {
            this.center = center;
            this.minLatitude = minLatitude;
            this.maxLatitude = maxLatitude;
            this.minLongitude = minLongitude;
            this.maxLongitude = maxLongitude;
        }

        @Override
        public LatLng getCenter() {
            return center;
        }
    }

    public static final class OsmMarkerOptions implements IMarkerOptions {
        LatLng position;
        Bitmap iconBitmap;
        int iconResource;
        float anchorU = Marker.ANCHOR_CENTER;
        float anchorV = Marker.ANCHOR_CENTER;
        boolean flat;
        String title;
        String snippet;

        @Override
        public IMarkerOptions position(LatLng latLng) {
            position = latLng;
            return this;
        }

        @Override
        public IMarkerOptions icon(Bitmap bitmap) {
            iconBitmap = bitmap;
            iconResource = 0;
            return this;
        }

        @Override
        public IMarkerOptions icon(int resId) {
            iconResource = resId;
            iconBitmap = null;
            return this;
        }

        @Override
        public IMarkerOptions anchor(float u, float v) {
            anchorU = u;
            anchorV = v;
            return this;
        }

        @Override
        public IMarkerOptions title(String value) {
            title = value;
            return this;
        }

        @Override
        public IMarkerOptions snippet(String value) {
            snippet = value;
            return this;
        }

        @Override
        public IMarkerOptions flat(boolean value) {
            flat = value;
            return this;
        }
    }

    public static final class OsmCircleOptions implements ICircleOptions {
        LatLng center;
        double radius;
        int strokeColor;
        int fillColor;
        int strokeWidth = 1;
        List<PatternItem> strokePattern;

        @Override
        public ICircleOptions center(LatLng latLng) {
            center = latLng;
            return this;
        }

        @Override
        public ICircleOptions radius(double value) {
            radius = Math.max(0, value);
            return this;
        }

        @Override
        public ICircleOptions strokeColor(int color) {
            strokeColor = color;
            return this;
        }

        @Override
        public ICircleOptions fillColor(int color) {
            fillColor = color;
            return this;
        }

        @Override
        public ICircleOptions strokePattern(List<PatternItem> patternItems) {
            strokePattern = patternItems;
            return this;
        }

        @Override
        public ICircleOptions strokeWidth(int width) {
            strokeWidth = Math.max(0, width);
            return this;
        }
    }

    public static final class OsmMapView implements IMapView {
        private final TelegramMapView mapView;
        private final OsmMapImpl map;
        private boolean myLocationEnabled;
        private boolean locationUpdatesStarted;
        private ILocationServiceProvider.ILocationRequest locationRequest;
        private ILocationServiceProvider.ILocationListener locationListener;
        private Consumer<Location> locationCallback;
        private Location lastLocation;

        public OsmMapView(Context context) {
            mapView = new TelegramMapView(context);
            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.setMultiTouchControls(true);
            mapView.setFlingEnabled(true);
            map = new OsmMapImpl(this, mapView);
            mapView.setMapListener(map.mapListener);
        }

        @Override
        public View getView() {
            return mapView;
        }

        @Override
        public void getMapAsync(Consumer<IMap> callback) {
            if (callback != null) {
                callback.accept(map);
            }
        }

        @Override
        public void onResume() {
            mapView.onResume();
            if (myLocationEnabled) {
                startLocationUpdates();
            }
        }

        @Override
        public void onPause() {
            stopLocationUpdates();
            mapView.onPause();
        }

        @Override
        public void onCreate(Bundle savedInstance) {
        }

        @Override
        public void onDestroy() {
            stopLocationUpdates();
            map.destroy();
            mapView.onDetach();
        }

        @Override
        public void onLowMemory() {
            mapView.getTileProvider().clearTileCache();
        }

        @Override
        public void setOnDispatchTouchEventInterceptor(ITouchInterceptor interceptor) {
            mapView.dispatchTouchEventInterceptor = interceptor;
        }

        @Override
        public void setOnInterceptTouchEventInterceptor(ITouchInterceptor interceptor) {
            mapView.interceptTouchEventInterceptor = interceptor;
        }

        @Override
        public void setOnLayoutListener(Runnable callback) {
            mapView.layoutListener = callback;
        }

        void setMyLocationEnabled(boolean enabled) {
            myLocationEnabled = enabled;
            if (enabled) {
                stopLocationUpdates();
                startLocationUpdates();
            } else {
                stopLocationUpdates();
            }
        }

        void setOnMyLocationChangeListener(Consumer<Location> callback) {
            locationCallback = callback;
            if (callback != null && lastLocation != null) {
                callback.accept(lastLocation);
            }
        }

        private void startLocationUpdates() {
            if (!myLocationEnabled || locationUpdatesStarted) {
                return;
            }
            try {
                ILocationServiceProvider provider = ApplicationLoader.getLocationServiceProvider();
                if (locationRequest == null) {
                    locationRequest = provider.onCreateLocationRequest();
                    locationRequest.setPriority(ILocationServiceProvider.PRIORITY_HIGH_ACCURACY);
                    locationRequest.setInterval(1000);
                    locationRequest.setFastestInterval(1000);
                }
                if (locationListener == null) {
                    locationListener = location -> {
                        if (location == null) {
                            return;
                        }
                        lastLocation = new Location(location);
                        if (locationCallback != null) {
                            locationCallback.accept(lastLocation);
                        }
                    };
                }
                provider.getLastLocation(location -> {
                    if (location != null) {
                        lastLocation = new Location(location);
                        if (locationCallback != null) {
                            locationCallback.accept(lastLocation);
                        }
                    }
                });
                provider.requestLocationUpdates(locationRequest, locationListener);
                locationUpdatesStarted = true;
            } catch (SecurityException | IllegalArgumentException ignored) {
                locationUpdatesStarted = false;
            } catch (Throwable e) {
                FileLog.e(e);
                locationUpdatesStarted = false;
            }
        }

        private void stopLocationUpdates() {
            if (!locationUpdatesStarted || locationListener == null) {
                return;
            }
            try {
                ApplicationLoader.getLocationServiceProvider().removeLocationUpdates(locationListener);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            locationUpdatesStarted = false;
        }

        private static final class TelegramMapView extends MapView {
            private ITouchInterceptor dispatchTouchEventInterceptor;
            private ITouchInterceptor interceptTouchEventInterceptor;
            private Runnable layoutListener;

            TelegramMapView(Context context) {
                super(context);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (dispatchTouchEventInterceptor != null) {
                    return dispatchTouchEventInterceptor.onInterceptTouchEvent(
                            event, value -> super.dispatchTouchEvent(value));
                }
                return super.dispatchTouchEvent(event);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                if (interceptTouchEventInterceptor != null) {
                    return interceptTouchEventInterceptor.onInterceptTouchEvent(
                            event, value -> super.onInterceptTouchEvent(value));
                }
                return super.onInterceptTouchEvent(event);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (layoutListener != null) {
                    post(layoutListener);
                }
            }
        }
    }

    public static final class OsmMapImpl implements IMap {
        private final OsmMapView owner;
        private final MapView mapView;
        private final List<OsmMarker> markers = new ArrayList<>();
        private OnCameraMoveStartedListener cameraMoveStartedListener;
        private Runnable cameraIdleListener;
        private Runnable cameraMoveListener;
        private OnMarkerClickListener markerClickListener;
        private int programmaticEvents;
        private final Runnable idleRunnable = () -> {
            if (cameraIdleListener != null) {
                cameraIdleListener.run();
            }
        };

        private final MapListener mapListener = new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                onMapChanged();
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                onMapChanged();
                return false;
            }
        };

        OsmMapImpl(OsmMapView owner, MapView mapView) {
            this.owner = owner;
            this.mapView = mapView;
        }

        private void onMapChanged() {
            int reason = programmaticEvents > 0
                    ? OnCameraMoveStartedListener.REASON_API_ANIMATION
                    : OnCameraMoveStartedListener.REASON_GESTURE;
            if (programmaticEvents > 0) {
                programmaticEvents--;
            }
            if (cameraMoveStartedListener != null) {
                cameraMoveStartedListener.onCameraMoveStarted(reason);
            }
            if (cameraMoveListener != null) {
                cameraMoveListener.run();
            }
            mapView.removeCallbacks(idleRunnable);
            mapView.postDelayed(idleRunnable, 100);
        }

        private void markProgrammaticMove() {
            programmaticEvents = 2;
            mapView.postDelayed(() -> programmaticEvents = 0, 500);
        }

        private void applyCameraUpdate(OsmCameraUpdate update) {
            if (update == null) {
                return;
            }
            markProgrammaticMove();
            if (update.bounds != null) {
                BoundingBox boundingBox = new BoundingBox(
                        update.bounds.maxLatitude,
                        update.bounds.maxLongitude,
                        update.bounds.minLatitude,
                        update.bounds.minLongitude);
                if (mapView.isLayoutOccurred()) {
                    mapView.zoomToBoundingBox(boundingBox, false, update.padding);
                } else {
                    mapView.post(() -> mapView.zoomToBoundingBox(boundingBox, false, update.padding));
                }
                return;
            }
            if (update.zoom >= 0) {
                mapView.getController().setZoom(update.zoom);
            }
            mapView.getController().setCenter(new GeoPoint(update.latitude, update.longitude));
        }

        private void destroy() {
            mapView.removeCallbacks(idleRunnable);
            cameraIdleListener = null;
            cameraMoveListener = null;
            cameraMoveStartedListener = null;
            markerClickListener = null;
            for (OsmMarker marker : new ArrayList<>(markers)) {
                marker.remove();
            }
            markers.clear();
        }

        @Override
        public void setMapType(int mapType) {
            ITileSource tileSource = mapType == MAP_TYPE_SATELLITE
                    ? TileSourceFactory.USGS_SAT
                    : TileSourceFactory.MAPNIK;
            mapView.setTileSource(tileSource);
        }

        @Override
        public void animateCamera(ICameraUpdate update) {
            moveCamera(update);
        }

        @Override
        public void animateCamera(ICameraUpdate update, ICancelableCallback callback) {
            moveCamera(update);
            if (callback != null) {
                callback.onFinish();
            }
        }

        @Override
        public void animateCamera(ICameraUpdate update, int duration, ICancelableCallback callback) {
            moveCamera(update);
            if (callback != null) {
                callback.onFinish();
            }
        }

        @Override
        public void moveCamera(ICameraUpdate update) {
            if (update instanceof OsmCameraUpdate) {
                applyCameraUpdate((OsmCameraUpdate) update);
            }
        }

        @Override
        public float getMaxZoomLevel() {
            return (float) mapView.getMaxZoomLevel();
        }

        @Override
        public float getMinZoomLevel() {
            return (float) mapView.getMinZoomLevel();
        }

        @Override
        public void setMyLocationEnabled(boolean enabled) {
            owner.setMyLocationEnabled(enabled);
        }

        @Override
        public IUISettings getUiSettings() {
            return new IUISettings() {
                @Override
                public void setZoomControlsEnabled(boolean enabled) {
                    mapView.setBuiltInZoomControls(enabled);
                }

                @Override
                public void setMyLocationButtonEnabled(boolean enabled) {
                    // tg supplies its own location button
                }

                @Override
                public void setCompassEnabled(boolean enabled) {
                    // tg supplies its own controls
                }
            };
        }

        @Override
        public void setOnCameraIdleListener(Runnable callback) {
            cameraIdleListener = callback;
        }

        @Override
        public void setOnCameraMoveStartedListener(OnCameraMoveStartedListener listener) {
            cameraMoveStartedListener = listener;
        }

        @Override
        public CameraPosition getCameraPosition() {
            org.osmdroid.api.IGeoPoint point = mapView.getMapCenter();
            return new CameraPosition(
                    new LatLng(point.getLatitude(), point.getLongitude()),
                    (float) mapView.getZoomLevelDouble());
        }

        @Override
        public void setOnMapLoadedCallback(Runnable callback) {
            if (callback != null) {
                mapView.post(callback);
            }
        }

        @Override
        public IProjection getProjection() {
            return latLng -> {
                Point point = new Point();
                mapView.getProjection().toPixels(new GeoPoint(latLng.latitude, latLng.longitude), point);
                return point;
            };
        }

        @Override
        public void setPadding(int left, int top, int right, int bottom) {
            mapView.setMapCenterOffset((left - right) / 2, (top - bottom) / 2);
        }

        @Override
        public void setMapStyle(IMapStyleOptions style) {
        }

        @Override
        public IMarker addMarker(IMarkerOptions markerOptions) {
            OsmMarkerOptions options = (OsmMarkerOptions) markerOptions;
            Marker marker = new Marker(mapView);
            if (options.position != null) {
                marker.setPosition(new GeoPoint(options.position.latitude, options.position.longitude));
            }
            if (options.iconBitmap != null) {
                marker.setIcon(new BitmapDrawable(mapView.getResources(), options.iconBitmap));
            } else if (options.iconResource != 0) {
                Drawable drawable = mapView.getContext().getDrawable(options.iconResource);
                if (drawable != null) {
                    marker.setIcon(drawable);
                }
            } else {
                marker.setDefaultIcon();
            }
            marker.setAnchor(options.anchorU, options.anchorV);
            marker.setFlat(options.flat);
            marker.setTitle(options.title);
            marker.setSnippet(options.snippet);

            OsmMarker result = new OsmMarker(this, marker);
            marker.setOnMarkerClickListener((clickedMarker, ignoredMapView) -> {
                return markerClickListener != null && markerClickListener.onClick(result);
            });
            markers.add(result);
            mapView.getOverlays().add(marker);
            mapView.invalidate();
            return result;
        }

        @Override
        public ICircle addCircle(ICircleOptions circleOptions) {
            OsmCircleOptions options = (OsmCircleOptions) circleOptions;
            OsmCircle circle = new OsmCircle(mapView, options);
            mapView.getOverlays().add(circle.polygon);
            mapView.invalidate();
            return circle;
        }

        @Override
        public void setOnMyLocationChangeListener(Consumer<Location> callback) {
            owner.setOnMyLocationChangeListener(callback);
        }

        @Override
        public void setOnMarkerClickListener(OnMarkerClickListener listener) {
            markerClickListener = listener;
        }

        @Override
        public void setOnCameraMoveListener(Runnable callback) {
            cameraMoveListener = callback;
        }
    }

    private static final class OsmMarker implements IMarker {
        private final OsmMapImpl owner;
        private final Marker marker;
        private Object tag;

        private OsmMarker(OsmMapImpl owner, Marker marker) {
            this.owner = owner;
            this.marker = marker;
        }

        @Override
        public Object getTag() {
            return tag;
        }

        @Override
        public void setTag(Object value) {
            tag = value;
        }

        @Override
        public LatLng getPosition() {
            GeoPoint point = marker.getPosition();
            return point == null ? new LatLng(0, 0) : new LatLng(point.getLatitude(), point.getLongitude());
        }

        @Override
        public void setPosition(LatLng latLng) {
            marker.setPosition(new GeoPoint(latLng.latitude, latLng.longitude));
            owner.mapView.invalidate();
        }

        @Override
        public void setRotation(int rotation) {
            marker.setRotation(rotation);
            owner.mapView.invalidate();
        }

        @Override
        public void setIcon(Bitmap bitmap) {
            marker.setIcon(new BitmapDrawable(owner.mapView.getResources(), bitmap));
            owner.mapView.invalidate();
        }

        @Override
        public void setIcon(int resId) {
            Drawable drawable = owner.mapView.getContext().getDrawable(resId);
            if (drawable != null) {
                marker.setIcon(drawable);
                owner.mapView.invalidate();
            }
        }

        @Override
        public void remove() {
            marker.closeInfoWindow();
            owner.mapView.getOverlays().remove(marker);
            owner.markers.remove(this);
            owner.mapView.invalidate();
        }
    }

    private static final class OsmCircle implements ICircle {
        private final MapView mapView;
        private final OsmCircleOptions options;
        private final Polygon polygon;

        private OsmCircle(MapView mapView, OsmCircleOptions options) {
            this.mapView = mapView;
            this.options = options;
            polygon = new Polygon(mapView);
            polygon.setFillColor(options.fillColor);
            polygon.setStrokeColor(options.strokeColor);
            polygon.setStrokeWidth(options.strokeWidth);
            if (options.strokePattern != null && !options.strokePattern.isEmpty()) {
                ArrayList<Float> intervals = new ArrayList<>();
                for (PatternItem item : options.strokePattern) {
                    if (item instanceof PatternItem.Dash) {
                        intervals.add((float) Math.max(1, ((PatternItem.Dash) item).length));
                    } else if (item instanceof PatternItem.Gap) {
                        intervals.add((float) Math.max(1, ((PatternItem.Gap) item).length));
                    }
                }
                if (intervals.size() >= 2) {
                    if ((intervals.size() & 1) != 0) {
                        intervals.addAll(new ArrayList<>(intervals));
                    }
                    float[] values = new float[intervals.size()];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = intervals.get(i);
                    }
                    polygon.getOutlinePaint().setPathEffect(new DashPathEffect(values, 0));
                }
            }
            updatePoints();
        }

        private void updatePoints() {
            LatLng center = options.center == null ? new LatLng(0, 0) : options.center;
            polygon.setPoints(Polygon.pointsAsCircle(
                    new GeoPoint(center.latitude, center.longitude), options.radius));
        }

        @Override
        public void setStrokeColor(int color) {
            options.strokeColor = color;
            polygon.setStrokeColor(color);
            mapView.invalidate();
        }

        @Override
        public void setFillColor(int color) {
            options.fillColor = color;
            polygon.setFillColor(color);
            mapView.invalidate();
        }

        @Override
        public void setRadius(double radius) {
            options.radius = Math.max(0, radius);
            updatePoints();
            mapView.invalidate();
        }

        @Override
        public double getRadius() {
            return options.radius;
        }

        @Override
        public void setCenter(LatLng latLng) {
            options.center = latLng;
            updatePoints();
            mapView.invalidate();
        }

        @Override
        public void remove() {
            mapView.getOverlays().remove(polygon);
            mapView.invalidate();
        }
    }
}
