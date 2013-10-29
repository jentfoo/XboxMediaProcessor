package com.jentfoo;

import java.io.File;

public class FileUtils {
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
      if (fileArray[i].getAbsolutePath().equals(searchFile.getAbsolutePath())) {
        return true;
      }
    }
    
    return false;
  }
}
