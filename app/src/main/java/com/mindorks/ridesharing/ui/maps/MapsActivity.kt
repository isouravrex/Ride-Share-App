package com.mindorks.ridesharing.ui.maps

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.mindorks.ridesharing.R
import com.mindorks.ridesharing.data.network.NetworkService
import com.mindorks.ridesharing.utils.MapUtils
import com.mindorks.ridesharing.utils.PermissionUtils
import com.mindorks.ridesharing.utils.ViewUtils

class MapsActivity : AppCompatActivity(),MapsView, OnMapReadyCallback {
    companion object{
        private const val TAG = "MapsActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE=999
    }
    private lateinit var presenter: MapsPresenter
    private lateinit var googleMap: GoogleMap
    private var fusedLocationProviderClient:FusedLocationProviderClient?=null
    private lateinit var locationCallback: LocationCallback
    private var currentLatLng:LatLng?=null
    private val nearbyMarkerList = arrayListOf<Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        ViewUtils.enableTransparentStatusBar(window)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        presenter= MapsPresenter(NetworkService())
        presenter.onAttach(this)
    }

    private fun moveCamera(latLng: LatLng){
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))

    }

    private fun animateCamera(latLng:LatLng){
        val cameraPosition =CameraPosition.Builder().target(latLng).zoom(15.5f).build()
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))

    }
    private fun addCarMarkerAndGet(latLng: LatLng): Marker{
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUtils.getCarBitmap(this))
        return  googleMap.addMarker(MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor))
    }

    private fun enableMyLocationOnMap(){
        googleMap.setPadding(0,ViewUtils.dpToPx(48f),0,0)
        googleMap.isMyLocationEnabled =true

    }

    private fun setupLocationListener(){
        fusedLocationProviderClient= FusedLocationProviderClient(this)
        val locationRequest =LocationRequest().setInterval(2000).setFastestInterval(2000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)

    locationCallback= object : LocationCallback() {
        override fun onLocationResult(locationResult : LocationResult) {
            super.onLocationResult(locationResult)
            if(currentLatLng==null){
                for(location in locationResult.locations){
                    if(currentLatLng==null){
                        currentLatLng= LatLng(location.latitude,location.longitude)
                        enableMyLocationOnMap()
                        moveCamera(currentLatLng!!)
                        animateCamera(currentLatLng!!)
                        presenter.requestNearbyCabs(currentLatLng!!)

                    }

                }
            }
        }
    }
        fusedLocationProviderClient?.requestLocationUpdates(locationRequest,locationCallback,
            Looper.myLooper())

    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
    }

    override fun onStart() {
        super.onStart()
        when{
            PermissionUtils.isAccessFineLocationGranted(this) ->{
                when{
                    PermissionUtils.isLocationEnabled(this)->{
                        setupLocationListener()

                    }else->{
                    PermissionUtils.showGPSNotEnabledDialog(this)
                }
                }

            }else->{
            PermissionUtils.requestAccessFineLocationPermission(this,
                LOCATION_PERMISSION_REQUEST_CODE)
        }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            LOCATION_PERMISSION_REQUEST_CODE->{
                if(grantResults.isNotEmpty()&& grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    when{
                        PermissionUtils.isLocationEnabled(this)->{
                            setupLocationListener()

                        }else->{
                        PermissionUtils.showGPSNotEnabledDialog(this)
                    }
                    }
                }
                else{
                    Toast.makeText(this,"Location Permission not Granted",Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        presenter.onDetach()
        super.onDestroy()
    }

    override fun showNearbyCabs(latLngList: List<LatLng>) {
        nearbyMarkerList.clear()
        for(latLng in latLngList){
            val nearbyCarMarker = addCarMarkerAndGet(latLng)
            nearbyMarkerList.add((nearbyCarMarker))
        }

    }
}
