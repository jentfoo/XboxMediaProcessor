XboxMediaProcessor
==================
Small java program which uses mencoder on Linux to convert videos to be playable on the Xbox.

This is a utility I use to make sure that videos are able to be played on the xbox.  I use a cron job to execute this program every hour, it then inspects one folder and looks for any videos which have not been converted for the xbox yet.  If the video is already a .avi it will just direct copy it.  If it is anything else it will use mencoder to convert the file into a format known to work with the xbox.

By running this on a cron job you can use this utility to constantly watch a directory to keep another directory up to date to be playable within the xbox.  Once you have watched the video you can move or delete it, and the updated directory file will be removed on the next run (thus keeping disk usage lowish).
