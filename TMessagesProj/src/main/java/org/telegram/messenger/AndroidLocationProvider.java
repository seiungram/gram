package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import androidx.core.util.Consumer;

import java.util.IdentityHashMap;
import java.util.Map;

/** Location provider backed only by Android's platform LocationManager API. */
@SuppressLint("MissingPermission")
public class AndroidLocationProvider implements ILocationServiceProvider {
    private LocationManager locationManager;
    private final Map<ILocationListener, LocationListener> listeners = new IdentityHashMap<>();

    @Override
    public void init(Context context) {
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public ILocationRequest onCreateLocationRequest() {
        return new AndroidLocationRequest();
    }

    @Override
    public IMapApiClient onCreateLocationServicesAPI(
            Context context,
            IAPIConnectionCallbacks connectionCallbacks,
            IAPIOnConnectionFailedListener failedListener) {
        return new AndroidLocationApiClient(connectionCallbacks, failedListener);
    }

    @Override
    public boolean checkServices() {
        if (locationManager == null) {
            return false;
        }
        try {
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public void getLastLocation(Consumer<Location> callback) {
        if (locationManager == null || callback == null) {
            return;
        }
        Location location = null;
        try {
            String[] providers = {
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
            };
            for (String provider : providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    location = locationManager.getLastKnownLocation(provider);
                    if (location != null) {
                        break;
                    }
                }
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            // Location permission/provider availability is handled by LocationController.
        }
        callback.accept(location);
    }

    @Override
    public void requestLocationUpdates(ILocationRequest request, ILocationListener locationListener) {
        if (locationManager == null || locationListener == null) {
            return;
        }
        removeLocationUpdates(locationListener);

        AndroidLocationRequest locationRequest = request instanceof AndroidLocationRequest
                ? (AndroidLocationRequest) request
                : new AndroidLocationRequest();
        LocationListener platformListener = new PlatformLocationListener(locationListener);
        listeners.put(locationListener, platformListener);

        long interval = Math.max(1L, locationRequest.intervalMillis);
        try {
            for (String provider : getEnabledProviders(locationRequest)) {
                locationManager.requestLocationUpdates(
                        provider, interval, 0f, platformListener, Looper.getMainLooper());
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            // LocationController continues with its permission-aware platform fallback.
        }
    }

    @Override
    public void removeLocationUpdates(ILocationListener locationListener) {
        LocationListener platformListener = listeners.remove(locationListener);
        if (platformListener != null && locationManager != null) {
            try {
                locationManager.removeUpdates(platformListener);
            } catch (SecurityException ignored) {
                // Permission may have been revoked while the request was active.
            }
        }
    }

    @Override
    public void checkLocationSettings(ILocationRequest request, Consumer<Integer> callback) {
        if (callback != null) {
            callback.accept(checkServices()
                    ? STATUS_SUCCESS
                    : STATUS_SETTINGS_CHANGE_UNAVAILABLE);
        }
    }

    private String[] getEnabledProviders(AndroidLocationRequest request) {
        boolean gpsEnabled = false;
        boolean networkEnabled = false;
        try {
            gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (RuntimeException ignored) {
            // Return no providers and let requestLocationUpdates handle the error.
        }

        if (request.priority == PRIORITY_HIGH_ACCURACY) {
            return gpsEnabled ? new String[]{LocationManager.GPS_PROVIDER}
                    : networkEnabled ? new String[]{LocationManager.NETWORK_PROVIDER} : new String[0];
        }
        if (networkEnabled && gpsEnabled) {
            return new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER};
        }
        if (networkEnabled) {
            return new String[]{LocationManager.NETWORK_PROVIDER};
        }
        return gpsEnabled ? new String[]{LocationManager.GPS_PROVIDER} : new String[0];
    }

    private static final class AndroidLocationRequest implements ILocationRequest {
        private int priority = PRIORITY_BALANCED_POWER_ACCURACY;
        private long intervalMillis = 10_000L;

        @Override
        public void setPriority(int priority) {
            this.priority = priority;
        }

        @Override
        public void setInterval(long interval) {
            intervalMillis = interval;
        }

        @Override
        public void setFastestInterval(long interval) {
            // LocationManager has no separate fastest interval setting.
        }
    }

    private static final class PlatformLocationListener implements LocationListener {
        private final ILocationListener listener;

        private PlatformLocationListener(ILocationListener listener) {
            this.listener = listener;
        }

        @Override
        public void onLocationChanged(Location location) {
            listener.onLocationChanged(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    }

    private final class AndroidLocationApiClient implements IMapApiClient {
        private final IAPIConnectionCallbacks connectionCallbacks;
        private final IAPIOnConnectionFailedListener failedListener;

        private AndroidLocationApiClient(
                IAPIConnectionCallbacks connectionCallbacks,
                IAPIOnConnectionFailedListener failedListener) {
            this.connectionCallbacks = connectionCallbacks;
            this.failedListener = failedListener;
        }

        @Override
        public void connect() {
            if (checkServices()) {
                connectionCallbacks.onConnected(null);
            } else {
                failedListener.onConnectionFailed();
            }
        }

        @Override
        public void disconnect() {
        }
    }
}
