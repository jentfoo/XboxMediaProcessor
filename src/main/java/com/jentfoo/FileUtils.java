package com.jentfoo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.threadly.util.Clock;
import org.threadly.util.ExceptionUtils;

public class FileUtils {
  private static final int BUFFER_SIZE = 8192;
  private static final int FILE_SIZE_STABILITY_TIME_IN_MILLIS = 1000 * 10;
  
  public static File makeNewFile(File destFolder, File sourceFile, 
                                 String desiredExtension) {
    String newName = maybeReplaceExt(sourceFile.getName(), desiredExtension);
    
    return new File(destFolder, newName);
  }

  public static String maybeReplaceExt(String name, String desiredExtension) {
    if (! desiredExtension.startsWith(".")) {
      desiredExtension = "." + desiredExtension;
    }
    
    String extension = getExtension(name);
    if (extension.equalsIgnoreCase(desiredExtension)) {
      return name;
    } else {
      int index = name.lastIndexOf('.');
      if (index > 0) {
        return name.substring(0, index) + desiredExtension;
      } else {
        return name + desiredExtension;
      }
    }
  }
  
  public static String getExtension(String name) {
    int index = name.lastIndexOf('.');
    if (index > 0) {
      return name.substring(index);
    } else {
      return "";
    }
  }

  public static boolean fileInArray(File[] fileArray, File searchFile) {
    for (int i = 0; i < fileArray.length; i++) {
      try {
        if (fileArray[i].getCanonicalPath().equals(searchFile.getCanonicalPath())) {
          return true;
        }
      } catch (IOException e) {
        ExceptionUtils.handleException(e);
        
        if (fileArray[i].getAbsolutePath().equals(searchFile.getAbsolutePath())) {
          return true;
        }
      }
    }
    
    return false;
  }
  
  public static boolean sizeStable(File file, 
                                   long originalSize, 
                                   long sizeCaptureTime) {
    long sizeAtStart = file.length();
    if (sizeAtStart != originalSize) {
      return sizeStable(file, sizeAtStart, Clock.lastKnownTimeMillis());
    }
    
    long elapsedTime = Clock.lastKnownTimeMillis() - sizeCaptureTime;
    if (elapsedTime < FILE_SIZE_STABILITY_TIME_IN_MILLIS - 10) {  // only sleep if we need to wait at least 10ms
      try {
        Thread.sleep(FILE_SIZE_STABILITY_TIME_IN_MILLIS - elapsedTime);
      } catch (InterruptedException e1) {
        Thread.currentThread().interrupt();
      }
    }
    
    long newSize = file.length();
    if (newSize != originalSize) {
      System.out.println("Filesize changed for file: " + file + 
                           "..." + originalSize + "/" + newSize);
      
      return false;
    } else {
      return true;
    }
  }

  public static void copyFile(File sourceFile, 
                              File destFile) throws IOException {
    InputStream in = null;
    OutputStream out = null;
    try {
      in = new FileInputStream(sourceFile);
      out = new FileOutputStream(destFile);

      byte[] buf = new byte[BUFFER_SIZE];
      int len;
      while ((len = in.read(buf)) > 0){
        out.write(buf, 0, len);
      }
    } finally {
      try {
        if (in != null) {
          in.close();
        }
      } finally {
        if (out != null) {
          out.close();
        }
      }
    }
  }
}
