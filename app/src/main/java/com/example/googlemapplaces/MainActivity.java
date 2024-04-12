package com.example.googlemapplaces;

import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private EditText editTextOrigin;
    private EditText editTextDestination;
    private Button buttonFindRoute;
    private GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editTextOrigin = findViewById(R.id.editTextOrigin);
        editTextDestination = findViewById(R.id.editTextDestination);
        buttonFindRoute = findViewById(R.id.buttonFindRoute);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        mapFragment.getMapAsync(this);

        buttonFindRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findRoute();
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
    }

    private void findRoute() {
        String origin = editTextOrigin.getText().toString().trim();
        String destination = editTextDestination.getText().toString().trim();

        // Определить начальное и конечное местоположение на карте
        LatLng originLatLng = getLocationFromAddress(origin);
        LatLng destinationLatLng = getLocationFromAddress(destination);

        if (originLatLng != null && destinationLatLng != null) {
            // Установить метки на карте
            mMap.addMarker(new MarkerOptions().position(originLatLng).title("Origin"));
            mMap.addMarker(new MarkerOptions().position(destinationLatLng).title("Destination"));

            // Создать границы, включающие начальное и конечное местоположение
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            builder.include(originLatLng);
            builder.include(destinationLatLng);
            LatLngBounds bounds = builder.build();

            // Приблизить к границам
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));

            // Построить маршрут между начальным и конечным местоположениями
            buildRoute(originLatLng, destinationLatLng);
        }
    }

    private LatLng getLocationFromAddress(String strAddress) {
        Geocoder geocoder = new Geocoder(this);
        List<Address> addresses;
        LatLng location = null;

        try {
            addresses = geocoder.getFromLocationName(strAddress, 1);
            if (addresses != null && addresses.size() > 0) {
                Address address = addresses.get(0);
                location = new LatLng(address.getLatitude(), address.getLongitude());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return location;
    }

    private void buildRoute(LatLng origin, LatLng destination) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://maps.googleapis.com/maps/api/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        DirectionsApiService service = retrofit.create(DirectionsApiService.class);

        Call<DirectionsResponse> call = service.getDirections(getLatLngString(origin), getLatLngString(destination), getString(R.string.my_map_directions_api_key));
        call.enqueue(new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                if (response.isSuccessful()) {
                    DirectionsResponse directionsResponse = response.body();
                    if (directionsResponse != null && directionsResponse.getRoutes() != null && !directionsResponse.getRoutes().isEmpty()) {
                        List<LatLng> points = decodePoly(directionsResponse.getRoutes().get(0).getOverviewPolyline().getPoints());
                        PolylineOptions options = new PolylineOptions().addAll(points).color(Color.BLUE).width(5);
                        mMap.addPolyline(options);
                    }
                } else {
                    // Handle error
                }
            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable t) {
                // Handle error
            }
        });
    }

    private String getLatLngString(LatLng latLng) {
        return latLng.latitude + "," + latLng.longitude;
    }

    private List<LatLng> decodePoly(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)), (((double) lng / 1E5)));
            poly.add(p);
        }

        return poly;
    }
}
