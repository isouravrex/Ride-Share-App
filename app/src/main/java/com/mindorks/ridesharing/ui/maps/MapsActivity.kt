package com.mindorks.ridesharing.ui.maps

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.mindorks.ridesharing.R
import com.mindorks.ridesharing.data.network.NetworkService
import com.mindorks.ridesharing.utils.AnimationUtils
import com.mindorks.ridesharing.utils.MapUtils
import com.mindorks.ridesharing.utils.PermissionUtils
import com.mindorks.ridesharing.utils.ViewUtils
import kotlinx.android.synthetic.main.activity_maps.*

class MapsActivity : AppCompatActivity(),MapsView, OnMapReadyCallback {
    companion object{
        private const val TAG = "MapsActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE=999
        private const val PICKUP_REQUEST_CODE=1
        private const val DROP_REQUEST_CODE=2

    }
    private lateinit var presenter: MapsPresenter
    private lateinit var googleMap: GoogleMap
    private var fusedLocationProviderClient:FusedLocationProviderClient?=null
    private lateinit var locationCallback: LocationCallback
    private var currentLatLng:LatLng?=null
    private var pickUpLatLng:LatLng?=null
    private var dropLatLng:LatLng?=null
    private var grayPolyLine :Polyline?= null
    private var blackPolyLine :Polyline?= null
    private val nearbyMarkerList = arrayListOf<Marker>()
    private var originMarker: Marker?=null
    private var destinationMarker: Marker?=null
    private var movingCabMarker: Marker?=null
    private var previousLatLngFromServer: LatLng?=null
    private var currentLatLngFromServer: LatLng?=null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        ViewUtils.enableTransparentStatusBar(window)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        presenter= MapsPresenter(NetworkService())
        presenter.onAttach(this)
        setupClickListener()
    }

    private fun setupClickListener() {
        pickUpTextView.setOnClickListener {
             launchLocationAutoCompleteActivity(PICKUP_REQUEST_CODE)
        }
        dropTextView.setOnClickListener {
            launchLocationAutoCompleteActivity(DROP_REQUEST_CODE)
        }
        requestCabButton.setOnClickListener {
            statusTextView.visibility=View.VISIBLE
            statusTextView.text=getString(R.string.requesting_your_cab)
            requestCabButton.isEnabled=false
            pickUpTextView.isEnabled=false
            dropTextView.isEnabled=false
            presenter.requestCab(pickUpLatLng!!,dropLatLng!!)
        }
        nextRideButton.setOnClickListener {
            reset()
        }
    }

    private fun launchLocationAutoCompleteActivity(requestCode: Int){
        val fields:List<Place.Field> = listOf(Place.Field.ID,Place.Field.NAME,Place.Field.LAT_LNG)
        val intent =Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY,fields).build(this)
        startActivityForResult(intent,requestCode)

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

    private fun addOriginDestinationMarkerAndGet(latLng: LatLng): Marker{
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUtils.getDestinationBitmap())
        return  googleMap.addMarker(MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor))
    }

    private fun setCurrentLocationAsPickUp(){
        pickUpLatLng=currentLatLng
        pickUpTextView.text=getString(R.string.current_location)
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
                        setCurrentLocationAsPickUp()
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

    private fun checkAndShowRequestButton(){
        if(pickUpLatLng!=null&&dropLatLng!=null){
            requestCabButton.visibility=View.VISIBLE
            requestCabButton.isEnabled=true
        }
    }

    private fun reset(){
        statusTextView.visibility=View.GONE
        nextRideButton.visibility=View.GONE
        nearbyMarkerList.forEach {
            it.remove()
        }
        nearbyMarkerList.clear()
        currentLatLngFromServer=null
        previousLatLngFromServer=null
        if(currentLatLng != null){
            moveCamera(currentLatLng!!)
            animateCamera(currentLatLng!!)
            setCurrentLocationAsPickUp()
            presenter.requestNearbyCabs(currentLatLng!!)
        }else
        {
            pickUpTextView.text=""
        }
        pickUpTextView.isEnabled=true
        dropTextView.isEnabled=true
        dropTextView.text=null
        movingCabMarker?.remove()
        grayPolyLine?.remove()
        blackPolyLine?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
        dropLatLng=null
        grayPolyLine=null
        blackPolyLine=null
        originMarker=null
        destinationMarker=null
        movingCabMarker=null
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode== PICKUP_REQUEST_CODE||requestCode== DROP_REQUEST_CODE){
            when(resultCode){
                Activity.RESULT_OK->{
                    val place = Autocomplete.getPlaceFromIntent(data!!)
                    when(requestCode){
                            PICKUP_REQUEST_CODE->{
                                pickUpTextView.text=place.name
                                pickUpLatLng=place.latLng
                                checkAndShowRequestButton()
                            }
                            DROP_REQUEST_CODE->{
                                dropTextView.text=place.name
                                dropLatLng=place.latLng
                                checkAndShowRequestButton()
                        }
                    }
                }
                AutocompleteActivity.RESULT_ERROR->{
                        val status: Status = Autocomplete.getStatusFromIntent(data!!)
                    Log.d(TAG,status.statusMessage!!)
                }
                Activity.RESULT_CANCELED->{

                }
            }
        }
    }

    override fun onDestroy() {
        presenter.onDetach()
        fusedLocationProviderClient?.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun showNearbyCabs(latLngList: List<LatLng>) {
        nearbyMarkerList.clear()
        for(latLng in latLngList){
            val nearbyCarMarker = addCarMarkerAndGet(latLng)
            nearbyMarkerList.add((nearbyCarMarker))
        }

    }

    override fun informCabBooked() {
        nearbyMarkerList.forEach {
            it.remove()
        }
        nearbyMarkerList.clear()
        requestCabButton.visibility=View.GONE
        statusTextView.text=getString(R.string.your_cab_is_booked)
    }

    override fun showPath(latLngList: List<LatLng>) {
        val builder = LatLngBounds.builder()
        for(latLng in latLngList){
            builder.include(latLng)
        }
        val bounds = builder.build()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,2))
        val polyLineOptions =PolylineOptions()
        polyLineOptions.color(Color.GRAY)
        polyLineOptions.width(5f)
        polyLineOptions.addAll(latLngList)
        grayPolyLine = googleMap.addPolyline(polyLineOptions)

        val blackPolyLineOptions =PolylineOptions()
        blackPolyLineOptions.color(Color.GRAY)
        blackPolyLineOptions.width(5f)
        blackPolyLine = googleMap.addPolyline(blackPolyLineOptions)

        originMarker=addOriginDestinationMarkerAndGet(latLngList[0])
        originMarker?.setAnchor(0.5f,0.5f)
        destinationMarker=addOriginDestinationMarkerAndGet(latLngList[latLngList.size-1])
        destinationMarker?.setAnchor(0.5f,0.5f)

        val polyLineAnimator =AnimationUtils.polyLineAnimator()
        polyLineAnimator.addUpdateListener { valueAnimator ->
            val percentValue=(valueAnimator.animatedValue as Int)
            val index = (grayPolyLine?.points!!.size) * (percentValue / 100.0f).toInt()
            blackPolyLine?.points = grayPolyLine?.points!!.subList(0,index)
        }
        polyLineAnimator.start()
    }

    override fun updateCabLocation(latLng: LatLng) {
        if(movingCabMarker==null){
            movingCabMarker=addCarMarkerAndGet(latLng)
        }
        if(previousLatLngFromServer==null){
              currentLatLngFromServer=latLng
            previousLatLngFromServer=currentLatLngFromServer
            movingCabMarker?.position=currentLatLngFromServer
            movingCabMarker?.setAnchor(0.5f,0.5f)
            animateCamera(currentLatLngFromServer!!)
        }else{
            previousLatLngFromServer=currentLatLngFromServer
            currentLatLngFromServer=latLng
            val valueAnimator=AnimationUtils.cabAnimator()
            valueAnimator.addUpdateListener { va ->
                if(currentLatLngFromServer!=null&&previousLatLngFromServer!=null) {
                    val multiplier = va.animatedFraction
                    val nextLocation =LatLng(
                        multiplier*currentLatLngFromServer!!.latitude+(1-multiplier)*previousLatLngFromServer!!.latitude,
                        multiplier*currentLatLngFromServer!!.longitude+(1-multiplier)*previousLatLngFromServer!!.longitude
                        )
                    movingCabMarker?.position=nextLocation
                    val rotation=MapUtils.getRotation(previousLatLngFromServer!!,nextLocation)
                    if(!rotation.isNaN()){
                        movingCabMarker?.rotation=rotation
                    }
                    movingCabMarker?.setAnchor(0.5f,0.5f)
                    animateCamera(nextLocation)
                }
            }
            valueAnimator.start()
        }
    }

    override fun informCabIsArriving() {
        statusTextView.text="Your Cab is Arriving"

    }

    override fun informCabArrived() {
        statusTextView.text="Your Cab Has Arrived"
        grayPolyLine?.remove()
        blackPolyLine?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
    }

    override fun informTripStart() {
        statusTextView.text=getString(R.string.you_are_on_a_trip)
        previousLatLngFromServer=null
    }

    override fun informTripEnd() {
        statusTextView.text=getString(R.string.trip_ended)
        nextRideButton.visibility=View.VISIBLE
        grayPolyLine?.remove()
        blackPolyLine?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
    }

    override fun showRoutesNotAvailableError() {
        val error = getString(R.string.routes_not_available_choose_different_locations)
        Toast.makeText(this,error,Toast.LENGTH_LONG).show()
        reset()
    }

    override fun showDirectionApiFailedError(error: String) {
        Toast.makeText(this,error,Toast.LENGTH_LONG).show()
        reset()
    }
}
