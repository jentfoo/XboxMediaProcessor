XboxMediaProcessor
==================
Small java program which uses mencoder or libav on Linux to convert videos to be playable on the Xbox.

This is a utility I use to make sure that videos are able to be played on the xbox.  I use a cron job to execute this program every hour, it then inspects one folder and looks for any videos which have not been converted for the xbox yet.  You can specify which converter to use, or it can automatically pick one depending on if mencoder or libav is installed.  It prefers libav, despite taking more processing time, the resulting videos are typically played better (smoother) on the xbox.

Mencoder converter: 
Mencoder converts the files to a .avi file with the xvid codec which is playable on the xbox.  If the video is already a .avi it will just direct copy it.  If it is anything else it will use mencoder to convert the file into a format known to work with the xbox.  This has the advantage to copying files which are likely already viewable for the xbox.

Libav converter:
The libav converter is much more advanced.  It inspects the audio codec and the video codec, as well as the container.  If all match what the xbox needs for h264 + ac3 it just copies the file.  Otherwise it copies or encodes the file as necessary to make it work for the xbox.

By running this on a cron job you can use this utility to constantly watch a directory to keep another directory up to date to be playable within the xbox.  Once you have watched the video you can move or delete it, and the updated directory file will be removed on the next run (thus keeping disk usage lowish).

I personally have cron set to run the included "updateScript" every hour to ensure that my most recent videos are always available for playback.
