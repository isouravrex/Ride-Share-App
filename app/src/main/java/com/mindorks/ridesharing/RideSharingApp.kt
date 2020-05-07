package com.mindorks.ridesharing

import android.app.Application
import com.google.android.libraries.places.api.Places
import com.google.maps.GeoApiContext
import com.mindorks.ridesharing.simulator.Simulator

class RideSharingApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Places.initialize(applicationContext, "AIzaSyCSKa5MMrHRrxC9tleV8bvcuoloUqrRGqw");
        Simulator.geoApiContext = GeoApiContext.Builder()
            .apiKey("AIzaSyCSKa5MMrHRrxC9tleV8bvcuoloUqrRGqw")
            .build()
    }

}