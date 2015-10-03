# SimplyTimelapse
A timelapse Android application for use with Sony Camera Remote API

This is a simple Android app building on top of the Sony Camera Remote API tutorial. The goal is to take pictures at fixed interval that can later be combined in a movie.

If you interested in using the app and not developing it please navigate to the Google Play application listing

https://play.google.com/store/apps/details?id=com.trudovak.simplytimelapse&hl=en

You may also be interested in using Sony camera application for timelapses as that does not require a phone or tablet in addition ot the camera. Sony's camera app also provides options to correct gradually exposure when lighting conditions may vary.

https://www.playmemoriescameraapps.com/portal/usbdetail.php?eid=IS9104-NPIA09014_00-000003

#Next steps

The following items seem to be the main pain points to address in the apps next versions:

* Improve the experience of connecting to the camera
* Assure that the phone does nto fall into  power saving mode while timelapse is being shot
* Add better icon
* Switch to Holo theme


#Some notes on code of the app

All of the app UI is now put into single activity that interacts with backend services. As the app maintains a complex state that should be updated from the backend services and displayed in UI the state is stored in the applicaiton object which is concurrently accessed by UI and backend. Events between the UI and backend propagate through Intents. 

I have removed the use of ad-hoc threads and thread pools that is prevelant in the sample code form Sony camera remote SDK.

